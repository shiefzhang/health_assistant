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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.max

/** 安全解析 ISO 时间字符串 */
internal fun parseInstantSafe(iso: String): java.time.Instant {
    try { return java.time.Instant.parse(iso) } catch (_: Exception) {}
    try { return java.time.OffsetDateTime.parse(iso).toInstant() } catch (_: Exception) {}
    try { return java.time.Instant.parse(iso.take(19) + "Z") } catch (_: Exception) {}
    return java.time.Instant.parse("${iso.take(10)}T00:00:00Z")
}

@Composable
fun GlucoseChart(
    records: List<GlucoseRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = records
        .filter { record ->
            try { parseInstantSafe(record.measuredAt); true }
            catch (_: Exception) { false }
        }
        .sortedBy { it.measuredAt }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rangeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val accentColor = MaterialTheme.colorScheme.primary
    val normalLow = 3.9
    val normalHigh = 10.0
    val maxValue = if (sorted.isNotEmpty()) max(12.0, sorted.maxOf { it.value }) + 1.0 else 13.0

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")
    val tooltipDateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    // 触摸选中的记录索引
    var selectedIndex by remember { mutableStateOf(-1) }

    val pointInfo: List<Pair<Offset, GlucoseRecord>> = remember(sorted) {
        // 布局参数在 Canvas 外部未知，暂存记录，在 Canvas 内计算坐标
        emptyList()
    }

    // 用一个 mutable 容器在 Canvas 内填充
    val points = remember { mutableListOf<Pair<Offset, GlucoseRecord>>() }

    Box(modifier = modifier.fillMaxWidth().height(260.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sorted) {
                    detectTapGestures { tapOffset ->
                        // 寻找最近的记录
                        val left = 40f
                        val right = size.width - 8f
                        val top = 16f
                        val bottom = size.height - 24f
                        val chartHeight = bottom - top

                        fun y(value: Double) = top + ((maxValue - value) / (maxValue - 3.0) * chartHeight).toFloat()

                        if (sorted.isEmpty()) return@detectTapGestures

                        val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                        val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                        val timeSpan = max(1L, endTime - startTime)

                        var nearestIdx = -1
                        var nearestDist = Float.MAX_VALUE

                        sorted.forEachIndexed { idx, record ->
                            val timeMs = parseInstantSafe(record.measuredAt).toEpochMilli()
                            val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                            val yPos = y(record.value)
                            val dist = kotlin.math.sqrt(
                                (tapOffset.x - x) * (tapOffset.x - x) +
                                (tapOffset.y - yPos) * (tapOffset.y - yPos)
                            )
                            if (dist < nearestDist) {
                                nearestDist = dist
                                nearestIdx = idx
                            }
                        }

                        // 仅在触摸距离 <= 60px 时选中
                        selectedIndex = if (nearestDist <= 60f) nearestIdx else -1
                    }
                }
        ) {
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
                size = Size(right - left, y(normalLow) - y(normalHigh)),
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
            drawLine(color = Color(0xFFF57C00).copy(alpha = 0.5f), start = Offset(left, y(normalHigh)), end = Offset(right, y(normalHigh)), strokeWidth = 1.5f)
            drawLine(color = Color(0xFF2E7D32).copy(alpha = 0.5f), start = Offset(left, y(normalLow)), end = Offset(right, y(normalLow)), strokeWidth = 1.5f)

            if (sorted.isNotEmpty()) {
                val startTime = parseInstantSafe(sorted.first().measuredAt).toEpochMilli()
                val endTime = parseInstantSafe(sorted.last().measuredAt).toEpochMilli()
                val timeSpan = max(1L, endTime - startTime)

                // 计算所有数据点的坐标
                val dataPoints = sorted.map { record ->
                    val timeMs = parseInstantSafe(record.measuredAt).toEpochMilli()
                    val x = left + ((timeMs - startTime).toFloat() / timeSpan) * (right - left)
                    val yPos = y(record.value)
                    Offset(x, yPos) to record
                }

                // 折线
                val path = Path()
                dataPoints.forEachIndexed { index, (pt, _) ->
                    if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, accentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // 数据点 — 高反差双层圆
                dataPoints.forEachIndexed { index, (pt, record) ->
                    val dotColor = glucoseStatusColor(record.value)
                    val isSelected = index == selectedIndex
                    val outerR = if (isSelected) 8f else 5f
                    val innerR = if (isSelected) 5f else 3f

                    // 深色外圈（高反差）
                    drawCircle(Color(0xFF444444), radius = if (isSelected) 9f else 6f, center = pt)
                    // 主色中圈
                    drawCircle(dotColor, radius = outerR, center = pt)
                    // 白色内圈
                    drawCircle(Color.White, radius = innerR, center = pt)
                    // 选中时加亮色实心
                    if (isSelected) {
                        drawCircle(dotColor, radius = 2f, center = pt)
                    }
                }

                // 选中点的十字准线
                if (selectedIndex in dataPoints.indices) {
                    val (pt, record) = dataPoints[selectedIndex]
                    // 竖线
                    drawLine(
                        color = accentColor.copy(alpha = 0.3f),
                        start = Offset(pt.x, top),
                        end = Offset(pt.x, bottom),
                        strokeWidth = 1f,
                    )
                    // 横线参考
                    drawLine(
                        color = accentColor.copy(alpha = 0.2f),
                        start = Offset(left, pt.y),
                        end = Offset(right, pt.y),
                        strokeWidth = 1f,
                    )
                }

                // Tooltip — 选中点的数值标签
                if (selectedIndex in dataPoints.indices) {
                    val (pt, record) = dataPoints[selectedIndex]
                    val tooltipText = buildString {
                        append(formatTimeForTooltip(record.measuredAt))
                        append("  ")
                        append(record.mealType)
                        append("\n")
                        append("血糖：${"%.1f".format(record.value)} mmol/L")
                    }
                    val tooltipStyle = TextStyle(fontSize = 11.sp, color = Color.White)
                    val tooltipResult = textMeasurer.measure(tooltipText, tooltipStyle)
                    val padding = 8f
                    val bgWidth = tooltipResult.size.width + padding * 2
                    val bgHeight = tooltipResult.size.height + padding * 2

                    // 确定 tooltip 位置（不超出边界）
                    var tx = pt.x - bgWidth / 2f
                    var ty = pt.y - bgHeight - 12f
                    if (tx < left) tx = left
                    if (tx + bgWidth > right) tx = right - bgWidth
                    if (ty < top) ty = pt.y + 12f

                    // 背景圆角矩形
                    drawRoundRect(
                        color = Color(0xE6333333),
                        topLeft = Offset(tx, ty),
                        size = Size(bgWidth, bgHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    )
                    // 三角形箭头指向数据点
                    val arrowTip = Offset(pt.x, ty + bgHeight)
                    drawLine(Color(0xE6333333), Offset(pt.x - 5f, ty + bgHeight), arrowTip, strokeWidth = 2f)
                    drawLine(Color(0xE6333333), Offset(pt.x + 5f, ty + bgHeight), arrowTip, strokeWidth = 2f)

                    drawText(tooltipResult, topLeft = Offset(tx + padding, ty + padding))
                }

                // X 轴日期标签
                val step = max(1, sorted.size / 5)
                dataPoints.forEachIndexed { index, (pt, record) ->
                    if (index % step == 0 || index == sorted.lastIndex) {
                        val label = parseInstantSafe(record.measuredAt)
                            .atZone(ZoneId.systemDefault())
                            .format(dateFormatter)
                        val result = textMeasurer.measure(label, labelStyle)
                        drawText(result, topLeft = Offset(pt.x - result.size.width / 2f, bottom + 4f))
                    }
                }
            }
        }
    }
}
