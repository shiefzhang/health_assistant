package com.healthassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.ui.theme.MetricGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun WeightChart(
    records: List<WeightRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = records.sortedBy { it.measuredAt }
    val minV = sorted.minOf { it.value }.toInt() - 5
    val maxV = sorted.maxOf { it.value }.toInt() + 5
    val valRange = maxV - minV
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MetricGreen
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val left = 40f; val right = size.width - 8f; val top = 16f; val bottom = size.height - 24f
        val chartHeight = bottom - top

        fun y(value: Double) = top + ((maxV - value) / valRange * chartHeight).toFloat()

        // 网格
        val ySteps = 4
        for (i in 0..ySteps) {
            val v = minV + (valRange * i / ySteps)
            val yPos = y(v.toDouble())
            drawLine(gridColor, Offset(left, yPos), Offset(right, yPos), strokeWidth = 1f)
            val result = textMeasurer.measure(v.toString(), labelStyle)
            drawText(result, topLeft = Offset(2f, yPos - result.size.height / 2f))
        }

        val startTime = Instant.parse(sorted.first().measuredAt).toEpochMilli()
        val endTime = Instant.parse(sorted.last().measuredAt).toEpochMilli()
        val timeSpan = max(1L, endTime - startTime)

        fun xPos(r: WeightRecord): Float {
            return left + ((Instant.parse(r.measuredAt).toEpochMilli() - startTime).toFloat() / timeSpan) * (right - left)
        }

        val path = Path()
        sorted.forEachIndexed { i, r ->
            val x = xPos(r); val yPos = y(r.value)
            if (i == 0) path.moveTo(x, yPos) else path.lineTo(x, yPos)
        }
        drawPath(path, accentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        sorted.forEach { r ->
            val x = xPos(r); val yPos = y(r.value)
            drawCircle(accentColor, radius = 4f, center = Offset(x, yPos))
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 2f, center = Offset(x, yPos))
        }

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
}
