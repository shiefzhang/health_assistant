package com.healthassistant.util

import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 数据导入导出工具，兼容 kv-export.json 和统一交换格式
 */
object ExportImportUtil {

    /**
     * 导出为统一 JSON 格式（包含所有指标类型）
     */
    fun exportToJson(
        glucoseRecords: List<GlucoseRecord>,
        weightRecords: List<WeightRecord> = emptyList(),
        bpRecords: List<BloodPressureRecord> = emptyList(),
    ): String {
        val glucoseArr = JSONArray().apply {
            glucoseRecords.forEach { put(it.toJson()) }
        }
        val weightArr = JSONArray().apply {
            weightRecords.forEach { put(it.toJson()) }
        }
        val bpArr = JSONArray().apply {
            bpRecords.forEach { put(it.toJson()) }
        }
        return JSONObject()
            .put("format", "health-assistant")
            .put("version", 2)
            .put("exportedAt", Instant.now().toString())
            .put("glucose", glucoseArr)
            .put("weight", weightArr)
            .put("bloodPressure", bpArr)
            .toString(2)
    }

    /** 从 JSON 导入血糖记录 */
    fun importGlucoseFromJson(text: String): List<GlucoseRecord> {
        val root = JSONObject(text)
        val result = mutableListOf<GlucoseRecord>()

        // 新版统一格式（v2）
        if (root.has("glucose")) {
            val array = root.getJSONArray("glucose")
            for (i in 0 until array.length()) {
                result.add(GlucoseRecord.fromJson(array.getJSONObject(i)))
            }
            return result
        }

        // 新版统一格式（v1）：records 数组
        if (root.has("records")) {
            val array = root.getJSONArray("records")
            for (i in 0 until array.length()) {
                result.add(GlucoseRecord.fromJson(array.getJSONObject(i)))
            }
            return result
        }

        // 旧版 kv-export.json 格式
        root.keys().forEach { key ->
            if (key.startsWith("record_")) {
                val raw = root.get(key)
                val obj = when (raw) {
                    is String -> JSONObject(raw)
                    is JSONObject -> raw
                    else -> return@forEach
                }
                result.add(GlucoseRecord.fromJson(obj))
            }
        }
        return result
    }

    /** 从 JSON 导入体重记录 */
    fun importWeightFromJson(text: String): List<WeightRecord> {
        val root = JSONObject(text)
        val result = mutableListOf<WeightRecord>()
        if (root.has("weight")) {
            val array = root.getJSONArray("weight")
            for (i in 0 until array.length()) {
                result.add(WeightRecord.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }

    /** 从 JSON 导入血压记录 */
    fun importBpFromJson(text: String): List<BloodPressureRecord> {
        val root = JSONObject(text)
        val result = mutableListOf<BloodPressureRecord>()
        if (root.has("bloodPressure")) {
            val array = root.getJSONArray("bloodPressure")
            for (i in 0 until array.length()) {
                result.add(BloodPressureRecord.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }
}
