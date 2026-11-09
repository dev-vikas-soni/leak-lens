package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import java.util.Collections

/**
 * Base class for all LeakLens inspections.
 *
 * - Findings go through [LeakReporter], which honours `@Suppress`/`//noinspection`, attaches the
 *   AI prompt fix only in the editor, and records a [LeakInfo] with a stable, line-independent
 *   signature (so baselines survive unrelated edits).
 * - Editor findings are published to [LeakLensProjectService] once the whole file has been inspected.
 * - [collectIssues] runs the same visitor over a whole file for batch ("Analyze project") use.
 */
abstract class LeakLensInspectionBase : LocalInspectionTool() {

    private val issuesKey: Key<MutableList<LeakInfo>> =
        Key.create("LeakLens.liveIssues.${javaClass.name}")

    override fun getGroupDisplayName() = GROUP_NAME

    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val fileIssues = session.putUserDataIfAbsent(
            issuesKey,
            Collections.synchronizedList(mutableListOf())
        )
        return buildLeakVisitor(LeakReporter(this, holder, isOnTheFly, fileIssues))
    }

    /** Builds the visitor. Report every problem through [LeakReporter.report]. */
    protected abstract fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor

    override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
        val issues = session.getUserData(issuesKey) ?: return
        val file = problemsHolder.file
        if (session.restrictRange != file.textRange) return
        val filePath = file.virtualFile?.path ?: return

        val snapshot = synchronized(issues) { issues.distinctBy { it.signature to it.sourceLine } }
        LeakLensProjectService.getInstance(file.project).updateLiveIssues(filePath, shortName, snapshot)
    }

    /**
     * Runs this inspection over every element of [file] outside of the highlighting pass.
     * Must be called inside a read action in smart mode.
     */
    fun collectIssues(file: PsiFile): List<LeakInfo> {
        val issues = mutableListOf<LeakInfo>()
        val holder = ProblemsHolder(InspectionManager.getInstance(file.project), file, false)
        val visitor = buildLeakVisitor(LeakReporter(this, holder, false, issues))
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                ProgressManager.checkCanceled()
                element.accept(visitor)
                super.visitElement(element)
            }
        })
        return issues.distinctBy { it.signature to it.sourceLine }
    }

    companion object {
        const val GROUP_NAME = "LeakLens"
    }
}

/** Describes one finding; [key] must be stable across edits (no line numbers). */
data class Finding(
    val key: String,
    val title: String,
    val location: String,
    val retainedClass: String,
    val suggestedFix: String,
    val severity: LeakSeverity = LeakSeverity.WARNING,
)

class LeakReporter internal constructor(
    private val tool: LeakLensInspectionBase,
    val holder: ProblemsHolder,
    val isOnTheFly: Boolean,
    private val issues: MutableList<LeakInfo>,
) {
    val file: PsiFile get() = holder.file

    fun report(
        element: PsiElement,
        message: String,
        finding: Finding,
        vararg fixes: LocalQuickFix,
        highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    ) {
        if (tool.isSuppressedFor(element)) return
        val line = LeakLensInspectionUtils.getLineNumber(element)
        val allFixes = fixes.toMutableList<LocalQuickFix>()
        if (isOnTheFly) allFixes += AskGeminiFix(message, finding.retainedClass, line)
        holder.registerProblem(element, message, highlightType, *allFixes.toTypedArray())

        issues += LeakInfo(
            signature = "${tool.shortName}:${finding.key}",
            shortDescription = finding.title,
            leakTrace = "${finding.location} (line $line)",
            retainedObjectClassName = finding.retainedClass,
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = finding.severity,
            referenceChain = emptyList(),
            suggestedFix = finding.suggestedFix,
            sourceFile = file.virtualFile?.path,
            sourceLine = line,
            inspectionId = tool.shortName,
        )
    }

    companion object {
        /** `com.foo.MyActivity#onCreate` style key for the declaration enclosing [element]. */
        fun memberKey(element: UElement, suffix: String? = null): String {
            val uClass = element as? UClass ?: element.getParentOfType(UClass::class.java)
            val method = element.getParentOfType(UMethod::class.java)
            val owner = uClass?.qualifiedName ?: uClass?.name ?: element.sourcePsi?.containingFile?.name ?: "?"
            return buildString {
                append(owner)
                if (method != null) append('#').append(method.name)
                if (suffix != null) append('#').append(suffix)
            }
        }
    }
}
