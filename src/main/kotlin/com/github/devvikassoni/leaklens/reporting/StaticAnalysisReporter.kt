package com.github.devvikassoni.leaklens.reporting

import com.github.devvikassoni.leaklens.model.StaticAnalysisIssue
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface StaticAnalysisReporter {
    fun report(issue: StaticAnalysisIssue)
    fun reportAll(issues: Collection<StaticAnalysisIssue>)
    fun updateFile(file: VirtualFile, ruleId: String, issues: Collection<StaticAnalysisIssue>)
    fun clearFile(file: VirtualFile)
    fun clearAll()

    companion object {
        fun getInstance(project: Project): StaticAnalysisReporter =
            project.getService(StaticAnalysisReporter::class.java)
    }
}

@Service(Service.Level.PROJECT)
class StaticAnalysisReporterImpl(private val project: Project) : StaticAnalysisReporter {

    override fun report(issue: StaticAnalysisIssue) {
        LeakLensProjectService.getInstance(project).addStaticIssue(issue)
    }

    override fun reportAll(issues: Collection<StaticAnalysisIssue>) {
        LeakLensProjectService.getInstance(project).addStaticIssues(issues)
    }

    override fun updateFile(
        file: VirtualFile,
        ruleId: String,
        issues: Collection<StaticAnalysisIssue>
    ) {
        LeakLensProjectService.getInstance(project)
            .updateStaticIssuesForFile(file.path, ruleId, issues)
    }

    override fun clearFile(file: VirtualFile) {
        LeakLensProjectService.getInstance(project).clearStaticIssuesForFile(file.path)
    }

    override fun clearAll() {
        LeakLensProjectService.getInstance(project).clearAllStaticIssues()
    }

    companion object {
        fun getInstance(project: Project): StaticAnalysisReporter =
            project.getService(StaticAnalysisReporterImpl::class.java)
    }
}
