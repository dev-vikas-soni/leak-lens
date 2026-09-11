package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.monitoring.DeviceMemoryMonitor
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Action to start/stop real-time memory monitoring.
 */
class MonitorMemoryAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val monitor = DeviceMemoryMonitor.getInstance(project)

        if (monitor.isActive()) {
            monitor.stopMonitoring()
            notify(project, "Memory monitoring stopped.", NotificationType.INFORMATION)
            return
        }

        monitor.startMonitoringFlow()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val monitor = DeviceMemoryMonitor.getInstance(project)
        val active = monitor.isActive()
        e.presentation.text = if (active) "Stop Memory Monitor" else "Start Memory Monitor"
        e.presentation.icon = if (active) AllIcons.Actions.Suspend else AllIcons.Actions.Profile
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", msg, type)
            .notify(project)
    }
}
