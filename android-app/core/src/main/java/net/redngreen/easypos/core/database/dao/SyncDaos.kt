package net.redngreen.easypos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.database.entity.ConflictLog
import net.redngreen.easypos.core.database.entity.JournalOp
import net.redngreen.easypos.core.database.entity.SyncState

@Dao
interface JournalDao {
    @Insert
    suspend fun append(op: JournalOp): Long

    /** Push batch source: unsynced ops in commit order (design D5, ≤200/batch). */
    @Query("SELECT * FROM journal_ops WHERE syncedAt IS NULL ORDER BY seq LIMIT :limit")
    suspend fun pending(limit: Int): List<JournalOp>

    @Query("SELECT COUNT(*) FROM journal_ops WHERE syncedAt IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE journal_ops SET syncedAt = :at WHERE id IN (:opIds)")
    suspend fun markSynced(opIds: List<String>, at: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM journal_ops WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}

@Dao
interface ConflictLogDao {
    @Insert
    suspend fun insert(conflict: ConflictLog)

    @Query("SELECT * FROM conflict_log ORDER BY at DESC LIMIT 200")
    fun observeRecent(): Flow<List<ConflictLog>>
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncState)
}
