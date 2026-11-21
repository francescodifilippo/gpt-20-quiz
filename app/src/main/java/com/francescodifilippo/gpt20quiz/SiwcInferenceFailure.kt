package com.francescodifilippo.gpt20quiz

/**
 * A strictly sanitized summary of an OpenAI Responses API rejection.
 * Never expose provider message text, request payloads, secrets or headers.
 */
class SiwcInferenceFailure(
    val statusCode: Int,
    providerCode: String?,
    providerParameter: String?,
    val requestedEffort: ReasoningEffort
) : RuntimeException("ChatGPT request rejected (HTTP $statusCode)") {
    val code = providerCode?.takeIf {
        it.length in 1..64 && it.all { c ->
            c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'
        }
    }
    val parameter = providerParameter?.takeIf {
        it.length in 1..64 && it.all { c ->
            c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'
        }
    }

    /**
     * A 400 unsupported_value with a named reasoning parameter is definitive;
     * if the API omits "param", an explicitly sent effort is only a hypothesis.
     */
    val mayBeEffortRelated: Boolean get() =
        statusCode == 400 && code == "unsupported_value" &&
            requestedEffort != ReasoningEffort.AUTO &&
            (parameter == null || parameter == "reasoning.effort")

    val isConfirmedEffortRejection: Boolean get() =
        mayBeEffortRelated && parameter == "reasoning.effort"

    fun displayMessage(italian: Boolean, model: String): String {
        if (mayBeEffortRelated) {
            val selected = requestedEffort.apiValue
            val isGpt62 = model.startsWith("gpt-6.2-")
            return if (italian) {
                (if (isConfirmedEffortRejection) "Effort non supportato"
                 else "Possibile effort non supportato") +
                    ": " + selected + " (HTTP 400)." +
                    (if (isGpt62 && selected in setOf("none", "minimal"))
                        " Per GPT-6.2 prova Basso (low) oppure Automatico."
                     else " Cambia effort oppure scegli Automatico, poi riprova.")
            } else {
                (if (isConfirmedEffortRejection) "Unsupported effort"
                 else "Possibly unsupported effort") +
                    ": " + selected + " (HTTP 400)." +
                    (if (isGpt62 && selected in setOf("none", "minimal"))
                        " For GPT-6.2 try Low or Automatic."
                     else " Choose another effort or Automatic, then retry.")
            }
        }
        val safeCode = code?.let { " · " + it }.orEmpty()
        val safeParam = parameter?.let { " · " + it }.orEmpty()
        return if (italian)
            "Richiesta ChatGPT respinta: HTTP " + statusCode + safeCode + safeParam
        else "ChatGPT request rejected: HTTP " + statusCode + safeCode + safeParam
    }
}
