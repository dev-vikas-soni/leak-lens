package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Detects anonymous inner classes that implicitly hold a reference to an outer Activity/Fragment.
 * Common pattern: new Runnable() { ... } or object : SomeCallback { ... } inside Activity.
 */
class AnonymousInnerClassLeakInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "LeakLens"
    override fun getDisplayName(): String = "Anonymous inner class may leak outer Activity/Fragment"
    override fun getShortName(): String = "LeakLensAnonymousInnerClassLeak"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
                super.visitAnonymousClass(aClass)

                // Check if the outer class is an Activity or Fragment
                val outerClass = PsiTreeUtil.getParentOfType(aClass, PsiClass::class.java, true) ?: return
                if (!isActivityOrFragment(outerClass)) return

                // Check if the anonymous class is assigned to a field or passed to a long-lived callback
                val parent = aClass.parent?.parent
                if (parent is PsiField || isPassedToLongLivedMethod(aClass)) {
                    holder.registerProblem(
                        aClass.baseClassReference,
                        "LeakLens: Anonymous inner class holds implicit reference to ${outerClass.name}. " +
                        "If this object outlives the Activity/Fragment, it will cause a memory leak. " +
                        "Consider using a static inner class with WeakReference.",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

    private fun isActivityOrFragment(psiClass: PsiClass): Boolean {
        var current = psiClass.superClass
        while (current != null) {
            val name = current.qualifiedName ?: ""
            if (name.contains("Activity") || name.contains("Fragment")) return true
            current = current.superClass
        }
        return false
    }

    private fun isPassedToLongLivedMethod(aClass: PsiAnonymousClass): Boolean {
        val methodCall = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression::class.java)
        val methodName = methodCall?.methodExpression?.referenceName ?: return false
        return methodName in listOf(
            "postDelayed", "post", "execute", "submit",
            "registerListener", "addCallback", "setOnClickListener",
            "addOnGlobalLayoutListener", "registerReceiver"
        )
    }
}

