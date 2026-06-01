package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory

/**
 * Manual "Dump Heap Now" action.
 * Discovers devices, lets user select a process, triggers heap dump, and analyzes.
 */
class DumpHeapAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val adbService = AdbHeapDumpService.getInstance(project)

        // Step 1: Get connected devices
        val devices = adbService.listDevices()
        if (devices.isEmpty()) {
            notify(project, "No connected devices found. Please connect a device or start an emulator.", NotificationType.WARNING)
            return
        }

        // Step 2: Select device (auto-select if only one)
        val deviceSerial = if (devices.size == 1) {
            devices.first()
        } else {
            val selected = Messages.showEditableChooseDialog(
                "Select a device:",
                "LeakLens - Device Selection",
                Messages.getQuestionIcon(),
                devices.toTypedArray(),
                devices.first(),
                null
            )
            selected ?: return
        }

        // Step 3: Get debuggable processes
        val processes = adbService.listDebuggableProcesses(deviceSerial)
        if (processes.isEmpty()) {
            notify(project, "No debuggable processes found on $deviceSerial. Make sure your app is running in debug mode.", NotificationType.WARNING)
            return
        }

        // Step 4: Select process
        val packageName = if (processes.size == 1) {
            processes.first()
        } else {
            val selected = Messages.showEditableChooseDialog(
                "Select a process to dump:",
                "LeakLens - Process Selection",
                Messages.getQuestionIcon(),
                processes.toTypedArray(),
                processes.first(),
                null
            )
            selected ?: return
        }

        // Step 5: Trigger dump and analyze
        LeakAnalysisCoordinator.getInstance(project).triggerAndAnalyze(deviceSerial, packageName)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", content, type)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
