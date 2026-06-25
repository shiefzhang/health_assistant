package com.healthassistant.data.repository

import com.healthassistant.data.local.BpDao
import com.healthassistant.data.local.GlucoseDao
import com.healthassistant.data.local.WeightDao
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthRepository(
    private val glucoseDao: GlucoseDao,
    private val weightDao: WeightDao,
    private val bpDao: BpDao,
) {
    // ═══════════════════ 血糖 ═══════════════════
    fun getAllGlucose(): Flow<List<GlucoseRecord>> = glucoseDao.getAllActive()
    suspend fun getAllGlucoseList(): List<GlucoseRecord> = glucoseDao.getAllActiveList()
    suspend fun upsertGlucose(record: GlucoseRecord) = glucoseDao.upsert(record)
    suspend fun deleteGlucose(id: String) = glucoseDao.softDelete(id, Instant.now().toString())
    suspend fun getGlucoseRecent(days: Int): List<GlucoseRecord> {
        val since = LocalDate.now(ZoneId.systemDefault()).minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        return glucoseDao.getActiveSince(since)
    }
    fun getGlucoseRecentFlow(days: Int): Flow<List<GlucoseRecord>> {
        val since = LocalDate.now(ZoneId.systemDefault()).minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        return glucoseDao.getActiveSinceFlow(since)
    }
    suspend fun getGlucoseStats(days: Int): GlucoseStats {
        val since = sinceInstant(days)
        return GlucoseStats(
            average = glucoseDao.averageSince(since) ?: 0.0,
            max = glucoseDao.maxSince(since) ?: 0.0,
            min = glucoseDao.minSince(since) ?: 0.0,
            inRangeCount = glucoseDao.inRangeCountSince(since),
            totalCount = glucoseDao.totalCountSince(since),
        )
    }

    // ═══════════════════ 体重 ═══════════════════
    fun getAllWeight(): Flow<List<WeightRecord>> = weightDao.getAllActive()
    suspend fun upsertWeight(record: WeightRecord) = weightDao.upsert(record)
    suspend fun deleteWeight(id: String) = weightDao.softDelete(id, Instant.now().toString())
    fun getWeightRecentFlow(days: Int): Flow<List<WeightRecord>> {
        val since = sinceInstant(days)
        return weightDao.getActiveSinceFlow(since)
    }
    suspend fun getWeightStats(days: Int): WeightStats {
        val since = sinceInstant(days)
        return WeightStats(
            average = weightDao.averageSince(since) ?: 0.0,
            max = weightDao.maxSince(since) ?: 0.0,
            min = weightDao.minSince(since) ?: 0.0,
            totalCount = weightDao.totalCountSince(since),
        )
    }
    suspend fun getAllWeightList(): List<WeightRecord> = weightDao.getAllActiveList()

    // ═══════════════════ 血压 ═══════════════════
    fun getAllBp(): Flow<List<BloodPressureRecord>> = bpDao.getAllActive()
    suspend fun upsertBp(record: BloodPressureRecord) = bpDao.upsert(record)
    suspend fun deleteBp(id: String) = bpDao.softDelete(id, Instant.now().toString())
    fun getBpRecentFlow(days: Int): Flow<List<BloodPressureRecord>> {
        val since = sinceInstant(days)
        return bpDao.getActiveSinceFlow(since)
    }
    suspend fun getBpStats(days: Int): BpStats {
        val since = sinceInstant(days)
        return BpStats(
            avgSystolic = bpDao.avgSystolicSince(since) ?: 0.0,
            avgDiastolic = bpDao.avgDiastolicSince(since) ?: 0.0,
            maxSystolic = bpDao.maxSystolicSince(since) ?: 0,
            minSystolic = bpDao.minSystolicSince(since) ?: 0,
            totalCount = bpDao.totalCountSince(since),
        )
    }
    suspend fun getAllBpList(): List<BloodPressureRecord> = bpDao.getAllActiveList()

    // ═══════════════════ 通用 ═══════════════════
    suspend fun importAllGlucose(records: List<GlucoseRecord>): Int {
        val (imported, _) = mergeImportDetailed(records, glucoseDao.getAllIncludingDeleted()) { it.id }
        return imported
    }
    suspend fun importAllWeight(records: List<WeightRecord>): Int {
        val (imported, _) = mergeImportDetailed(records, weightDao.getAllIncludingDeleted()) { it.id }
        return imported
    }
    suspend fun importAllBp(records: List<BloodPressureRecord>): Int {
        val (imported, _) = mergeImportDetailed(records, bpDao.getAllIncludingDeleted()) { it.id }
        return imported
    }

    /** 带去重统计的导入接口 */
    suspend fun importGlucoseDedup(records: List<GlucoseRecord>): ImportResult {
        val (imported, skipped) = mergeImportDetailed(records, glucoseDao.getAllIncludingDeleted()) { it.id }
        return ImportResult(imported, skipped)
    }

    suspend fun importWeightDedup(records: List<WeightRecord>): ImportResult {
        val (imported, skipped) = mergeImportDetailed(records, weightDao.getAllIncludingDeleted()) { it.id }
        return ImportResult(imported, skipped)
    }

    suspend fun importBpDedup(records: List<BloodPressureRecord>): ImportResult {
        val (imported, skipped) = mergeImportDetailed(records, bpDao.getAllIncludingDeleted()) { it.id }
        return ImportResult(imported, skipped)
    }
    suspend fun getDashboardWeightCount(): Int = weightDao.totalCountSince("1970-01-01T00:00:00Z")
    suspend fun getDashboardBpCount(): Int = bpDao.totalCountSince("1970-01-01T00:00:00Z")
    suspend fun getRecentWeight(days: Int): List<WeightRecord> {
        val since = sinceInstant(days)
        return weightDao.getActiveSince(since)
    }
    suspend fun getRecentBp(days: Int): List<BloodPressureRecord> {
        val since = sinceInstant(days)
        return bpDao.getActiveSince(since)
    }
    suspend fun purgeAllTombstones() {
        val before = Instant.now().minusSeconds(90 * 86400L).toString()
        glucoseDao.purgeTombstones(before)
        weightDao.purgeTombstones(before)
        bpDao.purgeTombstones(before)
    }

    suspend fun getGlucoseBetween(start: String, end: String): List<GlucoseRecord> =
        glucoseDao.getActiveBetween(start, end)

    suspend fun getBpBetween(start: String, end: String): List<BloodPressureRecord> =
        bpDao.getActiveBetween(start, end)

    suspend fun getWeightBetween(start: String, end: String): List<WeightRecord> =
        weightDao.getActiveBetween(start, end)

    // ═══════════════════ 辅助 ═══════════════════
    private fun sinceInstant(days: Int): String =
        LocalDate.now(ZoneId.systemDefault()).minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    private suspend fun <T> mergeImport(
        incoming: List<T>,
        existing: List<T>,
        idFn: (T) -> String,
    ): Int {
        val existingMap = existing.associateBy(idFn)
        return incoming.count { incoming ->
            val existingItem = existingMap[idFn(incoming)]
            existingItem == null || incoming.toString().hashCode() != existingItem.toString().hashCode()
        }.also { count ->
            if (count > 0) {
                @Suppress("UNCHECKED_CAST")
                when (incoming.firstOrNull()) {
                    is GlucoseRecord -> glucoseDao.upsertAll(incoming as List<GlucoseRecord>)
                    is WeightRecord -> weightDao.upsertAll(incoming as List<WeightRecord>)
                    is BloodPressureRecord -> bpDao.upsertAll(incoming as List<BloodPressureRecord>)
                }
            }
        }
    }

    /** 带去重统计的合并导入 */
    private suspend fun <T> mergeImportDetailed(
        incoming: List<T>,
        existing: List<T>,
        idFn: (T) -> String,
    ): Pair<Int, Int> {
        val existingMap = existing.associateBy(idFn)
        val changed = mutableListOf<T>()
        var skipped = 0
        for (item in incoming) {
            val existingItem = existingMap[idFn(item)]
            if (existingItem == null || item.toString() != existingItem.toString()) {
                changed.add(item)
            } else {
                skipped++
            }
        }
        if (changed.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            when (incoming.firstOrNull()) {
                is GlucoseRecord -> glucoseDao.upsertAll(changed as List<GlucoseRecord>)
                is WeightRecord -> weightDao.upsertAll(changed as List<WeightRecord>)
                is BloodPressureRecord -> bpDao.upsertAll(changed as List<BloodPressureRecord>)
            }
        }
        return Pair(changed.size, skipped)
    }
}

data class GlucoseStats(val average: Double, val max: Double, val min: Double, val inRangeCount: Int, val totalCount: Int) {
    val inRangePercent: Int get() = if (totalCount > 0) inRangeCount * 100 / totalCount else 0
}
data class WeightStats(val average: Double, val max: Double, val min: Double, val totalCount: Int)
data class BpStats(val avgSystolic: Double, val avgDiastolic: Double, val maxSystolic: Int, val minSystolic: Int, val totalCount: Int)
data class ImportResult(val imported: Int, val skipped: Int) {
    val total: Int get() = imported + skipped
}
