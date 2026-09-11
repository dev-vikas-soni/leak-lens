package com.github.devvikassoni.leaklens.startup

import com.github.devvikassoni.leaklens.monitoring.DeviceMemoryMonitor
import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.github.devvikassoni.leaklens.services.LogcatHeapDumpListener
import com.github.devvikassoni.leaklens.settings.LeakLensPluginState
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
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

        // Auto-start listener if enabled in settings
        val settings = LeakLensSettingsState.getInstance(project)
        if (settings.autoDetectEnabled) {
            listener.startListening()
        }

        // Run auto-cleanup if enabled
        if (settings.autoCleanupOnStart) {
            com.github.devvikassoni.leaklens.services.AdbHeapDumpService.getInstance(project)
                .clearLocalSnapshots()
        }

        // Onboarding Check
        showOnboardingIfNeeded(project)
    }

    private fun showOnboardingIfNeeded(project: Project) {
        val pluginState = LeakLensPluginState.getInstance()
        val monitor = DeviceMemoryMonitor.getInstance(project)

        if (!pluginState.onboardingShown && !monitor.isActive()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification(
                    "LeakLens Memory Monitor",
                    "Monitor your app's memory usage and detect potential memory leaks while you develop.",
                    NotificationType.INFORMATION
                )
                .addAction(object : NotificationAction("Start Memory Monitor") {
                    override fun actionPerformed(
                        e: AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        notification.expire()
                        monitor.startMonitoringFlow()
                    }
                })
                .notify(project)

            pluginState.onboardingShown = true
        }
    }
}
