package com.healthassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthassistant.data.model.WeightRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 录入/编辑体重记录对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightInputDialog(
    onDismiss: () -> Unit,
    onSave: (id: String, value: Double, heightCm: Double, bodyFatPercent: Double?, measuredAt: String, notes: String) -> Unit,
    existingRecord: WeightRecord? = null,
    lastRecord: WeightRecord? = null,
) {
    val isEdit = existingRecord != null
    val inherit = !isEdit && lastRecord != null
    val zone = java.time.ZoneId.systemDefault()

    var valueText by remember { mutableStateOf(
        if (isEdit) "%.1f".format(existingRecord!!.value) else ""
    ) }
    var heightText by remember { mutableStateOf(
        if (isEdit && existingRecord!!.heightCm > 0) "%.0f".format(existingRecord.heightCm)
        else if (inherit && lastRecord!!.heightCm > 0) "%.0f".format(lastRecord.heightCm)
        else ""
    ) }
    var fatText by remember { mutableStateOf(
        if (isEdit && existingRecord!!.bodyFatPercent != null) "%.1f".format(existingRecord.bodyFatPercent)
        else ""
    ) }
    var valueError by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf(
            if (isEdit) try { java.time.Instant.parse(existingRecord!!.measuredAt).atZone(zone).toLocalDate() }
            catch (_: Exception) { LocalDate.now() }
            else LocalDate.now()
        )
    }
    var selectedTime by remember {
        mutableStateOf(
            if (isEdit) try { java.time.Instant.parse(existingRecord!!.measuredAt).atZone(zone).toLocalTime() }
            catch (_: Exception) { LocalTime.now() }
            else LocalTime.now()
        )
    }
    var notes by remember { mutableStateOf(
        if (isEdit) existingRecord!!.notes
        else if (inherit) lastRecord!!.notes
        else ""
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑体重" else "记录体重", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it; valueError = false },
                    label = { Text("体重") },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = valueError,
                    supportingText = if (valueError) { { Text("请输入有效体重（20 - 500 kg）") } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("身高") },
                        suffix = { Text("cm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("体脂率") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    }
                    OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("饮食、运动或身体感受") },
                    minLines = 1, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = valueText.toDoubleOrNull()
                if (v == null || v < 20.0 || v > 500.0) { valueError = true; return@Button }
                val h = heightText.toDoubleOrNull()?.coerceIn(50.0, 300.0) ?: 0.0
                val fat = fatText.toDoubleOrNull()?.coerceIn(1.0, 70.0)
                val dateTime = java.time.LocalDateTime.of(selectedDate, selectedTime)
                val measuredAt = dateTime.atZone(zone).toInstant().toString()
                val recordId = existingRecord?.id ?: System.currentTimeMillis().toString()
                onSave(recordId, v, h, fat, measuredAt, notes)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
