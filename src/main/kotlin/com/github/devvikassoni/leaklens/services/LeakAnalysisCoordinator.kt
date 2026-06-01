package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Coordinates the full heap dump analysis pipeline:
 * 1. Pull .hprof from device (or use local file)
 * 2. Run Shark analysis
 * 3. Update project service with results
 * 4. Notify user
 */
@Service(Service.Level.PROJECT)
class LeakAnalysisCoordinator(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Analyze a heap dump from a remote device path.
     */
    fun analyzeFromDevice(deviceSerial: String?, remotePath: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Analyzing Heap Dump", true) {
            override fun run(indicator: ProgressIndicator) {
                val projectService = LeakLensProjectService.getInstance(project)
                projectService.setAnalyzing(true)

                try {
                    indicator.text = "Pulling heap dump from device..."
                    indicator.fraction = 0.1

                    val adbService = AdbHeapDumpService.getInstance(project)
                    val localFile = adbService.pullHeapDump(deviceSerial, remotePath)

                    if (localFile == null) {
                        notify("Failed to pull heap dump from device", NotificationType.ERROR)
                        return
                    }

                    analyzeLocalFile(localFile, indicator)
                } finally {
                    projectService.setAnalyzing(false)
                }
            }
        })
    }

    /**
     * Analyze a local .hprof file.
     */
    fun analyzeLocalHprof(hprofFile: File) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Analyzing Heap Dump", true) {
            override fun run(indicator: ProgressIndicator) {
                val projectService = LeakLensProjectService.getInstance(project)
                projectService.setAnalyzing(true)

                try {
                    analyzeLocalFile(hprofFile, indicator)
                } finally {
                    projectService.setAnalyzing(false)
                }
            }
        })
    }

    /**
     * Trigger a heap dump on the device and analyze it.
     */
    fun triggerAndAnalyze(deviceSerial: String?, packageName: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Capturing & Analyzing Heap Dump", true) {
            override fun run(indicator: ProgressIndicator) {
                val projectService = LeakLensProjectService.getInstance(project)
                projectService.setAnalyzing(true)

                try {
                    indicator.text = "Triggering heap dump on device..."
                    indicator.fraction = 0.05

                    val adbService = AdbHeapDumpService.getInstance(project)
                    val remotePath = adbService.triggerHeapDump(deviceSerial, packageName)

                    if (remotePath == null) {
                        notify("Failed to trigger heap dump on device", NotificationType.ERROR)
                        return
                    }

                    indicator.text = "Pulling heap dump from device..."
                    indicator.fraction = 0.2

                    val localFile = adbService.pullHeapDump(deviceSerial, remotePath)

                    if (localFile == null) {
                        notify("Failed to pull heap dump from device", NotificationType.ERROR)
                        return
                    }

                    // Clean up remote file
                    adbService.deleteRemoteFile(deviceSerial, remotePath)

                    analyzeLocalFile(localFile, indicator)
                } finally {
                    projectService.setAnalyzing(false)
                }
            }
        })
    }

    private fun analyzeLocalFile(hprofFile: File, indicator: ProgressIndicator) {
        indicator.text = "Running Shark heap analysis..."
        indicator.fraction = 0.2

        val sharkService = SharkAnalysisService.getInstance(project)
        val rawLeaks = sharkService.analyzeHprof(hprofFile)

        // Deobfuscation (Phase 7 - if mapping loaded)
        indicator.text = "Deobfuscating traces..."
        indicator.fraction = 0.4
        val deobService = com.github.devvikassoni.leaklens.deobfuscation.DeobfuscationService.getInstance(project)
        if (!deobService.hasMappings()) {
            val settings = com.github.devvikassoni.leaklens.settings.LeakLensSettingsState.getInstance(project)
            if (settings.autoDetectMapping) {
                deobService.autoDetectMappingFile()?.let { deobService.loadMappingFile(it) }
            }
        }
        var leaks = if (deobService.hasMappings()) {
            rawLeaks.map { leak ->
                leak.copy(
                    retainedObjectClassName = deobService.deobfuscateClassName(leak.retainedObjectClassName),
                    leakTrace = deobService.deobfuscateTrace(leak.leakTrace),
                    referenceChain = leak.referenceChain.map { ref ->
                        ref.copy(owningClassName = deobService.deobfuscateClassName(ref.owningClassName))
                    }
                )
            }
        } else rawLeaks

        // Fix suggestions from static rule engine
        indicator.text = "Generating fix suggestions..."
        indicator.fraction = 0.55

        val fixEngine = com.github.devvikassoni.leaklens.fix.FixSuggestionEngine()
        leaks = fixEngine.enrichWithFixes(leaks)

        // AI-assisted analysis for leaks not matched by static rules (opt-in)
        indicator.text = "AI analysis (if enabled)..."
        indicator.fraction = 0.7

        val aiService = com.github.devvikassoni.leaklens.ai.AiAnalysisService.getInstance(project)
        if (aiService.isEnabled()) {
            leaks = aiService.enrichWithAiSuggestions(leaks)
        }

        // Apply baseline filtering (Phase 7)
        indicator.text = "Applying baseline..."
        indicator.fraction = 0.85

        val settings = com.github.devvikassoni.leaklens.settings.LeakLensSettingsState.getInstance(project)
        val baselineManager = com.github.devvikassoni.leaklens.baseline.LeakBaselineManager.getInstance(project)
        val allLeaks = leaks
        if (settings.useBaseline) {
            leaks = baselineManager.filterNewLeaks(leaks)
        }

        indicator.text = "Processing results..."
        indicator.fraction = 0.9

        val projectService = LeakLensProjectService.getInstance(project)
        projectService.updateLeaks(leaks)

        // Store in history (both in-memory and persistent)
        projectService.addToHistory(allLeaks, hprofFile.name)

        indicator.fraction = 1.0

        val suppressed = allLeaks.size - leaks.size
        val message = if (leaks.isEmpty() && suppressed == 0) {
            "No memory leaks detected! ✅"
        } else if (leaks.isEmpty()) {
            "All ${suppressed} leak(s) are in baseline. No new leaks! ✅"
        } else {
            val critical = leaks.count { it.severity == com.github.devvikassoni.leaklens.model.LeakSeverity.CRITICAL }
            val warning = leaks.count { it.severity == com.github.devvikassoni.leaklens.model.LeakSeverity.WARNING }
            val library = leaks.count { it.severity == com.github.devvikassoni.leaklens.model.LeakSeverity.LIBRARY_LEAK }
            val baselineNote = if (suppressed > 0) " ($suppressed suppressed by baseline)" else ""
            "Found ${leaks.size} leak(s): 🔴 $critical critical, 🟡 $warning warning, 🟢 $library library$baselineNote"
        }

        notify(message, if (leaks.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING)

        logger.info("LeakLens: Analysis complete - ${leaks.size} leaks found from ${hprofFile.name}")
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", content, type)
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): LeakAnalysisCoordinator =
            project.getService(LeakAnalysisCoordinator::class.java)
    }
}

