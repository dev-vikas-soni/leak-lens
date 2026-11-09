package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Type-resolved classification of Android / Jetpack / coroutine types.
 *
 * Every check resolves the type and walks the real class hierarchy using exact FQNs,
 * so lookalike names (`MyContextHelper`, `HandlerRegistry`, `ViewModelFactory`) never match.
 */
object AndroidTypes {
    const val CONTEXT = "android.content.Context"
    const val APPLICATION = "android.app.Application"
    const val ACTIVITY = "android.app.Activity"
    const val SERVICE = "android.app.Service"
    const val DIALOG = "android.app.Dialog"
    const val FRAGMENT = "androidx.fragment.app.Fragment"
    const val FRAGMENT_LEGACY = "android.app.Fragment"
    const val VIEW = "android.view.View"
    const val DRAWABLE = "android.graphics.drawable.Drawable"
    const val HANDLER = "android.os.Handler"
    const val VIEW_BINDING = "androidx.viewbinding.ViewBinding"
    const val VIEW_DATA_BINDING = "androidx.databinding.ViewDataBinding"
    const val VIEW_MODEL = "androidx.lifecycle.ViewModel"
    const val VIEW_MODEL_FACTORY = "androidx.lifecycle.ViewModelProvider.Factory"
    const val LIFECYCLE_COROUTINE_SCOPE = "androidx.lifecycle.LifecycleCoroutineScope"
    const val LISTENABLE_WORKER = "androidx.work.ListenableWorker"
    const val FLOW = "kotlinx.coroutines.flow.Flow"
    const val GLOBAL_SCOPE = "kotlinx.coroutines.GlobalScope"
    const val COMPOSABLE = "androidx.compose.runtime.Composable"
    const val APPLICATION_CONTEXT_QUALIFIER = "dagger.hilt.android.qualifiers.ApplicationContext"
    const val EXECUTOR = "java.util.concurrent.Executor"
    const val TIMER = "java.util.Timer"
    const val THREAD = "java.lang.Thread"
    const val TIMER_TASK = "java.util.TimerTask"

    val SINGLETON_ANNOTATIONS = setOf("javax.inject.Singleton", "jakarta.inject.Singleton")
    val INJECT_ANNOTATIONS = setOf("javax.inject.Inject", "jakarta.inject.Inject")

    private val FRAGMENTS = listOf(FRAGMENT, FRAGMENT_LEGACY)
    private val BINDINGS = listOf(VIEW_BINDING, VIEW_DATA_BINDING)

    fun classOf(type: PsiType?): PsiClass? = (type as? PsiClassType)?.resolve() ?: PsiTypesUtil.getPsiClass(type)

    fun inherits(psiClass: PsiClass?, fqn: String): Boolean =
        psiClass != null && InheritanceUtil.isInheritor(psiClass, fqn)

    fun inherits(type: PsiType?, fqn: String): Boolean = inherits(classOf(type), fqn)

    fun isActivity(psiClass: PsiClass?) = inherits(psiClass, ACTIVITY)
    fun isFragment(psiClass: PsiClass?) = FRAGMENTS.any { inherits(psiClass, it) }
    fun isView(psiClass: PsiClass?) = inherits(psiClass, VIEW)
    fun isViewModel(psiClass: PsiClass?) = inherits(psiClass, VIEW_MODEL)
    fun isWorker(psiClass: PsiClass?) = inherits(psiClass, LISTENABLE_WORKER)

    /** Activity or Fragment: classes with a lifecycle that ends while the process lives on. */
    fun isLifecycleComponent(psiClass: PsiClass?) = isActivity(psiClass) || isFragment(psiClass)
    fun isLifecycleComponent(uClass: UClass?) = isLifecycleComponent(uClass?.javaPsi)

    /**
     * A type whose instances are bound to a UI lifecycle and must not be retained beyond it:
     * Activity, Fragment, View, Dialog or any non-Application Context.
     */
    fun isLifecycleBoundType(type: PsiType?): Boolean {
        val psiClass = classOf(type) ?: return false
        if (inherits(psiClass, APPLICATION)) return false
        return inherits(psiClass, CONTEXT) || isFragment(psiClass) || isView(psiClass) || inherits(psiClass, DIALOG)
    }

    fun isContextType(type: PsiType?): Boolean {
        val psiClass = classOf(type) ?: return false
        return inherits(psiClass, CONTEXT) && !inherits(psiClass, APPLICATION)
    }

    fun isViewOrBinding(type: PsiType?): Boolean {
        val psiClass = classOf(type)
        if (psiClass == null) {
            // Generated binding classes may be unresolved before the first build.
            val text = type?.canonicalText ?: return false
            return text.substringAfterLast('.').endsWith("Binding") && text.contains(".databinding.")
        }
        return isView(psiClass) || BINDINGS.any { inherits(psiClass, it) }
    }

    /**
     * Type of the actual receiver of [call]. Prefers the receiver expression's type because K2 UAST
     * reports an extension's declared receiver (e.g. `CoroutineScope`) from [UCallExpression.receiverType].
     */
    fun receiverTypeOf(call: UCallExpression): PsiType? =
        call.receiver?.getExpressionType() ?: call.receiverType

    fun isHandler(type: PsiType?) = inherits(type, HANDLER)
    fun isFlow(type: PsiType?) = inherits(type, FLOW)

    fun hasAnnotation(owner: PsiModifierListOwner?, fqns: Collection<String>): Boolean =
        owner != null && fqns.any { owner.hasAnnotation(it) }

    /**
     * True when the declaration carries Hilt's `@ApplicationContext` qualifier, checking the
     * Kotlin source as well because constructor-property annotations land on the parameter.
     */
    fun hasApplicationContextQualifier(element: PsiElement?): Boolean {
        if (element == null) return false
        if (element is PsiModifierListOwner && element.hasAnnotation(APPLICATION_CONTEXT_QUALIFIER)) return true
        val kt = (element.navigationElement as? KtAnnotated) ?: (element as? KtAnnotated) ?: return false
        return kt.annotationEntries.any { it.shortName?.asString() == "ApplicationContext" }
    }

    /** Resolves a (possibly qualified or `this.`-prefixed) reference expression. */
    fun resolveRef(expression: UExpression?): PsiElement? {
        val expr = expression?.skipParenthesizedExprDown() ?: return null
        return when (expr) {
            is UQualifiedReferenceExpression -> expr.resolve()
            is UResolvable -> expr.resolve()
            else -> null
        }
    }

    /** True if [expression] references [field] (Java field, Kotlin property or constructor property). */
    fun refersTo(expression: UExpression?, field: UField): Boolean {
        val expr = expression?.skipParenthesizedExprDown() ?: return false
        val resolved = resolveRef(expr)
        if (resolved != null) return isSameDeclaration(resolved, field)
        val name = when (expr) {
            is USimpleNameReferenceExpression -> expr.identifier
            is UQualifiedReferenceExpression ->
                (expr.selector as? USimpleNameReferenceExpression)?.identifier
                    .takeIf { expr.receiver is UThisExpression }
            else -> null
        }
        return name != null && name == field.name
    }

    fun isSameDeclaration(resolved: PsiElement, field: UElement): Boolean {
        val targets = setOfNotNull(field.sourcePsi, field.javaPsi, field.sourcePsi?.navigationElement)
        if (resolved in targets) return true
        val nav = resolved.navigationElement
        if (nav in targets) return true
        return resolved is PsiField && field.javaPsi is PsiField && resolved.isEquivalentTo(field.javaPsi)
    }
}
