package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine
import com.github.devvikassoni.leaklens.inspections.registry.RuleRegistry
import com.github.devvikassoni.leaklens.model.Lifetime
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class HiltScopeMismatchInspection : BaseLeakLensInspection(RuleRegistry.HILT_SCOPE_MISMATCH) {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return UastHintedVisitorAdapter.create(
            holder.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    val ownerLifetime = LifetimeEngine.getLifetime(node)
                    if (ownerLifetime == Lifetime.UNKNOWN) return false

                    // Check constructor parameters
                    val constructors = node.methods.filter { it.isConstructor }
                    for (constructor in constructors) {
                        val hasInject =
                            constructor.uAnnotations.any { it.qualifiedName == "javax.inject.Inject" }
                        if (!hasInject) continue

                        for (param in constructor.uastParameters) {
                            val relationship = LifetimeEngine.analyzeRelationship(
                                owner = node.javaPsi,
                                referencedType = param.type,
                                evidenceSource = "Constructor parameter ${param.name}"
                            )

                            if (relationship.isRisky) {
                                val elementToHighlight =
                                    param.uastAnchor?.sourcePsi ?: param.sourcePsi ?: param.javaPsi
                                    ?: continue

                                registerLeak(
                                    holder = holder,
                                    element = elementToHighlight,
                                    description = rule.description,
                                    className = node.javaPsi.qualifiedName,
                                    symbolName = "hilt_param_${param.name}",
                                    suggestedFix = "Remove the narrower-scoped dependency from the ${relationship.ownerLifetime} class.",
                                    ownerLifetime = relationship.ownerLifetime,
                                    referencedLifetime = relationship.referencedLifetime,
                                    evidence = relationship.evidence,
                                    riskExplanation = relationship.riskExplanation,
                                    confidence = relationship.confidence
                                )
                            }
                        }
                    }

                    // Check injected fields
                    for (field in node.fields) {
                        val hasInject =
                            field.uAnnotations.any { it.qualifiedName == "javax.inject.Inject" }
                        if (!hasInject) continue

                        val relationship = LifetimeEngine.analyzeRelationship(
                            owner = node.javaPsi,
                            referencedType = field.type,
                            evidenceSource = "Injected field ${field.name}"
                        )

                        if (relationship.isRisky) {
                            val elementToHighlight =
                                field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: field.javaPsi
                                ?: continue

                            registerLeak(
                                holder = holder,
                                element = elementToHighlight,
                                description = rule.description,
                                className = node.javaPsi.qualifiedName,
                                symbolName = "hilt_field_${field.name}",
                                suggestedFix = "Remove the narrower-scoped dependency from the ${relationship.ownerLifetime} class.",
                                ownerLifetime = relationship.ownerLifetime,
                                referencedLifetime = relationship.referencedLifetime,
                                evidence = relationship.evidence,
                                riskExplanation = relationship.riskExplanation,
                                confidence = relationship.confidence
                            )
                        }
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )
    }
}
