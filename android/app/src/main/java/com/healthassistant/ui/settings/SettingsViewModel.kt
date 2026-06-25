package com.healthassistant.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthassistant.MainActivity
import com.healthassistant.data.model.AppSettings
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.data.sync.SyncManager
import com.healthassistant.util.ExportImportUtil
import com.healthassistant.util.NormalPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val themeIndex: Int = 2, // "system" = index 2
    val syncStatus: String = "",
    val syncLoading: Boolean = false,
    val importExportStatus: String = "",
    val dbRecordCount: Int = 0,
)

class SettingsViewModel(
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var syncManager: SyncManager? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        syncManager = SyncManager(repository)
        loadSettings()
    }

    private fun loadSettings() {
        val s = MainActivity.loadSettings(appContext)
        _state.update {
            it.copy(
                settings = s,
                themeIndex = when (s.theme) {
                    "light" -> 0
                    "dark" -> 1
                    else -> 2
                },
            )
        }
    }

    fun updateTheme(index: Int) {
        val theme = when (index) { 0 -> "light"; 1 -> "dark"; else -> "system" }
        _state.update { it.copy(settings = it.settings.copy(theme = theme), themeIndex = index) }
        save()
    }

    fun updateWebdavUrl(url: String) {
        _state.update { it.copy(settings = it.settings.copy(webdavUrl = url)) }
    }

    fun updateWebdavUser(user: String) {
        _state.update { it.copy(settings = it.settings.copy(webdavUser = user)) }
    }

    fun updateWebdavPassword(password: String) {
        _state.update { it.copy(settings = it.settings.copy(webdavPassword = password)) }
    }

    fun updateAiUrl(url: String) {
        _state.update { it.copy(settings = it.settings.copy(aiBaseUrl = url)) }
    }

    fun updateAiKey(key: String) {
        _state.update { it.copy(settings = it.settings.copy(aiKey = key)) }
    }

    fun updateAiModel(model: String) {
        _state.update { it.copy(settings = it.settings.copy(aiModel = model)) }
    }

    fun save() {
        MainActivity.saveSettings(appContext, _state.value.settings)
    }

    /** 执行 WebDAV 同步 */
    fun runSync() {
        _state.update { it.copy(syncLoading = true, syncStatus = "") }
        viewModelScope.launch {
            try {
                syncManager?.let {
                    val result = withContext(Dispatchers.IO) {
                        it.sync(_state.value.settings)
                    }
                    _state.update {
                        it.copy(
                            syncStatus = "同步完成：导入 ${result.totalImported} 条",
                            syncLoading = false,
                        )
                    }
                } ?: throw RuntimeException("未初始化")
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        syncStatus = "同步失败：${e.message}",
                        syncLoading = false,
                    )
                }
            }
        }
    }

    /** 导入数据（按 type 字段路由到对应数据表） */
    fun importData(text: String) {
        viewModelScope.launch {
            try {
                var totalImported = 0
                var totalSkipped = 0
                withContext(Dispatchers.IO) {
                    val (glucose, weight, bp) = ExportImportUtil.importByType(text)
                    if (glucose.isNotEmpty()) {
                        val result = repository.importGlucoseDedup(glucose)
                        totalImported += result.imported
                        totalSkipped += result.skipped
                    }
                    if (weight.isNotEmpty()) {
                        val result = repository.importWeightDedup(weight)
                        totalImported += result.imported
                        totalSkipped += result.skipped
                    }
                    if (bp.isNotEmpty()) {
                        val result = repository.importBpDedup(bp)
                        totalImported += result.imported
                        totalSkipped += result.skipped
                    }
                }
                val msg = if (totalSkipped > 0) {
                    "导入完成：新增 $totalImported 条，跳过 $totalSkipped 条重复数据"
                } else {
                    "导入完成：新增 $totalImported 条"
                }
                _state.update { it.copy(importExportStatus = msg) }
            } catch (e: Exception) {
                _state.update { it.copy(importExportStatus = "导入失败：${e.message}") }
            }
        }
    }

    suspend fun getExportData(): String {
        val glucose = repository.getAllGlucoseList()
        val weight = repository.getAllWeightList()
        val bp = repository.getAllBpList()
        return ExportImportUtil.exportToJson(glucose, weight, bp)
    }

    fun setExportSuccess() {
        _state.update { it.copy(importExportStatus = "导出完成") }
    }

    fun setExportError(msg: String) {
        _state.update { it.copy(importExportStatus = "导出失败：$msg") }
    }

    class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
