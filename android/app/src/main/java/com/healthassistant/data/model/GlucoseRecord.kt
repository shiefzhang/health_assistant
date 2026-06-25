package com.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.time.Instant

/**
 * 血糖记录实体，兼容 kv-export.json 和统一交换格式。
 * type: blood_glucose（血糖）、urine_glucose（尿糖）等
 * mealType: 空腹、餐前、餐后2小时、睡前、晚餐后
 */
@Entity(tableName = "glucose_records")
data class GlucoseRecord(
    @PrimaryKey val id: String,
    val value: Double,
    val measuredAt: String,      // ISO-8601: "2026-02-07T08:09:00+08:00"
    val type: String = "blood_glucose",
    val mealType: String = "空腹",
    val notes: String = "",
    val updatedAt: String = Instant.now().toString(),
    val deleted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("value", value)
        .put("measuredAt", measuredAt)
        .put("type", type)
        .put("mealType", mealType)
        .put("notes", notes)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    companion object {
        private val ISO_WITH_TZ = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}([+-]\d{2}:\d{2}|Z)""")
        private val ISO_WITHOUT_TZ = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")

        /** 确保 measuredAt 包含时区，否则 +08:00 */
        private fun ensureTimeZone(iso: String): String {
            return when {
                iso.matches(ISO_WITH_TZ) -> iso
                iso.matches(ISO_WITHOUT_TZ) -> "$iso+08:00"
                iso.contains("T") -> iso // 其他格式先放行
                else -> "$iso${"T00:00:00+08:00"}" // 纯日期
            }
        }

        /** 从统一格式 JSON 对象解析 */
        fun fromJson(obj: JSONObject): GlucoseRecord {
            val measured = ensureTimeZone(obj.optString("measuredAt").ifBlank {
                // 兼容旧版 kv-export.json 的 date+time 字段
                "${obj.optString("date")}T${obj.optString("time", "00:00")}:00+08:00"
            })
            val rawMeal = obj.optString("mealType", "空腹")
            // 修复可能的乱码（旧版中文编码问题）
            val meal = when (rawMeal) {
                "绌鸿吂", "null", "" -> "空腹"
                "鏅氶鍚嶾" -> "晚餐后"
                else -> rawMeal
            }
            return GlucoseRecord(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                value = obj.getDouble("value"),
                measuredAt = measured,
                type = obj.optString("type", "blood_glucose"),
                mealType = meal,
                notes = obj.optString("notes"),
                updatedAt = obj.optString("updatedAt", measured),
                deleted = obj.optBoolean("deleted", false),
            )
        }
    }
}
