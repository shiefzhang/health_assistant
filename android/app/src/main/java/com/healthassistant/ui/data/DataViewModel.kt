package com.healthassistant.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 时间周期 */
enum class TimePeriod(val days: Int, val label: String) {
    WEEK(7, "近 1 周"),
    MONTH(30, "近 1 月"),
    YEAR(365, "近 1 年"),
}

data class DataScreenState(
    val period: TimePeriod = TimePeriod.WEEK,
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

    private val _state = MutableStateFlow(DataScreenState())
    val state: StateFlow<DataScreenState> = _state.asStateFlow()

    init {
        loadPeriod(TimePeriod.WEEK)
    }

    fun loadPeriod(period: TimePeriod) {
        _state.update { it.copy(period = period, isLoading = true) }
        viewModelScope.launch {
            repository.getGlucoseRecentFlow(period.days).collect { records ->
                val stats = repository.getGlucoseStats(period.days)
                _state.update {
                    it.copy(
                        records = records,
                        avg = stats.average,
                        max = stats.max,
                        min = stats.min,
                        inRangePercent = stats.inRangePercent,
                        totalCount = stats.totalCount,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun showInputDialog() {
        _state.update { it.copy(showInputDialog = true) }
    }

    fun hideInputDialog() {
        _state.update { it.copy(showInputDialog = false) }
    }

    fun saveRecord(id: String, value: Double, measuredAt: String, mealType: String, notes: String) {
        viewModelScope.launch {
            repository.upsertGlucose(
                GlucoseRecord(
                    id = id,
                    value = value,
                    measuredAt = measuredAt,
                    mealType = mealType,
                    notes = notes,
                )
            )
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            repository.deleteGlucose(id)
        }
    }

    /** 设置 AI 分析结果 */
    fun setAiResult(result: String) {
        _state.update { it.copy(aiAnalysis = result, isAiLoading = false, aiError = null) }
    }

    fun setAiError(error: String) {
        _state.update { it.copy(aiError = error, isAiLoading = false) }
    }

    fun setAiLoading() {
        _state.update { it.copy(isAiLoading = true, aiAnalysis = null, aiError = null) }
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DataViewModel(repository) as T
        }
    }
}
