package net.redngreen.easypos.core.data.sync

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
 * JSON-over-HTTP implementation of both remote data sources. Thin by design:
 * every non-2xx response becomes an [ApiException]; the engine owns retry logic.
 */
@Singleton
class HttpBackendApi @Inject constructor(
    private val transport: HttpTransport,
    private val config: SyncConfig,
) : SyncRemoteDataSource, AuthRemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun push(accessToken: String, batch: PushBatchRequest): PushResponse {
        val body = json.encodeToString(PushBatchRequest.serializer(), batch)
        return json.decodeFromString(PushResponse.serializer(), post("/sync/push", body, accessToken))
    }

    override suspend fun pull(accessToken: String, since: Long): PullResponse {
        val text = get("/sync/pull?since=$since", accessToken)
        return json.decodeFromString(PullResponse.serializer(), text)
    }

    override suspend fun requestCode(identifier: String, shopName: String?): RequestCodeResponse {
        val body = json.encodeToString(
            RequestCodeRequest.serializer(),
            RequestCodeRequest(identifier = identifier, shopName = shopName),
        )
        return json.decodeFromString(RequestCodeResponse.serializer(), post("/auth/register", body))
    }

    override suspend fun verify(identifier: String, code: String, shopName: String?): VerifyResponse {
        val body = json.encodeToString(
            VerifyRequest.serializer(),
            VerifyRequest(identifier = identifier, code = code, shopName = shopName),
        )
        return json.decodeFromString(VerifyResponse.serializer(), post("/auth/verify", body))
    }

    override suspend fun refresh(refreshToken: String): TokenPair {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        return json.decodeFromString(TokenPair.serializer(), post("/auth/refresh", body))
    }

    override suspend fun registerDevice(
        accessToken: String,
        deviceName: String?,
    ): DeviceRegisterResponse {
        val body = json.encodeToString(
            DeviceRegisterRequest.serializer(),
            DeviceRegisterRequest(deviceName = deviceName),
        )
        return json.decodeFromString(
            DeviceRegisterResponse.serializer(),
            post("/device/register", body, accessToken),
        )
    }

    private suspend fun get(path: String, token: String? = null): String {
        val response = transport.execute(
            method = "GET",
            url = config.baseUrl() + path,
            headers = authHeaders(token),
        )
        return response.bodyOrThrow()
    }

    private suspend fun post(path: String, body: String, token: String? = null): String {
        val response = transport.execute(
            method = "POST",
            url = config.baseUrl() + path,
            headers = authHeaders(token),
            body = body,
        )
        return response.bodyOrThrow()
    }

    private fun authHeaders(token: String?): Map<String, String> =
        if (token == null) emptyMap() else mapOf("Authorization" to "Bearer $token")

    private fun HttpResponse.bodyOrThrow(): String {
        if (status in 200..299) return body
        throw ApiException(status, parseMessage())
    }

    private fun HttpResponse.parseMessage(): String {
        val parsed = runCatching {
            json.decodeFromString(ApiErrorBody.serializer(), body)
        }.getOrNull() ?: return body.take(300)
        return when (val message = parsed.message) {
            is JsonPrimitive -> message.content
            is JsonArray -> message.joinToString("; ") { (it as? JsonPrimitive)?.content ?: it.toString() }
            null -> parsed.error ?: "request failed"
            else -> message.toString()
        }
    }
}
