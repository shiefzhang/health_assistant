package com.healthassistant.data.sync

import com.healthassistant.data.model.GlucoseRecord
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 分析服务
 * 调用兼容 OpenAI API 的接口生成健康评估
 */
object AiAnalysisService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(
        records: List<GlucoseRecord>,
        apiBaseUrl: String,
        apiKey: String,
        model: String,
    ): String {
        require(apiKey.isNotBlank()) { "请先配置 AI API Key" }

        val data = JSONArray()
        records.sortedByDescending { it.measuredAt }.take(90).forEach { data.put(it.toJson()) }

        val prompt = """
你是一位健康管理助手。请对以下血糖数据进行分析，内容包括：
1. 总体趋势分析
2. 异常值提醒
3. 各时段（空腹/餐后等）血糖特点
4. 与可讨论的健康建议要点

注意事项：
- 使用简体中文
- 语言通俗易懂
- 不要诊断疾病
- 不要推荐具体药物
- 结尾必须注明：⚠️ 本分析由 AI 生成，仅供参考，不能替代医生诊断。

数据：${data.toString(2)}
        """.trimIndent()

        val requestBody = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            .toString()

        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/chat/completions")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        val response = client.newCall(request).execute()
        require(response.code in 200..299) { "AI 请求失败：HTTP ${response.code}" }

        val body = response.body?.string() ?: throw RuntimeException("响应为空")
        val json = JSONObject(body)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

/**
 * 通用 AI API 调用（用于体重、血压等非血糖分析）
 */
suspend fun callAiApi(apiBaseUrl: String, apiKey: String, model: String, prompt: String): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val requestBody = JSONObject()
        .put("model", model)
        .put("temperature", 0.2)
        .put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        })
        .toString()

    val request = okhttp3.Request.Builder()
        .url("${apiBaseUrl.trimEnd('/')}/chat/completions")
        .post(requestBody.toRequestBody("application/json".toMediaType()))
        .header("Authorization", "Bearer $apiKey")
        .build()

    val response = client.newCall(request).execute()
    require(response.code in 200..299) { "AI 请求失败：HTTP ${response.code}" }
    val body = response.body?.string() ?: throw RuntimeException("响应为空")
    val json = JSONObject(body)
    return json.getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")
}
