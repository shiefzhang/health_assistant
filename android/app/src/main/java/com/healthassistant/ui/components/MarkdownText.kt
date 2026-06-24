package com.healthassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 简易 Markdown 渲染组件 — 支持：
 * - # ## ### 标题
 * - **粗体** / *斜体*
 * - - 无序列表
 * - 1. 有序列表
 * - `行内代码`
 * - --- 分隔线
 * - 普通段落
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val annotated = buildAnnotatedString {
        val lines = markdown.split("\n")
        var first = true

        for (line in lines) {
            if (!first) append("\n")
            first = false

            val trimmed = line.trim()

            when {
                // 分隔线
                trimmed.matches(Regex("^-{3,}$")) -> {
                    withStyle(SpanStyle(color = onSurfaceVariant)) {
                        append("─".repeat(20))
                    }
                }
                // 标题 ###
                trimmed.startsWith("###") -> {
                    val text = trimmed.removePrefix("###").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = onSurface)) {
                        renderInline(text, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                    }
                }
                // 标题 ##
                trimmed.startsWith("##") -> {
                    val text = trimmed.removePrefix("##").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryColor)) {
                        renderInline(text, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                    }
                }
                // 标题 #
                trimmed.startsWith("#") -> {
                    val text = trimmed.removePrefix("#").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = primaryColor)) {
                        renderInline(text, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                    }
                }
                // 无序列表
                trimmed.startsWith("- ") -> {
                    val text = trimmed.removePrefix("- ").trim()
                    append("• ")
                    renderInline(text, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                }
                // 有序列表
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    renderInline(trimmed, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                }
                else -> {
                    // 普通行，解析内联格式
                    renderInline(trimmed, this@buildAnnotatedString, onSurface, onSurfaceVariant)
                }
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun renderInline(
    text: String,
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    defaultColor: androidx.compose.ui.graphics.Color,
    mutedColor: androidx.compose.ui.graphics.Color,
) {
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    val italicRegex = Regex("""\*(.+?)\*""")
    val codeRegex = Regex("""`(.+?)`""")

    // 用 tokens 混合解析
    val tokens = mutableListOf<Pair<String, String>>() // (type, content)
    var remaining = text

    while (remaining.isNotEmpty()) {
        val boldMatch = boldRegex.find(remaining)
        val italicMatch = italicRegex.find(remaining)
        val codeMatch = codeRegex.find(remaining)

        // 找最近匹配
        val matches = listOfNotNull(
            boldMatch?.let { "bold" to it },
            italicMatch?.let { "italic" to it },
            codeMatch?.let { "code" to it },
        ).sortedBy { it.second.range.first }

        if (matches.isEmpty()) {
            tokens.add("text" to remaining)
            break
        }

        val (type, match) = matches.first()
        if (match.range.first > 0) {
            tokens.add("text" to remaining.substring(0, match.range.first))
        }
        tokens.add(type to match.groupValues[1])
        remaining = remaining.substring(match.range.last + 1)
    }

    for ((type, content) in tokens) {
        when (type) {
            "bold" -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) { append(content) }
            "italic" -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) { append(content) }
            "code" -> builder.withStyle(SpanStyle(fontSize = 13.sp, color = mutedColor)) { append(content) }
            else -> builder.withStyle(SpanStyle(color = defaultColor)) { append(content) }
        }
    }
}
