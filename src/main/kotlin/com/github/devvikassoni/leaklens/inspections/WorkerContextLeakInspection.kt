package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Worker subclasses that store a raw Context reference in a field.
 */
class WorkerContextLeakInspection : BaseLeakLensInspection(RuleRegistry.WORKER_CONTEXT_LEAK) {

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
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!isWorkerSubclass(node)) return false

                    for (field in node.fields) {
                        if (isContextField(field)) {
                            val fieldPsi = field.sourcePsi ?: field.javaPsi
                            if (fieldPsi != null && LeakLensInspectionUtils.isApplicationContext(
                                    fieldPsi
                                )
                            ) continue

                            val elementToHighlight =
                                field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue

                            registerLeak(
                                holder = holder,
                                element = elementToHighlight,
                                description = rule.description,
                                className = node.javaPsi.qualifiedName,
                                symbolName = "worker_${field.name}",
                                suggestedFix = "Replace with applicationContext (inherited from ListenableWorker)."
                            )
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }

    override fun getQuickFixes(element: com.intellij.psi.PsiElement): Array<com.intellij.codeInspection.LocalQuickFix> {
        val uField = element.toUElementOfType<UField>() ?: return emptyArray()
        return arrayOf(UseApplicationContextFix(uField.name))
    }

    private fun isWorkerSubclass(uClass: UClass): Boolean {
        val psiClass = uClass.javaPsi
        return workerBaseClasses.any { fqn ->
            com.intellij.psi.util.InheritanceUtil.isInheritor(psiClass, fqn)
        }
    }

    private fun isContextField(field: UField): Boolean {
        val typeFqn = field.type.canonicalText
        return typeFqn == "android.content.Context" || typeFqn == "Context"
    }
}
