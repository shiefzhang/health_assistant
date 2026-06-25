package com.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.time.Instant

/**
 * 体重记录实体
 * value: 体重(kg), heightCm: 身高(cm), bodyFatPercent: 体脂率(%) — 可选
 */
@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey val id: String,
    val value: Double,                  // kg
    val heightCm: Double = 0.0,         // 身高(cm)，0表示未记录
    val bodyFatPercent: Double? = null, // 体脂率(%)，null表示未记录
    val measuredAt: String,             // ISO-8601
    val notes: String = "",
    val updatedAt: String = Instant.now().toString(),
    val deleted: Boolean = false,
) {
    /** BMI 值（需要身高 > 0） */
    val bmi: Double?
        get() = if (heightCm > 0) value / ((heightCm / 100.0) * (heightCm / 100.0)) else null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("value", value)
        .put("heightCm", heightCm)
        .put("bodyFatPercent", bodyFatPercent)
        .put("measuredAt", measuredAt)
        .put("notes", notes)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    companion object {
        private val ISO_WITH_TZ = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}([+-]\d{2}:\d{2}|Z)""")
        private val ISO_WITHOUT_TZ = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")

        private fun ensureTimeZone(iso: String): String = when {
            iso.matches(ISO_WITH_TZ) -> iso
            iso.matches(ISO_WITHOUT_TZ) -> "$iso+08:00"
            else -> "$iso${"T00:00:00+08:00"}"
        }

        fun fromJson(obj: JSONObject): WeightRecord {
            val measured = ensureTimeZone(obj.optString("measuredAt").ifBlank {
                "${obj.optString("date")}T${obj.optString("time", "00:00")}:00+08:00"
            })
            return WeightRecord(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                value = obj.getDouble("value"),
                heightCm = obj.optDouble("heightCm", 0.0),
                bodyFatPercent = if (obj.has("bodyFatPercent") && !obj.isNull("bodyFatPercent"))
                    obj.optDouble("bodyFatPercent") else null,
                measuredAt = measured,
                notes = obj.optString("notes"),
                updatedAt = obj.optString("updatedAt", measured),
                deleted = obj.optBoolean("deleted", false),
            )
        }
    }
}
