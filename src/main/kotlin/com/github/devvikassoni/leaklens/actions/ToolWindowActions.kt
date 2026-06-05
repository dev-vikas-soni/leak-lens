package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.github.devvikassoni.leaklens.inspections.*

/**
 * Action to run static leak inspections on the currently open file.
 * Matches SonarLint's "Analyze current file" run icon.
 */
class AnalyzeCurrentFileAction : AnAction("Analyze Current File", "Run LeakLens static analysis on the active editor file", AllIcons.Actions.Execute) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val virtualFile = editor.virtualFile ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Analyzing ${virtualFile.name}", false) {
            override fun run(indicator: ProgressIndicator) {
                // Focus the tool window
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
                val holder = ProblemsHolder(manager, psiFile, false)

                ApplicationManager.getApplication().runReadAction {
                    for (inspection in inspections) {
                        val visitor = inspection.buildVisitor(holder, true)
                        psiFile.accept(visitor)
                    }
                }
                
                // Note: The inspections update LeakLensProjectService automatically via afterVisitFile 
                // in their visitors when isOnTheFly is true.
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
class AnalyzeProjectAction : AnAction("Analyze Project", "Run LeakLens static analysis on the entire project", AllIcons.Actions.Resume) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "LeakLens: Analyzing Project", true) {
            override fun run(indicator: ProgressIndicator) {
                // Focus the tool window
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
                
                // Collect files to scan
                val kotlinFiles = com.intellij.psi.search.FileTypeIndex.getFiles(com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("kt"), com.intellij.psi.search.GlobalSearchScope.projectScope(project))
                val javaFiles = com.intellij.psi.search.FileTypeIndex.getFiles(com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("java"), com.intellij.psi.search.GlobalSearchScope.projectScope(project))
                val allFiles = kotlinFiles + javaFiles
                
                allFiles.forEachIndexed { index, virtualFile ->
                    if (indicator.isCanceled) return@forEachIndexed
                    indicator.text = "Analyzing ${virtualFile.name}..."
                    indicator.fraction = index.toDouble() / allFiles.size.coerceAtLeast(1)

                    ApplicationManager.getApplication().runReadAction {
                        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@runReadAction
                        val holder = ProblemsHolder(manager, psiFile, false)
                        for (inspection in inspections) {
                            psiFile.accept(inspection.buildVisitor(holder, true))
                        }
                    }
                }
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Action to clear all detected leaks from the dashboard.
 */
class ClearAllAction : AnAction("Clear All", "Clear all detected leaks and issues", AllIcons.Actions.GC) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LeakLensProjectService.getInstance(project).clearLeaks()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
