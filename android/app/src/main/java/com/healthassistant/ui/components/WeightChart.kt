package com.healthassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.ui.theme.MetricGreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun WeightChart(
    records: List<WeightRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = records
        .filter { r -> try { parseInstantSafe(r.measuredAt); true } catch (_: Exception) { false } }
        .sortedBy { it.measuredAt }

    val minV = if (sorted.isNotEmpty()) sorted.minOf { it.value }.toInt() - 5 else 0
    val maxV = if (sorted.isNotEmpty()) sorted.maxOf { it.value }.toInt() + 5 else 100
    val valRange = (maxV - minV).coerceAtLeast(1)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MetricGreen
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

    var selectedIndex by remember { mutableStateOf(-1) }

    Box(modifier = modifier.fillMaxWidth().height(260.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sorted) {
                    detectTapGestures { tapOffset ->
                        val left = 40f; val right = size.width - 8f
                        val top = 16f; val bottom = size.height - 24f
                        val chartHeight = bottom - top
                        fun y(value: Double) = top + ((maxV - value) / valRange * chartHeight).toFloat()
                        if (sorted.isEmpty()) return@detectTapGestures

                        val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                        val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                        val timeSpan = max(1L, endTime - startTime)

                        var nearestIdx = -1; var nearestDist = Float.MAX_VALUE
                        sorted.forEachIndexed { idx, r ->
                            val timeMs = parseInstantSafe(r.measuredAt).toEpochMilli()
                            val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                            val yPos = y(r.value)
                            val dist = sqrt(
                                (tapOffset.x - x) * (tapOffset.x - x) + (tapOffset.y - yPos) * (tapOffset.y - yPos)
                            )
                            if (dist < nearestDist) { nearestDist = dist; nearestIdx = idx }
                        }
                        selectedIndex = if (nearestDist <= 60f) nearestIdx else -1
                    }
                }
        ) {
            val left = 40f; val right = size.width - 8f; val top = 16f; val bottom = size.height - 24f
            val chartHeight = bottom - top
            fun y(value: Double) = top + ((maxV - value) / valRange * chartHeight).toFloat()

            val ySteps = 4
            for (i in 0..ySteps) {
                val v = minV + (valRange * i / ySteps)
                val yPos = y(v.toDouble())
                drawLine(gridColor, Offset(left, yPos), Offset(right, yPos), strokeWidth = 1f)
                val result = textMeasurer.measure(v.toString(), labelStyle)
                drawText(result, topLeft = Offset(2f, yPos - result.size.height / 2f))
            }

            if (sorted.isNotEmpty()) {
                val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                val timeSpan = max(1L, endTime - startTime)

                fun xPos(r: WeightRecord): Float =
                    left + ((parseInstantSafe(r.measuredAt).toEpochMilli() - startTime).toFloat() / timeSpan) * (right - left)

                val path = Path()
                sorted.forEachIndexed { i, r ->
                    val x = xPos(r); val yPos = y(r.value)
                    if (i == 0) path.moveTo(x, yPos) else path.lineTo(x, yPos)
                }
                drawPath(path, accentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                sorted.forEachIndexed { i, r ->
                    val x = xPos(r); val yPos = y(r.value)
                    val isSel = i == selectedIndex
                    val outerR = if (isSel) 8f else 5f
                    val innerR = if (isSel) 5f else 3f
                    drawCircle(Color(0xFF444444), radius = if (isSel) 9f else 6f, center = Offset(x, yPos))
                    drawCircle(accentColor, radius = outerR, center = Offset(x, yPos))
                    drawCircle(Color.White, radius = innerR, center = Offset(x, yPos))
                    if (isSel) drawCircle(accentColor, radius = 2f, center = Offset(x, yPos))
                }

                if (selectedIndex in sorted.indices) {
                    val r = sorted[selectedIndex]
                    val x = xPos(r)
                    drawLine(accentColor.copy(alpha = 0.3f), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                }

                if (selectedIndex in sorted.indices) {
                    val r = sorted[selectedIndex]
                    val x = xPos(r); val yPos = y(r.value)
                    val bmiText = if (r.bmi != null) "  BMI ${"%.1f".format(r.bmi)}" else ""
                    val tooltipText = "${formatTimeForTooltip(r.measuredAt)}\n体重 ${"%.1f".format(r.value)} kg$bmiText"
                    drawTooltip(textMeasurer, tooltipText, Offset(x, yPos), left, right, top, bottom)
                }

                val step = max(1, sorted.size / 5)
                sorted.forEachIndexed { index, r ->
                    if (index % step == 0 || index == sorted.lastIndex) {
                        val x = xPos(r)
                        val label = parseInstantSafe(r.measuredAt).atZone(ZoneId.systemDefault()).format(dateFormatter)
                        val result = textMeasurer.measure(label, labelStyle)
                        drawText(result, topLeft = Offset(x - result.size.width / 2f, bottom + 4f))
                    }
                }
            }
        }
    }
}
