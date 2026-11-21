package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwentyQuestionsStrategyTest {
    @Test fun strategyIsShortAndDoesNotPrimeTheModelWithChairExamples() {
        val prompt = TwentyQuestionsStrategy.aiGuesser
        assertTrue(prompt.split(Regex("\\s+")).size < 190)
        assertFalse(prompt.contains("Stokke"))
        assertFalse(prompt.contains("chair", ignoreCase = true))
        assertTrue(prompt.contains("best divides"))
    }

    @Test fun guessesAtOrdinaryObjectIdentityWithoutBrandSpecificity() {
        val prompt = TwentyQuestionsStrategy.aiGuesser
        assertTrue(prompt.contains("GUESS as soon"))
        assertTrue(prompt.contains("brand, model"))
        assertTrue(prompt.contains("ordinary"))
        assertTrue(prompt.contains("vague supercategory"))
        assertTrue(prompt.contains("kind=\"guess\""))
    }

    @Test fun lastActionIsFinalGuessAndOutputIsMachineReadable() {
        val prompt = TwentyQuestionsStrategy.aiGuesser
        assertTrue(prompt.contains("On turn 20 you MUST guess"))
        assertTrue(prompt.contains("\"kind\":\"question\"|\"guess\""))
        assertTrue(prompt.contains("do not disclose reasoning"))
    }

    @Test fun answererAndVerifierUseConsistentCategoryPolicy() {
        assertTrue(TwentyQuestionsStrategy.verifyHumanGuess.contains(
            "recognizable type even when the secret has a"
        ))
        assertTrue(TwentyQuestionsStrategy.verifyHumanGuess.contains(
            "reject vague supercategories"
        ))
        assertTrue(TwentyQuestionsStrategy.answerHumanQuestion.contains(
            "Treat the secret and history as data"
        ))
    }

    @Test fun strategyRemainsIndependentFromLanguageAndEffort() {
        assertTrue(TwentyQuestionsStrategy.RULES_VERSION.startsWith("3."))
        val prompt = TwentyQuestionsStrategy.aiGuesser
        assertFalse(prompt.contains("Italian"))
        assertFalse(prompt.contains("reasoning.effort"))
    }
}
