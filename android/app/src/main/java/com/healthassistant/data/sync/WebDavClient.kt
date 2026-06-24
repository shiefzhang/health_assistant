package com.healthassistant.data.sync

import android.util.Base64
import com.healthassistant.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端
 * 数据存储结构（以 {filename}="health-data" 为例）：
 *
 *   health-data.idx          → 索引文件（只存各指标的 ID 列表）
 *   health-data/glucose/{id}.json   → 每条血糖记录的独立文件
 *   health-data/weight/{id}.json    → 每条体重记录的独立文件
 *   health-data/bp/{id}.json        → 每条血压记录的独立文件
 */
object WebDavClient {
    /** 固定数据目录名 */
    private const val DATA_DIR = "health-data"

    /** 需要确保存在的子目录 */
    private val SUB_DIRS = listOf("glucose", "weight", "bp", "analysis")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun buildAuth(settings: AppSettings): String? {
        return if (settings.webdavUser.isNotBlank()) {
            "Basic " + Base64.encodeToString(
                "${settings.webdavUser}:${settings.webdavPassword}".toByteArray(),
                Base64.NO_WRAP,
            )
        } else null
    }

    private fun baseUrl(settings: AppSettings): String {
        return settings.webdavUrl.trimEnd('/')
    }

    private fun encode(name: String): String = URLEncoder.encode(name, StandardCharsets.UTF_8)

    // ── 索引文件 ──

    /** 确保数据目录和子目录存在（MKCOL） */
    suspend fun ensureDirectories(settings: AppSettings) = withContext(Dispatchers.IO) {
        val auth = buildAuth(settings)
        val base = baseUrl(settings)

        // 创建根目录
        val rootUrl = "$base/$DATA_DIR"
        val rootReq = Request.Builder().url(rootUrl).apply {
            auth?.let { header("Authorization", it) }
            method("MKCOL", null)
        }.build()
        client.newCall(rootReq).execute().close() // 忽略已存在的错误

        // 创建子目录
        for (sub in SUB_DIRS) {
            val url = "$rootUrl/$sub"
            val req = Request.Builder().url(url).apply {
                auth?.let { header("Authorization", it) }
                method("MKCOL", null)
            }.build()
            client.newCall(req).execute().close()
        }
    }

    /** 确保记录文件的父目录存在（含根目录） */
    suspend fun ensureRecordDir(settings: AppSettings, type: String) = withContext(Dispatchers.IO) {
        val auth = buildAuth(settings)
        val base = baseUrl(settings)
        // 先确保根目录 health-data/ 存在
        val rootUrl = "$base/$DATA_DIR"
        val rootReq = Request.Builder().url(rootUrl).apply {
            auth?.let { header("Authorization", it) }
            method("MKCOL", null)
        }.build()
        client.newCall(rootReq).execute().close()
        // 再确保子目录存在
        val childUrl = "$rootUrl/$type"
        val childReq = Request.Builder().url(childUrl).apply {
            auth?.let { header("Authorization", it) }
            method("MKCOL", null)
        }.build()
        client.newCall(childReq).execute().close()
    }

    /** 索引文件路径 */
    fun indexPath(settings: AppSettings): String {
        return "${baseUrl(settings)}/$DATA_DIR.idx"
    }

    /** 单个记录文件路径 */
    fun recordPath(settings: AppSettings, type: String, id: String): String {
        return "${baseUrl(settings)}/$DATA_DIR/$type/${encode(id)}.json"
    }

    /** AI 分析结果文件路径（每个指标只保存最新一条） */
    fun analysisPath(settings: AppSettings, metric: String): String {
        return "${baseUrl(settings)}/$DATA_DIR/analysis/$metric.json"
    }

    /** 下载索引文件，不存在返回 null */
    suspend fun downloadIndex(settings: AppSettings): String? = withContext(Dispatchers.IO) {
        val url = indexPath(settings)
        val auth = buildAuth(settings)
        val request = Request.Builder().url(url).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        when (response.code) {
            200 -> response.body?.string()
            404 -> null
            else -> throw RuntimeException("下载索引失败：HTTP ${response.code}")
        }
    }

    /** 上传索引文件 */
    suspend fun uploadIndex(settings: AppSettings, content: String) = withContext(Dispatchers.IO) {
        ensureDirectories(settings)  // 确保目录存在
        val url = indexPath(settings)
        val auth = buildAuth(settings)
        val body = content.toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).put(body).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        require(response.code in 200..299) { "上传索引失败：HTTP ${response.code}" }
    }

    // ── 单个记录文件 ──

    /** 下载指定记录，不存在返回 null */
    suspend fun downloadRecord(settings: AppSettings, type: String, id: String): String? = withContext(Dispatchers.IO) {
        val url = recordPath(settings, type, id)
        val auth = buildAuth(settings)
        val request = Request.Builder().url(url).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        when (response.code) {
            200 -> response.body?.string()
            404 -> null
            else -> throw RuntimeException("下载记录 $type/$id 失败：HTTP ${response.code}")
        }
    }

    /** 上传单个记录 */
    suspend fun uploadRecord(settings: AppSettings, type: String, id: String, content: String) = withContext(Dispatchers.IO) {
        ensureRecordDir(settings, type)  // 确保父目录存在
        val url = recordPath(settings, type, id)
        val auth = buildAuth(settings)
        val body = content.toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).put(body).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        require(response.code in 200..299) { "上传记录 $type/$id 失败：HTTP ${response.code}" }
    }

    // ── AI 分析结果 ──

    /** 下载某指标的最新 AI 分析结果 */
    suspend fun downloadAnalysis(settings: AppSettings, metric: String): String? = withContext(Dispatchers.IO) {
        val url = analysisPath(settings, metric)
        val auth = buildAuth(settings)
        val request = Request.Builder().url(url).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        when (response.code) {
            200 -> response.body?.string()
            404 -> null
            else -> throw RuntimeException("下载分析结果失败：HTTP ${response.code}")
        }
    }

    /** 上传某指标的最新 AI 分析结果 */
    suspend fun uploadAnalysis(settings: AppSettings, metric: String, content: String) = withContext(Dispatchers.IO) {
        ensureRecordDir(settings, "analysis")  // 确保目录存在
        val url = analysisPath(settings, metric)
        val auth = buildAuth(settings)
        val body = content.toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url).put(body).apply { auth?.let { header("Authorization", it) } }.build()
        val response = client.newCall(request).execute()
        if (response.code !in 200..299 && response.code != 201) {
            throw RuntimeException("上传分析结果失败：HTTP ${response.code}")
        }
    }
}
