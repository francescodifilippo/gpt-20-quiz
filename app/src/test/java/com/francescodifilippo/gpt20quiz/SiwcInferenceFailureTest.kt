package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiwcInferenceFailureTest {
    @Test fun unsupportedEffortWithNamedParameterIsConfirmed() {
        val failure = SiwcInferenceFailure(400, "unsupported_value", "reasoning.effort", ReasoningEffort.MINIMAL)
        assertTrue(failure.isConfirmedEffortRejection)
        assertTrue(failure.mayBeEffortRelated)
        assertTrue(failure.displayMessage(true, "gpt-6.2-sol").contains("Basso (low)"))
        assertTrue(failure.displayMessage(true, "gpt-6.2-sol").contains("minimal"))
    }

    @Test fun unsupportedEffortWithoutParameterIsPresentedAsHypothesis() {
        val failure = SiwcInferenceFailure(400, "unsupported_value", null, ReasoningEffort.MINIMAL)
        assertTrue(failure.mayBeEffortRelated)
        assertFalse(failure.isConfirmedEffortRejection)
        assertTrue(failure.displayMessage(false, "gpt-6.2-sol").contains("Possibly"))
    }

    @Test fun automaticAndOtherRejectionsDoNotBlameEffort() {
        val auto = SiwcInferenceFailure(400, "unsupported_value", null, ReasoningEffort.AUTO)
        val model = SiwcInferenceFailure(400, "unsupported_value", "model", ReasoningEffort.MINIMAL)
        val unauthorized = SiwcInferenceFailure(401, "unauthorized", null, ReasoningEffort.MINIMAL)
        assertFalse(auto.mayBeEffortRelated)
        assertFalse(model.mayBeEffortRelated)
        assertFalse(unauthorized.mayBeEffortRelated)
    }

    @Test fun neverExposeUntrustedProviderText() {
        val failure = SiwcInferenceFailure(
            400, "unsupported_value\nBearer secret-token", "reasoning.effort;private",
            ReasoningEffort.HIGH
        )
        val text = failure.displayMessage(true, "some-model")
        assertFalse(text.contains("secret-token"))
        assertFalse(text.contains("private"))
        assertTrue(text.contains("HTTP 400"))
    }

    @Test fun lowEffortCanStillBeRejectedWithoutSilentDowngrade() {
        val failure = SiwcInferenceFailure(
            400, "unsupported_value", "reasoning.effort", ReasoningEffort.LOW
        )
        assertTrue(failure.mayBeEffortRelated)
        assertTrue(failure.displayMessage(true, "other-model").contains("low"))
        assertFalse(failure.displayMessage(true, "other-model").contains("Basso (low)"))
    }
}
