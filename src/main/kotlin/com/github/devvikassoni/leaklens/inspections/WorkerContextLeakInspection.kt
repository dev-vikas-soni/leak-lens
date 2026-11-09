package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects WorkManager workers that store the constructor Context in a field instead of using the
 * inherited `applicationContext`. Custom WorkerFactories may pass non-Application contexts, and the
 * stored field is an easy path to an accidental Activity leak.
 */
class WorkerContextLeakInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Worker stores Context in a field (use applicationContext)"
    override fun getShortName() = "LeakLensWorkerContextLeak"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!AndroidTypes.isWorker(node.javaPsi)) return false
                    for (field in node.fields) {
                        if (field.isStatic || !AndroidTypes.isContextType(field.type)) continue
                        val initializer = field.uastInitializer
                        if (ContextPassedToSingletonInspection.isApplicationContextExpression(initializer)) continue
                        val anchor = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                        reporter.report(
                            anchor,
                            "LeakLens: '${field.name}' stores a raw Context in a Worker. " +
                                "Use the inherited applicationContext instead.",
                            Finding(
                                key = LeakReporter.memberKey(field, field.name),
                                title = "Worker field holds Context",
                                location = "Field '${field.name}' in ${node.name}",
                                retainedClass = AndroidTypes.CONTEXT,
                                suggestedFix = "Remove '${field.name}' and use applicationContext " +
                                    "(inherited from ListenableWorker).",
                            ),
                            UseApplicationContextFix(field.name),
                        )
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
}
