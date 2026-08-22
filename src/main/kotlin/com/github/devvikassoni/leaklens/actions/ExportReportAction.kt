package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.reporting.ReportExporter
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.Desktop
import java.io.File

/**
 * Action to export leak analysis results as HTML/JSON/SARIF report.
 * After export, shows a balloon notification with a "Show in Files" CTA
 * that opens the containing folder in the system file manager.
 */
class ExportReportAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val leaks = LeakLensProjectService.getInstance(project).leaks.value

        if (leaks.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification(
                    "LeakLens",
                    "No leaks to export. Run an analysis first.",
                    NotificationType.INFORMATION
                )
                .notify(project)
            return
        }

        val descriptor =
            FileSaverDescriptor(
                "Export LeakLens Report",
                "Choose location and format",
                "html", "json", "sarif"
            )
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = saveDialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "leaklens-report") ?: return

        val file = wrapper.file
        when (file.extension.lowercase()) {
            "html" -> ReportExporter.exportHtml(leaks, file)
            "json" -> ReportExporter.exportJson(leaks, file)
            "sarif" -> ReportExporter.exportSarif(leaks, file)
            else -> ReportExporter.exportHtml(leaks, File(file.path + ".html"))
        }

        // Refresh VFS so the file appears in IDE immediately
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification(
                "LeakLens — Report Exported",
                "Saved as <b>${file.name}</b>",
                NotificationType.INFORMATION
            )

        // "Show in Files" CTA — opens the folder in the system file manager
        notification.addAction(
            NotificationAction.createSimple("Show in Files") {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                        .isSupported(Desktop.Action.BROWSE_FILE_DIR)
                ) {
                    Desktop.getDesktop().browseFileDirectory(file)
                } else if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                        .isSupported(Desktop.Action.OPEN)
                ) {
                    Desktop.getDesktop().open(file.parentFile)
                }
            }
        )

        // "Open File" CTA — open the report directly
        notification.addAction(
            NotificationAction.createSimple("Open Report") {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                        .isSupported(Desktop.Action.OPEN)
                ) {
                    Desktop.getDesktop().open(file)
                }
            }
        )

        notification.notify(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
