package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory

/**
 * Action to import a local .hprof file for analysis.
 * Useful for analyzing heap dumps that were previously saved or shared.
 */
class ImportHprofAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("hprof")
            .withTitle("Import Heap Dump")
            .withDescription("Select an .hprof file to analyze for memory leaks")

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            val file = java.io.File(virtualFile.path)
            if (file.exists() && file.extension == "hprof") {
                LeakAnalysisCoordinator.getInstance(project).analyzeLocalHprof(file)
            }
        }
    }

    override fun getActionUpdateThread(): com.intellij.openapi.actionSystem.ActionUpdateThread = 
        com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

