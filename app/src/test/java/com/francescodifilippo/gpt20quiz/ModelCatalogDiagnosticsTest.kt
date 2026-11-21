package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ModelCatalogDiagnosticsTest {
    @Test fun tokenFailureNeverLeaksTokenErrorText() {
        val error = ModelCatalogFailure(
            ModelCatalogStage.ACCESS_TOKEN,
            cause = IllegalStateException("Bearer secret-auth-token")
        )
        val result = ModelCatalogDiagnostics.description(error, true)
        assertTrue(result.contains("MODELS_TOKEN"))
        assertFalse(result.contains("secret-auth-token"))
    }

    @Test fun httpErrorRevealsOnlyStatusAndSafeProviderCode() {
        val failure = ModelCatalogFailure(
            ModelCatalogStage.HTTP,
            httpStatus = 403,
            providerErrorCode = "missing_scope"
        )
        val status = ModelCatalogDiagnostics.description(failure, true)
        assertTrue(status.contains("HTTP 403"))
        assertTrue(status.contains("missing_scope"))
        assertTrue(status.contains("MODELS_HTTP_403"))
    }

    @Test fun unknownProviderDataCannotBeShown() {
        val failure = ModelCatalogFailure(
            ModelCatalogStage.HTTP,
            httpStatus = 401,
            providerErrorCode = """{"access_token":"private"}"""
        )
        val status = ModelCatalogDiagnostics.description(failure, false)
        assertFalse(status.contains("access_token"))
        assertFalse(status.contains("private"))
        assertTrue(status.contains("HTTP 401"))
    }

    @Test fun networkTimeoutShowsSpecificActionableMessage() {
        val failure = ModelCatalogFailure(
            ModelCatalogStage.CONNECTION,
            cause = SocketTimeoutException("Could not reach private.example.com?code=abc")
        )
        val message = ModelCatalogDiagnostics.description(failure, true)
        assertTrue(message.contains("Timeout"))
        assertTrue(message.contains("MODELS_NETWORK"))
        assertFalse(message.contains("private.example.com"))
    }

    @Test fun dnsErrorIsIdentifiedWithoutExposingThrowableMessage() {
        val failure = ModelCatalogFailure(
            ModelCatalogStage.CONNECTION,
            cause = UnknownHostException("test.example.com")
        )
        val message = ModelCatalogDiagnostics.description(failure, true)
        assertTrue(message.contains("DNS"))
        assertFalse(message.contains("test.example.com"))
    }

    @Test fun invalidJsonHasOwnStableCode() {
        val failure = ModelCatalogFailure(ModelCatalogStage.JSON_RESPONSE)
        assertEquals("MODELS_BAD_RESPONSE", ModelCatalogDiagnostics.identifier(failure))
        assertTrue(ModelCatalogDiagnostics.description(failure, true).contains("/v1/models"))
    }

    @Test fun invalidCatalogHasOwnStableCode() {
        val failure = ModelCatalogFailure(ModelCatalogStage.MODEL_SELECTION)
        assertEquals("MODELS_BAD_CATALOG", ModelCatalogDiagnostics.identifier(failure))
    }

    @Test fun genericExceptionIsRedacted() {
        val exception = RuntimeException("super-secret-token Bearer xxxxx")
        val text = ModelCatalogDiagnostics.description(exception, true)
        assertTrue(text.contains("MODELS_UNEXPECTED"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("super-secret"))
    }

    @Test fun rateLimitsAndServerErrorsIdentified() {
        val rate = ModelCatalogFailure(ModelCatalogStage.HTTP, httpStatus = 429)
        assertTrue(ModelCatalogDiagnostics.description(rate, true).contains("limite"))
        val server = ModelCatalogFailure(ModelCatalogStage.HTTP, httpStatus = 503)
        assertTrue(ModelCatalogDiagnostics.description(server, true).contains("HTTP 503"))
    }
}
