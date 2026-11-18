package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.AnalysisHistoryEntry
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.github.devvikassoni.leaklens.settings.PersistedHistoryEntry
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Service(Service.Level.PROJECT)
class LeakLensProjectService(private val project: Project, val scope: CoroutineScope) {

    private val _leaks = MutableStateFlow<List<LeakInfo>>(emptyList())
    val leaks: StateFlow<List<LeakInfo>> = _leaks.asStateFlow()

    private val _liveIssues = MutableStateFlow<Map<String, Map<String, List<LeakInfo>>>>(emptyMap())
    private val _liveIssuesList = MutableStateFlow<List<LeakInfo>>(emptyList())
    val liveIssues: StateFlow<List<LeakInfo>> = _liveIssuesList.asStateFlow()

    private val _staticIssues =
        MutableStateFlow<Map<String, Map<String, List<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue>>>>(
            emptyMap()
        )
    private val _staticIssuesList =
        MutableStateFlow<List<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue>>(emptyList())
    val staticIssues: StateFlow<List<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue>> =
        _staticIssuesList.asStateFlow()

    private fun refreshLiveIssuesList() {
        _liveIssuesList.value = _liveIssues.value.values.flatMap { inspectionMap -> inspectionMap.values.flatten() }
    }

    private fun refreshStaticIssuesList() {
        _staticIssuesList.value = _staticIssues.value.values.flatMap { ruleMap -> ruleMap.values.flatten() }
    }

    /**
     * Returns live issues for a specific file to support efficient gutter markers.
     */
    fun getLiveIssuesForFile(filePath: String): List<LeakInfo> {
        return _liveIssues.value[filePath]?.values?.flatten() ?: emptyList()
    }

    fun getStaticIssuesForFile(filePath: String): List<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue> {
        return _staticIssues.value[filePath]?.values?.flatten() ?: emptyList()
    }

    // O(1) cache for gutter markers to avoid O(N*M) lag on the highlighting thread
    @Volatile
    var retainedClassNames: Set<String> = emptySet()
        private set

    @Volatile
    var referenceChainClassNames: Set<String> = emptySet()
        private set

    private val liveLeakyLines = ConcurrentHashMap<String, Set<Int>>()

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
        _liveIssues.update { currentFileMap ->
            val newFileMap = currentFileMap.toMutableMap()
            val currentInspectionMap = newFileMap[filePath]?.toMutableMap() ?: mutableMapOf()

            if (issues.isEmpty()) {
                currentInspectionMap.remove(inspectionName)
            } else {
                currentInspectionMap[inspectionName] = issues
            }

            if (currentInspectionMap.isEmpty()) {
                newFileMap.remove(filePath)
            } else {
                newFileMap[filePath] = currentInspectionMap
            }
            newFileMap
        }

        updateLineCache(filePath)
        refreshLiveIssuesList()
    }

    fun addStaticIssue(issue: com.github.devvikassoni.leaklens.model.StaticAnalysisIssue) {
        _staticIssues.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            val fileMap = newMap[issue.filePath]?.toMutableMap() ?: mutableMapOf()
            val ruleIssues = fileMap[issue.ruleId]?.toMutableList() ?: mutableListOf()

            // Deduplicate by fingerprint
            if (ruleIssues.none { it.fingerprint == issue.fingerprint }) {
                ruleIssues.add(issue)
            }

            newMap[issue.filePath] = fileMap
            newMap
        }
        updateLineCache(issue.filePath)
        refreshStaticIssuesList()
    }

    fun addStaticIssues(issues: Collection<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue>) {
        if (issues.isEmpty()) return
        val affectedFiles = mutableSetOf<String>()
        _staticIssues.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            issues.groupBy { it.filePath }.forEach { (filePath, fileIssues) ->
                affectedFiles.add(filePath)
                val newFileMap = newMap[filePath]?.toMutableMap() ?: mutableMapOf()
                fileIssues.groupBy { it.ruleId }.forEach { (ruleId, newRuleIssues) ->
                    val ruleIssues = newFileMap[ruleId]?.toMutableList() ?: mutableListOf()
                    newRuleIssues.forEach { issue ->
                        if (ruleIssues.none { it.fingerprint == issue.fingerprint }) {
                            ruleIssues.add(issue)
                        }
                    }
                    newFileMap[ruleId] = ruleIssues
                }
                newMap[filePath] = newFileMap
            }
            newMap
        }
        affectedFiles.forEach { updateLineCache(it) }
        refreshStaticIssuesList()
    }

    fun updateStaticIssuesForFile(
        filePath: String,
        ruleId: String,
        issues: Collection<com.github.devvikassoni.leaklens.model.StaticAnalysisIssue>
    ) {
        _staticIssues.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            val fileMap = newMap[filePath]?.toMutableMap() ?: mutableMapOf()

            if (issues.isEmpty()) {
                fileMap.remove(ruleId)
            } else {
                // Deduplicate within the incoming batch itself
                val distinctIssues = issues.distinctBy { it.fingerprint }
                fileMap[ruleId] = distinctIssues
            }

            if (fileMap.isEmpty()) {
                newMap.remove(filePath)
            } else {
                newMap[filePath] = fileMap
            }
            newMap
        }
        updateLineCache(filePath)
        refreshStaticIssuesList()
    }

    fun clearStaticIssuesForFile(filePath: String) {
        _staticIssues.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            newMap.remove(filePath)
            newMap
        }
        updateLineCache(filePath)
        refreshStaticIssuesList()
    }

    fun clearAllStaticIssues() {
        _staticIssues.value = emptyMap()
        _staticIssuesList.value = emptyList()
        liveLeakyLines.clear()
    }

    private fun updateLineCache(filePath: String) {
        val staticIssues = _staticIssues.value[filePath]?.values?.flatten() ?: emptyList()
        val liveIssues = _liveIssues.value[filePath]?.values?.flatten() ?: emptyList()

        val lines = (staticIssues.map { it.line } +
                liveIssues.mapNotNull { it.signature.substringAfterLast('_').toIntOrNull() })
            .toSet()

        if (lines.isEmpty()) {
            liveLeakyLines.remove(filePath)
        } else {
            liveLeakyLines[filePath] = lines
        }
    }

    fun isLineLeaky(filePath: String, line: Int): Boolean {
        return liveLeakyLines[filePath]?.contains(line) == true
    }

    fun restartDaemonForFile(filePath: String) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
            }
        }
    }

    fun clearLiveIssuesForFile(filePath: String) {
        _liveIssues.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            newMap.remove(filePath)
            newMap
        }
        liveLeakyLines.remove(filePath)
        refreshLiveIssuesList()
    }

    fun clearAllLiveIssues() {
        _liveIssues.value = emptyMap()
        _liveIssuesList.value = emptyList()
        liveLeakyLines.clear()
    }

    fun setAnalyzing(analyzing: Boolean) {
        _isAnalyzing.value = analyzing
    }

    fun clearLeaks() {
        _leaks.value = emptyList()
        _liveIssues.value = emptyMap()
        _liveIssuesList.value = emptyList()
        liveLeakyLines.clear()
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
        _history.update { current -> (current + entry).takeLast(50) }
        logger.info("LeakLens: Added analysis to history (total: ${_history.value.size} entries)")

        // Persist to project-level storage
        persistHistory(entry)
    }

    fun clearHistory() {
        _history.value = emptyList()
        val settings = LeakLensSettingsState.getInstance(project)
        settings.historyEntries.clear()
        // Force state save if necessary, though IntelliJ usually handles this
    }

    private fun persistHistory(entry: AnalysisHistoryEntry) {
        val settings = LeakLensSettingsState.getInstance(project)
        // Only persist if the user explicitly enabled it in settings
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
