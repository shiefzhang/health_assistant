package com.healthassistant.ui.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.data.sync.AiAnalysisService
import com.healthassistant.ui.components.GlucoseChart
import com.healthassistant.ui.components.RecordInputDialog
import com.healthassistant.ui.components.StatCard
import com.healthassistant.ui.theme.glucoseStatusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseDataPage(
    repository: HealthRepository,
    viewModel: DataViewModel = viewModel(factory = DataViewModel.Factory(repository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingRecord by remember { mutableStateOf<GlucoseRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<GlucoseRecord?>(null) }
    var showingAnalysis by remember { mutableStateOf(false) }

    // 从全局存储读取最新分析结果
    val analysisText = com.healthassistant.data.sync.AnalysisStore.getText("glucose")
    val showAnalysis = analysisText != null && showingAnalysis

    // 删除确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = {
                Text("确定删除 ${target.measuredAt.take(10)} 的血糖记录（${"%.1f".format(target.value)} mmol/L）吗？\n此操作不可撤销。")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteRecord(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 录入/编辑对话框
    if (state.showInputDialog) {
        RecordInputDialog(
            onDismiss = { viewModel.hideInputDialog(); editingRecord = null },
            onSave = { id, value, measuredAt, mealType, notes ->
                viewModel.saveRecord(id, value, measuredAt, mealType, notes)
            },
            existingRecord = editingRecord,
            lastRecord = if (editingRecord == null) state.records.firstOrNull() else null,
        )
    }

    // 监听 AI 分析完成 → 存入全局存储
    LaunchedEffect(state.aiAnalysis) {
        state.aiAnalysis?.let {
            com.healthassistant.data.sync.AnalysisStore.save(
                "glucose", it, java.time.Instant.now().toString()
            )
            showingAnalysis = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题 + 录入按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "血糖 数据",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FilledTonalIconButton(onClick = { viewModel.showInputDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "新增记录")
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "血糖 趋势",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlucoseChart(
                        records = state.records,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 统计摘要
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(label = "平均值", value = "%.1f".format(state.avg), modifier = Modifier.weight(1f))
                StatCard(label = "最高", value = "%.1f".format(state.max), modifier = Modifier.weight(1f))
                StatCard(label = "最低", value = "%.1f".format(state.min), modifier = Modifier.weight(1f))
                StatCard(label = "达标率", value = "${state.inRangePercent}%", modifier = Modifier.weight(1f))
            }
        }

        // AI 分析按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.setAiLoading()
                        scope.launch {
                            try {
                                val settings = com.healthassistant.MainActivity.loadSettings(context)
                                val result = withContext(Dispatchers.IO) {
                                    AiAnalysisService.analyze(
                                        records = state.records,
                                        apiBaseUrl = settings.aiBaseUrl,
                                        apiKey = settings.aiKey,
                                        model = settings.aiModel,
                                    )
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
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

        // AI 分析结果（内联显示）
        if (showAnalysis) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "AI 分析结果",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = { showingAnalysis = false }) {
                                Text("收起", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        com.healthassistant.ui.components.MarkdownText(
                            markdown = analysisText ?: "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // AI 错误提示
        state.aiError?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 记录列表标题
        if (state.records.isNotEmpty()) {
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "共 ${state.totalCount} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 记录条目
        items(state.records, key = { it.id }) { record ->
            GlucoseRecordItem(
                record = record,
                onDelete = { deleteTarget = record },
                onEdit = {
                    editingRecord = record
                    viewModel.showInputDialog()
                },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GlucoseRecordItem(
    record: GlucoseRecord,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    val displayTime = remember(record.measuredAt) {
        try {
            Instant.parse(record.measuredAt)
                .atZone(zone)
                .format(dateFormatter)
        } catch (_: Exception) {
            record.measuredAt
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.mealType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (record.type != "blood_glucose") {
                        Spacer(Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(record.type, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = displayTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (record.notes.isNotBlank()) {
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "%.1f".format(record.value),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = glucoseStatusColor(record.value),
                ),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "mmol/L",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
