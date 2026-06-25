package com.healthassistant.data.local

import androidx.room.*
import com.healthassistant.data.model.WeightRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    fun getAllActive(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    suspend fun getAllActiveList(): List<WeightRecord>

    @Query("""
        SELECT * FROM weight_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveSince(since: String): List<WeightRecord>

    @Query("""
        SELECT * FROM weight_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt DESC
    """)
    fun getActiveSinceFlow(since: String): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC")
    suspend fun getAllIncludingDeleted(): List<WeightRecord>

    @Query("SELECT * FROM weight_records WHERE id = :id")
    suspend fun getById(id: String): WeightRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WeightRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<WeightRecord>)

    @Query("UPDATE weight_records SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("DELETE FROM weight_records WHERE deleted = 1 AND updatedAt < :before")
    suspend fun purgeTombstones(before: String)

    @Query("SELECT AVG(value) FROM weight_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun averageSince(since: String): Double?

    @Query("SELECT MAX(value) FROM weight_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun maxSince(since: String): Double?

    @Query("SELECT MIN(value) FROM weight_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun minSince(since: String): Double?

    @Query("SELECT COUNT(*) FROM weight_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun totalCountSince(since: String): Int

    @Query("""
        SELECT * FROM weight_records 
        WHERE deleted = 0 AND measuredAt >= :start AND measuredAt < :end
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveBetween(start: String, end: String): List<WeightRecord>
}
