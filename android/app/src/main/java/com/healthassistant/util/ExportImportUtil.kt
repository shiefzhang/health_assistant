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

    /** 从 JSON 导入血糖记录（兼容对象、数组、kv-export 三种格式） */
    fun importGlucoseFromJson(text: String): List<GlucoseRecord> {
        val trimmed = text.trim()

        // 纯 JSON 数组格式 [ {...}, ... ]
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            val result = mutableListOf<GlucoseRecord>()
            for (i in 0 until array.length()) {
                result.add(GlucoseRecord.fromJson(array.getJSONObject(i)))
            }
            return result
        }

        val root = JSONObject(trimmed)
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

    /** 从 JSON 导入体重记录（兼容对象和数组格式） */
    fun importWeightFromJson(text: String): List<WeightRecord> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) return emptyList() // 纯数组格式不含体重
        val root = JSONObject(trimmed)
        val result = mutableListOf<WeightRecord>()
        if (root.has("weight")) {
            val array = root.getJSONArray("weight")
            for (i in 0 until array.length()) {
                result.add(WeightRecord.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }

    /** 从 JSON 导入血压记录（兼容对象和数组格式） */
    fun importBpFromJson(text: String): List<BloodPressureRecord> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) return emptyList()
        val root = JSONObject(trimmed)
        val result = mutableListOf<BloodPressureRecord>()
        if (root.has("bloodPressure")) {
            val array = root.getJSONArray("bloodPressure")
            for (i in 0 until array.length()) {
                result.add(BloodPressureRecord.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }

    /**
     * 按 type 字段路由导入到对应数据表
     * @return Triple(glucose, weight, bloodPressure)
     */
    fun importByType(text: String): Triple<List<GlucoseRecord>, List<WeightRecord>, List<BloodPressureRecord>> {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) {
            // 非数组格式 → 回退到原逻辑
            return Triple(importGlucoseFromJson(text), importWeightFromJson(text), importBpFromJson(text))
        }

        val array = JSONArray(trimmed)
        val glucose = mutableListOf<GlucoseRecord>()
        val weight = mutableListOf<WeightRecord>()
        val bp = mutableListOf<BloodPressureRecord>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.optString("type", "")

            when (type) {
                "blood_glucose" -> glucose.add(GlucoseRecord.fromJson(obj))
                "weight" -> weight.add(WeightRecord.fromJson(obj))
                "blood_pressure", "bp" -> bp.add(BloodPressureRecord.fromJson(obj))
                else -> {
                    // 无 type 或未知 type → 按字段推断
                    if (obj.has("systolic") || obj.has("diastolic")) {
                        bp.add(BloodPressureRecord.fromJson(obj))
                    } else if (obj.has("heightCm") || obj.has("bodyFatPercent")) {
                        weight.add(WeightRecord.fromJson(obj))
                    } else {
                        glucose.add(GlucoseRecord.fromJson(obj))
                    }
                }
            }
        }
        return Triple(glucose, weight, bp)
    }
}
