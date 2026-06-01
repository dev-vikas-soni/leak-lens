package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.monitoring.DeviceMemoryMonitor
import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

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

        val adbService = AdbHeapDumpService.getInstance(project)
        val devices = adbService.listDevices()
        if (devices.isEmpty()) {
            notify(project, "No connected devices found.", NotificationType.WARNING)
            return
        }

        val device = devices.first()
        val processes = adbService.listDebuggableProcesses(device)
        if (processes.isEmpty()) {
            notify(project, "No debuggable processes found.", NotificationType.WARNING)
            return
        }

        val packageName = if (processes.size == 1) processes.first() else {
            Messages.showEditableChooseDialog("Select process:", "LeakLens Monitor", Messages.getQuestionIcon(), processes.toTypedArray(), processes.first(), null) ?: return
        }

        monitor.startMonitoring(device, packageName)
        notify(project, "Monitoring $packageName memory...", NotificationType.INFORMATION)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val monitor = DeviceMemoryMonitor.getInstance(project)
        e.presentation.text = if (monitor.isActive()) "Stop Memory Monitor" else "Start Memory Monitor"
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", msg, type)
            .notify(project)
    }
}

