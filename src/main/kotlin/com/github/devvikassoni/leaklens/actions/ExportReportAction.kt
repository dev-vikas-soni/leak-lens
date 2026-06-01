package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.reporting.ReportExporter
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import java.io.File

/**
 * Action to export leak analysis results as HTML/JSON/SARIF report.
 */
class ExportReportAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val leaks = LeakLensProjectService.getInstance(project).leaks.value

        if (leaks.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification("LeakLens", "No leaks to export. Run an analysis first.", NotificationType.INFORMATION)
                .notify(project)
            return
        }

        val descriptor = FileSaverDescriptor("Export LeakLens Report", "Choose location and format", "html", "json", "sarif")
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = saveDialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "leaklens-report") ?: return

        val file = wrapper.file
        when (file.extension?.lowercase()) {
            "html" -> ReportExporter.exportHtml(leaks, file)
            "json" -> ReportExporter.exportJson(leaks, file)
            "sarif" -> ReportExporter.exportSarif(leaks, file)
            else -> ReportExporter.exportHtml(leaks, File(file.path + ".html"))
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification("LeakLens", "Report exported to ${file.name}", NotificationType.INFORMATION)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

