package com.healthassistant.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.data.model.WeightRecord
import com.healthassistant.data.repository.HealthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

data class WeightScreenState(
    val period: TimePeriod = TimePeriod.WEEK,
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
        loadPeriod(TimePeriod.WEEK)
    }

    fun loadPeriod(period: TimePeriod) {
        _state.update { it.copy(period = period, isLoading = true) }
        viewModelScope.launch {
            repository.getWeightRecentFlow(period.days).collect { records ->
                val stats = repository.getWeightStats(period.days)
                _state.update {
                    it.copy(
                        records = records,
                        avg = stats.average,
                        max = stats.max,
                        min = stats.min,
                        totalCount = stats.totalCount,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setAiResult(result: String) {
        _state.update { it.copy(aiAnalysis = result, isAiLoading = false, aiError = null) }
    }

    fun setAiError(error: String) {
        _state.update { it.copy(aiError = error, isAiLoading = false) }
    }

    fun setAiLoading() {
        _state.update { it.copy(isAiLoading = true, aiAnalysis = null, aiError = null) }
    }

    suspend fun analyzeWithAi(
        records: List<WeightRecord>,
        apiBaseUrl: String,
        apiKey: String,
        model: String,
    ): String {
        require(apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
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

        val body = org.json.JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("messages", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            .toString()

        val requestBody = "application/json; charset=utf-8".toMediaType().let {
            okhttp3.RequestBody.create(it, body)
        }
        val request = okhttp3.Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/chat/completions")
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")
            .build()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        require(response.code in 200..299) { "AI 请求失败：HTTP ${response.code}" }
        val json = org.json.JSONObject(response.body?.string() ?: throw RuntimeException("响应为空"))
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WeightViewModel(repository) as T
        }
    }
}
