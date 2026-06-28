package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement

class FlowLifecycleInspection : LocalInspectionTool() {

    override fun getGroupDisplayName() = "LeakLens"
    override fun getDisplayName() = "LeakLens: Unsafe collection of Flow in UI"
    override fun getShortName() = "LeakLensFlowLifecycleLeak"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val uElement = element.toUElement() ?: return
                if (uElement is UCallExpression) {
                    visitCallExpression(uElement)
                }
            }

            private fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName
                if (methodName == "collect" || methodName == "collectLatest") {
                    val containingClass = node.getContainingUClass()
                    if (containingClass != null && isAndroidUiClass(containingClass)) {
                        // Check if inside repeatOnLifecycle
                        if (!isInsideRepeatOnLifecycle(node) && !usesFlowWithLifecycle(node)) {
                            val sourcePsi =
                                node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return
                            val className = containingClass.name ?: "Activity/Fragment"
                            val line = LeakLensInspectionUtils.getLineNumber(sourcePsi)
                            val description =
                                "LeakLens: Unsafe collection of Flow in UI. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks."
                            holder.registerProblem(
                                sourcePsi,
                                description,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                WrapWithRepeatOnLifecycleFix(),
                                AskGeminiFix(description, className, line)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isAndroidUiClass(uClass: UClass): Boolean {
        val psiClass = uClass.javaPsi
        val superTypes = psiClass.supers
        for (superType in superTypes) {
            val fqName = superType.qualifiedName
            if (fqName == "android.app.Activity" ||
                fqName == "androidx.fragment.app.Fragment" ||
                fqName == "android.app.Fragment" ||
                fqName == "androidx.activity.ComponentActivity" ||
                fqName == "androidx.appcompat.app.AppCompatActivity"
            ) {
                return true
            }
            if (superType.supers.any { it.qualifiedName == "android.app.Activity" || it.qualifiedName == "androidx.fragment.app.Fragment" }) {
                return true
            }
        }
        return false
    }

    private fun isInsideRepeatOnLifecycle(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UCallExpression) {
                if (parent.methodName == "repeatOnLifecycle") {
                    return true
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun usesFlowWithLifecycle(node: UCallExpression): Boolean {
        // node is the collect() call.
        // It could be receiver.collect()
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression) {
            val receiver = parent.receiver
            if (receiver is UCallExpression && receiver.methodName == "flowWithLifecycle") {
                return true
            }
            // Check deeper if there are multiple calls e.g. flow.flowWithLifecycle().collectLatest()
            if (hasFlowWithLifecycleInChain(receiver)) {
                return true
            }
        }
        // Sometimes the call expression contains the receiver itself in node.receiver
        val receiver = node.receiver
        if (receiver != null) {
            if (hasFlowWithLifecycleInChain(receiver)) {
                return true
            }
        }
        return false
    }

    private fun hasFlowWithLifecycleInChain(element: UElement): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is UCallExpression && current.methodName == "flowWithLifecycle") {
                return true
            }
            if (current is UQualifiedReferenceExpression) {
                if (current.selector is UCallExpression && (current.selector as UCallExpression).methodName == "flowWithLifecycle") {
                    return true
                }
                current = current.receiver
            } else if (current is UCallExpression) {
                current = current.receiver
            } else {
                break
            }
        }
        return false
    }
}
