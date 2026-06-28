package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.LeakLensBundle
import com.github.devvikassoni.leaklens.inspections.AnonymousInnerClassLeakInspection
import com.github.devvikassoni.leaklens.inspections.ContextPassedToSingletonInspection
import com.github.devvikassoni.leaklens.inspections.GlobalScopeWithContextInspection
import com.github.devvikassoni.leaklens.inspections.MissingRemoveCallbacksInspection
import com.github.devvikassoni.leaklens.inspections.StaticActivityReferenceInspection
import com.github.devvikassoni.leaklens.inspections.ViewReferenceHeldInspection
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Action to open the Marketplace review page.
 */
class RatePluginAction : AnAction(
    LeakLensBundle.message("leaklens.action.rate.title"),
    LeakLensBundle.message("leaklens.action.rate.description"),
    AllIcons.Actions.IntentionBulb
) {
    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse("https://plugins.jetbrains.com/plugin/32079-leaklens--memory-leak-detector--ai-assistant/edit/reviews")
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
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val virtualFile = editor.virtualFile ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            LeakLensBundle.message("leaklens.progress.analyzing.file", virtualFile.name),
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                ApplicationManager.getApplication().invokeLater {
                    ToolWindowManager.getInstance(project).getToolWindow("LeakLens")?.show()
                }

                val inspections = listOf(
                    StaticActivityReferenceInspection(),
                    AnonymousInnerClassLeakInspection(),
                    ContextPassedToSingletonInspection(),
                    MissingRemoveCallbacksInspection(),
                    GlobalScopeWithContextInspection(),
                    ViewReferenceHeldInspection()
                )

                val manager = InspectionManager.getInstance(project)
                val projectService = LeakLensProjectService.getInstance(project)

                // Use non-blocking read action for better IDE responsiveness
                ReadAction.nonBlocking<Unit> {
                    projectService.clearLiveIssuesForFile(virtualFile.path)

                    for (inspection in inspections) {
                        val holder = ProblemsHolder(manager, psiFile, false)
                        val visitor = inspection.buildVisitor(holder, false)
                        psiFile.accept(visitor)
                    }
                }.executeSynchronously()
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = if (project != null) FileEditorManager.getInstance(project).selectedTextEditor else null
        e.presentation.isEnabled = editor != null
    }
}

/**
 * Action to run static leak inspections on all files in the project.
 */
class AnalyzeProjectAction : AnAction(
    LeakLensBundle.message("leaklens.action.analyze.project.title"),
    LeakLensBundle.message("leaklens.action.analyze.project.description"),
    AllIcons.Actions.Resume
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            LeakLensBundle.message("leaklens.progress.analyzing.project"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                ApplicationManager.getApplication().invokeLater {
                    ToolWindowManager.getInstance(project).getToolWindow("LeakLens")?.show()
                }

                val inspections = listOf(
                    StaticActivityReferenceInspection(),
                    AnonymousInnerClassLeakInspection(),
                    ContextPassedToSingletonInspection(),
                    MissingRemoveCallbacksInspection(),
                    GlobalScopeWithContextInspection(),
                    ViewReferenceHeldInspection()
                )

                val manager = InspectionManager.getInstance(project)
                val projectService = LeakLensProjectService.getInstance(project)

                val ktType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                    .getFileTypeByExtension("kt")
                val javaType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                    .getFileTypeByExtension("java")

                val kotlinFiles =
                    FileTypeIndex.getFiles(ktType, GlobalSearchScope.projectScope(project))
                val javaFiles =
                    FileTypeIndex.getFiles(javaType, GlobalSearchScope.projectScope(project))
                val allFiles = kotlinFiles + javaFiles

                projectService.clearAllLiveIssues()
                
                allFiles.forEachIndexed { index, virtualFile ->
                    if (indicator.isCanceled) return@forEachIndexed
                    indicator.text =
                        LeakLensBundle.message("leaklens.progress.analyzing.file", virtualFile.name)
                    indicator.fraction = index.toDouble() / allFiles.size.coerceAtLeast(1)

                    // Execute each file analysis in a non-blocking way to keep EDT free
                    ReadAction.nonBlocking<Unit> {
                        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                            ?: return@nonBlocking
                        
                        for (inspection in inspections) {
                            val holder = ProblemsHolder(manager, psiFile, false)
                            psiFile.accept(inspection.buildVisitor(holder, false))
                        }
                    }.executeSynchronously()
                }
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
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
            e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "LeakLens: Analyzing Selection", true) {
                override fun run(indicator: ProgressIndicator) {
                    ApplicationManager.getApplication().invokeLater {
                        ToolWindowManager.getInstance(project).getToolWindow("LeakLens")?.show()
                    }

                    val inspections = listOf(
                        StaticActivityReferenceInspection(),
                        AnonymousInnerClassLeakInspection(),
                        ContextPassedToSingletonInspection(),
                        MissingRemoveCallbacksInspection(),
                        GlobalScopeWithContextInspection(),
                        ViewReferenceHeldInspection()
                    )

                    val manager = InspectionManager.getInstance(project)
                    val projectService = LeakLensProjectService.getInstance(project)

                    val filesToScan = mutableSetOf<com.intellij.openapi.vfs.VirtualFile>()
                    virtualFiles.forEach { vf -> collectFiles(vf, filesToScan) }

                    filesToScan.forEachIndexed { index, virtualFile ->
                        if (indicator.isCanceled) return@forEachIndexed
                        indicator.text = LeakLensBundle.message(
                            "leaklens.progress.analyzing.file",
                            virtualFile.name
                        )
                        indicator.fraction = index.toDouble() / filesToScan.size.coerceAtLeast(1)

                        ReadAction.nonBlocking<Unit> {
                            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                                ?: return@nonBlocking
                            projectService.clearLiveIssuesForFile(virtualFile.path)

                            for (inspection in inspections) {
                                val holder = ProblemsHolder(manager, psiFile, false)
                                psiFile.accept(inspection.buildVisitor(holder, false))
                            }
                        }.executeSynchronously()
                    }
                }

                private fun collectFiles(
                    vf: com.intellij.openapi.vfs.VirtualFile,
                    result: MutableSet<com.intellij.openapi.vfs.VirtualFile>
                ) {
                    if (vf.isDirectory) {
                        vf.children.forEach { collectFiles(it, result) }
                    } else if (vf.extension == "kt" || vf.extension == "java") {
                        result.add(vf)
                    }
                }
            })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
