package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.inspections.LeakLensInspectionBase
import com.github.devvikassoni.leaklens.inspections.LeakLensInspectionRegistry
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager

/**
 * Runs all enabled LeakLens inspections over a set of files outside the editor highlighting pass
 * and publishes the findings to [LeakLensProjectService].
 *
 * Honours the current inspection profile (disabled inspections and scopes), `@Suppress`
 * annotations, dumb mode and cancellation.
 */
class StaticAnalysisRunner(
    private val project: Project,
    private val inspections: List<LeakLensInspectionBase> = LeakLensInspectionRegistry.createAll(),
) {
    data class Result(val filesScanned: Int, val issuesFound: Int)

    fun analyze(files: Collection<VirtualFile>, indicator: ProgressIndicator? = null): Result {
        val service = LeakLensProjectService.getInstance(project)
        var issues = 0
        var scanned = 0
        files.forEachIndexed { index, file ->
            indicator?.checkCanceled()
            indicator?.text2 = file.name
            indicator?.fraction = index.toDouble() / files.size.coerceAtLeast(1)

            val perTool = DumbService.getInstance(project).runReadActionInSmartMode(
                Computable { analyzeFile(file) }
            ) ?: return@forEachIndexed

            scanned++
            service.clearLiveIssuesForFile(file.path)
            perTool.forEach { (shortName, found) ->
                service.updateLiveIssues(file.path, shortName, found)
                issues += found.size
            }
        }
        return Result(scanned, issues)
    }

    private fun analyzeFile(file: VirtualFile) =
        if (!file.isValid) {
            null
        } else {
            PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
                val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
                inspections
                    .filter { tool ->
                        val key = HighlightDisplayKey.find(tool.shortName)
                        key == null || profile.isToolEnabled(key, psiFile)
                    }
                    .associate { tool ->
                        tool.shortName to try {
                            tool.collectIssues(psiFile)
                        } catch (e: ProcessCanceledException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                            thisLogger().warn("LeakLens: ${tool.shortName} failed on ${file.path}", e)
                            emptyList()
                        }
                    }
            }
        }

    companion object {
        private val SOURCE_EXTENSIONS = setOf("kt", "java")

        fun isSource(project: Project, file: VirtualFile): Boolean =
            !file.isDirectory && file.extension in SOURCE_EXTENSIONS &&
                ProjectFileIndex.getInstance(project).isInSourceContent(file) &&
                !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)

        /** Expands directories into project source files (skipping excluded/generated folders). */
        fun expand(project: Project, roots: Array<VirtualFile>): List<VirtualFile> {
            val index = ProjectFileIndex.getInstance(project)
            val result = LinkedHashSet<VirtualFile>()
            for (root in roots) {
                VfsUtilCore.visitChildrenRecursively(
                    root,
                    object : VirtualFileVisitor<Unit>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            ProgressManager.checkCanceled()
                            if (file.isDirectory) return !index.isExcluded(file)
                            if (isSource(project, file)) result += file
                            return true
                        }
                    }
                )
            }
            return result.toList()
        }

        fun allProjectSources(project: Project): List<VirtualFile> {
            val result = mutableListOf<VirtualFile>()
            ProjectFileIndex.getInstance(project).iterateContent { file ->
                ProgressManager.checkCanceled()
                if (isSource(project, file)) result += file
                true
            }
            return result
        }
    }
}
