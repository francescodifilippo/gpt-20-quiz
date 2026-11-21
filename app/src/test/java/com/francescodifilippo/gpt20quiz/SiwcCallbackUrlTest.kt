package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SiwcCallbackUrlTest {
    private val port = 37965
    private val state = "carefully-checked-random-state"
    private val valid = "http://127.0.0.1:37965/auth/callback?code=sample-code&state=carefully-checked-random-state"

    @Test fun acceptsOnlyExactPendingLoopback() {
        assertEquals("/auth/callback?code=sample-code&state=carefully-checked-random-state",
            SiwcCallbackUrl.localRequestTarget(valid, port, state))
    }

    @Test fun rejectsWrongPortAndHost() {
        val wrongPort = valid.replace(":37965", ":37966")
        val wrongHost = valid.replace("127.0.0.1", "localhost")
        val remote = valid.replace("127.0.0.1", "example.com")
        for (candidate in listOf(wrongPort, wrongHost, remote)) {
            assertThrows(IllegalArgumentException::class.java) {
                SiwcCallbackUrl.localRequestTarget(candidate, port, state)
            }
        }
    }

    @Test fun rejectsChangedStateAndPathAndScheme() {
        val mismatched = valid.replace("carefully-checked-random-state", "other-state")
        val path = valid.replace("/auth/callback", "/wrong/path")
        val https = valid.replace("http://", "https://")
        for (candidate in listOf(mismatched, path, https)) {
            assertThrows(IllegalArgumentException::class.java) {
                SiwcCallbackUrl.localRequestTarget(candidate, port, state)
            }
        }
    }

    @Test fun rejectsMissingCodeOrDuplicateSensitiveFields() {
        val missingCode = valid.replace("code=sample-code&", "")
        val repeatedState = valid + "&state=carefully-checked-random-state"
        val repeatedCode = valid + "&code=another-code"
        val error = valid + "&error=access_denied"
        for (candidate in listOf(missingCode, repeatedState, repeatedCode, error)) {
            assertThrows(IllegalArgumentException::class.java) {
                SiwcCallbackUrl.localRequestTarget(candidate, port, state)
            }
        }
    }

    @Test fun preservesEncodedValues() {
        val encoded = "http://127.0.0.1:37965/auth/callback" +
            "?code=abc%2F123%2B456&state=carefully-checked-random-state"
        assertEquals(
            "/auth/callback?code=abc%2F123%2B456&state=carefully-checked-random-state",
            SiwcCallbackUrl.localRequestTarget(encoded, port, state)
        )
    }
}
