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
        val accessToken = auth.accessToken()
        val connection = (URL("https://api.openai.com/v1/models").openConnection() as HttpURLConnection)
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Authorization", "Bearer " + accessToken)
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText().take(200_000) } ?: ""
            if (status !in 200..299) {
                throw IllegalStateException("Model catalog unavailable (HTTP " + status + ")")
            }
            val root = JSONObject(body)
            val records = root.optJSONArray("models")
                ?: throw IllegalStateException("No SIWC model catalog returned")
            buildList {
                for (i in 0 until records.length()) {
                    val model = records.optJSONObject(i) ?: continue
                    if (model.optString("visibility") != "list") continue
                    val slug = model.optString("slug").trim()
                    // Model names come from the account catalog, not freeform
                    // user text. Limit UI labels and never render entire server
                    // responses, JSON or unexpected multiline content.
                    if (slug.isBlank() || slug.length > 120 ||
                        slug.any { it.isWhitespace() || it.isISOControl() }) continue
                    val label = model.optString("display_name", slug)
                        .replace(Regex("[\\p{Cntrl}]+"), " ").trim()
                        .take(72)
                        .ifBlank { slug }
                    add(Pair(slug, label))
                }
            }
        } finally { connection.disconnect() }
    }

    private suspend fun completion(
        model: String,
        instructions: String,
        state: JSONObject
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
                val message = connection.errorStream?.bufferedReader()?.use {
                    it.readText().take(5000)
                } ?: ""
                val code = runCatching {
                    JSONObject(message).optJSONObject("error")?.optString("code")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "ChatGPT request failed (HTTP " + status + ")" +
                        if (code.isBlank()) "" else ": " + code.take(120)
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
        history: List<GameExchange>
    ): AiTurn {
        check(session.role == PlayerRole.RESPONDER)
        val instructions = """
            You are playing Twenty Questions as the GUESSER. The human has a secret
            object, living thing, person, place, fictional character or concept.
            Deduce the secret from the full answer history. Never pretend to know
            the secret. Ask a single high-information YES/NO question per turn,
            with no duplicate or redundant questions. Handle uncertainty in answers.
            On the last turn you MUST issue a final guess. You may guess earlier.
            Only return a JSON object: {"kind":"question","text":"..."} or
            {"kind":"guess","text":"..."}. Nothing else. Text must be in the
            selected language. A guess must identify one concrete candidate.
        """.trimIndent() + "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val historyArray = JSONArray()
        history.forEach {
            historyArray.put(JSONObject().put("question", it.question)
                .put("answer", it.answer))
        }
        val state = JSONObject().put("history", historyArray)
            .put("questionNumber", session.questionNumber)
            .put("questionsRemaining", session.questionsRemaining)
            .put("language", session.language.effectiveLanguageTag)
        val result = completion(model, instructions, state)
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
    suspend fun selectSecret(model: String, session: GameSessionState): String {
        check(session.role == PlayerRole.GUESSER)
        val instructions = """
            Choose one widely recognizable and specific secret for a Twenty
            Questions guessing game. Avoid abstract impossibilities, aliases,
            extremely obscure entries or ambiguous group names.
            Return only JSON: {"secret":"..."}.
            The secret must be the canonical name, suitable for the specified
            language. No hints, no explanations, no surrounding text.
        """.trimIndent() + "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val result = completion(model, instructions, JSONObject()
            .put("action", "choose_secret")
            .put("language", session.language.effectiveLanguageTag))
        val secret = result.optString("secret").trim()
        check(secret.isNotBlank() && secret.length < 180)
        return secret
    }

    suspend fun answerHumanQuestion(
        model: String,
        session: GameSessionState,
        secret: String,
        history: List<GameExchange>,
        question: String
    ): String {
        check(session.role == PlayerRole.GUESSER)
        val instructions = """
            You are the ANSWERER in Twenty Questions. Your hidden secret is
            provided in the structured input. Never reveal it before the end.
            Answer the human's latest yes/no question truthfully based on that
            secret and prior context. Choose exactly one of:
            yes, no, probably, probably_not, unknown.
            Return only JSON, e.g. {"answer":"yes"}.
            Do not add commentary or hints.
        """.trimIndent() + "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val previous = JSONArray()
        history.forEach {
            previous.put(JSONObject().put("question", it.question).put("answer", it.answer))
        }
        val result = completion(model, instructions, JSONObject()
            .put("secret", secret)
            .put("history", previous)
            .put("question", question)
            .put("language", session.language.effectiveLanguageTag))
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
        guess: String
    ): Boolean {
        check(session.role == PlayerRole.GUESSER)
        val instructions = """
            Judge whether the human's final guess semantically identifies
            the same specific secret. Accept obvious aliases and spelling
            variants in the chosen language, but reject broader categories
            or different entities. Only return JSON {"correct":true}
            or {"correct":false}. Do not reveal the secret.
        """.trimIndent() + "\n\n" + GameLanguagePrompt.initialInstructions(session)
        val result = completion(model, instructions, JSONObject()
            .put("secret", secret).put("guess", guess))
        check(result.has("correct") && result.get("correct") is Boolean)
        return result.getBoolean("correct")
    }
}
