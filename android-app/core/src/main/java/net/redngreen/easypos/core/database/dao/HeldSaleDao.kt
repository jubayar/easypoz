package net.redngreen.easypos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.database.entity.HeldSale

/** Parked carts — local-only, never synced (design D4). */
@Dao
interface HeldSaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(held: HeldSale)

    @Query("DELETE FROM held_sales WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM held_sales ORDER BY heldAt DESC")
    fun observeAll(): Flow<List<HeldSale>>
}
