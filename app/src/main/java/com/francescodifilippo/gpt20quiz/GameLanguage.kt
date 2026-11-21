package com.francescodifilippo.gpt20quiz

import java.util.Locale

/** Display names are editorial labels, not guarantees of full UI localization. */
data class LanguageOption(
    val code: String,
    val nativeName: String,
    val italianName: String,
    val englishName: String
) {
    fun label(italianUi: Boolean): String =
        if (italianUi) italianName else englishName

    fun matches(query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        return q.isEmpty() || listOf(code, nativeName, italianName, englishName)
            .any { it.lowercase(Locale.ROOT).contains(q) }
    }
}

/** A resolved language is frozen when a new match starts. */
data class GameLanguageSelection(
    val preferenceCode: String,
    val effectiveLanguageTag: String,
    val effectiveLanguageName: String
)

object GameLanguages {
    const val AUTO = "auto"
    /** Fixed order: Latin, Esperanto, Automatic, then the 24 official EU languages. */
    val options: List<LanguageOption> = listOf(
        LanguageOption("la", "Latina", "Latino", "Latin"),
        LanguageOption("eo", "Esperanto", "Esperanto", "Esperanto"),
        LanguageOption(AUTO, "Device language", "Automatica · dispositivo", "Automatic · device"),
        LanguageOption("bg", "Български", "Bulgaro", "Bulgarian"),
        LanguageOption("cs", "Čeština", "Ceco", "Czech"),
        LanguageOption("hr", "Hrvatski", "Croato", "Croatian"),
        LanguageOption("da", "Dansk", "Danese", "Danish"),
        LanguageOption("et", "Eesti", "Estone", "Estonian"),
        LanguageOption("fi", "Suomi", "Finlandese", "Finnish"),
        LanguageOption("fr", "Français", "Francese", "French"),
        LanguageOption("el", "Ελληνικά", "Greco", "Greek"),
        LanguageOption("en", "English", "Inglese", "English"),
        LanguageOption("ga", "Gaeilge", "Irlandese", "Irish"),
        LanguageOption("it", "Italiano", "Italiano", "Italian"),
        LanguageOption("lv", "Latviešu", "Lettone", "Latvian"),
        LanguageOption("lt", "Lietuvių", "Lituano", "Lithuanian"),
        LanguageOption("mt", "Malti", "Maltese", "Maltese"),
        LanguageOption("nl", "Nederlands", "Olandese", "Dutch"),
        LanguageOption("pl", "Polski", "Polacco", "Polish"),
        LanguageOption("pt", "Português", "Portoghese", "Portuguese"),
        LanguageOption("ro", "Română", "Rumeno", "Romanian"),
        LanguageOption("sk", "Slovenčina", "Slovacco", "Slovak"),
        LanguageOption("sl", "Slovenščina", "Sloveno", "Slovenian"),
        LanguageOption("es", "Español", "Spagnolo", "Spanish"),
        LanguageOption("sv", "Svenska", "Svedese", "Swedish"),
        LanguageOption("de", "Deutsch", "Tedesco", "German"),
        LanguageOption("hu", "Magyar", "Ungherese", "Hungarian")
    )

    fun option(code: String?): LanguageOption =
        options.firstOrNull { it.code == code } ?: options[2]

    fun resolve(choiceCode: String?, deviceLanguageTag: String): GameLanguageSelection {
        val choice = option(choiceCode)
        val locale = Locale.forLanguageTag(deviceLanguageTag)
        val deviceTag = locale.toLanguageTag()
            .takeIf { locale.language.isNotBlank() && it != "und" } ?: "en"
        val actualTag = if (choice.code == AUTO) deviceTag else choice.code
        val displayName = if (choice.code == AUTO) {
            Locale.forLanguageTag(actualTag).getDisplayLanguage(Locale.ENGLISH)
                .ifBlank { "Device language" }
        } else choice.englishName
        return GameLanguageSelection(choice.code, actualTag, displayName)
    }
}

/** Snapshot passed to a future SIWC adapter; no network request is made in demo mode. */
data class GameSessionState(
    val role: PlayerRole,
    val language: GameLanguageSelection,
    val questionNumber: Int,
    val questionsRemaining: Int
) {
    init {
        require(questionNumber in 1..GameRules.MAX_TURNS)
        require(questionsRemaining in 1..GameRules.MAX_TURNS)
        require(questionNumber + questionsRemaining == GameRules.MAX_TURNS + 1)
    }
}

object GameLanguagePrompt {
    /** Include these instructions with the versioned Twenty Questions system rules. */
    fun initialInstructions(state: GameSessionState): String = """
        GAME LANGUAGE POLICY
        Language choice: ${state.language.preferenceCode}
        Effective language code: ${state.language.effectiveLanguageTag}
        Effective language name: ${state.language.effectiveLanguageName}
        Use the effective language for every question, guess and explanation.
        Do not switch languages during the match. Preserve canonical answer codes
        and the structured response schema; only player-facing text is translated.
        Treat the selected language as fixed even if the device locale changes.
        The player's role is ${state.role.name}.
        Next turn: ${state.questionNumber}
        Turns remaining: ${state.questionsRemaining}
    """.trimIndent()
}
