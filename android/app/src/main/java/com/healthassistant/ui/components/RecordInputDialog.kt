package com.healthassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthassistant.data.model.GlucoseRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 录入/编辑血糖记录对话框
 * @param existingRecord 传入则进入编辑模式，预填已有数据
 * @param lastRecord 新增时从此记录继承数值和测量状态（时间保持当前）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordInputDialog(
    onDismiss: () -> Unit,
    onSave: (id: String, value: Double, measuredAt: String, mealType: String, notes: String) -> Unit,
    existingRecord: GlucoseRecord? = null,
    lastRecord: GlucoseRecord? = null,
) {
    val isEdit = existingRecord != null
    val inherit = !isEdit && lastRecord != null
    val zone = java.time.ZoneId.systemDefault()

    var valueText by remember { mutableStateOf(
        if (isEdit) "%.1f".format(existingRecord!!.value)
        else if (inherit) "%.1f".format(lastRecord!!.value)
        else ""
    ) }
    var valueError by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf(
            if (isEdit) try {
                java.time.Instant.parse(existingRecord!!.measuredAt).atZone(zone).toLocalDate()
            } catch (_: Exception) { LocalDate.now() }
            else LocalDate.now()
        )
    }
    var selectedTime by remember {
        mutableStateOf(
            if (isEdit) try {
                java.time.Instant.parse(existingRecord!!.measuredAt).atZone(zone).toLocalTime()
            } catch (_: Exception) { LocalTime.now() }
            else LocalTime.now()
        )
    }
    val mealTypes = listOf("空腹", "餐前", "餐后2小时", "睡前", "晚餐后")
    val defaultMealIndex = if (inherit) mealTypes.indexOf(lastRecord!!.mealType).coerceAtLeast(0) else 0
    var selectedMeal by remember {
        mutableStateOf(
            if (isEdit) mealTypes.indexOf(existingRecord!!.mealType).coerceAtLeast(0)
            else defaultMealIndex
        )
    }
    var notes by remember { mutableStateOf(
        if (isEdit) existingRecord!!.notes
        else if (inherit) lastRecord!!.notes
        else ""
    ) }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "编辑血糖" else "记录血糖", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 血糖值输入
                OutlinedTextField(
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        valueError = false
                    },
                    label = { Text("血糖值") },
                    suffix = { Text("mmol/L") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = valueError,
                    supportingText = if (valueError) {
                        { Text("请输入 0.1 - 40.0 的有效值") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 测量时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(selectedDate.format(dateFormatter))
                    }
                    OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(selectedTime.format(timeFormatter))
                    }
                }

                // 测量状态
                Text("测量状态", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    mealTypes.take(3).forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedMeal == index,
                            onClick = { selectedMeal = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = 3,
                            ),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mealTypes.drop(3).forEachIndexed { index, label ->
                        val realIndex = index + 3
                        FilterChip(
                            selected = selectedMeal == realIndex,
                            onClick = { selectedMeal = realIndex },
                            label = { Text(label) },
                        )
                    }
                }

                // 备注
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("饮食、运动或身体感受") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = valueText.toDoubleOrNull()
                if (v == null || v < 0.1 || v > 40.0) {
                    valueError = true
                    return@Button
                }
                val zone = java.time.ZoneId.systemDefault()
                val dateTime = java.time.LocalDateTime.of(selectedDate, selectedTime)
                val measuredAt = dateTime.atZone(zone).toInstant().toString()
                val recordId = existingRecord?.id ?: System.currentTimeMillis().toString()
                onSave(recordId, v, measuredAt, mealTypes[selectedMeal], notes)
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
