package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.ai.AiAnalysisService
import com.github.devvikassoni.leaklens.baseline.LeakBaselineManager
import com.github.devvikassoni.leaklens.compat.CompatibilityLogger
import com.github.devvikassoni.leaklens.compat.ProgressFacade
import com.github.devvikassoni.leaklens.deobfuscation.DeobfuscationService
import com.github.devvikassoni.leaklens.fix.FixSuggestionEngine
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Orchestrates the full memory leak analysis lifecycle.
 *
 * This coordinator manages the sequence of capturing heap dumps from devices,
 * performing the analysis using the Shark engine, and updating the project
 * state with the results. It handles user notifications and ensures long-running
 * tasks are executed on background threads with appropriate progress reporting.
 */
@Service(Service.Level.PROJECT)
class LeakAnalysisCoordinator(private val project: Project) {

    /**
     * Context of the last triggered heap dump to allow quick verification.
     */
    data class DumpContext(val deviceSerial: String?, val packageName: String)

    var lastDumpContext: DumpContext? = null
        private set

    /**
     * Analyze a heap dump from a remote device path.
     */
    fun analyzeFromDevice(deviceSerial: String?, remotePath: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Analyzing Heap Dump", true) {
            override fun run(indicator: ProgressIndicator) {
                val projectService = LeakLensProjectService.getInstance(project)
                projectService.setAnalyzing(true)

                try {
                    ProgressFacade.setText(indicator, "Pulling heap dump from device...")
                    ProgressFacade.setFraction(indicator, 0.1)

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
        lastDumpContext = DumpContext(deviceSerial, packageName)
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "LeakLens: Capturing & Analyzing Heap Dump",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val projectService = LeakLensProjectService.getInstance(project)
                projectService.setAnalyzing(true)

                try {
                    ProgressFacade.setText(indicator, "Triggering heap dump on device...")
                    ProgressFacade.setFraction(indicator, 0.05)

                    val adbService = AdbHeapDumpService.getInstance(project)
                    val remotePath = adbService.triggerHeapDump(deviceSerial, packageName)

                    if (remotePath == null) {
                        notify("Failed to trigger heap dump on device", NotificationType.ERROR)
                        return
                    }

                    // Wait for the dump to be flushed to disk on the device
                    ProgressFacade.setText(indicator, "Waiting for heap dump to complete...")
                    for (i in 1..30) {
                        ProgressFacade.checkCanceled(indicator)
                        Thread.sleep(100) // Total 3 seconds safe wait
                    }

                    ProgressFacade.setText(indicator, "Pulling heap dump from device...")
                    ProgressFacade.setFraction(indicator, 0.2)

                    val localFile = adbService.pullHeapDump(deviceSerial, remotePath)

                    if (localFile == null) {
                        notify("Failed to pull heap dump from device", NotificationType.ERROR)
                        return
                    }

                    // Clean up remote file
                    adbService.deleteRemoteFile(deviceSerial, remotePath)

                    analyzeLocalFile(localFile, indicator)

                    // Cleanup local hprof after analysis to avoid disk bloat
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                } finally {
                    projectService.setAnalyzing(false)
                }
            }
        })
    }

    private fun analyzeLocalFile(hprofFile: File, indicator: ProgressIndicator) {
        val fileSize = hprofFile.length()
        val fileSizeMb = fileSize / (1024 * 1024)
        val maxMemory = Runtime.getRuntime().maxMemory()

        if (fileSize > maxMemory * 0.8) {
            notify(
                "Heap dump (${fileSizeMb}MB) is very large relative to IDE memory. Analysis might crash or be extremely slow.",
                NotificationType.WARNING
            )
        }

        if (fileSizeMb > 500) {
            ProgressFacade.setText(
                indicator,
                "Large heap dump detected (${fileSizeMb}MB). This may take a while..."
            )
        } else {
            ProgressFacade.setText(indicator, "Running Shark heap analysis...")
        }
        ProgressFacade.setFraction(indicator, 0.2)

        val sharkService = SharkAnalysisService.getInstance(project)
        val rawLeaks = sharkService.analyzeHprof(hprofFile, indicator)

        // Deobfuscation
        ProgressFacade.setText(indicator, "Deobfuscating traces...")
        ProgressFacade.setFraction(indicator, 0.4)
        val deobService = DeobfuscationService.getInstance(project)
        if (!deobService.hasMappings()) {
            val settings = LeakLensSettingsState.getInstance(project)
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
        } else {
            rawLeaks
        }

        // Fix suggestions from static rule engine
        ProgressFacade.setText(indicator, "Generating fix suggestions...")
        ProgressFacade.setFraction(indicator, 0.55)

        val fixEngine = FixSuggestionEngine()
        leaks = fixEngine.enrichWithFixes(leaks)

        // AI-assisted analysis for leaks not matched by static rules
        ProgressFacade.setText(indicator, "AI analysis (if enabled)...")
        ProgressFacade.setFraction(indicator, 0.7)

        val aiService = AiAnalysisService.getInstance(project)
        if (aiService.isEnabled()) {
            leaks = aiService.enrichWithAiSuggestions(leaks)
        }

        // Apply baseline filtering
        ProgressFacade.setText(indicator, "Applying baseline...")
        ProgressFacade.setFraction(indicator, 0.85)

        val settings = LeakLensSettingsState.getInstance(project)
        val baselineManager = LeakBaselineManager.getInstance(project)
        val allLeaks = leaks
        if (settings.useBaseline) {
            leaks = baselineManager.filterNewLeaks(leaks)
        }

        ProgressFacade.setText(indicator, "Processing results...")
        ProgressFacade.setFraction(indicator, 0.9)

        val projectService = LeakLensProjectService.getInstance(project)
        projectService.updateLeaks(leaks)

        // Store in history (both in-memory and persistent)
        projectService.addToHistory(allLeaks, hprofFile.name)

        ProgressFacade.setFraction(indicator, 1.0)

        val suppressed = allLeaks.size - leaks.size
        val message = if (leaks.isEmpty() && suppressed == 0) {
            "No memory leaks detected! ✅"
        } else if (leaks.isEmpty()) {
            "All $suppressed leak(s) are in baseline. No new leaks! ✅"
        } else {
            val critical = leaks.count { it.severity == LeakSeverity.CRITICAL }
            val warning = leaks.count { it.severity == LeakSeverity.WARNING }
            val library = leaks.count { it.severity == LeakSeverity.LIBRARY_LEAK }
            val baselineNote = if (suppressed > 0) " ($suppressed suppressed by baseline)" else ""
            "Found ${leaks.size} leak(s): 🔴 $critical critical, 🟡 $warning warning, 🟢 $library library$baselineNote"
        }

        notify(message, if (leaks.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING)

        CompatibilityLogger.info("LeakLens: Analysis complete - ${leaks.size} leaks found from ${hprofFile.name}")
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
