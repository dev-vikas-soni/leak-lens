package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.services.LogcatHeapDumpListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Toggles the logcat listener that auto-detects LeakCanary heap dumps.
 */
class ToggleAutoDetectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val listener = LogcatHeapDumpListener.getInstance(project)

        if (listener.isActive()) {
            listener.stopListening()
            notify(project, "Auto-detect stopped", NotificationType.INFORMATION)
        } else {
            listener.startListening()
            notify(project, "Auto-detect started. Listening for LeakCanary heap dumps...", NotificationType.INFORMATION)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val listener = LogcatHeapDumpListener.getInstance(project)
        e.presentation.text = if (listener.isActive()) "Stop Auto-Detect" else "Start Auto-Detect"
        e.presentation.isEnabledAndVisible = true
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", content, type)
            .notify(project)
    }
}
