package com.healthassistant.data.local

import androidx.room.*
import com.healthassistant.data.model.BloodPressureRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BpDao {
    @Query("SELECT * FROM bp_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    fun getAllActive(): Flow<List<BloodPressureRecord>>

    @Query("SELECT * FROM bp_records WHERE deleted = 0 ORDER BY measuredAt DESC")
    suspend fun getAllActiveList(): List<BloodPressureRecord>

    @Query("""
        SELECT * FROM bp_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveSince(since: String): List<BloodPressureRecord>

    @Query("""
        SELECT * FROM bp_records 
        WHERE deleted = 0 AND measuredAt >= :since 
        ORDER BY measuredAt DESC
    """)
    fun getActiveSinceFlow(since: String): Flow<List<BloodPressureRecord>>

    @Query("SELECT * FROM bp_records ORDER BY measuredAt DESC")
    suspend fun getAllIncludingDeleted(): List<BloodPressureRecord>

    @Query("SELECT * FROM bp_records WHERE id = :id")
    suspend fun getById(id: String): BloodPressureRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BloodPressureRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<BloodPressureRecord>)

    @Query("UPDATE bp_records SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("DELETE FROM bp_records WHERE deleted = 1 AND updatedAt < :before")
    suspend fun purgeTombstones(before: String)

    @Query("SELECT AVG(CAST(systolic AS REAL)) FROM bp_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun avgSystolicSince(since: String): Double?

    @Query("SELECT AVG(CAST(diastolic AS REAL)) FROM bp_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun avgDiastolicSince(since: String): Double?

    @Query("SELECT COUNT(*) FROM bp_records WHERE deleted = 0 AND measuredAt >= :since")
    suspend fun totalCountSince(since: String): Int

    @Query("""
        SELECT MAX(systolic) FROM bp_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun maxSystolicSince(since: String): Int?

    @Query("""
        SELECT MIN(systolic) FROM bp_records 
        WHERE deleted = 0 AND measuredAt >= :since
    """)
    suspend fun minSystolicSince(since: String): Int?

    @Query("""
        SELECT * FROM bp_records 
        WHERE deleted = 0 AND measuredAt >= :start AND measuredAt < :end
        ORDER BY measuredAt ASC
    """)
    suspend fun getActiveBetween(start: String, end: String): List<BloodPressureRecord>
}
