package com.healthassistant.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WeightScreenState(
    val period: ReportPeriod = ReportPeriod.forType(ReportType.WEEK),
    val records: List<WeightRecord> = emptyList(),
    val avg: Double = 0.0,
    val max: Double = 0.0,
    val min: Double = 0.0,
    val totalCount: Int = 0,
    val aiAnalysis: String? = null,
    val isAiLoading: Boolean = false,
    val aiError: String? = null,
    val isLoading: Boolean = true,
)

class WeightViewModel(
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeightScreenState())
    val state: StateFlow<WeightScreenState> = _state.asStateFlow()

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
                    repository.getWeightBetween(p.startIso, p.endIso)
                }
                val values = records.map { it.value }
                _state.update {
                    it.copy(
                        records = records,
                        avg = if (values.isNotEmpty()) values.average() else 0.0,
                        max = if (values.isNotEmpty()) values.max() else 0.0,
                        min = if (values.isNotEmpty()) values.min() else 0.0,
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
        records: List<WeightRecord>,
        apiBaseUrl: String,
        apiKey: String,
        model: String,
    ): String {
        require(apiKey.isNotBlank()) { "请先配置 AI API Key" }
        val data = org.json.JSONArray()
        records.sortedByDescending { it.measuredAt }.take(90).forEach { data.put(it.toJson()) }
        val prompt = """
你是一位健康管理助手。请对以下体重数据进行分析，内容包括：
1. 总体趋势分析
2. 体重变化幅度
3. BMI 评估（如有身高数据）
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
            return WeightViewModel(repository) as T
        }
    }
}
