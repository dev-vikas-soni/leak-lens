package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Hilt scope mismatches: a scoped class injecting (via constructor or field, directly or through
 * `Provider`/`Lazy`) a dependency whose scope is not an ancestor of its own component, e.g. a
 * `@Singleton` holding an `@ActivityScoped` object (and therefore the Activity).
 */
class HiltScopeMismatchInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Hilt Scope Mismatch (Wider scope holds narrower scope)"
    override fun getShortName() = "LeakLensHiltScopeMismatch"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    val classScope = scopeOf(node.javaPsi) ?: return false
                    val allowed = ALLOWED[classScope] ?: return false

                    val injected = mutableListOf<Pair<UElement, PsiType>>()
                    node.methods.filter { it.isConstructor && hasInject(it.javaPsi) }
                        .forEach { ctor -> ctor.uastParameters.forEach { injected += it to it.type } }
                    node.fields.filter { hasInject(it.javaPsi) || hasInject(it.sourcePsi) }
                        .forEach { injected += it to it.type }

                    for ((element, type) in injected) {
                        val dependency = AndroidTypes.classOf(unwrapProvider(type)) ?: continue
                        val depScope = scopeOf(dependency) ?: continue
                        if (depScope in allowed) continue
                        val anchor = element.sourcePsi ?: continue
                        val name = (element as? com.intellij.psi.PsiNamedElement)?.name
                            ?: (anchor as? com.intellij.psi.PsiNamedElement)?.name
                            ?: "dependency"
                        val classLabel = simple(classScope)
                        val depLabel = simple(depScope)
                        reporter.report(
                            anchor,
                            "LeakLens: Scope mismatch. @$classLabel ${node.name} injects @$depLabel " +
                                "${dependency.name}, which will be retained beyond its lifecycle.",
                            Finding(
                                key = LeakReporter.memberKey(node, "inject:${dependency.qualifiedName}"),
                                title = "@$classLabel injects @$depLabel ${dependency.name}",
                                location = "Injected '$name' in ${node.name}",
                                retainedClass = dependency.qualifiedName ?: dependency.name ?: "Unknown",
                                suggestedFix = "Inject the narrower-scoped dependency where it is used, pass it as " +
                                    "a method argument, or widen its scope deliberately.",
                                severity = LeakSeverity.CRITICAL,
                            ),
                        )
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )

    private fun hasInject(element: Any?): Boolean = when (element) {
        is com.intellij.psi.PsiModifierListOwner -> AndroidTypes.hasAnnotation(element, AndroidTypes.INJECT_ANNOTATIONS)
        is KtAnnotated -> element.annotationEntries.any { it.shortName?.asString() == "Inject" }
        else -> false
    }

    private fun unwrapProvider(type: PsiType): PsiType {
        val classType = type as? PsiClassType ?: return type
        val fqn = classType.resolve()?.qualifiedName ?: return type
        return if (fqn in PROVIDERS) classType.parameters.firstOrNull() ?: type else type
    }

    private fun scopeOf(psiClass: PsiClass): String? {
        if (psiClass.hasAnnotation(HILT_VIEW_MODEL)) return VIEW_MODEL_SCOPED
        return SCOPES.firstOrNull { psiClass.hasAnnotation(it) }
    }

    private fun simple(fqn: String) = fqn.substringAfterLast('.')

    private companion object {
        const val SINGLETON = "javax.inject.Singleton"
        const val JAKARTA_SINGLETON = "jakarta.inject.Singleton"
        const val ACTIVITY_RETAINED = "dagger.hilt.android.scopes.ActivityRetainedScoped"
        const val VIEW_MODEL_SCOPED = "dagger.hilt.android.scopes.ViewModelScoped"
        const val ACTIVITY_SCOPED = "dagger.hilt.android.scopes.ActivityScoped"
        const val FRAGMENT_SCOPED = "dagger.hilt.android.scopes.FragmentScoped"
        const val VIEW_SCOPED = "dagger.hilt.android.scopes.ViewScoped"
        const val SERVICE_SCOPED = "dagger.hilt.android.scopes.ServiceScoped"
        const val HILT_VIEW_MODEL = "dagger.hilt.android.lifecycle.HiltViewModel"

        val SCOPES = listOf(
            SINGLETON,
            JAKARTA_SINGLETON,
            ACTIVITY_RETAINED,
            VIEW_MODEL_SCOPED,
            ACTIVITY_SCOPED,
            FRAGMENT_SCOPED,
            VIEW_SCOPED,
            SERVICE_SCOPED,
        )
        val PROVIDERS = setOf("javax.inject.Provider", "jakarta.inject.Provider", "dagger.Lazy")

        private val ROOT = setOf(SINGLETON, JAKARTA_SINGLETON)

        /** Scopes a component may depend on: its own and its ancestors in the Hilt component hierarchy. */
        val ALLOWED: Map<String, Set<String>> = mapOf(
            SINGLETON to ROOT,
            JAKARTA_SINGLETON to ROOT,
            ACTIVITY_RETAINED to ROOT + ACTIVITY_RETAINED,
            VIEW_MODEL_SCOPED to ROOT + ACTIVITY_RETAINED + VIEW_MODEL_SCOPED,
            ACTIVITY_SCOPED to ROOT + ACTIVITY_RETAINED + ACTIVITY_SCOPED,
            FRAGMENT_SCOPED to ROOT + ACTIVITY_RETAINED + ACTIVITY_SCOPED + FRAGMENT_SCOPED,
            VIEW_SCOPED to ROOT + ACTIVITY_RETAINED + ACTIVITY_SCOPED + VIEW_SCOPED,
            SERVICE_SCOPED to ROOT + SERVICE_SCOPED,
        )
    }
}
