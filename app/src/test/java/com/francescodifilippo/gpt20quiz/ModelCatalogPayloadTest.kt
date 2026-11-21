package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogPayloadTest {
    @Test fun bomAndWhitespaceAreRemovedWithoutChangingJson() {
        val input = "\uFEFF \n {\"models\":[]}"
        assertEquals("{\"models\":[]}", ModelCatalogPayload.normalize(input))
    }

    @Test fun recognizesWellFormedJsonWithoutExposingIt() {
        val body = "{\"models\":[{\"slug\":\"secret-name\"}]}"
        val diagnostic = ModelCatalogPayload.fingerprint(body, "application/json; charset=utf-8")
        assertEquals("json", diagnostic.contentType)
        assertEquals("json-object", diagnostic.kind)
        assertEquals(body.length, diagnostic.characters)
        assertFalse(diagnostic.toString().contains("secret-name"))
    }

    @Test fun recognizesHtmlInjectedByBrowserOrProxy() {
        val body = "<!doctype html><html><body>private user content</body></html>"
        val result = ModelCatalogPayload.fingerprint(body, "text/html; charset=UTF-8")
        assertEquals("html", result.contentType)
        assertEquals("html", result.kind)
        assertFalse(result.toString().contains("private user content"))
    }

    @Test fun recognizesEmptyResponse() {
        val result = ModelCatalogPayload.fingerprint(" \uFEFF ", "application/json")
        assertEquals("empty", result.kind)
    }

    @Test fun recognizesUnexpectedJsonArrayAndSse() {
        assertEquals("json-array",
            ModelCatalogPayload.fingerprint("[]", "application/json").kind)
        assertEquals("event-stream",
            ModelCatalogPayload.fingerprint("data: {\"event\":\"private\"}\n\n", "text/event-stream").kind)
    }

    @Test fun unknownHeaderIsNeverReflected() {
        val diagnostic = ModelCatalogPayload.fingerprint(
            "not-json", "text/private-key-secret=123456"
        )
        assertEquals("unknown", diagnostic.contentType)
        assertEquals("unknown", diagnostic.kind)
        assertFalse(diagnostic.toString().contains("private-key-secret"))
    }

    @Test fun fingerprintUsesKnownLabelsOnly() {
        val expected = listOf("html", "json", "event-stream", "text", "binary", "unknown", "missing")
        assertTrue(expected.contains(
            ModelCatalogPayload.fingerprint("<html>", "text/html").contentType
        ))
    }
}
