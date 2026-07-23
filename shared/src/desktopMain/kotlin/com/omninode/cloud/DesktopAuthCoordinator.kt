package com.omninode.cloud

import com.omninode.di.OmniNodeServices
import com.omninode.network.OmniHttpClientFactory
import com.omninode.platform.DesktopOAuthCallbacks
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Desktop OAuth / Google identity surface.
 * Uses PKCE + loopback redirect (http://127.0.0.1) per Google native-app policy.
 */
object DesktopAuthCoordinator {
    data class PendingAuth(
        val codeVerifier: String,
        val state: String,
        val redirectUri: String
    )

    @Volatile
    private var pending: PendingAuth? = null

    /** Last loopback redirect used — required as Firebase signInWithIdp requestUri. */
    @Volatile
    private var lastOAuthRedirectUri: String = DESKTOP_OAUTH_LOOPBACK_REDIRECT_URI

    fun oauthRedirectUriForFirebase(): String = lastOAuthRedirectUri

    fun beginAuthorizationUrl(webClientId: String): String {
        val verifier = randomUrlSafe(64)
        val challenge = sha256Base64Url(verifier)
        val state = randomUrlSafe(24)
        val redirectUri = DesktopOAuthLoopbackServer.start { result ->
            DesktopOAuthCallbacks.emit(result)
        }
        lastOAuthRedirectUri = redirectUri
        pending = PendingAuth(codeVerifier = verifier, state = state, redirectUri = redirectUri)
        return buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=").append(webClientId.encodeUrl())
            append("&redirect_uri=").append(redirectUri.encodeUrl())
            append("&response_type=code")
            append("&scope=").append("openid%20email%20profile")
            append("&code_challenge=").append(challenge.encodeUrl())
            append("&code_challenge_method=S256")
            append("&state=").append(state.encodeUrl())
            append("&access_type=online")
            append("&prompt=select_account")
        }
    }

    suspend fun exchangeCodeForIdToken(code: String, state: String?): String {
        val pendingAuth = pending ?: error("No pending OAuth request")
        if (state != null && state != pendingAuth.state) {
            error("OAuth state mismatch")
        }
        val clientSecret = googleWebClientSecret()
        require(clientSecret.isNotBlank()) {
            "Set omninode.google.web.client.secret in gradle.properties " +
                "(Google Cloud Console → Web client → Client secret), then rebuild OmniNode.app"
        }
        val redirectUri = pendingAuth.redirectUri
        lastOAuthRedirectUri = redirectUri
        pending = null
        val client = OmniNodeServices.httpClient
        val json = OmniHttpClientFactory.defaultJson
        val response = client.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "code=${code.encodeUrl()}" +
                    "&client_id=${googleWebClientId().encodeUrl()}" +
                    "&client_secret=${clientSecret.encodeUrl()}" +
                    "&redirect_uri=${redirectUri.encodeUrl()}" +
                    "&grant_type=authorization_code" +
                    "&code_verifier=${pendingAuth.codeVerifier.encodeUrl()}"
            )
        }
        if (!response.status.isSuccess()) {
            error("Token exchange failed (${response.status}): ${response.bodyAsText()}")
        }
        val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return obj["id_token"]?.jsonPrimitive?.contentOrNull
            ?: error("Token response missing id_token")
    }

    fun cancelPending() {
        pending = null
        DesktopOAuthLoopbackServer.stop()
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8)
}

actual fun googleWebClientId(): String =
    GeneratedDesktopCloudConfig.WEB_CLIENT_ID.trim().ifBlank {
        System.getProperty("omninode.google.web.client.id").orEmpty()
    }

internal fun googleWebClientSecret(): String =
    System.getenv("OMNINODE_GOOGLE_WEB_CLIENT_SECRET")?.trim().orEmpty().ifBlank {
        GeneratedDesktopCloudConfig.WEB_CLIENT_SECRET.trim()
    }

actual fun firebaseApiKey(): String =
    GeneratedDesktopCloudConfig.FIREBASE_API_KEY.trim().ifBlank {
        System.getProperty("omninode.firebase.api.key").orEmpty()
    }

actual fun firebaseProjectId(): String =
    GeneratedDesktopCloudConfig.FIREBASE_PROJECT_ID.trim().ifBlank {
        System.getProperty("omninode.firebase.project.id").orEmpty()
    }

actual fun currentPlatformLabel(): String = "desktop"
