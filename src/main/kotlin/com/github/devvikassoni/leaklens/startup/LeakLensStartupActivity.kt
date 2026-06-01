package com.github.devvikassoni.leaklens.startup

import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.github.devvikassoni.leaklens.services.LogcatHeapDumpListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LeakLensStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("LeakLens: Plugin initialized for project '${project.name}'")

        // Wire up auto-detection: when logcat listener detects a heap dump, analyze it
        val listener = LogcatHeapDumpListener.getInstance(project)
        val coordinator = LeakAnalysisCoordinator.getInstance(project)

        listener.onHeapDumpDetected = { deviceSerial, hprofPath ->
            thisLogger().info("LeakLens: Auto-detected heap dump, starting analysis...")
            coordinator.analyzeFromDevice(deviceSerial, hprofPath)
        }
    }
}
