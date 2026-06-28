package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Worker subclasses that store a raw Context reference in a field.
 *
 * Problematic pattern:
 *   class MyWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params)
 *
 * Safe pattern:
 *   class MyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
 *       fun doWork() = applicationContext.doSomething()  // use inherited getter
 *   }
 *
 * Why it matters: WorkManager can be configured with custom factories that supply
 * non-Application contexts. Storing the raw ctx makes it trivial for a refactor
 * to accidentally introduce an Activity leak. Always use applicationContext.
 */
class WorkerContextLeakInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Worker stores Context in a field (use applicationContext)"
    override fun getShortName() = "LeakLensWorkerContextLeak"

    // Worker base class FQNs
    private val workerBaseClasses = setOf(
        "androidx.work.Worker",
        "androidx.work.ListenableWorker",
        "androidx.work.CoroutineWorker",
        "androidx.work.RxWorker"
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val fileIssues = mutableListOf<LeakInfo>()

        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {

                override fun visitClass(node: UClass): Boolean {
                    if (!isWorkerSubclass(node)) return false

                    // Check all declared fields for Context type
                    for (field in node.fields) {
                        if (isContextField(field)) {
                            val elementToHighlight =
                                field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                            val description =
                                "LeakLens: '${field.name}' stores a raw Context in a Worker. " +
                                        "Use applicationContext (inherited from ListenableWorker) instead to avoid leaks."

                            holder.registerProblem(
                                elementToHighlight,
                                description,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                UseApplicationContextFix(field.name ?: "context"),
                                AskGeminiFix(
                                    description,
                                    node.name ?: "Worker",
                                    LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                )
                            )

                            fileIssues.add(createLeakInfo(node, field))
                        }
                    }



                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(holder, "WorkerContextLeak", fileIssues)
                }
            },
            arrayOf(UClass::class.java, UFile::class.java)
        )
    }

    private fun isWorkerSubclass(uClass: UClass): Boolean {
        val psiClass = uClass.javaPsi
        // Walk the superclass chain
        var current = psiClass.superClass
        while (current != null) {
            val fqn = current.qualifiedName ?: ""
            if (workerBaseClasses.contains(fqn)) return true
            current = current.superClass
        }
        // Fallback: raw extends text (handles mocks in test data)
        psiClass.extendsList?.referenceElements?.forEach { ref ->
            val text = ref.text
            if (text == "Worker" || text == "ListenableWorker" ||
                text == "CoroutineWorker" || text == "RxWorker"
            ) return true
        }
        return false
    }

    private fun isContextField(field: UField): Boolean {
        val typeFqn = field.type.canonicalText
        return typeFqn == "android.content.Context" || typeFqn == "Context"
    }

    private fun isContextParam(param: UParameter): Boolean {
        val typeFqn = param.type.canonicalText
        return typeFqn == "android.content.Context" || typeFqn == "Context"
    }

    /** Detect Kotlin `val`/`var` primary constructor params (they become backing fields). */
    private fun isKtPropertyParam(param: UParameter): Boolean {
        val sourcePsi = param.sourcePsi ?: return false
        // In Kotlin PSI, KtParameter has isValOrVar() when the param is a property
        return sourcePsi.javaClass.simpleName == "KtParameter" &&
                (sourcePsi.text.trimStart().startsWith("val ") ||
                        sourcePsi.text.trimStart().startsWith("var "))
    }

    private fun createLeakInfo(uClass: UClass, field: UField): LeakInfo {
        val line = field.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "worker_context_leak_${uClass.name}_${field.name}_$line",
            shortDescription = "Worker field holds Context",
            leakTrace = "Field '${field.name}' in ${uClass.name} (line $line)",
            retainedObjectClassName = "android.content.Context",
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = LeakSeverity.WARNING,
            referenceChain = emptyList(),
            suggestedFix = "Replace '${field.name}' usages with applicationContext (inherited from ListenableWorker)."
        )
    }
}
