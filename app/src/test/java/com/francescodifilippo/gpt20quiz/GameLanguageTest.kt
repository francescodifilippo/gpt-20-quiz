package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLanguageTest {
    private val officialEuLanguages = setOf(
        "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
        "hu", "ga", "it", "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv"
    )

    @Test fun exactlyTwentySixPlayableLanguagesPlusAutomatic() {
        val all = GameLanguages.options.map { it.code }
        assertEquals(27, all.size)
        assertEquals(27, all.toSet().size)
        assertEquals(officialEuLanguages, all.toSet() - setOf("la", "eo", "auto"))
    }

    @Test fun latinAndEsperantoAppearFirstWithoutOverridingDefault() {
        assertEquals(listOf("la", "eo", "auto"),
            GameLanguages.options.take(3).map { it.code })
        assertEquals(GameLanguages.AUTO, GameLanguages.option(null).code)
        assertEquals(GameLanguages.AUTO, GameLanguages.option("invalid").code)
    }

    @Test fun automaticResolvesFullDeviceTagNotJustEnglishOrItalian() {
        val selected = GameLanguages.resolve("auto", "fr-CA")
        assertEquals("auto", selected.preferenceCode)
        assertEquals("fr-CA", selected.effectiveLanguageTag)
        assertEquals("French", selected.effectiveLanguageName)
    }

    @Test fun automaticSupportsDeviceLanguagesOutsideCatalog() {
        assertEquals("ja-JP",
            GameLanguages.resolve("auto", "ja-JP").effectiveLanguageTag)
        assertEquals("en",
            GameLanguages.resolve("auto", "und").effectiveLanguageTag)
    }

    @Test fun manualChoiceAlwaysOverridesDeviceLanguage() {
        assertEquals("la", GameLanguages.resolve("la", "it-IT").effectiveLanguageTag)
        assertEquals("eo", GameLanguages.resolve("eo", "fr-FR").effectiveLanguageTag)
        assertEquals("bg", GameLanguages.resolve("bg", "en-US").effectiveLanguageTag)
    }

    @Test fun searchMatchesNativeNameAndOtherLabels() {
        assertTrue(GameLanguages.option("el").matches("Ελληνικά"))
        assertTrue(GameLanguages.option("ga").matches("Irish"))
        assertTrue(GameLanguages.option("la").matches("Latino"))
        assertFalse(GameLanguages.option("la").matches("English"))
    }

    @Test fun languagePromptIsOneSentenceWithResolvedLanguage() {
        val session = GameSessionState(
            role = PlayerRole.RESPONDER,
            language = GameLanguages.resolve("eo", "it-IT"),
            questionNumber = 2,
            questionsRemaining = 19
        )
        val instructions = GameLanguagePrompt.initialInstructions(session)
        assertTrue(instructions.contains("Use Esperanto (eo)"))
        assertTrue(instructions.contains("JSON field names and answer codes unchanged"))
        assertEquals(1, instructions.count { it == '.' })
        // Model role and remaining turns are already passed via structured
        // input; don't repeat them in the language directive.
        assertFalse(instructions.contains("RESPONDER"))
        assertFalse(instructions.contains("Turns remaining"))
    }

    @Test fun frozenAutomaticSessionIsNotChangedByLaterDeviceLocale() {
        val frozen = GameLanguages.resolve("auto", "pt-BR")
        val session = GameSessionState(
            PlayerRole.GUESSER, frozen, questionNumber = 1, questionsRemaining = 20
        )
        val laterDevice = GameLanguages.resolve("auto", "de-DE")
        assertEquals("pt-BR", session.language.effectiveLanguageTag)
        assertEquals("de-DE", laterDevice.effectiveLanguageTag)
        assertTrue(GameLanguagePrompt.initialInstructions(session).contains("pt-BR"))
    }

    @Test fun invalidSessionTurnStateIsRejected() {
        val lang = GameLanguages.resolve("la", "en-GB")
        assertThrows(IllegalArgumentException::class.java) {
            GameSessionState(PlayerRole.GUESSER, lang, 5, 20)
        }
    }
}
