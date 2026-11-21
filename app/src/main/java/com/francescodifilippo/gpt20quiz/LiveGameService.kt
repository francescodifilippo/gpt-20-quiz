package com.francescodifilippo.gpt20quiz

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GameExchange(val question: String, val answer: String)

data class AiTurn(val kind: String, val text: String)

/**
 * Direct SIWC inference; no API key, remote app server or scripted answer.
 * This client is only instantiated when the user's account granted plan access.
 */
class LiveGameService(private val auth: SiwcAuthManager) {
    suspend fun availableModels(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val accessToken = try {
            auth.accessToken()
        } catch (e: Exception) {
            throw ModelCatalogFailure(ModelCatalogStage.ACCESS_TOKEN, cause = e)
        }
        val connection = try {
            (URL("https://api.openai.com/v1/models").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 35_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Authorization", "Bearer " + accessToken)
                    instanceFollowRedirects = false
                }
        } catch (e: Exception) {
            throw ModelCatalogFailure(ModelCatalogStage.CONNECTION, cause = e)
        }
        try {
            val status = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                throw ModelCatalogFailure(ModelCatalogStage.CONNECTION, cause = e)
            }
            if (status !in 200..299) {
                // Non-success bodies are bounded and only a safe provider code
                // is extracted; never display raw HTTP responses.
                val code = try {
                    val reader = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)
                    reader?.use {
                        val buffer = CharArray(4096)
                        val size = it.read(buffer)
                        if (size > 0) {
                            val root = JSONObject(String(buffer, 0, size))
                            val err = root.optJSONObject("error")
                            err?.optString("code")?.takeIf(String::isNotBlank)
                                ?: err?.optString("type")
                        } else null
                    }
                } catch (_: Exception) { null }
                throw ModelCatalogFailure(
                    ModelCatalogStage.HTTP,
                    httpStatus = status,
                    providerErrorCode = code
                )
            }
            // Stream the full root object. Unknown fields (including lengthy
            // agent metadata) are skipped; no full response body is built or
            // persisted in memory, logs or UI. The earlier 512 KiB truncation
            // produced a false MODELS_BAD_RESPONSE for large valid JSON.
            val contentCategory =
                ModelCatalogPayload.fingerprint("", connection.contentType).contentType
            val requestId = connection.getHeaderField("x-request-id")
            try {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                    ModelCatalogStreamParser.parse(it)
                }
            } catch (e: ModelCatalogStreamParser.Failure) {
                val stage = when (e.problem) {
                    ModelCatalogStreamParser.Problem.MALFORMED_JSON ->
                        ModelCatalogStage.JSON_RESPONSE
                    ModelCatalogStreamParser.Problem.MISSING_MODELS ->
                        ModelCatalogStage.MODEL_SELECTION
                    ModelCatalogStreamParser.Problem.TOO_LARGE ->
                        ModelCatalogStage.TOO_LARGE
                }
                throw ModelCatalogFailure(
                    stage = stage,
                    httpStatus = status,
                    contentTypeCategory = contentCategory,
                    bodyCategory = e.bodyCategory,
                    responseCharacters = e.charactersRead,
                    requestId = requestId,
                    cause = e
                )
            } catch (e: java.io.IOException) {
                throw ModelCatalogFailure(ModelCatalogStage.CONNECTION, cause = e)
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun completion(
        model: String,
        instructions: String,
        state: JSONObject,
        effort: ReasoningEffort
    ): JSONObject = withContext(Dispatchers.IO) {
        val request = JSONObject()
            .put("model", model)
            .put("store", false)
            .put("stream", true)
            .put("instructions", instructions)
            .put("input", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("content", state.toString())
            ))
        // This is a user-selected request parameter, never a prompt hint.
        // AUTO deliberately omits the field and lets the model choose.
        effort.requestedApiValue()?.let {
            request.put("reasoning", JSONObject().put("effort", it))
        }
        val accessToken = auth.accessToken()
        val connection = (URL("https://api.openai.com/v1/responses").openConnection()
            as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 90_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setRequestProperty("Authorization", "Bearer " + accessToken)
        try {
            connection.outputStream.use {
                it.write(request.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Read a bounded error response; extract code and parameter,
                // never the provider's raw message or any request credentials.
                val responseText = connection.errorStream?.bufferedReader()?.use {
                    it.readText().take(5000)
                }.orEmpty()
                val details = runCatching {
                    JSONObject(responseText).optJSONObject("error")
                }.getOrNull()
                throw SiwcInferenceFailure(
                    statusCode = status,
                    providerCode = details?.optString("code"),
                    providerParameter = details?.optString("param"),
                    requestedEffort = effort
                )
            }
            val output = StringBuilder()
            var completed = false
            // SSE: events may span multiple data: lines; process only on blank separator.
            val dataLines = mutableListOf<String>()
            fun consumeEvent() {
                if (dataLines.isEmpty()) return
                val payload = dataLines.joinToString("\n")
                dataLines.clear()
                if (payload == "[DONE]") return
                val event = runCatching { JSONObject(payload) }.getOrNull() ?: return
                when (event.optString("type")) {
                    "response.output_text.delta" -> output.append(event.optString("delta"))
                    "response.completed" -> completed = true
                    "response.failed", "response.incomplete", "error" -> {
                        val problem = event.optJSONObject("response")
                            ?.optJSONObject("error")?.optString("code")
                            ?: event.optJSONObject("error")?.optString("code")
                            ?: "stream_incomplete"
                        throw IllegalStateException("ChatGPT response failed: " + problem.take(100))
                    }
                }
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) consumeEvent()
                    else if (line.startsWith("data:")) dataLines.add(line.substring(5).trimStart())
                    // Ignore SSE comments and other event metadata.
                }
            }
            consumeEvent()
            check(completed) { "Response stream interrupted before completion" }
            check(output.isNotBlank()) { "The model did not return a text answer" }
            try {
                JSONObject(output.toString().trim())
            } catch (_: Exception) {
                throw IllegalStateException("Model response did not match the JSON game contract")
            }
        } finally { connection.disconnect() }
    }

    /**
     * The human keeps a secret and answers. The model asks, then guesses.
     * The 20th action MUST be a guess.
     */
    suspend fun nextAiQuestion(
        model: String,
        session: GameSessionState,
        history: List<GameExchange>,
        effort: ReasoningEffort
    ): AiTurn {
        check(session.role == PlayerRole.RESPONDER)
        val instructions = TwentyQuestionsStrategy.aiGuesser +
            "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val historyArray = JSONArray()
        history.forEach {
            historyArray.put(JSONObject().put("question", it.question)
                .put("answer", it.answer))
        }
        val state = JSONObject().put("history", historyArray)
            .put("questionNumber", session.questionNumber)
            .put("questionsRemaining", session.questionsRemaining)
            .put("language", session.language.effectiveLanguageTag)
        val result = completion(model, instructions, state, effort)
        val kind = result.optString("kind")
        val text = result.optString("text").trim()
        check(kind == "question" || kind == "guess")
        check(text.isNotBlank() && text.length <= 400)
        check(session.questionsRemaining != 1 || kind == "guess") {
            "Model failed to provide its mandatory final guess"
        }
        return AiTurn(kind, text)
    }

    /**
     * The AI secretly picks an answer. It is held only in local app memory
     * and is sent back with the full history for consistent responses.
     */
    suspend fun selectSecret(
        model: String, session: GameSessionState, effort: ReasoningEffort
    ): String {
        check(session.role == PlayerRole.GUESSER)
        val instructions = TwentyQuestionsStrategy.chooseAiSecret +
            "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val result = completion(model, instructions, JSONObject()
            .put("action", "choose_secret")
            .put("language", session.language.effectiveLanguageTag), effort)
        val secret = result.optString("secret").trim()
        check(secret.isNotBlank() && secret.length < 180)
        return secret
    }

    suspend fun answerHumanQuestion(
        model: String,
        session: GameSessionState,
        secret: String,
        history: List<GameExchange>,
        question: String,
        effort: ReasoningEffort
    ): String {
        check(session.role == PlayerRole.GUESSER)
        val instructions = TwentyQuestionsStrategy.answerHumanQuestion +
            "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val previous = JSONArray()
        history.forEach {
            previous.put(JSONObject().put("question", it.question).put("answer", it.answer))
        }
        val result = completion(model, instructions, JSONObject()
            .put("secret", secret)
            .put("history", previous)
            .put("question", question)
            .put("language", session.language.effectiveLanguageTag), effort)
        val answer = result.optString("answer")
        check(answer in setOf("yes", "no", "probably", "probably_not", "unknown")) {
            "Unexpected answer from the model"
        }
        return answer
    }

    suspend fun verifyHumanGuess(
        model: String,
        session: GameSessionState,
        secret: String,
        guess: String,
        effort: ReasoningEffort
    ): Boolean {
        check(session.role == PlayerRole.GUESSER)
        val instructions = TwentyQuestionsStrategy.verifyHumanGuess +
            "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val result = completion(model, instructions, JSONObject()
            .put("secret", secret).put("guess", guess), effort)
        check(result.has("correct") && result.get("correct") is Boolean)
        return result.getBoolean("correct")
    }
}
