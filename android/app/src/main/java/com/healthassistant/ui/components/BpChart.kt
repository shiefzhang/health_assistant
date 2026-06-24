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
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.ui.theme.MetricBlue
import com.healthassistant.ui.theme.MetricOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 血压双线折线图 — 蓝色为收缩压，橙色为舒张压
 */
@Composable
fun BpChart(
    records: List<BloodPressureRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = records.sortedBy { it.measuredAt }
    val sysMax = sorted.maxOf { it.systolic }
    val diaMax = sorted.maxOf { it.diastolic }
    val maxVal = (max(sysMax, diaMax) + 20).coerceAtLeast(180).toDouble()
    val minVal = 40.0

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sysColor = MetricBlue
    val diaColor = MetricOrange
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val left = 40f
        val right = size.width - 8f
        val top = 16f
        val bottom = size.height - 24f
        val chartHeight = bottom - top

        fun y(value: Double) = top + ((maxVal - value) / (maxVal - minVal) * chartHeight).toFloat()

        // 参考线: 140/90 (1级高血压线)
        drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(left, y(140.0)), Offset(right, y(140.0)), strokeWidth = 1f)
        drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(left, y(90.0)), Offset(right, y(90.0)), strokeWidth = 1f)

        // 网格
        val yValues = listOf(60, 90, 120, 140, 160, 180).filter { it <= maxVal }
        yValues.forEach { v ->
            val yPos = y(v.toDouble())
            drawLine(gridColor, Offset(left, yPos), Offset(right, yPos), strokeWidth = 1f)
            val result = textMeasurer.measure(v.toString(), labelStyle)
            drawText(result, topLeft = Offset(2f, yPos - result.size.height / 2f))
        }

        val startTime = Instant.parse(sorted.first().measuredAt).toEpochMilli()
        val endTime = Instant.parse(sorted.last().measuredAt).toEpochMilli()
        val timeSpan = max(1L, endTime - startTime)

        fun xPos(record: BloodPressureRecord): Float {
            val timeMs = Instant.parse(record.measuredAt).toEpochMilli()
            return left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
        }

        // 收缩压折线
        val sysPath = Path()
        sorted.forEachIndexed { i, r ->
            val x = xPos(r); val yPos = y(r.systolic.toDouble())
            if (i == 0) sysPath.moveTo(x, yPos) else sysPath.lineTo(x, yPos)
        }
        drawPath(sysPath, sysColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        sorted.forEach { r ->
            val x = xPos(r); val yPos = y(r.systolic.toDouble())
            drawCircle(sysColor, radius = 4f, center = Offset(x, yPos))
            drawCircle(Color.White, radius = 2f, center = Offset(x, yPos))
        }

        // 舒张压折线
        val diaPath = Path()
        sorted.forEachIndexed { i, r ->
            val x = xPos(r); val yPos = y(r.diastolic.toDouble())
            if (i == 0) diaPath.moveTo(x, yPos) else diaPath.lineTo(x, yPos)
        }
        drawPath(diaPath, diaColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        sorted.forEach { r ->
            val x = xPos(r); val yPos = y(r.diastolic.toDouble())
            drawCircle(diaColor, radius = 4f, center = Offset(x, yPos))
            drawCircle(Color.White, radius = 2f, center = Offset(x, yPos))
        }

        // X 轴日期
        val step = max(1, sorted.size / 5)
        sorted.forEachIndexed { index, r ->
            if (index % step == 0 || index == sorted.lastIndex) {
                val x = xPos(r)
                val label = Instant.parse(r.measuredAt).atZone(ZoneId.systemDefault()).format(dateFormatter)
                val result = textMeasurer.measure(label, labelStyle)
                drawText(result, topLeft = Offset(x - result.size.width / 2f, bottom + 4f))
            }
        }
    }

    // 图例
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).offset(y = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(sysColor, radius = 5f) }
            Spacer(Modifier.width(4.dp))
            Text("收缩压", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(diaColor, radius = 5f) }
            Spacer(Modifier.width(4.dp))
            Text("舒张压", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
