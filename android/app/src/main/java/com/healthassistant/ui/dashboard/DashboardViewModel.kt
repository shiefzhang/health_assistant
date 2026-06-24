package com.healthassistant.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.model.GlucoseRecord
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val recentRecords: List<DashboardRecord> = emptyList(),
    val latestWeight: WeightRecord? = null,
    val latestBp: BloodPressureRecord? = null,
    val latestHeartRate: Int = 0,
    val isLoading: Boolean = true,
)

class DashboardViewModel(
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllGlucose(),
                repository.getAllWeight(),
                repository.getAllBp(),
            ) { glucose, weight, bp ->
                val list = mutableListOf<DashboardRecord>()
                glucose.forEach { list.add(DashboardRecord.Glucose(it)) }
                weight.forEach { list.add(DashboardRecord.Weight(it)) }
                bp.forEach { list.add(DashboardRecord.BloodPressure(it)) }
                // 按时间倒序排列
                list.sortByDescending { it.measuredAt }

                val latestWeight = weight.firstOrNull()
                val latestBp = bp.firstOrNull()

                DashboardState(
                    recentRecords = list.take(20),
                    latestWeight = latestWeight,
                    latestBp = latestBp,
                    latestHeartRate = latestBp?.heartRate ?: 0,
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository) as T
        }
    }
}
