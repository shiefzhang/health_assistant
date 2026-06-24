package com.healthassistant

import android.app.Application
import com.healthassistant.data.local.AppDatabase
import com.healthassistant.data.repository.HealthRepository

class HealthTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: HealthRepository by lazy {
        HealthRepository(
            glucoseDao = database.glucoseDao(),
            weightDao = database.weightDao(),
            bpDao = database.bpDao(),
        )
    }
}
