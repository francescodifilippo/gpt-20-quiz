package com.francescodifilippo.gpt20quiz

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SiwcSession(
    val clientId: String,
    val subject: String,
    val email: String,
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val scopes: String,
    val expiresAtMs: Long,
    val earliestRefreshAtMs: Long
) {
    val canUsePlan: Boolean get() = scopes.split(' ').contains("chatgpt.tokens.use.direct")
}

private object SiwcEndpoints {
    const val issuer = "https://auth.openai.com"
    const val authorize = "$issuer/api/accounts/authorize"
    const val token = "$issuer/api/accounts/oauth/token"
    const val revoke = "$issuer/api/accounts/oauth/revoke"
    const val jwks = "$issuer/.well-known/jwks.json"
    const val resource = "https://api.openai.com/v1"
    const val callbackPath = "/auth/callback"
}

private fun base64UrlDecode(value: String): ByteArray =
    Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

private fun randomSecret(bytes: Int = 32): String {
    val value = ByteArray(bytes)
    SecureRandom().nextBytes(value)
    return Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun httpJson(url: String, method: String = "GET", body: String? = null,
                     bearer: String? = null, formEncoded: Boolean = false): JSONObject {
    val connection = (URL(url).openConnection() as HttpURLConnection)
    connection.requestMethod = method
    connection.connectTimeout = 12000
    connection.readTimeout = 35000
    connection.setRequestProperty("Accept", "application/json")
    if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer)
    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type",
            if (formEncoded) "application/x-www-form-urlencoded" else "application/json")
    }
    try {
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText().take(8192) } ?: ""
        if (status !in 200..299) {
            // Do not expose credentials or the full response body to diagnostics.
            val detail = runCatching {
                val obj = JSONObject(text)
                obj.optJSONObject("error")?.optString("code")?.take(100)
                    ?: obj.optString("error", "HTTP " + status).take(100)
            }.getOrDefault("HTTP " + status)
            throw IllegalStateException("OpenAI request failed (" + status + "): " + detail)
        }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    } finally { connection.disconnect() }
}

private fun postForm(url: String, params: Map<String, String>): JSONObject {
    val data = params.entries.joinToString("&") {
        java.net.URLEncoder.encode(it.key, "UTF-8") + "=" +
            java.net.URLEncoder.encode(it.value, "UTF-8")
    }
    return httpJson(url, "POST", data, formEncoded = true)
}

/**
 * Sensitive SIWC credentials are held only in an AES-256 GCM encrypted record,
 * with a device-protected AndroidKeyStore key that is never exported.
 */
private class EncryptedSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("siwc_local_v1", Context.MODE_PRIVATE)
    private val alias = "gpt20_siwc_credentials_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun hostId(): String {
        val existing = prefs.getString("host_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = "urn:uuid:" + UUID.randomUUID().toString()
        check(prefs.edit().putString("host_id", created).commit())
        return created
    }

    fun load(): SiwcSession? {
        val raw = prefs.getString("encrypted_session", null) ?: return null
        return try {
            val packed = Base64.decode(raw, Base64.NO_WRAP)
            require(packed.size > 13)
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val obj = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
            SiwcSession(
                obj.getString("client_id"), obj.getString("subject"),
                obj.optString("email", ""), obj.optString("id_token", ""),
                obj.getString("access_token"), obj.optString("refresh_token", ""),
                obj.optString("scope", ""), obj.getLong("expires_at_ms"),
                obj.optLong("earliest_refresh_at_ms", 0)
            )
        } catch (_: Exception) { null }
    }

    fun save(session: SiwcSession) {
        val obj = JSONObject()
            .put("client_id", session.clientId).put("subject", session.subject)
            .put("email", session.email).put("id_token", session.idToken)
            .put("access_token", session.accessToken).put("refresh_token", session.refreshToken)
            .put("scope", session.scopes).put("expires_at_ms", session.expiresAtMs)
            .put("earliest_refresh_at_ms", session.earliestRefreshAtMs)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(obj.toString().toByteArray(StandardCharsets.UTF_8))
        check(prefs.edit().putString("encrypted_session",
            Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }

    fun disconnect() { check(prefs.edit().remove("encrypted_session").commit()) }
}

class SiwcAuthManager(context: Context) {
    private val app = context.applicationContext
    private val store = EncryptedSessionStore(app)
    @Volatile private var awaitingCallback: Pair<Int, String>? = null

    fun sessionOrNull(): SiwcSession? = store.load()

    /**
     * Secure recovery if browser loopback routing fails. A callback URL copied
     * from the browser is accepted ONLY while this process has a pending
     * authorization transaction, with an exact matching port, path and state.
     *
     * The URL/code is not saved, logged, or sent to any diagnostics service.
     */
    suspend fun submitManualCallback(raw: String) = withContext(Dispatchers.IO) {
        val pending = awaitingCallback
            ?: throw IllegalStateException("There is no active ChatGPT sign-in")
        val target = SiwcCallbackUrl.localRequestTarget(raw, pending.first, pending.second)
        Socket(InetAddress.getByName("127.0.0.1"), pending.first).use { socket ->
            socket.soTimeout = 5000
            val request = "GET " + target + " HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + pending.first +
                "\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val reply = socket.getInputStream().bufferedReader().readLine().orEmpty()
            check(reply.startsWith("HTTP/1.1 200 ")) {
                "Callback rejected by the active sign-in listener"
            }
        }
    }

    /**
     * Opens the device's external browser. Callback comes to a one-use loopback
     * listener on the device. Browser may need to be closed manually afterward.
     * Every credential exchange and validation occurs off the UI thread.
     */
    suspend fun signIn(): SiwcSession = withContext(Dispatchers.IO) {
        val previous = store.load()
        val initial = previous == null
        val state = randomSecret()
        val nonce = randomSecret()
        val verifier = randomSecret(64)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.UTF_8))
        val challenge = Base64.encodeToString(sha,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 150_000
            val redirect = "http://127.0.0.1:" + server.localPort + SiwcEndpoints.callbackPath
            val clientId = if (initial) "dynamic_agent_client" else previous!!.clientId
            val url = Uri.parse(SiwcEndpoints.authorize).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("scope",
                    "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
                .appendQueryParameter("resource", SiwcEndpoints.resource)
                .appendQueryParameter("ext_agent_host_id", store.hostId())
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .apply {
                    // Explicit re-consent is required if identity sign-in succeeded but plan usage was declined.
                    if (previous != null && !previous.canUsePlan)
                        appendQueryParameter("prompt", "consent")
                    if (initial) appendQueryParameter("agent_name_hint", "GPT 20 Quiz")
                    else if (previous!!.idToken.isNotBlank())
                        appendQueryParameter("id_token_hint", previous.idToken)
                }.build()
            // Keep the process hosting our loopback listener alive while the
            // authorization browser is in the foreground.
            awaitingCallback = Pair(server.localPort, state)
            try {
                SiwcCallbackService.start(app)
                val tab = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                    .build()
                tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tab.launchUrl(app, url)

            val callback = server.accept().use { socket ->
                socket.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val requestLine = reader.readLine() ?: throw IllegalStateException("Empty callback")
                require(requestLine.length < 4096)
                val first = requestLine.split(' ')
                require(first.size >= 3 && first[0] == "GET")
                // A literal route match prevents code exchange through another path.
                val path = first[1]
                require(path.substringBefore('?') == SiwcEndpoints.callbackPath)
                var hostHeader = ""
                var headerCount = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isBlank()) break
                    headerCount++
                    require(headerCount <= 64 && header.length < 8192)
                    if (header.startsWith("Host:", ignoreCase = true))
                        hostHeader = header.substringAfter(':').trim()
                }
                require(hostHeader == "127.0.0.1:" + server.localPort)
                val query = Uri.parse("http://127.0.0.1" + path)
                val returnedState = query.getQueryParameter("state") ?: ""
                val validState = MessageDigest.isEqual(state.toByteArray(), returnedState.toByteArray())
                val error = query.getQueryParameter("error")
                val success = validState && error == null && !query.getQueryParameter("code").isNullOrBlank()
                val html = if (success)
                    "<html><body><h2>GPT 20 Quiz</h2><p>Authorization received. Return to the app.</p></body></html>"
                else
                    "<html><body><h2>GPT 20 Quiz</h2><p>Sign-in was not completed. Return to the app.</p></body></html>"
                val packet = html.toByteArray(StandardCharsets.UTF_8)
                val response = "HTTP/1.1 " + (if (success) "200 OK" else "400 Bad Request") +
                    "\r\nContent-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Security-Policy: default-src 'none'\r\n" +
                    "Cache-Control: no-store\r\nContent-Length: " + packet.size +
                    "\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(response.toByteArray(StandardCharsets.US_ASCII))
                socket.getOutputStream().write(packet)
                socket.getOutputStream().flush()
                check(validState) { "OAuth state does not match" }
                if (error != null) throw IllegalStateException("Sign-in denied: " + error.take(60))
                val code = query.getQueryParameter("code")
                    ?: throw IllegalStateException("Missing authorization code")
                val issued = query.getQueryParameter("client_id")
                val registrationId = if (initial) {
                    require(!issued.isNullOrBlank() && issued != "dynamic_agent_client")
                    issued
                } else {
                    if (issued != null) require(issued == previous!!.clientId)
                    previous!!.clientId
                }
                Pair(code, registrationId)
            }

            val response = postForm(SiwcEndpoints.token, mapOf(
                "grant_type" to "authorization_code",
                "code" to callback.first,
                "client_id" to callback.second,
                "code_verifier" to verifier,
                "redirect_uri" to redirect,
                "resource" to SiwcEndpoints.resource
            ))
            val idToken = response.getString("id_token")
            val identity = validateIdToken(idToken, callback.second, nonce)
            if (previous != null) require(identity.first == previous.subject) {
                "Account identity differs from saved registration"
            }
            val scopes = response.optString("scope", "")
            val now = System.currentTimeMillis()
            val session = SiwcSession(
                callback.second, identity.first, identity.second, idToken,
                response.getString("access_token"), response.optString("refresh_token", ""),
                scopes, now + response.optLong("expires_in", 3600L) * 1000L,
                now + response.optLong("earliest_refresh_at", 0L) * 1000L
            )
            store.save(session)
            session
            } finally {
                awaitingCallback = null
                SiwcCallbackService.stop(app)
            }
        }
    }

    /** Verify RSA signature and OIDC claims against the current OpenAI JWKS. */
    private fun validateIdToken(jwt: String, clientId: String, nonce: String): Pair<String, String> {
        val parts = jwt.split('.')
        require(parts.size == 3) { "Invalid ID token format" }
        val header = JSONObject(String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8))
        val claims = JSONObject(String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8))
        require(header.optString("alg") == "RS256") { "Unsupported ID token signing algorithm" }
        val kid = header.getString("kid")
        val keys = httpJson(SiwcEndpoints.jwks).getJSONArray("keys")
        var jwk: JSONObject? = null
        for (i in 0 until keys.length()) {
            val candidate = keys.getJSONObject(i)
            if (candidate.optString("kid") == kid && candidate.optString("kty") == "RSA"
                && candidate.optString("alg") == "RS256") {
                jwk = candidate
                break
            }
        }
        val actualKey = jwk ?: error("OpenAI signing key not found")
        val rsa = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(
            BigInteger(1, base64UrlDecode(actualKey.getString("n"))),
            BigInteger(1, base64UrlDecode(actualKey.getString("e")))
        ))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(rsa)
        signature.update((parts[0] + "." + parts[1]).toByteArray(StandardCharsets.US_ASCII))
        check(signature.verify(base64UrlDecode(parts[2]))) { "Invalid ID token signature" }
        require(claims.getString("iss") == SiwcEndpoints.issuer)
        val audience = claims.get("aud")
        val audOk = if (audience is String) audience == clientId
            else if (audience is org.json.JSONArray)
                (0 until audience.length()).any { audience.optString(it) == clientId }
            else false
        require(audOk) { "Incorrect token audience" }
        if (audience is org.json.JSONArray && audience.length() > 1) {
            require(claims.optString("azp") == clientId) { "Incorrect authorized party" }
        }
        val current = System.currentTimeMillis() / 1000L
        require(claims.getLong("exp") > current) { "Expired ID token" }
        require(claims.optLong("iat", current) <= current + 120)
        require(claims.optLong("nbf", current) <= current + 120)
        require(claims.getString("nonce") == nonce) { "Incorrect ID token nonce" }
        val subject = claims.getString("sub")
        require(subject.isNotBlank())
        return Pair(subject, claims.optString("email", ""))
    }

    suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        val saved = store.load() ?: error("Connect your ChatGPT account first")
        check(saved.canUsePlan) { "ChatGPT plan usage was not authorized" }
        val now = System.currentTimeMillis()
        if (saved.expiresAtMs > now + 60_000L) return@withContext saved.accessToken
        require(saved.refreshToken.isNotBlank()) { "Reconnect ChatGPT to renew the session" }
        val response = postForm(SiwcEndpoints.token, mapOf(
            "grant_type" to "refresh_token",
            "client_id" to saved.clientId,
            "refresh_token" to saved.refreshToken,
            "resource" to SiwcEndpoints.resource
        ))
        val refreshed = saved.copy(
            accessToken = response.getString("access_token"),
            refreshToken = response.getString("refresh_token"),
            idToken = response.optString("id_token", saved.idToken),
            scopes = response.optString("scope", saved.scopes),
            expiresAtMs = now + response.optLong("expires_in", 3600) * 1000L
        )
        check(refreshed.canUsePlan) { "ChatGPT plan permission is no longer granted" }
        store.save(refreshed)
        refreshed.accessToken
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        val session = store.load()
        var revoked = session == null
        if (session != null && session.refreshToken.isNotBlank()) {
            revoked = try {
                postForm(SiwcEndpoints.revoke, mapOf(
                    "token" to session.refreshToken,
                    "token_type_hint" to "refresh_token",
                    "client_id" to session.clientId
                ))
                true
            } catch (_: Exception) { false }
        }
        store.disconnect()
        revoked
    }
}
