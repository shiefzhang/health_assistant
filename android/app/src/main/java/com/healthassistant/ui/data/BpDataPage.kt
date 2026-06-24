package com.healthassistant.ui.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.ui.components.BpChart
import com.healthassistant.ui.components.BpInputDialog
import com.healthassistant.ui.components.StatCard
import com.healthassistant.ui.theme.MetricGreen
import com.healthassistant.ui.theme.MetricOrange
import com.healthassistant.ui.theme.MetricPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun bpStatus(sys: Int, dia: Int): Pair<String, androidx.compose.ui.graphics.Color> = when {
    sys < 120 && dia < 80 -> "理想" to MetricGreen
    sys < 130 && dia < 85 -> "正常" to MetricGreen
    sys < 140 || dia < 90 -> "正常高值" to MetricOrange
    sys < 160 || dia < 100 -> "1级高血压" to MetricOrange
    else -> "2级高血压" to MetricPurple
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BpDataPage(
    repository: HealthRepository,
    viewModel: BpViewModel = viewModel(factory = BpViewModel.Factory(repository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInputDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<BloodPressureRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<BloodPressureRecord?>(null) }
    var showingAnalysis by remember { mutableStateOf(false) }

    val analysisText = com.healthassistant.data.sync.AnalysisStore.getText("bloodPressure")
    val showAnalysis = analysisText != null && showingAnalysis

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定删除 ${target.measuredAt.take(10)} 的血压记录（${target.systolic}/${target.diastolic} mmHg）吗？\n此操作不可撤销。") },
            confirmButton = {
                Button(onClick = { scope.launch { repository.deleteBp(target.id) }; deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    // 录入/编辑对话框
    if (showInputDialog) {
        BpInputDialog(
            onDismiss = { showInputDialog = false; editingRecord = null },
            onSave = { id, sys, dia, hr, measuredAt, notes ->
                scope.launch { repository.upsertBp(BloodPressureRecord(id, sys, dia, hr, measuredAt, notes)) }
            },
            existingRecord = editingRecord,
            lastRecord = if (editingRecord == null) state.records.firstOrNull() else null,
        )
    }

    // 监听 AI 分析完成 → 存入全局存储
    LaunchedEffect(state.aiAnalysis) {
        state.aiAnalysis?.let {
            com.healthassistant.data.sync.AnalysisStore.save(
                "bloodPressure", it, java.time.Instant.now().toString()
            )
            showingAnalysis = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题 + 新增
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("血压 数据", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                FilledTonalIconButton(onClick = { showInputDialog = true; editingRecord = null }) {
                    Icon(Icons.Default.Add, "新增记录")
                }
            }
        }

        // 最新读数 + 周期切换
        if (state.records.isNotEmpty()) {
            val latest = state.records.first()
            val (status, color) = bpStatus(latest.systolic, latest.diastolic)
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = color, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("最新读数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${latest.systolic}/${latest.diastolic}",
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = color))
                                Spacer(Modifier.width(4.dp))
                                Text("mmHg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Text(status, style = MaterialTheme.typography.labelMedium, color = color)
                        }
                        if (latest.heartRate > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("脉搏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${latest.heartRate}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color))
                                Text("bpm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 周期切换
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimePeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { viewModel.loadPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, TimePeriod.entries.size),
                    ) { Text(period.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        // 图表
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("血压趋势", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    BpChart(records = state.records, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 统计摘要
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = "平均收缩压", value = "%.0f".format(state.avgSystolic), modifier = Modifier.weight(1f))
                StatCard(label = "平均舒张压", value = "%.0f".format(state.avgDiastolic), modifier = Modifier.weight(1f))
                StatCard(label = "最高", value = "${state.maxSystolic}", modifier = Modifier.weight(1f))
                StatCard(label = "最低", value = "${state.minSystolic}", modifier = Modifier.weight(1f))
            }
        }

        // AI 分析
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.setAiLoading()
                        scope.launch {
                            try {
                                val settings = com.healthassistant.MainActivity.loadSettings(context)
                                val result = withContext(Dispatchers.IO) {
                                    viewModel.analyzeWithAi(state.records, settings.aiBaseUrl, settings.aiKey, settings.aiModel)
                                }
                                viewModel.setAiResult(result)
                            } catch (e: Exception) {
                                viewModel.setAiError(e.message ?: "AI 分析失败")
                            }
                        }
                    },
                    enabled = !state.isAiLoading && state.records.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("分析中…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 分析")
                    }
                }
            }
        }

        // AI 分析结果（内联）
        if (showAnalysis) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 分析结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showingAnalysis = false }) { Text("收起", style = MaterialTheme.typography.bodySmall) }
                        }
                        Spacer(Modifier.height(8.dp))
                        com.healthassistant.ui.components.MarkdownText(markdown = analysisText ?: "", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        state.aiError?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("⚠️ AI 分析仅供参考，不能替代医生诊断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 参考范围
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("参考范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BpRangeRow("理想", "< 120 / < 80", MetricGreen)
                    BpRangeRow("正常", "120-129 / 80-84", MetricGreen)
                    BpRangeRow("正常高值", "130-139 / 85-89", MetricOrange)
                    BpRangeRow("1级高血压", "140-159 / 90-99", MetricOrange)
                    BpRangeRow("2级高血压", "≥ 160 / ≥ 100", MetricPurple)
                }
            }
        }

        // 记录列表
        if (state.records.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        items(state.records, key = { it.id }) { record ->
            BpRecordItem(record = record, onDelete = { deleteTarget = record }, onEdit = { editingRecord = record; showInputDialog = true })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BpRangeRow(label: String, range: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(8.dp), shape = MaterialTheme.shapes.extraSmall, color = color) {}
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BpRecordItem(record: BloodPressureRecord, onDelete: () -> Unit, onEdit: () -> Unit) {
    val displayTime = remember(record.measuredAt) {
        try { Instant.parse(record.measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) }
        catch (_: Exception) { record.measuredAt }
    }
    val (status, color) = bpStatus(record.systolic, record.diastolic)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, style = MaterialTheme.typography.labelMedium, color = color)
                    Spacer(Modifier.width(8.dp))
                    Text(displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${record.systolic}/${record.diastolic}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color))
            Spacer(Modifier.width(4.dp))
            Text("mmHg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
