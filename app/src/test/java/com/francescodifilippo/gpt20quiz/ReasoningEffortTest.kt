package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEffortTest {
    @Test fun allEffortsAvailableIndependentOfModel() {
        assertEquals(
            listOf("auto", "none", "minimal", "low", "medium", "high", "xhigh", "max"),
            ReasoningEffortPolicy.userChoices.map { it.apiValue }
        )
        // No model argument exists in the UI policy: this is explicitly a
        // global preference, not a list filtered for one model slug.
        assertEquals(8, ReasoningEffortPolicy.userChoices.size)
    }

    @Test fun automaticOmitsReasoningField() {
        assertNull(ReasoningEffort.AUTO.requestedApiValue())
    }

    @Test fun explicitChoiceIsNeverDowngradedOrTranslated() {
        assertEquals("max", ReasoningEffort.MAX.requestedApiValue())
        assertEquals("xhigh", ReasoningEffort.XHIGH.requestedApiValue())
        assertEquals("none", ReasoningEffort.NONE.requestedApiValue())
        assertEquals("minimal", ReasoningEffort.MINIMAL.requestedApiValue())
    }

    @Test fun savedPreferenceCanBeRestoredWithoutModelLookup() {
        for (value in ReasoningEffort.entries) {
            assertEquals(value, ReasoningEffort.fromPreference(value.apiValue))
        }
        assertEquals(ReasoningEffort.AUTO, ReasoningEffort.fromPreference(null))
        assertEquals(ReasoningEffort.AUTO, ReasoningEffort.fromPreference("garbage"))
    }

    @Test fun allChoicesHaveVisibleLabels() {
        for (effort in ReasoningEffort.entries) {
            assertTrue(effort.label(true).isNotBlank())
            assertTrue(effort.label(false).isNotBlank())
            assertFalse(effort.label(true).contains("{"))
        }
    }
}
