package com.healthassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.time.Instant

/**
 * 血压记录实体
 */
@Entity(tableName = "bp_records")
data class BloodPressureRecord(
    @PrimaryKey val id: String,
    val systolic: Int,               // 收缩压 mmHg
    val diastolic: Int,              // 舒张压 mmHg
    val heartRate: Int = 0,          // 脉搏（可选）
    val measuredAt: String,          // ISO-8601
    val notes: String = "",
    val updatedAt: String = Instant.now().toString(),
    val deleted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("systolic", systolic)
        .put("diastolic", diastolic)
        .put("heartRate", heartRate)
        .put("measuredAt", measuredAt)
        .put("notes", notes)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    companion object {
        fun fromJson(obj: JSONObject): BloodPressureRecord {
            val measured = obj.optString("measuredAt").ifBlank {
                "${obj.optString("date")}T${obj.optString("time", "00:00")}:00+08:00"
            }
            return BloodPressureRecord(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                systolic = obj.getInt("systolic"),
                diastolic = obj.getInt("diastolic"),
                heartRate = obj.optInt("heartRate", 0),
                measuredAt = measured,
                notes = obj.optString("notes"),
                updatedAt = obj.optString("updatedAt", measured),
                deleted = obj.optBoolean("deleted", false),
            )
        }
    }
}
