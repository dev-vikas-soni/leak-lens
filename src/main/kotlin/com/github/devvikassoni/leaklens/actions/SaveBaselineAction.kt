package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.baseline.LeakBaselineManager
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to save current leaks as baseline (suppress in future analyses).
 */
class SaveBaselineAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val leaks = LeakLensProjectService.getInstance(project).leaks.value

        if (leaks.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification("LeakLens", "No leaks to baseline.", NotificationType.INFORMATION)
                .notify(project)
            return
        }

        LeakBaselineManager.getInstance(project).saveBaseline(leaks)

        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", "Saved ${leaks.size} leak(s) to leak-baseline.json. These will be suppressed in future analyses.", NotificationType.INFORMATION)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

