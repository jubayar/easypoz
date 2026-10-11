package net.redngreen.easypos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only local operation journal (design D5): every mutation commits in
 * one Room transaction that applies the change AND appends its op. `seq` is
 * the push order; `id` is the op UUID — the server idempotency key.
 */
@Entity(
    tableName = "journal_ops",
    indices = [Index(value = ["id"], unique = true), Index("syncedAt")],
)
data class JournalOp(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0L,
    val id: String,
    val shopId: String,
    val deviceId: String,
    val entity: String,
    val opType: String,
    val payloadJson: String,
    val actorUserId: String? = null,
    val createdAt: Long,
    val syncedAt: Long? = null,
)

/** LWW resolutions from pull, surfaced for review in settings (design D5). */
@Entity(tableName = "conflict_log")
data class ConflictLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entity: String,
    val entityId: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val resolution: String, // LOCAL_WINS | REMOTE_WINS
    val at: Long,
)

/** Device-local sync bookkeeping (cursor, last ack, …) — never synced. */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val key: String,
    val value: String,
)
