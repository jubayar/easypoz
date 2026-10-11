package net.redngreen.easypos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.database.entity.ActivityLog

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLog)

    @Query("SELECT * FROM activity_log ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_log ORDER BY at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<ActivityLog>
}
