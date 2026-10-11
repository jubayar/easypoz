package net.redngreen.easypos.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Push/pull against the shop's cloud account. */
interface SyncRemoteDataSource {
    suspend fun push(accessToken: String, batch: PushBatchRequest): PushResponse

    suspend fun pull(accessToken: String, since: Long): PullResponse
}

/** Account, OTP, token, and device-registration endpoints. */
interface AuthRemoteDataSource {
    suspend fun requestCode(identifier: String, shopName: String? = null): RequestCodeResponse

    suspend fun verify(identifier: String, code: String, shopName: String? = null): VerifyResponse

    suspend fun refresh(refreshToken: String): TokenPair

    suspend fun registerDevice(accessToken: String, deviceName: String?): DeviceRegisterResponse
}

/**
 * Ktor implementation of both remote data sources. Thin by design: every
 * non-2xx response becomes an [ApiException]; the engine owns retry logic.
 * The base URL is device-local state (see [SyncConfig]); tokens travel as
 * explicit per-call headers — refresh stays owned by the sync engine.
 */
@Singleton
class HttpBackendApi @Inject constructor(
    private val client: HttpClient,
    private val config: SyncConfig,
) : SyncRemoteDataSource, AuthRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun push(accessToken: String, batch: PushBatchRequest): PushResponse =
        post("/sync/push", accessToken, batch)

    override suspend fun pull(accessToken: String, since: Long): PullResponse =
        get("/sync/pull?since=$since", accessToken)

    override suspend fun requestCode(identifier: String, shopName: String?): RequestCodeResponse =
        post("/auth/register", null, RequestCodeRequest(identifier = identifier, shopName = shopName))

    override suspend fun verify(identifier: String, code: String, shopName: String?): VerifyResponse =
        post("/auth/verify", null, VerifyRequest(identifier = identifier, code = code, shopName = shopName))

    override suspend fun refresh(refreshToken: String): TokenPair =
        post("/auth/refresh", null, RefreshRequest(refreshToken))

    override suspend fun registerDevice(
        accessToken: String,
        deviceName: String?,
    ): DeviceRegisterResponse =
        post("/device/register", accessToken, DeviceRegisterRequest(deviceName = deviceName))

    private suspend inline fun <reified T : Any> get(path: String, token: String?): T =
        checked(client.get(config.baseUrl() + path) { auth(token) })

    private suspend inline fun <reified T : Any> post(path: String, token: String?, body: Any): T =
        checked(client.post(config.baseUrl() + path) {
            auth(token)
            setBody(body)
        })

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String?) {
        if (token != null) header("Authorization", "Bearer $token")
    }

    private suspend inline fun <reified T : Any> checked(response: HttpResponse): T {
        val text = response.bodyAsText()
        val status = response.status.value
        if (status !in 200..299) throw ApiException(status, parseMessage(text))
        return json.decodeFromString(text)
    }

    private fun parseMessage(body: String): String {
        val parsed = runCatching {
            json.decodeFromString<ApiErrorBody>(body)
        }.getOrNull() ?: return body.take(300)
        return when (val message = parsed.message) {
            is JsonPrimitive -> message.content
            is JsonArray -> message.joinToString("; ") { (it as? JsonPrimitive)?.content ?: it.toString() }
            null -> parsed.error ?: "request failed"
            else -> message.toString()
        }
    }
}
