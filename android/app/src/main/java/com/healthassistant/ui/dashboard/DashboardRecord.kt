package com.healthassistant.ui.dashboard

import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord

/** 仪表盘统一记录类型 */
sealed class DashboardRecord(
    open val id: String,
    open val measuredAt: String,
) {
    data class Glucose(val record: com.healthassistant.data.model.GlucoseRecord) :
        DashboardRecord(record.id, record.measuredAt)

    data class BloodPressure(val record: com.healthassistant.data.model.BloodPressureRecord) :
        DashboardRecord(record.id, record.measuredAt)

    data class Weight(val record: com.healthassistant.data.model.WeightRecord) :
        DashboardRecord(record.id, record.measuredAt)
}
