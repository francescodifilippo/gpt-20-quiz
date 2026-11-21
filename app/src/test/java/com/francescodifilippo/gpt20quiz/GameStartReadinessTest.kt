package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStartReadinessTest {
    @Test fun noAccountRequiresAuthentication() {
        assertEquals(StartBlocker.SIGN_IN,
            GameStartReadiness.evaluate(false, false, false, ""))
    }

    @Test fun identityOnlyIsNotPlanConsent() {
        assertEquals(StartBlocker.AUTHORIZE_PLAN,
            GameStartReadiness.evaluate(true, false, false, "any"))
    }

    @Test fun modelLoadingExplainsDisabledButton() {
        assertEquals(StartBlocker.WAIT_FOR_MODELS,
            GameStartReadiness.evaluate(true, true, true, ""))
    }

    @Test fun missingModelExplainsDisabledButton() {
        assertEquals(StartBlocker.SELECT_MODEL,
            GameStartReadiness.evaluate(true, true, false, ""))
    }

    @Test fun playableOnlyWithSelectedModelAndGrantedPlan() {
        assertEquals(StartBlocker.READY,
            GameStartReadiness.evaluate(true, true, false, "model-from-account"))
    }

    @Test fun bothUiLanguagesHaveInstructionsForEveryState() {
        for (blocker in StartBlocker.entries) {
            assertTrue(GameStartReadiness.explanation(blocker, true).isNotBlank())
            assertTrue(GameStartReadiness.explanation(blocker, false).isNotBlank())
        }
    }
}
