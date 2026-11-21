package com.francescodifilippo.gpt20quiz

/**
 * User preference independent of the selected model.
 *
 * All choices are shown for every account model. Acceptance of specific
 * values remains model-dependent: an unsupported value can yield HTTP 400.
 * Never silently replace or downgrade a user's explicit selection.
 */
enum class ReasoningEffort(val apiValue: String) {
    AUTO("auto"),
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    fun label(italian: Boolean): String = when (this) {
        AUTO -> if (italian) "Automatico (predefinito del modello)" else "Automatic (model default)"
        NONE -> if (italian) "Nessuno" else "None"
        MINIMAL -> if (italian) "Minimo" else "Minimal"
        LOW -> if (italian) "Basso" else "Low"
        MEDIUM -> if (italian) "Medio" else "Medium"
        HIGH -> if (italian) "Alto" else "High"
        XHIGH -> if (italian) "Molto alto" else "Extra high"
        MAX -> if (italian) "Massimo" else "Maximum"
    }

    /** Automatic omits the API parameter; all other values are sent unchanged. */
    fun requestedApiValue(): String? = apiValue.takeUnless { this == AUTO }

    companion object {
        fun fromPreference(value: String?): ReasoningEffort =
            entries.firstOrNull { it.apiValue == value } ?: AUTO
    }
}

object ReasoningEffortPolicy {
    /** Never filter by model or reset the preference on model changes. */
    val userChoices: List<ReasoningEffort> = ReasoningEffort.entries.toList()
}
