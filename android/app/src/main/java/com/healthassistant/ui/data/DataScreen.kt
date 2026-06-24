package com.healthassistant.ui.data

import androidx.compose.runtime.Composable
import com.healthassistant.data.repository.HealthRepository

/** 由仪表盘驱动切换，数据页不显示切换标签 */
@Composable
fun DataScreen(
    repository: HealthRepository,
    metric: String = "glucose",
) {
    when (metric) {
        "glucose" -> GlucoseDataPage(repository = repository)
        "weight" -> WeightDataPage(repository = repository)
        "bloodPressure" -> BpDataPage(repository = repository)
        else -> OtherMetricsDataPage(metric = metric)
    }
}
