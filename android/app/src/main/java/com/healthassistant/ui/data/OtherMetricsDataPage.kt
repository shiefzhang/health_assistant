package com.healthassistant.ui.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthassistant.ui.theme.MetricBlue
import com.healthassistant.ui.theme.MetricGreen
import com.healthassistant.ui.theme.MetricOrange
import com.healthassistant.ui.theme.MetricPink
import com.healthassistant.ui.theme.MetricPurple

private data class MetricReferenceInfo(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val ranges: List<Pair<String, String>>,
    val tips: List<String>,
)

private fun getMetricInfo(metric: String): MetricReferenceInfo {
    return when (metric) {
        "bloodPressure" -> MetricReferenceInfo(
            title = "血压",
            icon = Icons.Default.FavoriteBorder,
            accentColor = MetricPurple,
            ranges = listOf(
                "理想血压" to "收缩压 < 120 / 舒张压 < 80",
                "正常血压" to "收缩压 120-129 / 舒张压 80-84",
                "正常高值" to "收缩压 130-139 / 舒张压 85-89",
                "1级高血压" to "收缩压 140-159 / 舒张压 90-99",
                "2级高血压" to "收缩压 ≥ 160 / 舒张压 ≥ 100",
            ),
            tips = listOf(
                "减少钠盐摄入，每日食盐不超过 5 克",
                "保持规律运动，每周至少 150 分钟中等强度运动",
                "控制体重，BMI 保持在 18.5-24.9 范围",
                "限制饮酒，男性每日不超过 25 克酒精",
                "保持充足睡眠，每日 7-8 小时",
                "定期监测血压，建议每周测量 2-3 次",
            ),
        )
        "heartRate" -> MetricReferenceInfo(
            title = "心率",
            icon = Icons.Default.FavoriteBorder,
            accentColor = MetricPink,
            ranges = listOf(
                "运动员" to "40-60 次/分",
                "成年人（优秀）" to "60-70 次/分",
                "成年人（正常）" to "70-85 次/分",
                "成年人（偏高）" to "85-100 次/分",
                "异常（需就医）" to "> 100 或 < 40 次/分",
            ),
            tips = listOf(
                "规律有氧运动（快走、慢跑、游泳）可降低静息心率",
                "减少咖啡因和尼古丁摄入",
                "保持充足睡眠，避免长期熬夜",
                "管理压力，可尝试冥想或深呼吸练习",
                "保持健康体重，减轻心脏负担",
                "如持续心率异常，请及时就医检查",
            ),
        )
        "steps" -> MetricReferenceInfo(
            title = "步数",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            accentColor = MetricOrange,
            ranges = listOf(
                "基础活动量" to "< 5,000 步/天",
                "轻度活跃" to "5,000 - 7,499 步/天",
                "中度活跃" to "7,500 - 9,999 步/天",
                "活跃" to "10,000 - 12,499 步/天",
                "高度活跃" to "≥ 12,500 步/天",
            ),
            tips = listOf(
                "每日 10,000 步是常见的健康目标",
                "利用碎片时间步行：午餐后散步 15 分钟",
                "优先选择走楼梯代替电梯",
                "使用计步器或手机应用追踪每日步数",
                "步行时保持适当速度（每分钟 100-120 步为佳）",
                "结合快走和慢走交替，提高运动效果",
            ),
        )
        else -> MetricReferenceInfo(
            title = "健康指标",
            icon = Icons.Default.Speed,
            accentColor = MetricGreen,
            ranges = listOf(
                "正常范围" to "请咨询医生获取具体参考值",
            ),
            tips = listOf(
                "保持均衡饮食和规律作息",
                "定期进行健康检查",
                "记录数据趋势，及时发现异常变化",
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherMetricsDataPage(metric: String) {
    val info = remember(metric) { getMetricInfo(metric) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        Text(
            "${info.title} 数据",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // 参考范围
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        tint = info.accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "参考范围",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HorizontalDivider()

                info.ranges.forEachIndexed { index, (label, range) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = range,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index < info.ranges.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        // 健康小贴士
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = info.accentColor.copy(alpha = 0.08f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "健康小贴士",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = info.accentColor,
                    )
                }

                info.tips.forEachIndexed { index, tip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = info.accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // 开发提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${info.title}数据记录正在开发中",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前版本已支持血糖数据的完整记录与统计分析。\n${info.title}的记录、图表和AI分析功能将在后续版本中上线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
