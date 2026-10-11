package net.redngreen.easypos.core.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire shapes for the sync backend (design D5/D6). These mirror the NestJS
 * contracts exactly: `src/sync/journal-op.schemas.ts`, `auth.schemas.ts`,
 * `device.schemas.ts`. Timestamps are ISO 8601 strings; money is cents.
 *
 * Ops push the entity payload as a JSON **object** (not a string) so the
 * server's Zod schemas can validate it field-by-field.
 */

@Serializable
data class SyncOpWire(
    val id: String,
    val seq: Long,
    val entity: String,
    val opType: String,
    val actorUserId: String? = null,
    val payload: JsonElement,
    val createdAt: String, // ISO 8601
)

@Serializable
data class PushBatchRequest(
    val deviceId: String,
    val ops: List<SyncOpWire>,
)

@Serializable
data class OpFieldError(
    val field: String,
    val message: String,
)

@Serializable
data class OpAck(
    val id: String,
    val status: String, // applied | duplicate | rejected
    val errors: List<OpFieldError> = emptyList(),
)

@Serializable
data class PushResponse(
    val acks: List<OpAck> = emptyList(),
)

@Serializable
data class PullOpWire(
    val seq: Long,
    val journalOpId: String,
    val entity: String,
    val entityId: String? = null,
    val opType: String,
    val payload: JsonElement,
    val actorUserId: String? = null,
    val createdAt: String,
)

@Serializable
data class PullResponse(
    val ops: List<PullOpWire> = emptyList(),
    val cursor: String = "0",
    val hasMore: Boolean = false,
)

// ─── Auth (POST /auth/*) ────────────────────────────────────────────────────

@Serializable
data class RequestCodeRequest(
    val identifier: String,
    val shopName: String? = null,
)

@Serializable
data class RequestCodeResponse(
    val isNewAccount: Boolean = false,
    val otpExpiresAt: String? = null,
    val devCode: String? = null,
)

@Serializable
data class VerifyRequest(
    val identifier: String,
    val code: String,
    val shopName: String? = null,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val accessExpiresAt: String? = null,
    val refreshToken: String,
    val refreshExpiresAt: String? = null,
)

@Serializable
data class VerifyResponse(
    val shopId: String,
    val isNewAccount: Boolean = false,
    val accessToken: String,
    val accessExpiresAt: String? = null,
    val refreshToken: String,
    val refreshExpiresAt: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

// ─── Device (POST /device/register) ─────────────────────────────────────────

@Serializable
data class DeviceRegisterRequest(
    val deviceName: String? = null,
)

@Serializable
data class DeviceRegisterResponse(
    val deviceId: String,
    val demotedCount: Int = 0,
    val graceMs: Long = 0,
    val accessToken: String,
    val accessExpiresAt: String? = null,
    val refreshToken: String,
    val refreshExpiresAt: String? = null,
)

/** Nest exception body; `message` may be a string or a string array. */
@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val message: JsonElement? = null,
    val error: String? = null,
)

/** Thrown for any non-2xx backend response. */
class ApiException(val status: Int, val apiMessage: String) :
    Exception("HTTP $status: $apiMessage")
