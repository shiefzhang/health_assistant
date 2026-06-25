package com.healthassistant.ui.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.ui.components.StatCard
import com.healthassistant.ui.components.WeightChart
import com.healthassistant.ui.components.WeightInputDialog
import com.healthassistant.ui.theme.MetricGreen
import com.healthassistant.ui.theme.MetricPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDataPage(
    repository: HealthRepository,
    viewModel: WeightViewModel = viewModel(factory = WeightViewModel.Factory(repository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInputDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<WeightRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<WeightRecord?>(null) }
    var showingAnalysis by remember { mutableStateOf(false) }

    val analysisText = com.healthassistant.data.sync.AnalysisStore.getText("weight")
    val showAnalysis = analysisText != null && showingAnalysis

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定删除 ${target.measuredAt.take(10)} 的体重记录（${"%.1f".format(target.value)} kg）吗？\n此操作不可撤销。") },
            confirmButton = {
                Button(onClick = { scope.launch { repository.deleteWeight(target.id) }; deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    // 录入/编辑
    if (showInputDialog) {
        WeightInputDialog(
            onDismiss = { showInputDialog = false; editingRecord = null },
            onSave = { id, value, heightCm, bodyFatPercent, measuredAt, notes ->
                scope.launch {
                    repository.upsertWeight(WeightRecord(id, value, heightCm, bodyFatPercent, measuredAt, notes))
                }
            },
            existingRecord = editingRecord,
            lastRecord = if (editingRecord == null) state.records.firstOrNull() else null,
        )
    }

    // 监听 AI 分析完成
    LaunchedEffect(state.aiAnalysis) {
        state.aiAnalysis?.let {
            com.healthassistant.data.sync.AnalysisStore.save(
                "weight", it, java.time.Instant.now().toString()
            )
            showingAnalysis = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("体重 数据", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                FilledTonalIconButton(onClick = { showInputDialog = true; editingRecord = null }) {
                    Icon(Icons.Default.Add, "新增记录")
                }
            }
        }

        // 周期切换
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReportType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.period.type == type,
                        onClick = { viewModel.switchType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReportType.entries.size),
                    ) { Text(type.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        // 日期导航
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousPeriod() }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上${state.period.type.label.first()}期")
                }
                Text(
                    state.period.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { viewModel.nextPeriod() }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下${state.period.type.label.first()}期")
                }
            }
        }

        // 图表
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("体重趋势", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    WeightChart(records = state.records, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 统计
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = "平均值", value = "%.1f".format(state.avg), modifier = Modifier.weight(1f))
                StatCard(label = "最高", value = "%.1f".format(state.max), modifier = Modifier.weight(1f))
                StatCard(label = "最低", value = "%.1f".format(state.min), modifier = Modifier.weight(1f))
                StatCard(label = "记录数", value = "${state.totalCount}", modifier = Modifier.weight(1f))
            }
        }

        // AI
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
                        Spacer(Modifier.width(8.dp)); Text("分析中…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("AI 分析")
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

        // 列表
        if (state.records.isNotEmpty()) {
            item { HorizontalDivider(); Spacer(Modifier.height(4.dp)); Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        }
        items(state.records, key = { it.id }) { record ->
            WeightRecordItem(record = record, onDelete = { deleteTarget = record }, onEdit = { editingRecord = record; showInputDialog = true })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun WeightRecordItem(record: WeightRecord, onDelete: () -> Unit, onEdit: () -> Unit) {
    val displayTime = remember(record.measuredAt) {
        try { Instant.parse(record.measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) }
        catch (_: Exception) { record.measuredAt }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.bmi != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("BMI ${"%.1f".format(record.bmi)}", style = MaterialTheme.typography.labelSmall, color = MetricGreen)
                    }
                    if (record.bodyFatPercent != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("体脂 ${"%.1f".format(record.bodyFatPercent)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("%.1f".format(record.value), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MetricPink))
            Spacer(Modifier.width(4.dp))
            Text("kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
