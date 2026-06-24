package com.healthassistant.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.ui.components.HealthMetricCard
import com.healthassistant.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: HealthRepository,
    onNavigateToData: (metric: String) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(repository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 从统一记录中找到最新血糖值（给卡片展示）
    val latestGlucose = remember(state.recentRecords) {
        state.recentRecords.filterIsInstance<DashboardRecord.Glucose>().firstOrNull()?.record
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 标题
        item {
            Text(
                "健康概览",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 指标卡片网格
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HealthMetricCard(
                    title = "血糖",
                    value = latestGlucose?.let { "%.1f".format(it.value) } ?: "--",
                    unit = "mmol/L",
                    statusText = if (latestGlucose == null) "暂无数据"
                    else when {
                        latestGlucose.value < 3.9 -> "偏低"
                        latestGlucose.value <= 6.1 -> "正常"
                        latestGlucose.value <= 10.0 -> "偏高"
                        else -> "异常偏高"
                    },
                    accentColor = metricGreenColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToData("glucose") },
                )
                HealthMetricCard(
                    title = "血压",
                    value = state.latestBp?.let { "${it.systolic}/${it.diastolic}" } ?: "--",
                    unit = "mmHg",
                    statusText = state.latestBp?.let {
                        when {
                            it.systolic < 120 && it.diastolic < 80 -> "理想"
                            it.systolic < 130 && it.diastolic < 85 -> "正常"
                            it.systolic < 140 || it.diastolic < 90 -> "正常高值"
                            it.systolic < 160 || it.diastolic < 100 -> "1级高血压"
                            else -> "2级高血压"
                        }
                    } ?: "暂无数据",
                    accentColor = metricBlueColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToData("bloodPressure") },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HealthMetricCard(
                    title = "心率",
                    value = if (state.latestHeartRate > 0) "${state.latestHeartRate}" else "--",
                    unit = "bpm",
                    statusText = if (state.latestHeartRate > 0) {
                        when {
                            state.latestHeartRate < 60 -> "偏低"
                            state.latestHeartRate <= 85 -> "正常"
                            state.latestHeartRate <= 100 -> "偏高"
                            else -> "异常偏高"
                        }
                    } else "暂无数据",
                    accentColor = metricOrangeColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToData("heartRate") },
                )
                HealthMetricCard(
                    title = "步数",
                    value = "--",
                    unit = "步",
                    statusText = "待完善",
                    accentColor = metricPurpleColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToData("steps") },
                )
            }
        }

        // 体重指标
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HealthMetricCard(
                    title = "体重",
                    value = state.latestWeight?.let { "%.1f".format(it.value) } ?: "--",
                    unit = "kg",
                    statusText = state.latestWeight?.let { "最新记录" } ?: "暂无数据",
                    accentColor = metricPinkColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToData("weight") },
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // 最近记录
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "共 ${state.recentRecords.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 最近记录列表
        items(state.recentRecords, key = { it.id }) { record ->
            DashboardRecordItem(record = record)
        }

        // 底部留白
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DashboardRecordItem(record: DashboardRecord) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    val displayTime = remember(record.measuredAt) {
        try { Instant.parse(record.measuredAt).atZone(zone).format(dateFormatter) }
        catch (_: Exception) { record.measuredAt }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (record) {
                is DashboardRecord.Glucose -> {
                    val r = record.record
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("血糖", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(r.mealType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%.1f".format(r.value), style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold, color = glucoseStatusColor(r.value)))
                    Spacer(Modifier.width(4.dp))
                    Text("mmol/L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                is DashboardRecord.BloodPressure -> {
                    val r = record.record
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("血压", style = MaterialTheme.typography.labelMedium, color = metricBlueColor)
                            Spacer(Modifier.width(8.dp))
                            val status = when {
                                r.systolic < 120 && r.diastolic < 80 -> "理想"
                                r.systolic < 130 && r.diastolic < 85 -> "正常"
                                r.systolic < 140 || r.diastolic < 90 -> "正常高值"
                                r.systolic < 160 || r.diastolic < 100 -> "1级高血压"
                                else -> "2级高血压"
                            }
                            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${r.systolic}/${r.diastolic}", style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold, color = metricBlueColor))
                    Spacer(Modifier.width(4.dp))
                    Text("mmHg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                is DashboardRecord.Weight -> {
                    val r = record.record
                    Column(Modifier.weight(1f)) {
                        Text("体重", style = MaterialTheme.typography.labelMedium, color = metricPinkColor)
                        Text(displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%.1f".format(r.value), style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold, color = metricPinkColor))
                    Spacer(Modifier.width(4.dp))
                    Text("kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

