package com.francescodifilippo.gpt20quiz

/**
 * Error details that are safe to show to the account owner.
 *
 * OAuth access/refresh tokens, authorization codes, HTTP request bodies and raw
 * provider messages must never appear in model-catalog diagnostics or logs.
 */
enum class ModelCatalogStage { ACCESS_TOKEN, CONNECTION, HTTP, JSON_RESPONSE, MODEL_SELECTION, TOO_LARGE }

class ModelCatalogFailure(
    val stage: ModelCatalogStage,
    val httpStatus: Int? = null,
    providerErrorCode: String? = null,
    val contentTypeCategory: String? = null,
    val bodyCategory: String? = null,
    val responseCharacters: Int? = null,
    val requestId: String? = null,
    cause: Throwable? = null
) : RuntimeException("SIWC catalog failed at " + stage.name, cause) {
    val safeProviderCode = providerErrorCode
        ?.takeIf { it.length in 1..64 && it.all { c ->
            c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'
        } }
    val safeRequestId = requestId?.takeIf {
        it.length in 1..90 && it.all { c ->
            c.isLetterOrDigit() || c in "-_."
        }
    }
    val safeContentType = contentTypeCategory?.takeIf {
        it in setOf("json", "html", "text", "event-stream", "binary", "unknown", "missing")
    }
    val safeBodyCategory = bodyCategory?.takeIf {
        it in setOf("empty", "json-object", "json-array", "json-primitive",
            "html", "xml", "event-stream", "binary", "unknown")
    }
}

/** Short actionable text and a stable identifier that may be reported to developers. */
object ModelCatalogDiagnostics {
    fun identifier(error: Throwable): String {
        val failure = error as? ModelCatalogFailure
            ?: return "MODELS_UNEXPECTED"
        return when (failure.stage) {
            ModelCatalogStage.ACCESS_TOKEN -> "MODELS_TOKEN"
            ModelCatalogStage.CONNECTION -> "MODELS_NETWORK"
            ModelCatalogStage.HTTP -> "MODELS_HTTP_" +
                (failure.httpStatus?.toString() ?: "UNKNOWN")
            ModelCatalogStage.JSON_RESPONSE -> "MODELS_BAD_RESPONSE"
            ModelCatalogStage.MODEL_SELECTION -> "MODELS_BAD_CATALOG"
            ModelCatalogStage.TOO_LARGE -> "MODELS_TOO_LARGE"
        }
    }

    fun description(error: Throwable, italian: Boolean): String {
        val failure = error as? ModelCatalogFailure
            ?: return if (italian)
                "Errore inatteso nel catalogo. Riprova e indica il codice MODELS_UNEXPECTED."
            else "Unexpected model catalog error. Retry and report MODELS_UNEXPECTED."

        val provider = failure.safeProviderCode?.let { " (codice: " + it + ")" }.orEmpty()
        val prefix = if (italian) "SIWC · " else "SIWC · "
        val hint = when (failure.stage) {
            ModelCatalogStage.ACCESS_TOKEN -> if (italian)
                "Impossibile ottenere il token per i modelli. Riconnetti ChatGPT se il problema persiste."
            else
                "Could not obtain the model access token. Reconnect ChatGPT if this persists."
            ModelCatalogStage.CONNECTION -> {
                val cause = failure.cause
                when {
                    cause is java.net.SocketTimeoutException -> if (italian)
                        "Timeout verso api.openai.com. Controlla rete/VPN e riprova."
                    else "Timeout reaching api.openai.com. Check network/VPN and retry."
                    cause is java.net.UnknownHostException -> if (italian)
                        "Impossibile risolvere api.openai.com (DNS). Controlla la connessione."
                    else "Cannot resolve api.openai.com (DNS). Check your connection."
                    cause is javax.net.ssl.SSLException -> if (italian)
                        "Errore TLS collegandosi a api.openai.com. Controlla VPN/proxy e data del dispositivo."
                    else "TLS error connecting to api.openai.com. Check VPN/proxy and device date."
                    else -> if (italian)
                        "Impossibile collegarsi a api.openai.com. Riprova su Wi-Fi o altra rete."
                    else "Could not reach api.openai.com. Retry on Wi-Fi or another network."
                }
            }
            ModelCatalogStage.HTTP -> when (failure.httpStatus ?: 0) {
                401 -> if (italian)
                    "HTTP 401: token rifiutato o scaduto. Scollega e riconnetti ChatGPT."
                else "HTTP 401: token rejected or expired. Disconnect and reconnect ChatGPT."
                403 -> if (italian)
                    "HTTP 403: il servizio ha negato l'accesso al catalogo. Verifica le autorizzazioni SIWC."
                else "HTTP 403: access to the model catalog denied. Check SIWC permissions."
                429 -> if (italian)
                    "HTTP 429: limite temporaneo raggiunto. Riprova più tardi."
                else "HTTP 429: rate limit reached. Try again later."
                in 500..599 -> if (italian)
                    "HTTP " + failure.httpStatus + ": errore temporaneo del servizio OpenAI."
                else "HTTP " + failure.httpStatus + ": temporary OpenAI service error."
                else -> if (italian)
                    "Il catalogo ha restituito HTTP " + (failure.httpStatus ?: "?") + "."
                else "Model catalog returned HTTP " + (failure.httpStatus ?: "?") + "."
            }
            ModelCatalogStage.JSON_RESPONSE -> if (italian)
                "La risposta di /v1/models non contiene un catalogo JSON valido."
            else "The /v1/models response was not a valid catalog JSON."
            ModelCatalogStage.MODEL_SELECTION -> if (italian)
                "Il catalogo non contiene l'elenco dei modelli nel formato SIWC atteso."
            else "The catalog does not contain the expected SIWC model list."
            ModelCatalogStage.TOO_LARGE -> if (italian)
                "Il catalogo supera il limite di sicurezza di 16 milioni di caratteri."
            else "The model catalog exceeds the 16-million-character safety limit."
        }
        val fingerprint = if (failure.stage == ModelCatalogStage.JSON_RESPONSE ||
            failure.stage == ModelCatalogStage.MODEL_SELECTION ||
            failure.stage == ModelCatalogStage.TOO_LARGE) {
            listOfNotNull(
                failure.httpStatus?.let { "HTTP " + it },
                failure.safeContentType?.let { "tipo " + it },
                failure.safeBodyCategory?.let { "contenuto " + it },
                failure.responseCharacters?.takeIf { it in 0..ModelCatalogStreamParser.MAX_CHARS + 1 }
                    ?.let { it.toString() + " caratteri" },
                failure.safeRequestId?.let { "request-id " + it }
            ).joinToString(" · ").take(210)
        } else ""
        return prefix + hint + provider +
            (if (fingerprint.isBlank()) "" else "\n" + fingerprint) +
            " [" + identifier(failure) + "]"
    }
}
