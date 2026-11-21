package com.francescodifilippo.gpt20quiz

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Validates manually copied browser callbacks before they are forwarded to
 * the same one-use loopback listener used by the browser.
 *
 * OAuth codes MUST remain inside the app: never log, share, persist or upload
 * callback URLs as a diagnostic attachment.
 */
object SiwcCallbackUrl {
    const val PATH = "/auth/callback"

    fun localRequestTarget(
        submittedUrl: String,
        expectedPort: Int,
        expectedState: String
    ): String {
        require(submittedUrl.length in 35..8192) { "Paste the full callback URL from the browser" }
        require(expectedPort in 1..65535 && expectedState.isNotBlank())
        val uri = try { URI(submittedUrl.trim()) }
        catch (_: Exception) { throw IllegalArgumentException("Invalid callback URL") }
        require(uri.scheme == "http"
            && uri.host == "127.0.0.1"
            && uri.port == expectedPort
            && uri.rawPath == PATH
            && uri.rawUserInfo == null
            && uri.rawFragment == null) {
            "The callback does not match the pending sign-in request"
        }
        val pairs = uri.rawQuery?.split('&')?.map {
            val at = it.indexOf('=')
            require(at > 0)
            val key = URLDecoder.decode(it.substring(0, at), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(it.substring(at + 1), StandardCharsets.UTF_8.name())
            key to value
        } ?: throw IllegalArgumentException("Missing callback parameters")
        fun only(name: String): String? {
            val found = pairs.filter { it.first == name }
            require(found.size <= 1) { "Duplicate callback parameter" }
            return found.singleOrNull()?.second
        }
        val receivedState = only("state") ?: ""
        require(MessageDigest.isEqual(
            receivedState.toByteArray(StandardCharsets.UTF_8),
            expectedState.toByteArray(StandardCharsets.UTF_8)
        )) { "The callback state does not match the active sign-in" }
        val code = only("code")
        require(code != null && code.isNotBlank() && code.length < 4096) {
            "Missing authorization code in callback"
        }
        require(only("error") == null) { "The authorization request was denied" }
        // Keep the original percent-encoded query intact for the listener.
        return uri.rawPath + "?" + uri.rawQuery
    }
}
