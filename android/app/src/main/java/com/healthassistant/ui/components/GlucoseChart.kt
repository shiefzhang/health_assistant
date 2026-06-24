package com.healthassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.ui.theme.glucoseStatusColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 血糖折线图组件
 * 显示指定记录的血糖变化趋势，带正常范围（3.9-10.0）背景
 */
@Composable
fun GlucoseChart(
    records: List<GlucoseRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sorted = records.sortedBy { it.measuredAt }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rangeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val accentColor = MaterialTheme.colorScheme.primary
    val normalLow = 3.9
    val normalHigh = 10.0
    val maxValue = max(12.0, sorted.maxOf { it.value }) + 1.0

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val left = 40f
        val right = size.width - 8f
        val top = 16f
        val bottom = size.height - 24f
        val chartHeight = bottom - top

        fun y(value: Double) = top + ((maxValue - value) / (maxValue - 3.0) * chartHeight).toFloat()

        // 正常范围背景
        drawRect(
            color = rangeColor,
            topLeft = Offset(left, y(normalHigh)),
            size = androidx.compose.ui.geometry.Size(right - left, y(normalLow) - y(normalHigh)),
        )

        // 网格线和 Y 轴标签
        val yValues = listOf(4.0, 7.0, 10.0, 13.0).filter { it <= maxValue }
        yValues.forEach { v ->
            val yPos = y(v)
            drawLine(gridColor, Offset(left, yPos), Offset(right, yPos), strokeWidth = 1f)
            val result = textMeasurer.measure(v.toInt().toString(), labelStyle)
            drawText(result, topLeft = Offset(2f, yPos - result.size.height / 2f))
        }

        // 异常范围参考线
        drawLine(
            color = Color(0xFFF57C00).copy(alpha = 0.5f),
            start = Offset(left, y(normalHigh)),
            end = Offset(right, y(normalHigh)),
            strokeWidth = 1.5f,
        )
        drawLine(
            color = Color(0xFF2E7D32).copy(alpha = 0.5f),
            start = Offset(left, y(normalLow)),
            end = Offset(right, y(normalLow)),
            strokeWidth = 1.5f,
        )

        // 折线
        if (sorted.isNotEmpty()) {
            val path = Path()
            val startTime = Instant.parse(sorted.first().measuredAt).toEpochMilli()
            val endTime = Instant.parse(sorted.last().measuredAt).toEpochMilli()
            val timeSpan = max(1L, endTime - startTime)

            sorted.forEachIndexed { index, record ->
                val timeMs = Instant.parse(record.measuredAt).toEpochMilli()
                val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                val yPos = y(record.value)
                if (index == 0) path.moveTo(x, yPos) else path.lineTo(x, yPos)
            }

            drawPath(path, accentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // 数据点
            sorted.forEachIndexed { index, record ->
                val timeMs = Instant.parse(record.measuredAt).toEpochMilli()
                val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                val yPos = y(record.value)
                val dotColor = glucoseStatusColor(record.value)
                drawCircle(dotColor, radius = 4f, center = Offset(x, yPos))
                drawCircle(Color.White, radius = 2f, center = Offset(x, yPos))
            }

            // X 轴日期标签
            val step = max(1, sorted.size / 5)
            sorted.forEachIndexed { index, record ->
                if (index % step == 0 || index == sorted.lastIndex) {
                    val timeMs = Instant.parse(record.measuredAt).toEpochMilli()
                    val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                    val label = Instant.parse(record.measuredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(dateFormatter)
                    val result = textMeasurer.measure(label, labelStyle)
                    drawText(result, topLeft = Offset(x - result.size.width / 2f, bottom + 4f))
                }
            }
        }
    }
}
