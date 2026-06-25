package com.healthassistant.ui.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 时间周期（用于血压/体重页） */
enum class TimePeriod(val days: Int, val label: String) {
    WEEK(7, "近 1 周"),
    MONTH(30, "近 1 月"),
    YEAR(365, "近 1 年"),
}

data class DataScreenState(
    val period: ReportPeriod = ReportPeriod.forType(ReportType.WEEK),
    val records: List<GlucoseRecord> = emptyList(),
    val avg: Double = 0.0,
    val max: Double = 0.0,
    val min: Double = 0.0,
    val inRangePercent: Int = 0,
    val totalCount: Int = 0,
    val showInputDialog: Boolean = false,
    val aiAnalysis: String? = null,
    val isAiLoading: Boolean = false,
    val aiError: String? = null,
    val isLoading: Boolean = true,
)

class DataViewModel(
    private val repository: HealthRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "DataViewModel"
    }

    private val _state = MutableStateFlow(DataScreenState())
    val state: StateFlow<DataScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun switchType(type: ReportType) {
        _state.update { it.copy(period = ReportPeriod.forType(type), isLoading = true) }
        load()
    }

    fun previousPeriod() {
        _state.update { it.copy(period = it.period.previous(), isLoading = true) }
        load()
    }

    fun nextPeriod() {
        _state.update { it.copy(period = it.period.next(), isLoading = true) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val p = _state.value.period
                Log.d(TAG, "load: period=${p.label} start=${p.startIso} end=${p.endIso}")
                val records = withContext(Dispatchers.IO) {
                    repository.getGlucoseBetween(p.startIso, p.endIso)
                }
                Log.d(TAG, "load: got ${records.size} glucose records")
                if (records.isNotEmpty()) {
                    Log.d(TAG, "load: first=${records.first().measuredAt} last=${records.last().measuredAt}")
                }
                val values = records.map { it.value }
                val inRangeCount = records.count { it.value in 3.9..10.0 }
                _state.update {
                    it.copy(
                        records = records,
                        avg = if (values.isNotEmpty()) values.average() else 0.0,
                        max = if (values.isNotEmpty()) values.max() else 0.0,
                        min = if (values.isNotEmpty()) values.min() else 0.0,
                        inRangePercent = if (records.isNotEmpty()) inRangeCount * 100 / records.size else 0,
                        totalCount = records.size,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed: ${e.message}", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showInputDialog() { _state.update { it.copy(showInputDialog = true) } }
    fun hideInputDialog() { _state.update { it.copy(showInputDialog = false) } }

    fun saveRecord(id: String, value: Double, measuredAt: String, mealType: String, notes: String) {
        viewModelScope.launch { repository.upsertGlucose(GlucoseRecord(id, value, measuredAt, mealType = mealType, notes = notes)) }
    }

    fun deleteRecord(id: String) { viewModelScope.launch { repository.deleteGlucose(id) } }

    fun setAiResult(result: String) { _state.update { it.copy(aiAnalysis = result, isAiLoading = false, aiError = null) } }
    fun setAiError(error: String) { _state.update { it.copy(aiError = error, isAiLoading = false) } }
    fun setAiLoading() { _state.update { it.copy(isAiLoading = true, aiAnalysis = null, aiError = null) } }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DataViewModel(repository) as T
        }
    }
}
