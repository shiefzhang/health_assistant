package com.healthassistant.data.model

/**
 * 应用设置
 * theme: "light" / "dark" / "system"
 */
data class AppSettings(
    val theme: String = "system",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiKey: String = "",
    val aiModel: String = "gpt-4.1-mini",
)
