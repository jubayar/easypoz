package net.redngreen.easypos.core.data.journal

import androidx.room.withTransaction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.redngreen.easypos.core.data.db.EasyPosDatabase
import net.redngreen.easypos.core.data.entity.JournalOp

/**
 * The single door for synced mutations (design D5): applies the change and
 * appends its JournalOp in ONE Room transaction, so the journal never lags
 * state. Anything that must reach the cloud goes through [journalized].
 */
@Singleton
class JournalizedWriter @Inject constructor(
    private val db: EasyPosDatabase,
) {
    @PublishedApi
    internal val json = Json { encodeDefaults = true }

    /** One journal op to append atomically alongside a mutation. */
    data class OpSpec(
        val entity: String,
        val opType: String,
        val payloadJson: String,
    )

    /**
     * Runs [block] inside a transaction; if it returns a non-null op
     * description, the op row is appended before commit. On rollback the op
     * vanishes with the change — atomic by construction.
     */
    suspend fun journalized(
        shopId: String,
        deviceId: String,
        entity: String,
        opType: String,
        actorUserId: String?,
        payloadJson: String,
        block: suspend () -> Unit,
    ) {
        journalizedBatch(
            shopId,
            deviceId,
            actorUserId,
            listOf(OpSpec(entity, opType, payloadJson)),
            block,
        )
    }

    /**
     * Multi-op variant: one transaction appends several ops (e.g. a product
     * upsert plus its initial-stock delta) with the change — journal never
     * lags state, even for compound mutations.
     */
    suspend fun journalizedBatch(
        shopId: String,
        deviceId: String,
        actorUserId: String?,
        specs: List<OpSpec>,
        block: suspend () -> Unit,
    ) {
        val opIds = specs.map { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        db.withTransaction {
            block()
            specs.forEachIndexed { index, spec ->
                db.journalDao().append(
                    JournalOp(
                        id = opIds[index],
                        shopId = shopId,
                        deviceId = deviceId,
                        entity = spec.entity,
                        opType = spec.opType,
                        payloadJson = spec.payloadJson,
                        actorUserId = actorUserId,
                        createdAt = now,
                    )
                )
            }
        }
    }

    /** Encodes a @Serializable payload to the JSON string the journal stores. */
    inline fun <reified T> encode(payload: T): String = json.encodeToString(payload)
}
