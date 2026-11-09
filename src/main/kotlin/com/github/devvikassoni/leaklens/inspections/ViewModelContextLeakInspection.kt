package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects ViewModels (including AndroidViewModel) that store an Activity, Fragment, View, Dialog,
 * Drawable or non-Application Context. Contexts qualified with Hilt's `@ApplicationContext` or
 * initialised from `applicationContext`/`getApplication()` are allowed.
 */
class ViewModelContextLeakInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "ViewModel holds Activity/Context/View reference"
    override fun getShortName() = "LeakLensViewModelContextLeak"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    if (!AndroidTypes.isViewModel(node.javaPsi)) return false
                    for (field in node.fields) {
                        if (field.isStatic || !isLeakyField(field, node)) continue
                        val anchor = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                        val typeName = field.type.presentableText.removeSuffix("?")
                        reporter.report(
                            anchor,
                            "LeakLens: ViewModel '${node.name}' stores a $typeName in '${field.name}'. " +
                                "ViewModels survive configuration changes and will leak it.",
                            Finding(
                                key = LeakReporter.memberKey(field, field.name),
                                title = "ViewModel stores $typeName",
                                location = "Field '${field.name}' in ${node.name}",
                                retainedClass = field.type.canonicalText,
                                suggestedFix = "Expose state (StateFlow/LiveData) instead of holding UI objects. " +
                                    "If a Context is required, use AndroidViewModel.getApplication() or " +
                                    "inject @ApplicationContext.",
                            ),
                        )
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )

    private fun isLeakyField(field: UField, owner: UClass): Boolean {
        val psiClass = AndroidTypes.classOf(field.type) ?: return false
        val isUi = AndroidTypes.isActivity(psiClass) || AndroidTypes.isFragment(psiClass) ||
            AndroidTypes.isView(psiClass) || AndroidTypes.inherits(psiClass, AndroidTypes.DIALOG) ||
            AndroidTypes.inherits(psiClass, AndroidTypes.DRAWABLE)
        if (isUi) return true
        if (!AndroidTypes.isContextType(field.type)) return false
        return !isApplicationContextSourced(field, owner)
    }

    private fun isApplicationContextSourced(field: UField, owner: UClass): Boolean {
        if (AndroidTypes.hasApplicationContextQualifier(field.sourcePsi) ||
            AndroidTypes.hasApplicationContextQualifier(field.javaPsi)
        ) {
            return true
        }
        field.uastInitializer?.let { init ->
            if (ContextPassedToSingletonInspection.isApplicationContextExpression(init)) return true
            if (owner.methods.any { m ->
                    m.isConstructor && m.uastParameters.any { p ->
                        AndroidTypes.hasApplicationContextQualifier(p.sourcePsi ?: p.javaPsi) &&
                            ParameterFlow.refersToParameter(init, p)
                    }
                }
            ) {
                return true
            }
        }
        var fromQualifiedParam = false
        owner.methods.filter { it.isConstructor }.forEach { ctor ->
            val qualified = ctor.uastParameters.filter {
                AndroidTypes.hasApplicationContextQualifier(it.sourcePsi ?: it.javaPsi)
            }
            if (qualified.isEmpty()) return@forEach
            ctor.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    if (node.operator == UastBinaryOperator.ASSIGN &&
                        AndroidTypes.refersTo(node.leftOperand, field) &&
                        qualified.any { ParameterFlow.refersToParameter(node.rightOperand, it) }
                    ) {
                        fromQualifiedParam = true
                    }
                    return super.visitBinaryExpression(node)
                }
            })
        }
        return fromQualifiedParam
    }
}
