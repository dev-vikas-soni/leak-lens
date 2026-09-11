package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine
import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class ViewModelContextLeakInspection : BaseLeakLensInspection(RuleRegistry.VIEWMODEL_CONTEXT_LEAK) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {

                override fun visitClass(node: UClass): Boolean {
                    if (LifetimeEngine.getLifetime(node) == com.github.devvikassoni.leaklens.model.Lifetime.VIEWMODEL) {
                        for (uField in node.fields) {
                            val fieldType = uField.type
                            val fieldPsi = uField.sourcePsi ?: uField.javaPsi
                            if (fieldPsi != null && LeakLensInspectionUtils.isApplicationContext(
                                    fieldPsi
                                )
                            ) continue

                            val relationship = LifetimeEngine.analyzeRelationship(
                                owner = node.javaPsi,
                                referencedType = fieldType,
                                evidenceSource = "Field ${uField.name}"
                            )

                            if (relationship.isRisky) {
                                val elementToHighlight =
                                    uField.uastAnchor?.sourcePsi ?: uField.sourcePsi
                                    ?: node.sourcePsi ?: continue

                                registerLeak(
                                    holder = holder,
                                    element = elementToHighlight,
                                    description = "Storing ${fieldType.presentableText} in a ViewModel will cause a memory leak.",
                                    className = node.javaPsi.qualifiedName,
                                    symbolName = "viewmodel_${uField.name}",
                                    suggestedFix = "Remove the View/Context reference from the ViewModel.",
                                    ownerLifetime = relationship.ownerLifetime,
                                    referencedLifetime = relationship.referencedLifetime,
                                    evidence = relationship.evidence,
                                    riskExplanation = relationship.riskExplanation,
                                    confidence = relationship.confidence
                                )
                            }
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }
}
