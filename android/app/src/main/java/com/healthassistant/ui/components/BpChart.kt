package com.healthassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.ui.theme.MetricBlue
import com.healthassistant.ui.theme.MetricOrange
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 血压双线折线图 — 蓝色为收缩压，橙色为舒张压
 */
@Composable
fun BpChart(
    records: List<BloodPressureRecord>,
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

    val sysMax = if (sorted.isNotEmpty()) sorted.maxOf { it.systolic } else 0
    val diaMax = if (sorted.isNotEmpty()) sorted.maxOf { it.diastolic } else 0
    val maxVal = (max(sysMax, diaMax) + 20).coerceAtLeast(180).toDouble()
    val minVal = 40.0

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sysColor = MetricBlue
    val diaColor = MetricOrange
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
                        fun y(value: Double) = top + ((maxVal - value) / (maxVal - minVal) * chartHeight).toFloat()
                        if (sorted.isEmpty()) return@detectTapGestures

                        val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                        val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                        val timeSpan = max(1L, endTime - startTime)

                        var nearestIdx = -1; var nearestDist = Float.MAX_VALUE
                        sorted.forEachIndexed { idx, r ->
                            val timeMs = parseInstantSafe(r.measuredAt).toEpochMilli()
                            val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                            val yPos = y(r.systolic.toDouble())
                            val dist = sqrt(
                                (tapOffset.x - x) * (tapOffset.x - x) + (tapOffset.y - yPos) * (tapOffset.y - yPos)
                            )
                            if (dist < nearestDist) { nearestDist = dist; nearestIdx = idx }
                        }
                        selectedIndex = if (nearestDist <= 60f) nearestIdx else -1
                    }
                }
        ) {
            val left = 40f; val right = size.width - 8f
            val top = 16f; val bottom = size.height - 24f
            val chartHeight = bottom - top

            fun y(value: Double) = top + ((maxVal - value) / (maxVal - minVal) * chartHeight).toFloat()

            // 参考线
            drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(left, y(140.0)), Offset(right, y(140.0)), strokeWidth = 1f)
            drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(left, y(90.0)), Offset(right, y(90.0)), strokeWidth = 1f)

            val yValues = listOf(60, 90, 120, 140, 160, 180).filter { it <= maxVal }
            yValues.forEach { v ->
                val yPos = y(v.toDouble())
                drawLine(gridColor, Offset(left, yPos), Offset(right, yPos), strokeWidth = 1f)
                val result = textMeasurer.measure(v.toString(), labelStyle)
                drawText(result, topLeft = Offset(2f, yPos - result.size.height / 2f))
            }

            if (sorted.isNotEmpty()) {
                val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                val timeSpan = max(1L, endTime - startTime)

                fun xPos(record: BloodPressureRecord): Float {
                    val timeMs = parseInstantSafe(record.measuredAt).toEpochMilli()
                    return left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                }

                // 收缩压折线 + 数据点
                val sysPath = Path()
                sorted.forEachIndexed { i, r ->
                    val x = xPos(r); val yPos = y(r.systolic.toDouble())
                    if (i == 0) sysPath.moveTo(x, yPos) else sysPath.lineTo(x, yPos)
                }
                drawPath(sysPath, sysColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                sorted.forEachIndexed { i, r ->
                    val x = xPos(r)
                    val sysY = y(r.systolic.toDouble())
                    val isSel = i == selectedIndex
                    val outerR = if (isSel) 8f else 5f
                    val innerR = if (isSel) 5f else 3f
                    drawCircle(Color(0xFF444444), radius = if (isSel) 9f else 6f, center = Offset(x, sysY))
                    drawCircle(sysColor, radius = outerR, center = Offset(x, sysY))
                    drawCircle(Color.White, radius = innerR, center = Offset(x, sysY))
                    if (isSel) drawCircle(sysColor, radius = 2f, center = Offset(x, sysY))
                }

                // 舒张压折线 + 数据点
                val diaPath = Path()
                sorted.forEachIndexed { i, r ->
                    val x = xPos(r); val yPos = y(r.diastolic.toDouble())
                    if (i == 0) diaPath.moveTo(x, yPos) else diaPath.lineTo(x, yPos)
                }
                drawPath(diaPath, diaColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                sorted.forEachIndexed { i, r ->
                    val x = xPos(r)
                    val diaY = y(r.diastolic.toDouble())
                    val isSel = i == selectedIndex
                    val outerR = if (isSel) 8f else 5f
                    val innerR = if (isSel) 5f else 3f
                    drawCircle(Color(0xFF444444), radius = if (isSel) 9f else 6f, center = Offset(x, diaY))
                    drawCircle(diaColor, radius = outerR, center = Offset(x, diaY))
                    drawCircle(Color.White, radius = innerR, center = Offset(x, diaY))
                    if (isSel) drawCircle(diaColor, radius = 2f, center = Offset(x, diaY))
                }

                // 选中点的十字准线
                if (selectedIndex in sorted.indices) {
                    val r = sorted[selectedIndex]
                    val x = xPos(r)
                    drawLine(sysColor.copy(alpha = 0.3f), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                    drawLine(sysColor.copy(alpha = 0.2f), Offset(left, y(r.systolic.toDouble())), Offset(right, y(r.systolic.toDouble())), strokeWidth = 1f)
                }

                // Tooltip
                if (selectedIndex in sorted.indices) {
                    val r = sorted[selectedIndex]
                    val x = xPos(r)
                    val yPos = y(r.systolic.toDouble())
                    val tooltipText = "${formatTimeForTooltip(r.measuredAt)}\n收缩压 ${r.systolic} / 舒张压 ${r.diastolic} mmHg"
                    drawTooltip(textMeasurer, tooltipText, Offset(x, yPos), left, right, top, bottom)
                }

                // X 轴日期
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

    // 图例
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).offset(y = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(sysColor, radius = 5f) }
            Spacer(Modifier.width(4.dp))
            Text("收缩压", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(diaColor, radius = 5f) }
            Spacer(Modifier.width(4.dp))
            Text("舒张压", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 在画布上绘制 Tooltip 背景 + 文字
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTooltip(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    point: Offset,
    left: Float, right: Float, top: Float, bottom: Float,
) {
    val tooltipStyle = TextStyle(fontSize = 11.sp, color = Color.White)
    val result = textMeasurer.measure(text, tooltipStyle)
    val padding = 8f
    val bgWidth = result.size.width + padding * 2
    val bgHeight = result.size.height + padding * 2
    var tx = point.x - bgWidth / 2f
    var ty = point.y - bgHeight - 12f
    if (tx < left) tx = left
    if (tx + bgWidth > right) tx = right - bgWidth
    if (ty < top) ty = point.y + 12f

    drawRoundRect(Color(0xE6333333), Offset(tx, ty), Size(bgWidth, bgHeight), CornerRadius(8f, 8f))
    drawLine(Color(0xE6333333), Offset(point.x - 5f, ty + bgHeight), Offset(point.x, ty + bgHeight), strokeWidth = 2f)
    drawLine(Color(0xE6333333), Offset(point.x + 5f, ty + bgHeight), Offset(point.x, ty + bgHeight), strokeWidth = 2f)
    drawText(result, topLeft = Offset(tx + padding, ty + padding))
}

internal fun formatTimeForTooltip(iso: String): String {
    return try {
        parseInstantSafe(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    } catch (_: Exception) { iso }
}
