package net.redngreen.easypos.core.data.sync

import java.time.Instant
import kotlinx.serialization.json.Json
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.JournalDao
import net.redngreen.easypos.core.data.entity.JournalOp

/** Result of one sync pass. */
sealed interface SyncOutcome {
    data class Success(val pushed: Int, val pulled: Int) : SyncOutcome

    /** No account is linked — nothing to sync (offline-only operation). */
    data object NotSignedIn : SyncOutcome

    data class Failure(val message: String) : SyncOutcome
}

/**
 * The background sync engine (design D5, tasks 12.1–12.2). One pass =
 * push every pending op in ordered batches, then pull and apply remote
 * changes. It never runs on the sell path; callers are the WorkManager
 * worker and the connectivity/foreground triggers.
 *
 * Push is idempotent by op id (server-side), so a retry after a partial
 * batch is safe. Pull skips self-echo ops (our own pushed rows come back in
 * the change log) before applying.
 */
class SyncEngine(
    private val journalDao: JournalDao,
    private val store: LocalSyncStore,
    private val remote: SyncRemoteDataSource,
    private val auth: AuthRemoteDataSource,
    private val tokens: TokenStore,
    private val identity: LocalIdentity,
    private val applier: RemoteApplier,
    private val status: SyncStatusRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Full pass: refresh token if needed → push → pull. */
    suspend fun syncOnce(): SyncOutcome {
        val stored = tokens.load()
        if (stored == null) {
            status.setSignedIn(false)
            return SyncOutcome.NotSignedIn
        }
        status.setSignedIn(true)
        status.markSyncing()

        val token = try {
            freshAccessToken(stored)
        } catch (e: Exception) {
            status.markError(messageOf(e))
            return SyncOutcome.Failure(messageOf(e))
        }
        if (token == null) {
            status.markError(REFRESH_FAILED)
            return SyncOutcome.Failure(REFRESH_FAILED)
        }

        return try {
            val pushed = pushPending(token)
            val pulled = pullAndApply(token)
            status.markDone()
            SyncOutcome.Success(pushed, pulled)
        } catch (e: Exception) {
            status.markError(messageOf(e))
            SyncOutcome.Failure(messageOf(e))
        }
    }

    /** Device replacement / first link (task 12.5): full restore from zero. */
    suspend fun restoreFromCloud(): SyncOutcome {
        store.setCursor(0L)
        return syncOnce()
    }

    /**
     * Pushes pending ops in ordered batches of ≤200 (design D5). Ops acked
     * `applied` or `duplicate` are marked synced. Ops rejected as malformed
     * are also marked synced — a permanently invalid op must not wedge the
     * queue forever (the writers are the only source of these payloads).
     */
    suspend fun pushPending(token: String): Int {
        var pushed = 0
        var batches = 0
        while (batches++ < MAX_PUSH_BATCHES) {
            val pending = journalDao.pending(BATCH_SIZE)
            if (pending.isEmpty()) break

            val response = remote.push(
                token,
                PushBatchRequest(deviceId = identity.deviceId(), ops = pending.map { it.toWire() }),
            )
            val ackedIds = response.acks.map { it.id }.toSet()
            val toMark = pending.map { it.id }.filter { it in ackedIds }
            if (toMark.isEmpty()) break // server acked nothing — stop, don't spin
            journalDao.markSynced(toMark, clock())
            pushed += toMark.size
        }
        return pushed
    }

    /**
     * Pulls from the persisted cursor and applies each op with LWW. Self-echo
     * ops are skipped. The cursor only advances after ops are applied, so a
     * crash mid-page re-pulls the page (idempotent).
     */
    suspend fun pullAndApply(token: String): Int {
        var applied = 0
        var pages = 0
        while (pages++ < MAX_PULL_PAGES) {
            val since = store.cursor()
            val response = remote.pull(token, since)

            for (op in response.ops) {
                if (store.journalExists(op.journalOpId)) continue
                applier.apply(op)
                applied++
            }

            val next = response.cursor.toLongOrNull() ?: since
            store.setCursor(next)
            if (!response.hasMore || response.ops.isEmpty() || next == since) break
        }
        return applied
    }

    /** Returns a valid access token, refreshing when it is expired (or near). */
    private suspend fun freshAccessToken(stored: AuthTokens): String? {
        if (clock() < stored.accessExpiresAt - EXPIRY_SKEW_MS) return stored.accessToken
        val pair = auth.refresh(stored.refreshToken)
        val refreshed = AuthTokens(
            shopId = stored.shopId,
            accessToken = pair.accessToken,
            refreshToken = pair.refreshToken,
            accessExpiresAt = isoMillis(pair.accessExpiresAt ?: ""),
            refreshExpiresAt = isoMillis(pair.refreshExpiresAt ?: ""),
        )
        tokens.save(refreshed)
        return refreshed.accessToken
    }

    private fun JournalOp.toWire() = SyncOpWire(
        id = id,
        seq = seq,
        entity = entity,
        opType = opType,
        actorUserId = actorUserId,
        payload = json.parseToJsonElement(payloadJson),
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
    )

    private fun messageOf(e: Exception): String = when (e) {
        is ApiException -> e.apiMessage
        else -> e.message ?: "sync failed"
    }

    companion object {
        const val BATCH_SIZE = 200
        private const val MAX_PUSH_BATCHES = 1_000
        private const val MAX_PULL_PAGES = 10_000
        private const val EXPIRY_SKEW_MS = 60_000L
        private const val REFRESH_FAILED = "session expired — sign in again"
    }
}
