package net.redngreen.easypos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one place an [HttpClient] is configured. The engine is injected so tests
 * swap in a mock engine; the sync stack attaches per-call auth headers itself
 * (refresh is owned by the sync engine), so the bearer plugin is opt-in.
 */
object HttpClientFactory {

    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val REQUEST_TIMEOUT_MS = 30_000L
    private const val READ_TIMEOUT_MS = 30_000L

    fun create(
        engine: HttpClientEngine,
        tokenStore: TokenStore? = null,
    ): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = READ_TIMEOUT_MS
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        tokenStore?.let { store ->
            install(Auth) {
                bearer {
                    loadTokens {
                        store.load()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    }
                }
            }
        }
    }
}
