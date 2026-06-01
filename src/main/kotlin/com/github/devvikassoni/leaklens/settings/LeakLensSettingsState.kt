package com.github.devvikassoni.leaklens.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent project-level settings for LeakLens.
 * Stores configuration and analysis history across IDE sessions.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "LeakLensSettings",
    storages = [Storage("leaklens.xml")]
)
class LeakLensSettingsState : PersistentStateComponent<LeakLensSettingsState> {

    // General settings
    var autoDetectEnabled: Boolean = false
    var showGutterIcons: Boolean = true
    var analysisOnImport: Boolean = true

    // AI settings
    var aiEnabled: Boolean = false
    var aiProvider: String = "none" // "none", "openai", "gemini"
    var aiApiKey: String = ""
    var aiAnonymizePackageNames: Boolean = true
    var aiSendFullContext: Boolean = false

    // History settings
    var maxHistoryEntries: Int = 50
    var persistHistory: Boolean = true

    // Monitoring settings (Phase 6)
    var autoHeapDumpThresholdMb: Int = 256  // 0 = disabled
    var monitorIntervalMs: Long = 5000

    // Baseline settings (Phase 7)
    var useBaseline: Boolean = true
    var baselineFilePath: String = "leak-baseline.json"

    // Deobfuscation
    var autoDetectMapping: Boolean = true
    var mappingFilePath: String = ""

    // Persisted history (serialized as simple format)
    var historyEntries: MutableList<PersistedHistoryEntry> = mutableListOf()

    override fun getState(): LeakLensSettingsState = this

    override fun loadState(state: LeakLensSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): LeakLensSettingsState =
            project.getService(LeakLensSettingsState::class.java)
    }
}

/**
 * Serializable history entry for XML persistence.
 */
data class PersistedHistoryEntry(
    var timestamp: Long = 0,
    var sourceName: String = "",
    var leakCount: Int = 0,
    var criticalCount: Int = 0,
    var warningCount: Int = 0,
    var libraryLeakCount: Int = 0
)

