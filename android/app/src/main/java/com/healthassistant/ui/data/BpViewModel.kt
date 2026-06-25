package com.healthassistant.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.BloodPressureRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BpScreenState(
    val period: ReportPeriod = ReportPeriod.forType(ReportType.WEEK),
    val records: List<BloodPressureRecord> = emptyList(),
    val avgSystolic: Double = 0.0,
    val avgDiastolic: Double = 0.0,
    val maxSystolic: Int = 0,
    val minSystolic: Int = 0,
    val totalCount: Int = 0,
    val aiAnalysis: String? = null,
    val isAiLoading: Boolean = false,
    val aiError: String? = null,
    val isLoading: Boolean = true,
)

class BpViewModel(
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BpScreenState())
    val state: StateFlow<BpScreenState> = _state.asStateFlow()

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
                val records = withContext(Dispatchers.IO) {
                    repository.getBpBetween(p.startIso, p.endIso)
                }
                val avgSys = if (records.isNotEmpty()) records.map { it.systolic }.average() else 0.0
                val avgDia = if (records.isNotEmpty()) records.map { it.diastolic }.average() else 0.0
                val maxSys = if (records.isNotEmpty()) records.maxOf { it.systolic } else 0
                val minSys = if (records.isNotEmpty()) records.minOf { it.systolic } else 0
                _state.update {
                    it.copy(
                        records = records,
                        avgSystolic = avgSys,
                        avgDiastolic = avgDia,
                        maxSystolic = maxSys,
                        minSystolic = minSys,
                        totalCount = records.size,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setAiResult(result: String) { _state.update { it.copy(aiAnalysis = result, isAiLoading = false, aiError = null) } }
    fun setAiError(error: String) { _state.update { it.copy(aiError = error, isAiLoading = false) } }
    fun setAiLoading() { _state.update { it.copy(isAiLoading = true, aiAnalysis = null, aiError = null) } }

    suspend fun analyzeWithAi(
        records: List<BloodPressureRecord>,
        apiBaseUrl: String,
        apiKey: String,
        model: String,
    ): String {
        require(apiKey.isNotBlank()) { "请先配置 AI API Key" }
        val data = org.json.JSONArray()
        records.sortedByDescending { it.measuredAt }.take(90).forEach { data.put(it.toJson()) }
        val prompt = """
你是一位健康管理助手。请对以下血压数据进行分析，内容包括：
1. 总体趋势分析
2. 收缩压与舒张压的平均水平
3. 血压控制评估（参考标准 140/90）
4. 可讨论的健康建议要点

注意事项：
- 使用简体中文
- 语言通俗易懂
- 不要诊断疾病
- 不要推荐具体药物
- 结尾必须注明：⚠️ 本分析由 AI 生成，仅供参考，不能替代医生诊断。

数据：${data.toString(2)}
        """.trimIndent()
        return com.healthassistant.data.sync.callAiApi(apiBaseUrl, apiKey, model, prompt)
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BpViewModel(repository) as T
        }
    }
}
