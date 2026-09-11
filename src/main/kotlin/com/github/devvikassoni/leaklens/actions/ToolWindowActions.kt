package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.LeakLensBundle
import com.github.devvikassoni.leaklens.compat.ProgressFacade
import com.github.devvikassoni.leaklens.inspections.AnonymousInnerClassLeakInspection
import com.github.devvikassoni.leaklens.inspections.ComposeContextLeakInspection
import com.github.devvikassoni.leaklens.inspections.ContextPassedToSingletonInspection
import com.github.devvikassoni.leaklens.inspections.DeprecatedLifecycleScopeInspection
import com.github.devvikassoni.leaklens.inspections.FlowLifecycleInspection
import com.github.devvikassoni.leaklens.inspections.GlobalScopeWithContextInspection
import com.github.devvikassoni.leaklens.inspections.HiltScopeMismatchInspection
import com.github.devvikassoni.leaklens.inspections.MissingRemoveCallbacksInspection
import com.github.devvikassoni.leaklens.inspections.StaticActivityReferenceInspection
import com.github.devvikassoni.leaklens.inspections.ViewModelContextLeakInspection
import com.github.devvikassoni.leaklens.inspections.ViewReferenceHeldInspection
import com.github.devvikassoni.leaklens.inspections.WorkerContextLeakInspection
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

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
                runInspections(project, listOf(psiFile))
            }
        })
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

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

                val ktType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                    .getFileTypeByExtension("kt")
                val javaType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance()
                    .getFileTypeByExtension("java")
                val kotlinFiles =
                    FileTypeIndex.getFiles(ktType, GlobalSearchScope.projectScope(project))
                val javaFiles =
                    FileTypeIndex.getFiles(javaType, GlobalSearchScope.projectScope(project))
                val allFiles = (kotlinFiles + javaFiles).mapNotNull {
                    PsiManager.getInstance(project).findFile(it)
                }

                runInspections(project, allFiles, indicator)
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun runInspections(
    project: com.intellij.openapi.project.Project,
    files: List<PsiFile>,
    indicator: ProgressIndicator? = null
) {
    val inspections = listOf(
        StaticActivityReferenceInspection(),
        AnonymousInnerClassLeakInspection(),
        ContextPassedToSingletonInspection(),
        MissingRemoveCallbacksInspection(),
        GlobalScopeWithContextInspection(),
        ViewReferenceHeldInspection(),
        ComposeContextLeakInspection(),
        FlowLifecycleInspection(),
        ViewModelContextLeakInspection(),
        DeprecatedLifecycleScopeInspection(),
        WorkerContextLeakInspection(),
        HiltScopeMismatchInspection()
    )

    val manager = InspectionManager.getInstance(project)
    val projectService = LeakLensProjectService.getInstance(project)

    files.forEachIndexed { index, psiFile ->
        if (indicator?.isCanceled == true) return@forEachIndexed
        indicator?.let {
            ProgressFacade.setText(
                it,
                LeakLensBundle.message("leaklens.progress.analyzing.file", psiFile.name)
            )
            ProgressFacade.setFraction(it, index.toDouble() / files.size.coerceAtLeast(1))
        }

        ApplicationManager.getApplication().runReadAction {
            val virtualFile = psiFile.virtualFile ?: return@runReadAction
            projectService.clearLiveIssuesForFile(virtualFile.path)
            projectService.clearStaticIssuesForFile(virtualFile.path)

            for (inspection in inspections) {
                val holder = ProblemsHolder(manager, psiFile, false)
                val session = createSession(psiFile) ?: continue
                val visitor = inspection.buildVisitor(holder, false, session)
                psiFile.accept(visitor)
                inspection.inspectionFinished(session, holder)
            }
        }
    }
}

private fun createSession(psiFile: PsiFile): LocalInspectionToolSession? {
    return try {
        // Try the 3-arg constructor (PsiFile, int, int) which is public in most versions but might be hidden in some AS builds
        val constructor = LocalInspectionToolSession::class.java.getDeclaredConstructor(
            PsiFile::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        constructor.newInstance(psiFile, 0, psiFile.textLength)
    } catch (e: Exception) {
        null
    }
}

class ClearAllAction : AnAction(
    LeakLensBundle.message("leaklens.action.clear.title"),
    LeakLensBundle.message("leaklens.action.clear.description"),
    AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LeakLensProjectService.getInstance(project).clearLeaks()
        LeakLensProjectService.getInstance(project).clearAllStaticIssues()
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class ClearLocalSnapshotsAction : AnAction(
    "Clear Local Snapshots",
    "Delete all local .hprof files generated by LeakLens",
    AllIcons.Actions.DeleteTag
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        com.github.devvikassoni.leaklens.services.AdbHeapDumpService.getInstance(project)
            .clearLocalSnapshots()
    }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class AnalyzeSelectedFilesAction : AnAction(
    "Analyze Memory Leaks",
    "Run LeakLens static analysis on selected files",
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
                    val filesToScan = mutableListOf<PsiFile>()
                    virtualFiles.forEach { vf -> collectFiles(project, vf, filesToScan) }
                    runInspections(project, filesToScan, indicator)
                }
            })
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun collectFiles(
    project: com.intellij.openapi.project.Project,
    vf: com.intellij.openapi.vfs.VirtualFile,
    result: MutableList<PsiFile>
) {
    if (vf.isDirectory) {
        vf.children.forEach { collectFiles(project, it, result) }
    } else if (vf.extension == "kt" || vf.extension == "java") {
        PsiManager.getInstance(project).findFile(vf)?.let { result.add(it) }
    }
}
