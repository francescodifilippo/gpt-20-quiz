package com.francescodifilippo.gpt20quiz

/**
 * Non-sensitive diagnostics for unexpected HTTP bodies.
 * NEVER include, persist or log server-response text, header values, tokens,
 * cookies, authorization codes or URLs in a diagnostic.
 */
data class ModelCatalogPayloadFingerprint(
    val contentType: String,
    val kind: String,
    val characters: Int
)

object ModelCatalogPayload {
    const val MAX_RESPONSE_CHARACTERS = 512_000

    /**
     * Android's JSONObject parser does not consistently tolerate a UTF-8 BOM.
     * Normalization removes only harmless leading whitespace/BOM; it does not
     * try to parse HTML, SSE, or alternative undocumented catalog formats.
     */
    fun normalize(raw: String): String =
        raw.trimStart { ch -> ch == '\uFEFF' || ch == '\u200B' || ch.isWhitespace() }

    fun fingerprint(raw: String, contentType: String?): ModelCatalogPayloadFingerprint {
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
        val safeContentType = when {
            mediaType == null || mediaType.isBlank() -> "missing"
            mediaType == "application/json" || mediaType.endsWith("+json") -> "json"
            mediaType == "text/html" || mediaType == "application/xhtml+xml" -> "html"
            mediaType == "text/event-stream" -> "event-stream"
            mediaType == "text/plain" -> "text"
            mediaType == "application/octet-stream" -> "binary"
            else -> "unknown"
        }
        val prefix = normalize(raw).take(60).lowercase()
        val bodyType = when {
            prefix.isBlank() -> "empty"
            prefix.startsWith("<!doctype html") ||
                prefix.startsWith("<html") ||
                prefix.startsWith("<head") ||
                prefix.startsWith("<body") -> "html"
            prefix.startsWith("<?xml") -> "xml"
            prefix.startsWith("<") -> "html"
            prefix.startsWith("event:") ||
                prefix.startsWith("data:") -> "event-stream"
            prefix.startsWith("{") -> "json-object"
            prefix.startsWith("[") -> "json-array"
            prefix.startsWith("\"") ||
                prefix.startsWith("null") ||
                prefix.startsWith("true") ||
                prefix.startsWith("false") ||
                prefix.first().isDigit() -> "json-primitive"
            prefix.any { it == '\uFFFD' || it == '\u0000' } -> "binary"
            else -> "unknown"
        }
        return ModelCatalogPayloadFingerprint(safeContentType, bodyType, raw.length)
    }
}
