package com.francescodifilippo.gpt20quiz

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

/**
 * Process SIWC GET /v1/models incrementally: do NOT turn the entire HTTP body
 * into a String/JSONObject. The provider can return a large root JSON object
 * with fields unrelated to the account-visible model list.
 *
 * This component only keeps the visible model slugs and display labels.
 * All other values, including possible lengthy internal metadata, are skipped
 * without showing or persisting their content.
 */
object ModelCatalogStreamParser {
    const val MAX_CHARS = 16 * 1024 * 1024
    const val MAX_DISPLAY_MODELS = 512

    enum class Problem { MALFORMED_JSON, MISSING_MODELS, TOO_LARGE }

    class Failure(
        val problem: Problem,
        val charactersRead: Int,
        val bodyCategory: String,
        cause: Throwable? = null
    ) : Exception("Model catalog stream parse failure: " + problem, cause)

    private class ObservingBoundedReader(private val input: Reader) : Reader() {
        var seen: Int = 0
            private set
        private var first: Char? = null

        val bodyCategory: String
            get() = when (first) {
                null -> "empty"
                '{' -> "json-object"
                '[' -> "json-array"
                '<' -> "html"
                '"' -> "json-primitive"
                else -> "unknown"
            }

        override fun read(cbuf: CharArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= cbuf.size - length)
            if (length == 0) return 0
            // Read one extra character beyond the cap to reliably detect a
            // too-large response without ever accumulating its content.
            val budget = MAX_CHARS + 1 - seen
            if (budget <= 0) {
                throw Failure(Problem.TOO_LARGE, seen, bodyCategory)
            }
            val count = input.read(cbuf, offset, minOf(length, budget))
            if (count < 0) return -1
            for (i in offset until offset + count) {
                if (first == null) {
                    val char = cbuf[i]
                    if (!char.isWhitespace() && char != '\uFEFF' && char != '\u200B') {
                        first = char
                    }
                }
            }
            seen += count
            if (seen > MAX_CHARS) throw Failure(Problem.TOO_LARGE, seen, bodyCategory)
            return count
        }

        override fun close() = input.close()
    }

    fun parse(source: Reader): List<Pair<String, String>> {
        val observing = ObservingBoundedReader(source)
        var discoveredModels = false
        val visible = ArrayList<Pair<String, String>>()
        try {
            JsonReader(observing).use { json ->
                json.isLenient = false
                if (json.peek() != JsonToken.BEGIN_OBJECT) {
                    throw Failure(Problem.MALFORMED_JSON, observing.seen, observing.bodyCategory)
                }
                json.beginObject()
                while (json.hasNext()) {
                    val name = json.nextName()
                    if (name == "models" && json.peek() == JsonToken.BEGIN_ARRAY) {
                        if (discoveredModels) throw Failure(
                            Problem.MALFORMED_JSON, observing.seen, observing.bodyCategory
                        )
                        discoveredModels = true
                        json.beginArray()
                        while (json.hasNext()) {
                            if (json.peek() == JsonToken.BEGIN_OBJECT) {
                                parseModel(json)?.let { model ->
                                    if (visible.size < MAX_DISPLAY_MODELS) visible.add(model)
                                }
                            } else {
                                json.skipValue()
                            }
                        }
                        json.endArray()
                    } else {
                        json.skipValue()
                    }
                }
                json.endObject()
                if (json.peek() != JsonToken.END_DOCUMENT) {
                    throw Failure(Problem.MALFORMED_JSON, observing.seen, observing.bodyCategory)
                }
            }
            if (!discoveredModels) throw Failure(
                Problem.MISSING_MODELS, observing.seen, observing.bodyCategory
            )
            return visible
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            // The underlying parse exception may include response excerpts.
            // Keep it as a private cause, never render e.message or trace.
            throw Failure(Problem.MALFORMED_JSON, observing.seen, observing.bodyCategory, e)
        }
    }

    private fun parseModel(json: JsonReader): Pair<String, String>? {
        var visibility = ""
        var slug = ""
        var displayName = ""
        json.beginObject()
        while (json.hasNext()) {
            val field = json.nextName()
            when (field) {
                "visibility" -> visibility = safeString(json, 40)
                "slug" -> slug = safeString(json, 200)
                "display_name" -> displayName = safeString(json, 160)
                else -> json.skipValue()
            }
        }
        json.endObject()
        if (visibility != "list" || slug.isBlank() || slug.length > 200 ||
            slug.any { it.isWhitespace() || it.isISOControl() }) return null
        val label = displayName.replace(Regex("[\\p{Cntrl}]+"), " ")
            .trim().take(72).ifBlank { slug }
        return slug to label
    }

    private fun safeString(json: JsonReader, maxLength: Int): String {
        if (json.peek() != JsonToken.STRING) {
            json.skipValue()
            return ""
        }
        val value = json.nextString()
        return if (value.length <= maxLength) value else ""
    }
}
