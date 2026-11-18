package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects an Activity/View/UI Context passed to a singleton (Kotlin `object`, `@Singleton`, or a class
 * with a static self-typed instance field) **and stored** by it, or captured via lambdas.
 */
class ContextPassedToSingletonInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Activity Context passed to Singleton"
    override fun getShortName() = "LeakLensContextPassedToSingleton"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.valueArgumentCount == 0) return false
                    val method = node.resolve() ?: return false
                    var targetClass = method.containingClass ?: return false

                    val receiverExpr = node.receiver
                    if (receiverExpr != null) {
                        val receiverRef = AndroidTypes.resolveRef(receiverExpr)
                        val receiverMember = receiverRef as? PsiMember
                        if (receiverMember != null && receiverMember.containingClass != null) {
                            val receiverOwner = receiverMember.containingClass!!
                            if (singletonOf(receiverOwner) != null) {
                                targetClass = receiverOwner
                            }
                        }
                    }

                    val singleton = singletonOf(targetClass) ?: return false
                    val uMethod = method.toUElementOfType<UMethod>()
                    if (uMethod != null && (uMethod.sourcePsi as? KtNamedFunction)?.receiverTypeReference != null) return false

                    for (index in 0 until node.valueArgumentCount) {
                        val arg = node.getArgumentForParameter(index) ?: continue
                        val isContextArg = isUiContextArgument(arg, node)
                        val isCapturingLambda = isLambdaOrAnonymousClass(arg) && capturesOuterUi(arg, node)

                        if (!isContextArg && !isCapturingLambda) continue

                        if (isContextArg && uMethod != null && index < method.parameterList.parametersCount) {
                            val parameter = ParameterFlow.parameterOf(uMethod, index)
                            if (parameter != null && !ParameterFlow.storesParameter(uMethod, parameter)) continue
                        }

                        val anchor = arg.sourcePsi ?: continue
                        val singletonName = singleton.name ?: "Singleton"
                        val fix = if (isContextArg) applicationContextFix(arg) else null

                        reporter.report(
                            anchor,
                            "LeakLens: Activity/UI Context is stored by singleton '$singletonName' and will leak. " +
                                "Pass applicationContext instead.",
                            Finding(
                                key = LeakReporter.memberKey(node, "${singleton.qualifiedName}.${method.name}"),
                                title = "UI Context stored by $singletonName",
                                location = "Passed to $singletonName.${method.name}()",
                                retainedClass = arg.getExpressionType()?.canonicalText ?: AndroidTypes.CONTEXT,
                                suggestedFix = "Pass context.applicationContext to $singletonName.",
                            ),
                            *listOfNotNull(fix).toTypedArray(),
                        )
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )

    private fun isLambdaOrAnonymousClass(arg: UExpression): Boolean {
        val expr = arg.skipParenthesizedExprDown()
        return expr is ULambdaExpression || expr is UObjectLiteralExpression || expr is UCallableReferenceExpression
    }

    private fun singletonOf(owner: PsiClass): PsiClass? {
        if (AndroidTypes.hasAnnotation(owner, AndroidTypes.SINGLETON_ANNOTATIONS)) return owner
        val ktObject = owner.navigationElement as? KtObjectDeclaration
        if (ktObject != null && !ktObject.isObjectLiteral()) {
            if (!ktObject.isCompanion()) return owner
            val outer = owner.containingClass ?: return null
            return outer.takeIf { hasStaticSelfField(it) }
        }
        return owner.takeIf { hasStaticSelfField(it) }
    }

    private fun hasStaticSelfField(psiClass: PsiClass) = psiClass.fields.any {
        it.hasModifierProperty(PsiModifier.STATIC) && AndroidTypes.classOf(it.type) == psiClass
    }

    private fun isUiContextArgument(arg: UExpression, call: UCallExpression): Boolean {
        val expr = arg.skipParenthesizedExprDown()
        val psiClass = AndroidTypes.classOf(expr.getExpressionType()) ?: return false
        if (AndroidTypes.inherits(psiClass, AndroidTypes.APPLICATION)) return false
        if (AndroidTypes.isActivity(psiClass) || AndroidTypes.isFragment(psiClass) ||
            AndroidTypes.isView(psiClass) || AndroidTypes.inherits(psiClass, AndroidTypes.DIALOG)
        ) {
            return true
        }
        if (!AndroidTypes.inherits(psiClass, AndroidTypes.CONTEXT)) return false
        if (isApplicationContextExpression(expr)) return false
        val caller = call.getContainingUClass()?.javaPsi
        return AndroidTypes.isLifecycleComponent(caller) || AndroidTypes.isView(caller)
    }

    private fun capturesOuterUi(expression: UExpression, call: UCallExpression): Boolean {
        val outer = call.getContainingUClass() ?: return false
        if (!AndroidTypes.isLifecycleComponent(outer)) return false
        val outerName = outer.name ?: return false
        var captures = false
        expression.accept(object : AbstractUastVisitor() {
            override fun visitThisExpression(node: UThisExpression): Boolean {
                if (node.label == null || node.label == outerName) captures = true
                return captures
            }

            override fun visitElement(node: UElement): Boolean {
                if (captures) return true
                if (node is UCallExpression || node is USimpleNameReferenceExpression) {
                    val resolved = AndroidTypes.resolveRef(node as? UExpression)
                    if (resolved is PsiMember && !resolved.hasModifierProperty(PsiModifier.STATIC)) {
                        val memberOwner = resolved.containingClass
                        if (memberOwner != null && InheritanceUtil.isInheritorOrSelf(outer.javaPsi, memberOwner, true)) {
                            captures = true
                        }
                    }
                }
                return captures
            }
        })
        return captures
    }

    private fun applicationContextFix(arg: UExpression): LocalQuickFix? {
        val expr = arg.skipParenthesizedExprDown()
        if (!AndroidTypes.inherits(expr.getExpressionType(), AndroidTypes.CONTEXT)) return null
        val psi = expr.sourcePsi ?: return null
        if (!LeakFixUtils.isKotlin(psi)) return UseApplicationContextQuickFix()
        val safe = expr is UThisExpression ||
            (expr is UCallExpression && expr.methodName in setOf("requireContext", "requireActivity"))
        return if (safe) UseApplicationContextQuickFix() else null
    }

    private class UseApplicationContextQuickFix : LocalQuickFix {
        override fun getName() = "Use applicationContext"
        override fun getFamilyName() = "LeakLens: Use applicationContext"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            if (LeakFixUtils.isKotlin(element)) {
                val text = if (element.text == "this") "applicationContext" else "${element.text}.applicationContext"
                element.replace(KtPsiFactory(project).createExpression(text))
            } else {
                val simple = element is PsiThisExpression || element is PsiReferenceExpression ||
                    element is PsiMethodCallExpression
                val text = when {
                    element.text == "this" -> "getApplicationContext()"
                    simple -> "${element.text}.getApplicationContext()"
                    else -> "(${element.text}).getApplicationContext()"
                }
                element.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(text, element))
            }
        }
    }

    companion object {
        internal fun isApplicationContextExpression(expr: UExpression?): Boolean {
            if (expr == null) return false
            val resolved = AndroidTypes.resolveRef(expr) ?: (expr as? UCallExpression)?.resolve()
            val name = (resolved as? com.intellij.psi.PsiMethod)?.name
                ?: (expr as? UCallExpression)?.methodName
                ?: expr.sourcePsi?.text?.substringAfterLast('.')
            return name == "getApplicationContext" || name == "applicationContext" || name == "getApplication"
        }
    }
}
