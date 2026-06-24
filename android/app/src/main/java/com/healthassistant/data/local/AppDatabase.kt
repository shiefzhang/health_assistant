package com.healthassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord

@Database(
    entities = [GlucoseRecord::class, WeightRecord::class, BloodPressureRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
    abstract fun weightDao(): WeightDao
    abstract fun bpDao(): BpDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ── v1 → v2：新增体重表和血压表 ──
        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `weight_records` (
                    `id` TEXT NOT NULL,
                    `value` REAL NOT NULL,
                    `measuredAt` TEXT NOT NULL,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `updatedAt` TEXT NOT NULL,
                    `deleted` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `bp_records` (
                    `id` TEXT NOT NULL,
                    `systolic` INTEGER NOT NULL,
                    `diastolic` INTEGER NOT NULL,
                    `heartRate` INTEGER NOT NULL DEFAULT 0,
                    `measuredAt` TEXT NOT NULL,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `updatedAt` TEXT NOT NULL,
                    `deleted` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
        }

        // ── v2 → v3：体重表增加身高和体脂率字段 ──
        private val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE `weight_records` ADD COLUMN `heightCm` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `weight_records` ADD COLUMN `bodyFatPercent` REAL")
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_assistant.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
