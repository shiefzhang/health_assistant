package com.healthassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthassistant.data.model.BloodPressureRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 录入/编辑血压记录对话框
 * @param existingRecord 传入则进入编辑模式
 * @param lastRecord 新增时从此记录继承数值（时间保持当前）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BpInputDialog(
    onDismiss: () -> Unit,
    onSave: (id: String, systolic: Int, diastolic: Int, heartRate: Int, measuredAt: String, notes: String) -> Unit,
    existingRecord: BloodPressureRecord? = null,
    lastRecord: BloodPressureRecord? = null,
) {
    val isEdit = existingRecord != null
    val inherit = !isEdit && lastRecord != null
    val zone = java.time.ZoneId.systemDefault()

    var systolicText by remember { mutableStateOf(
        if (isEdit) existingRecord!!.systolic.toString()
        else if (inherit) lastRecord!!.systolic.toString()
        else ""
    ) }
    var diastolicText by remember { mutableStateOf(
        if (isEdit) existingRecord!!.diastolic.toString()
        else if (inherit) lastRecord!!.diastolic.toString()
        else ""
    ) }
    var hrText by remember { mutableStateOf(
        if (isEdit && existingRecord!!.heartRate > 0) existingRecord.heartRate.toString()
        else if (inherit && lastRecord!!.heartRate > 0) lastRecord.heartRate.toString()
        else ""
    ) }
    var systolicError by remember { mutableStateOf(false) }
    var diastolicError by remember { mutableStateOf(false) }
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
        title = { Text(if (isEdit) "编辑血压" else "记录血压", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = systolicText,
                        onValueChange = { systolicText = it; systolicError = false },
                        label = { Text("收缩压") },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = systolicError,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = diastolicText,
                        onValueChange = { diastolicText = it; diastolicError = false },
                        label = { Text("舒张压") },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = diastolicError,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = hrText,
                    onValueChange = { hrText = it },
                    label = { Text("脉搏（可选）") },
                    suffix = { Text("次/分") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    placeholder = { Text("身体感受或用药情况") },
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val sys = systolicText.toIntOrNull()
                val dia = diastolicText.toIntOrNull()
                if (sys == null || sys < 30 || sys > 300) { systolicError = true; return@Button }
                if (dia == null || dia < 20 || dia > 250) { diastolicError = true; return@Button }
                val zone = java.time.ZoneId.systemDefault()
                val dateTime = java.time.LocalDateTime.of(selectedDate, selectedTime)
                val measuredAt = dateTime.atZone(zone).toInstant().toString()
                val hr = hrText.toIntOrNull() ?: 0
                val recordId = existingRecord?.id ?: System.currentTimeMillis().toString()
                onSave(recordId, sys, dia, hr, measuredAt, notes)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
