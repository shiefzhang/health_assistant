package com.healthassistant.data.local

import androidx.room.*
import com.healthassistant.data.model.GlucoseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    fun getAllActive(): Flow<List<GlucoseRecord>>

    @Query("SELECT * FROM glucose_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    suspend fun getAllActiveList(): List<GlucoseRecord>

    @Query("SELECT * FROM glucose_records WHERE deleted = 1 ORDER BY updatedAt DESC")
    suspend fun getAllDeleted(): List<GlucoseRecord>

    @Query("SELECT * FROM glucose_records ORDER BY measuredAt DESC")
    suspend fun getAllIncludingDeleted(): List<GlucoseRecord>

    @Query("SELECT * FROM glucose_records WHERE id = :id")
    suspend fun getById(id: String): GlucoseRecord?

    @Query("""
        SELECT * FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveSince(since: String): List<GlucoseRecord>

    @Query("""
        SELECT * FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt DESC
    """)
    fun getActiveSinceFlow(since: String): Flow<List<GlucoseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: GlucoseRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<GlucoseRecord>)

    @Query("UPDATE glucose_records SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("DELETE FROM glucose_records WHERE deleted = 1 AND updatedAt < :before")
    suspend fun purgeTombstones(before: String)

    @Query("SELECT COUNT(*) FROM glucose_records WHERE deleted = 0")
    fun countActive(): Flow<Int>

    @Query("""
        SELECT AVG(value) FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun averageSince(since: String): Double?

    @Query("""
        SELECT MAX(value) FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun maxSince(since: String): Double?

    @Query("""
        SELECT MIN(value) FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun minSince(since: String): Double?

    @Query("""
        SELECT COUNT(*) FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since AND value >= 3.9 AND value <= 10.0
    """)
    suspend fun inRangeCountSince(since: String): Int

    @Query("""
        SELECT COUNT(*) FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun totalCountSince(since: String): Int

    @Query("""
        SELECT * FROM glucose_records 
        WHERE deleted = 0 AND measuredAt >= :start AND measuredAt < :end
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveBetween(start: String, end: String): List<GlucoseRecord>
}
