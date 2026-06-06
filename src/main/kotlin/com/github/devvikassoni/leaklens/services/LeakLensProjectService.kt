package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.AnalysisHistoryEntry
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.github.devvikassoni.leaklens.settings.PersistedHistoryEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Service(Service.Level.PROJECT)
class LeakLensProjectService(private val project: Project, val scope: CoroutineScope) {

    private val _leaks = MutableStateFlow<List<LeakInfo>>(emptyList())
    val leaks: StateFlow<List<LeakInfo>> = _leaks.asStateFlow()

    private val _liveIssues = MutableStateFlow<Map<String, Map<String, List<LeakInfo>>>>(emptyMap())
    val liveIssues: StateFlow<List<LeakInfo>> = _liveIssues
        .map { fileMap -> fileMap.values.flatMap { inspectionMap -> inspectionMap.values.flatten() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // O(1) cache for gutter markers to avoid O(N*M) lag on the highlighting thread
    @Volatile
    var retainedClassNames: Set<String> = emptySet()
        private set
    
    @Volatile
    var referenceChainClassNames: Set<String> = emptySet()
        private set

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _history = MutableStateFlow<List<AnalysisHistoryEntry>>(emptyList())
    val history: StateFlow<List<AnalysisHistoryEntry>> = _history.asStateFlow()

    private val logger = thisLogger()

    init {
        // Load persisted history on initialization
        loadPersistedHistory()
    }

    fun updateLeaks(newLeaks: List<LeakInfo>) {
        _leaks.value = newLeaks
        
        // Update caches for line markers
        retainedClassNames = newLeaks.map { it.retainedObjectClassName }.toSet()
        referenceChainClassNames = newLeaks.flatMap { leak -> leak.referenceChain.map { it.owningClassName } }.toSet()
        
        logger.info("LeakLens: Updated with ${newLeaks.size} leak(s)")
    }

    fun updateLiveIssues(filePath: String, inspectionName: String, issues: List<LeakInfo>) {
        synchronized(this) {
            val currentFileMap = _liveIssues.value.toMutableMap()
            val currentInspectionMap = currentFileMap[filePath]?.toMutableMap() ?: mutableMapOf()
            
            if (issues.isEmpty()) {
                currentInspectionMap.remove(inspectionName)
            } else {
                currentInspectionMap[inspectionName] = issues
            }
            
            if (currentInspectionMap.isEmpty()) {
                currentFileMap.remove(filePath)
            } else {
                currentFileMap[filePath] = currentInspectionMap
            }
            
            _liveIssues.value = currentFileMap
        }
    }

    fun setAnalyzing(analyzing: Boolean) {
        _isAnalyzing.value = analyzing
    }

    fun clearLeaks() {
        _leaks.value = emptyList()
        _liveIssues.value = emptyMap()
        retainedClassNames = emptySet()
        referenceChainClassNames = emptySet()
    }

    fun addToHistory(leaks: List<LeakInfo>, sourceName: String) {
        val entry = AnalysisHistoryEntry(
            timestamp = System.currentTimeMillis(),
            sourceName = sourceName,
            leakCount = leaks.size,
            criticalCount = leaks.count { it.severity == LeakSeverity.CRITICAL },
            warningCount = leaks.count { it.severity == LeakSeverity.WARNING },
            libraryLeakCount = leaks.count { it.severity == LeakSeverity.LIBRARY_LEAK },
            leaks = leaks
        )
        _history.value = _history.value + entry
        logger.info("LeakLens: Added analysis to history (total: ${_history.value.size} entries)")

        // Persist to project-level storage
        persistHistory(entry)
    }

    fun clearHistory() {
        _history.value = emptyList()
        val settings = LeakLensSettingsState.getInstance(project)
        settings.historyEntries.clear()
    }

    private fun persistHistory(entry: AnalysisHistoryEntry) {
        val settings = LeakLensSettingsState.getInstance(project)
        if (!settings.persistHistory) return

        settings.historyEntries.add(
            PersistedHistoryEntry(
                timestamp = entry.timestamp,
                sourceName = entry.sourceName,
                leakCount = entry.leakCount,
                criticalCount = entry.criticalCount,
                warningCount = entry.warningCount,
                libraryLeakCount = entry.libraryLeakCount
            )
        )

        // Trim to max entries
        while (settings.historyEntries.size > settings.maxHistoryEntries) {
            settings.historyEntries.removeAt(0)
        }
    }

    private fun loadPersistedHistory() {
        try {
            val settings = LeakLensSettingsState.getInstance(project)
            if (!settings.persistHistory) return

            val entries = settings.historyEntries.map { persisted ->
                AnalysisHistoryEntry(
                    timestamp = persisted.timestamp,
                    sourceName = persisted.sourceName,
                    leakCount = persisted.leakCount,
                    criticalCount = persisted.criticalCount,
                    warningCount = persisted.warningCount,
                    libraryLeakCount = persisted.libraryLeakCount,
                    leaks = emptyList() // Full leak data not persisted, only summary
                )
            }
            _history.value = entries
            logger.info("LeakLens: Loaded ${entries.size} history entries from storage")
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to load persisted history", e)
        }
    }

    companion object {
        fun getInstance(project: Project): LeakLensProjectService =
            project.getService(LeakLensProjectService::class.java)
    }
}
