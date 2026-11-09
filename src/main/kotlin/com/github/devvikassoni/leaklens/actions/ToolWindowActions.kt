package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.LeakLensBundle
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.github.devvikassoni.leaklens.services.StaticAnalysisRunner
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to open the Marketplace review page.
 */
class RatePluginAction : AnAction(
    LeakLensBundle.message("leaklens.action.rate.title"),
    LeakLensBundle.message("leaklens.action.rate.description"),
    AllIcons.Actions.IntentionBulb
) {
    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse(
            "https://plugins.jetbrains.com/plugin/32079-leaklens--memory-leak-detector--ai-assistant/edit/reviews"
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Action to run static leak inspections on the currently open file.
 */
class AnalyzeCurrentFileAction : AnAction(
    LeakLensBundle.message("leaklens.action.analyze.file.title"),
    LeakLensBundle.message("leaklens.action.analyze.file.description"),
    AllIcons.Actions.Execute
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile ?: return
        runStaticAnalysis(
            project,
            LeakLensBundle.message("leaklens.progress.analyzing.file", virtualFile.name)
        ) { listOf(virtualFile) }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = project?.let { FileEditorManager.getInstance(it).selectedTextEditor?.virtualFile }
        e.presentation.isEnabled = file != null && file.extension in setOf("kt", "java")
    }
}

/**
 * Action to run static leak inspections on all source files in the project.
 */
class AnalyzeProjectAction : AnAction(
    LeakLensBundle.message("leaklens.action.analyze.project.title"),
    LeakLensBundle.message("leaklens.action.analyze.project.description"),
    AllIcons.Actions.Resume
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runStaticAnalysis(project, LeakLensBundle.message("leaklens.progress.analyzing.project"), clearAll = true) {
            ReadAction.compute<List<VirtualFile>, RuntimeException> {
                StaticAnalysisRunner.allProjectSources(project)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Shared background runner for the static analysis actions: collects files, runs every enabled
 * LeakLens inspection, publishes results and reports a summary notification.
 */
internal fun runStaticAnalysis(
    project: Project,
    title: String,
    clearAll: Boolean = false,
    filesProvider: () -> List<VirtualFile>,
) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
        override fun run(indicator: ProgressIndicator) {
            ApplicationManager.getApplication().invokeLater({
                ToolWindowManager.getInstance(project).getToolWindow("LeakLens")?.show()
            }, project.disposed)
            indicator.isIndeterminate = false
            val files = filesProvider()
            if (clearAll) LeakLensProjectService.getInstance(project).clearAllLiveIssues()
            val result = StaticAnalysisRunner(project).analyze(files, indicator)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification(
                    "LeakLens static analysis",
                    "Scanned ${result.filesScanned} file(s), found ${result.issuesFound} potential leak(s).",
                    if (result.issuesFound == 0) NotificationType.INFORMATION else NotificationType.WARNING
                )
                .notify(project)
        }
    })
}

/**
 * Action to clear all detected leaks from the dashboard.
 */
class ClearAllAction : AnAction(
    LeakLensBundle.message("leaklens.action.clear.title"),
    LeakLensBundle.message("leaklens.action.clear.description"),
    AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LeakLensProjectService.getInstance(project).clearLeaks()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Action to clear all local .hprof files generated by LeakLens.
 */
class ClearLocalSnapshotsAction : AnAction(
    "Clear Local Snapshots",
    "Delete all local .hprof files generated by LeakLens to free up space",
    AllIcons.Actions.DeleteTag
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        com.github.devvikassoni.leaklens.services.AdbHeapDumpService.getInstance(project)
            .clearLocalSnapshots()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Action to run static leak inspections on files selected in the Project View.
 */
class AnalyzeSelectedFilesAction : AnAction(
    "Analyze Memory Leaks",
    "Run LeakLens static analysis on selected files or directories",
    AllIcons.Actions.Find
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFiles =
            e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        runStaticAnalysis(project, "LeakLens: Analyzing selection") {
            ReadAction.compute<List<VirtualFile>, RuntimeException> {
                StaticAnalysisRunner.expand(project, virtualFiles)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
