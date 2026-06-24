package com.healthassistant.data.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/**
 * AI 分析结果内存存储 — 每个指标仅保留最新一条
 * 使用 Compose mutableStateMapOf 实现 UI 自动响应
 */
object AnalysisStore {
    /** metric → (text, updatedAt) */
    private val store = mutableStateMapOf<String, Pair<String, String>>()

    /** 保存分析结果 */
    fun save(metric: String, text: String, updatedAt: String) {
        store[metric] = text to updatedAt
    }

    /** 获取分析文本 */
    fun getText(metric: String): String? = store[metric]?.first

    /** 获取更新时间 */
    fun getUpdatedAt(metric: String): String? = store[metric]?.second

    /** 检查是否有结果 */
    fun hasResult(metric: String): Boolean = store.containsKey(metric)
}
