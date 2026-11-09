package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects a UI Context (e.g. `LocalContext.current`, an Activity or a View) passed from Compose code
 * into a ViewModel or ViewModelProvider.Factory that stores it.
 */
class ComposeContextLeakInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Context/Activity passed to ViewModel in Compose"
    override fun getShortName() = "LeakLensComposeContextLeak"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.valueArgumentCount == 0 || !isComposableContext(node)) return false
                    val method = node.resolve() ?: return false
                    val target = method.containingClass ?: return false
                    val isVmTarget = AndroidTypes.isViewModel(target) ||
                        (method.isConstructor && AndroidTypes.inherits(target, AndroidTypes.VIEW_MODEL_FACTORY))
                    if (!isVmTarget) return false
                    val uMethod = method.toUElementOfType<UMethod>() ?: return false

                    for (index in 0 until method.parameterList.parametersCount) {
                        val arg = node.getArgumentForParameter(index)?.skipParenthesizedExprDown() ?: continue
                        val argType = arg.getExpressionType()
                        if (!AndroidTypes.isLifecycleBoundType(argType)) continue
                        if (ContextPassedToSingletonInspection.isApplicationContextExpression(arg)) continue
                        val parameter = ParameterFlow.parameterOf(uMethod, index) ?: continue
                        if (!ParameterFlow.storesParameter(uMethod, parameter)) continue
                        val anchor = arg.sourcePsi ?: continue
                        val fix = AppendApplicationContextFix().takeIf {
                            AndroidTypes.isContextType(argType) && LeakFixUtils.isKotlin(anchor) &&
                                argType?.canonicalText?.endsWith("?") != true
                        }
                        reporter.report(
                            anchor,
                            "LeakLens: Context passed to ${target.name} is stored by the ViewModel, which " +
                                "outlives this composition and its Activity.",
                            Finding(
                                key = LeakReporter.memberKey(node, "${target.qualifiedName}.${method.name}#$index"),
                                title = "Context passed to ${target.name} from Compose",
                                location = "${target.name}.${method.name}() argument",
                                retainedClass = argType?.canonicalText ?: AndroidTypes.CONTEXT,
                                suggestedFix = "Pass LocalContext.current.applicationContext, or keep Context " +
                                    "usage in the UI layer.",
                            ),
                            *listOfNotNull(fix).toTypedArray(),
                        )
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )

    private fun isComposableContext(node: UCallExpression): Boolean {
        if (node.getContainingUMethod()?.javaPsi?.hasAnnotation(AndroidTypes.COMPOSABLE) == true) return true
        var parent = node.uastParent
        while (parent != null && parent !is UClass) {
            if (parent is UCallExpression && parent.methodName == "setContent") return true
            parent = parent.uastParent
        }
        return false
    }

    private class AppendApplicationContextFix : LocalQuickFix {
        override fun getName() = "Pass applicationContext instead"
        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            element.replace(KtPsiFactory(project).createExpression("${element.text}.applicationContext"))
        }
    }
}
