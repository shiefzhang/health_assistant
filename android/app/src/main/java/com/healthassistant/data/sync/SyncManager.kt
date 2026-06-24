package com.healthassistant.data.sync

import com.healthassistant.data.model.AppSettings
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * 同步管理器 — 每个记录独立保存为单独文件
 *
 * 索引文件格式：
 * {
 *   "glucose": [{"id":"...", "updatedAt":"..."}, ...],
 *   "weight":  [{"id":"...", "updatedAt":"..."}, ...],
 *   "bloodPressure": [{"id":"...", "updatedAt":"..."}, ...]
 * }
 *
 * 记录文件：{filename}/{type}/{id}.json
 */
class SyncManager(private val repository: HealthRepository) {

    suspend fun sync(settings: AppSettings): SyncResult {
        require(settings.webdavUrl.isNotBlank()) { "请先配置 WebDAV 地址" }
        require(settings.webdavUser.isNotBlank()) { "请先配置 WebDAV 用户名" }

        // 1. 下载远程索引
        val remoteIndexText = WebDavClient.downloadIndex(settings)
        val remoteIndex = if (remoteIndexText != null) JSONObject(remoteIndexText) else JSONObject()

        // 2. 下载远程新增/更新的记录
        var importedGlucose = 0
        var importedWeight = 0
        var importedBp = 0

        val localGlucose = repository.getAllGlucoseList()
        val localWeight = repository.getAllWeightList()
        val localBp = repository.getAllBpList()

        importedGlucose = downloadMissing(settings, remoteIndex, "glucose", localGlucose) { json ->
            repository.importAllGlucose(listOf(GlucoseRecord.fromJson(json)))
        }
        importedWeight = downloadMissing(settings, remoteIndex, "weight", localWeight) { json ->
            repository.importAllWeight(listOf(WeightRecord.fromJson(json)))
        }
        importedBp = downloadMissing(settings, remoteIndex, "bloodPressure", localBp) { json ->
            repository.importAllBp(listOf(BloodPressureRecord.fromJson(json)))
        }

        repository.purgeAllTombstones()

        // 3. 上传本地新增/更新的记录，并构建新索引
        val newIndex = JSONObject()

        newIndex.put("glucose", uploadChanged(settings, remoteIndex, "glucose",
            repository.getAllGlucoseList()))
        newIndex.put("weight", uploadChanged(settings, remoteIndex, "weight",
            repository.getAllWeightList()))
        newIndex.put("bloodPressure", uploadChanged(settings, remoteIndex, "bloodPressure",
            repository.getAllBpList()))

        // 4. 上传新索引
        WebDavClient.uploadIndex(settings, newIndex.toString(2))

        // 5. 同步 AI 分析结果（双向：远程→本地 + 本地→远程，每个指标仅最新一条）
        syncAnalysis(settings)

        return SyncResult(importedGlucose, importedWeight, importedBp)
    }

    suspend fun uploadOnly(settings: AppSettings) {
        val remoteIndexText = WebDavClient.downloadIndex(settings)
        val remoteIndex = if (remoteIndexText != null) JSONObject(remoteIndexText) else JSONObject()
        val newIndex = JSONObject()

        newIndex.put("glucose", uploadChanged(settings, remoteIndex, "glucose",
            repository.getAllGlucoseList()))
        newIndex.put("weight", uploadChanged(settings, remoteIndex, "weight",
            repository.getAllWeightList()))
        newIndex.put("bloodPressure", uploadChanged(settings, remoteIndex, "bloodPressure",
            repository.getAllBpList()))

        WebDavClient.uploadIndex(settings, newIndex.toString(2))
        syncAnalysis(settings)
    }

    // ── 私有辅助 ──

    /** 下载远程比本地新的记录 */
    private suspend fun downloadMissing(
        settings: AppSettings,
        remoteIndex: JSONObject,
        type: String,
        localRecords: List<*>,
        importFn: suspend (JSONObject) -> Int,
    ): Int {
        val remoteEntries = remoteIndex.optJSONArray(type) ?: return 0
        val localMap = localRecords.filterNotNull().associateBy { recordId(it) }
        var count = 0

        for (i in 0 until remoteEntries.length()) {
            val entry = remoteEntries.getJSONObject(i)
            val remoteId = entry.getString("id")
            val remoteUpdatedAt = entry.optString("updatedAt", "")
            val localRecord = localMap[remoteId]
            val localUpdatedAt = if (localRecord != null) recordUpdatedAt(localRecord) else ""

            if (localRecord == null || remoteUpdatedAt > localUpdatedAt) {
                val text = WebDavClient.downloadRecord(settings, type, remoteId)
                if (text != null) {
                    importFn(JSONObject(text))
                    count++
                }
            }
        }
        return count
    }

    /** 上传本地比远程新的记录，返回索引条目数组 */
    private suspend fun uploadChanged(
        settings: AppSettings,
        remoteIndex: JSONObject,
        type: String,
        localRecords: List<*>,
    ): JSONArray {
        val arr = JSONArray()
        val remoteArr = remoteIndex.optJSONArray(type)

        for (record in localRecords.filterNotNull()) {
            val id = recordId(record)
            val updatedAt = recordUpdatedAt(record)
            val remoteEntry = findById(remoteArr, id)
            val needUpload = remoteEntry == null || remoteEntry.optString("updatedAt") < updatedAt

            if (needUpload) {
                val json = recordToJsonString(record)
                WebDavClient.uploadRecord(settings, type, id, json)
            }

            arr.put(JSONObject().apply {
                put("id", id)
                put("updatedAt", updatedAt)
            })
        }
        return arr
    }

    private fun recordId(record: Any): String = when (record) {
        is GlucoseRecord -> record.id
        is WeightRecord -> record.id
        is BloodPressureRecord -> record.id
        else -> throw IllegalArgumentException()
    }

    private fun recordUpdatedAt(record: Any): String = when (record) {
        is GlucoseRecord -> record.updatedAt
        is WeightRecord -> record.updatedAt
        is BloodPressureRecord -> record.updatedAt
        else -> throw IllegalArgumentException()
    }

    private fun recordToJsonString(record: Any): String = when (record) {
        is GlucoseRecord -> record.toJson().toString(2)
        is WeightRecord -> record.toJson().toString(2)
        is BloodPressureRecord -> record.toJson().toString(2)
        else -> throw IllegalArgumentException()
    }

    private fun findById(arr: JSONArray?, id: String): JSONObject? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            if (entry.getString("id") == id) return entry
        }
        return null
    }

    /** 同步 AI 分析结果：下载远程 → 覆盖本地，上传本地 → 覆盖远程（每个指标只保留最新一条） */
    private suspend fun syncAnalysis(settings: AppSettings) {
        val analysisStore = com.healthassistant.data.sync.AnalysisStore
        for (metric in listOf("glucose", "weight", "bloodPressure")) {
            // 下载远程
            try {
                val remoteJson = WebDavClient.downloadAnalysis(settings, metric)
                if (remoteJson != null) {
                    val obj = JSONObject(remoteJson)
                    val remoteText = obj.optString("text", "")
                    val remoteTime = obj.optString("updatedAt", "")
                    // 如果远程比本地新（或本地没有），覆盖本地
                    val localTime = analysisStore.getUpdatedAt(metric)
                    if (remoteText.isNotBlank() && (localTime == null || remoteTime > localTime)) {
                        analysisStore.save(metric, remoteText, remoteTime)
                    }
                }
            } catch (_: Exception) { /* 忽略下载失败 */ }

            // 上传本地
            try {
                val localText = analysisStore.getText(metric)
                val localTime = analysisStore.getUpdatedAt(metric)
                if (localText != null && localTime != null) {
                    val payload = JSONObject().apply {
                        put("text", localText)
                        put("updatedAt", localTime)
                    }
                    WebDavClient.uploadAnalysis(settings, metric, payload.toString(2))
                }
            } catch (_: Exception) { /* 忽略上传失败 */ }
        }
    }
}

data class SyncResult(
    val importedGlucose: Int,
    val importedWeight: Int,
    val importedBp: Int,
) {
    val totalImported: Int get() = importedGlucose + importedWeight + importedBp
}
