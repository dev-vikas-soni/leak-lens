package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Hilt scope mismatches where a wider-scoped class (like @Singleton)
 * injects a narrower-scoped dependency (like an @ActivityScoped component).
 *
 * This causes the narrower-scoped dependency (and potentially the Activity itself)
 * to be retained for the lifetime of the wider scope, leading to a memory leak.
 */
class HiltScopeMismatchInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "Hilt Scope Mismatch (Wider scope holds narrower scope)"
    override fun getShortName() = "LeakLensHiltScopeMismatch"

    private val widerScopes = setOf(
        "javax.inject.Singleton",
        "dagger.hilt.android.scopes.ActivityRetainedScoped",
        "dagger.hilt.android.scopes.ViewModelScoped"
    )

    private val narrowerScopes = setOf(
        "dagger.hilt.android.scopes.ActivityScoped",
        "dagger.hilt.android.scopes.FragmentScoped",
        "dagger.hilt.android.scopes.ViewScoped"
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
                    // 1. Check if the class has a wider scope (e.g., @Singleton)
                    val classScope = getScopeAnnotation(node.javaPsi) ?: return false

                    // We only care if it's one of the "wider" scopes leaking a narrower one.
                    // For simplicity, let's treat Singleton as leaking anything narrower.
                    if (!isWiderScope(classScope)) return false

                    // 2. Look for @Inject constructors (or all constructors if we assume constructor injection)
                    val constructors = node.methods.filter { it.isConstructor }
                    for (constructor in constructors) {
                        val hasInject =
                            constructor.annotations.any { it.qualifiedName == "javax.inject.Inject" }
                        if (!hasInject) continue

                        // 3. Check constructor parameters
                        for (param in constructor.uastParameters) {
                            val paramTypeClass = PsiTypesUtil.getPsiClass(param.type) ?: continue
                            val paramScope = getScopeAnnotation(paramTypeClass) ?: continue

                            if (isNarrowerScope(paramScope)) {
                                val elementToHighlight =
                                    param.sourcePsi ?: param.javaPsi ?: continue
                                val description =
                                    "LeakLens: Scope mismatch. A $classScope class cannot inject a $paramScope dependency. " +
                                            "This will leak the narrower scope."

                                holder.registerProblem(
                                    elementToHighlight,
                                    description,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    AskGeminiFix(
                                        description,
                                        node.name ?: "Unknown",
                                        LeakLensInspectionUtils.getLineNumber(elementToHighlight)
                                    )
                                )

                                fileIssues.add(createLeakInfo(node, param, classScope, paramScope))
                            }
                        }
                    }
                    return false
                }

                override fun afterVisitFile(node: UFile) {
                    LeakLensInspectionUtils.reportLiveIssue(holder, "HiltScopeMismatch", fileIssues)
                }
            },
            arrayOf(UClass::class.java, UFile::class.java)
        )
    }

    private fun getScopeAnnotation(psiClass: PsiClass): String? {
        val annotations = psiClass.annotations
        for (annotation in annotations) {
            val fqn = annotation.qualifiedName ?: continue
            if (widerScopes.contains(fqn) || narrowerScopes.contains(fqn)) {
                return fqn
            }
        }
        return null
    }

    private fun isWiderScope(scopeFqn: String): Boolean {
        return widerScopes.contains(scopeFqn)
    }

    private fun isNarrowerScope(scopeFqn: String): Boolean {
        return narrowerScopes.contains(scopeFqn)
    }

    private fun createLeakInfo(
        uClass: UClass,
        param: UParameter,
        classScope: String,
        paramScope: String
    ): LeakInfo {
        val line = param.sourcePsi?.let { LeakLensInspectionUtils.getLineNumber(it) } ?: 0
        return LeakInfo(
            signature = "hilt_scope_mismatch_${uClass.name}_${param.name}_$line",
            shortDescription = "Scope mismatch: $classScope injects $paramScope",
            leakTrace = "Constructor parameter '${param.name}' in ${uClass.name} (line $line)",
            retainedObjectClassName = param.type.canonicalText,
            retainedByteSize = 0L,
            retainedObjectCount = 1,
            severity = LeakSeverity.CRITICAL,
            referenceChain = emptyList(),
            suggestedFix = "Remove the narrower-scoped dependency from the $classScope class or change scopes to match."
        )
    }
}
