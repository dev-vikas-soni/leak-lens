package com.github.devvikassoni.leaklens.inspections.engine

import com.github.devvikassoni.leaklens.model.Confidence
import com.github.devvikassoni.leaklens.model.Lifetime
import com.github.devvikassoni.leaklens.model.LifetimeRelationship
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression

/**
 * Shared engine for reasoning about object and scope lifetimes.
 */
object LifetimeEngine {

    /**
     * Resolves the [Lifetime] of the given class.
     */
    fun getLifetime(uClass: UClass): Lifetime {
        return getLifetime(uClass.javaPsi)
    }

    /**
     * Resolves the [Lifetime] of the given PsiClass.
     */
    fun getLifetime(psiClass: PsiClass): Lifetime {
        // 1. Check DI Scopes (Hilt/Dagger)
        if (hasAnnotation(psiClass, "javax.inject.Singleton")) return Lifetime.SINGLETON
        if (hasAnnotation(
                psiClass,
                "dagger.hilt.android.scopes.ActivityRetainedScoped"
            )
        ) return Lifetime.SINGLETON
        if (hasAnnotation(
                psiClass,
                "dagger.hilt.android.scopes.ActivityScoped"
            )
        ) return Lifetime.ACTIVITY
        if (hasAnnotation(
                psiClass,
                "dagger.hilt.android.scopes.FragmentScoped"
            )
        ) return Lifetime.FRAGMENT
        if (hasAnnotation(psiClass, "dagger.hilt.android.scopes.ViewScoped")) return Lifetime.VIEW

        // 2. Check Static/Singleton patterns
        if (isManualSingleton(psiClass)) return Lifetime.SINGLETON

        // 3. Check Android Components
        if (InheritanceUtil.isInheritor(
                psiClass,
                "android.app.Application"
            )
        ) return Lifetime.APPLICATION
        if (InheritanceUtil.isInheritor(
                psiClass,
                "androidx.lifecycle.ViewModel"
            )
        ) return Lifetime.VIEWMODEL
        if (InheritanceUtil.isInheritor(psiClass, "android.app.Activity")) return Lifetime.ACTIVITY
        if (InheritanceUtil.isInheritor(
                psiClass,
                "androidx.activity.ComponentActivity"
            )
        ) return Lifetime.ACTIVITY
        if (InheritanceUtil.isInheritor(psiClass, "android.app.Fragment")) return Lifetime.FRAGMENT
        if (InheritanceUtil.isInheritor(
                psiClass,
                "androidx.fragment.app.Fragment"
            )
        ) return Lifetime.FRAGMENT
        if (InheritanceUtil.isInheritor(psiClass, "android.view.View")) return Lifetime.VIEW
        if (InheritanceUtil.isInheritor(
                psiClass,
                "androidx.work.ListenableWorker"
            )
        ) return Lifetime.WORKER
        if (InheritanceUtil.isInheritor(psiClass, "android.app.Service")) return Lifetime.SERVICE
        if (InheritanceUtil.isInheritor(
                psiClass,
                "android.content.Context"
            )
        ) return Lifetime.ACTIVITY // Pessimistic
        if (InheritanceUtil.isInheritor(
                psiClass,
                "android.graphics.drawable.Drawable"
            )
        ) return Lifetime.VIEW // Drawable retains View

        return Lifetime.UNKNOWN
    }

    /**
     * Resolves the [Lifetime] of a given type.
     */
    fun getLifetime(type: PsiType): Lifetime {
        val psiClass =
            com.intellij.psi.util.PsiTypesUtil.getPsiClass(type) ?: return Lifetime.UNKNOWN
        return getLifetime(psiClass)
    }

    /**
     * Resolves the [Lifetime] of an expression, considering specific Android/Compose APIs.
     */
    fun getExpressionLifetime(expression: UExpression): Lifetime {
        val text = expression.sourcePsi?.text ?: ""

        return when {
            text.contains("applicationContext") || text.contains("getApplicationContext") -> Lifetime.APPLICATION
            text.contains("LocalContext.current") -> {
                if (text.contains("applicationContext")) Lifetime.APPLICATION else Lifetime.ACTIVITY
            }

            else -> getLifetime(expression.getExpressionType() ?: return Lifetime.UNKNOWN)
        }
    }

    /**
     * Resolves the [Lifetime] of a Compose construct (e.g. remember, LaunchedEffect).
     */
    fun getComposeConstructLifetime(node: UCallExpression): Lifetime {
        val name = node.methodName ?: return Lifetime.UNKNOWN
        return when {
            name.startsWith("remember") -> Lifetime.COMPOSITION
            name.endsWith("Effect") -> Lifetime.COMPOSITION
            name == "produceState" -> Lifetime.COMPOSITION
            name == "derivedStateOf" -> Lifetime.COMPOSITION
            else -> Lifetime.UNKNOWN
        }
    }

    /**
     * Resolves the [Lifetime] of a CoroutineScope expression.
     */
    fun getScopeLifetime(scope: UExpression): Lifetime {
        val type = scope.getExpressionType()
        val text = scope.sourcePsi?.text ?: ""

        return when {
            text.contains("GlobalScope") -> Lifetime.PROCESS
            text.contains("viewModelScope") -> Lifetime.VIEWMODEL
            text.contains("lifecycleScope") -> Lifetime.ACTIVITY // Default to Activity for lifecycleScope
            text.contains("viewLifecycleOwner.lifecycleScope") -> Lifetime.FRAGMENT
            text.contains("rememberCoroutineScope") -> Lifetime.COMPOSITION
            type != null && InheritanceUtil.isInheritor(
                com.intellij.psi.util.PsiTypesUtil.getPsiClass(
                    type
                ), "kotlinx.coroutines.CoroutineScope"
            ) -> {
                // Try to resolve owner of custom scope
                Lifetime.UNKNOWN
            }

            else -> Lifetime.UNKNOWN
        }
    }

    /**
     * Analyzes the relationship between an asynchronous owner (scope) and a referenced object.
     */
    fun analyzeAsynchronousRelationship(
        scopeLifetime: Lifetime,
        referencedType: PsiType,
        evidenceSource: String
    ): LifetimeRelationship {
        val refLifetime = getLifetime(referencedType)

        val confidence = when {
            scopeLifetime == Lifetime.PROCESS && refLifetime == Lifetime.ACTIVITY -> Confidence.HIGH
            scopeLifetime == Lifetime.VIEWMODEL && refLifetime == Lifetime.ACTIVITY -> Confidence.HIGH
            scopeLifetime.priority > refLifetime.priority -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val riskExplanation = if (scopeLifetime.canOutlive(refLifetime)) {
            "Asynchronous scope ($scopeLifetime) can outlive referenced object ($refLifetime)."
        } else null

        return LifetimeRelationship(
            ownerLifetime = scopeLifetime,
            referencedLifetime = refLifetime,
            evidence = evidenceSource,
            confidence = confidence,
            riskExplanation = riskExplanation
        )
    }

    /**
     * Analyzes the relationship between an owner and a referenced object.
     */
    fun analyzeRelationship(
        owner: PsiClass,
        referencedType: PsiType,
        evidenceSource: String
    ): LifetimeRelationship {
        val ownerLifetime = getLifetime(owner)
        val refLifetime = getLifetime(referencedType)

        val confidence = when {
            ownerLifetime == Lifetime.SINGLETON && refLifetime == Lifetime.ACTIVITY -> Confidence.HIGH
            ownerLifetime == Lifetime.VIEWMODEL && refLifetime == Lifetime.ACTIVITY -> Confidence.HIGH
            ownerLifetime == Lifetime.WORKER && refLifetime == Lifetime.ACTIVITY -> Confidence.HIGH
            ownerLifetime.priority > refLifetime.priority -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val riskExplanation = if (ownerLifetime.canOutlive(refLifetime)) {
            "Owner ($ownerLifetime) can outlive referenced object ($refLifetime)."
        } else null

        return LifetimeRelationship(
            ownerLifetime = ownerLifetime,
            referencedLifetime = refLifetime,
            evidence = evidenceSource,
            confidence = confidence,
            riskExplanation = riskExplanation
        )
    }

    private fun hasAnnotation(psiClass: PsiClass, fqn: String): Boolean {
        return psiClass.annotations.any { it.qualifiedName?.contains(fqn) == true }
    }

    private fun isManualSingleton(psiClass: PsiClass): Boolean {
        // Basic check for static INSTANCE field or object declaration
        return psiClass.fields.any { it.hasModifierProperty(PsiModifier.STATIC) && it.name == "INSTANCE" }
    }
}
