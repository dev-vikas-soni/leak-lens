package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Lightweight, intra-procedural "does this parameter escape into a field?" analysis.
 *
 * Recognises direct field assignment, Kotlin constructor properties, property initialisers,
 * insertion into a field collection (`listeners.add(param)`) and forwarding to a constructor
 * that stores it (bounded depth).
 */
internal object ParameterFlow {
    private const val MAX_DEPTH = 2
    private val COLLECTION_ADDERS = setOf("add", "addAll", "put", "set", "offer", "push", "plusAssign")

    fun parameterOf(method: UMethod, index: Int): UParameter? = method.uastParameters.getOrNull(index)

    fun storesParameter(method: UMethod, parameter: UParameter, depth: Int = 0): Boolean {
        val ktParam = parameter.sourcePsi as? KtParameter
        if (ktParam != null && ktParam.hasValOrVar()) return true
        if (method.isConstructor && storedByPropertyInitializer(method.getContainingUClass(), parameter)) return true

        var stored = false
        method.uastBody?.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                if (node.operator == UastBinaryOperator.ASSIGN &&
                    refersToParameter(node.rightOperand, parameter) &&
                    isField(AndroidTypes.resolveRef(node.leftOperand))
                ) {
                    stored = true
                }
                return stored || super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (stored) return true
                val argIndex = node.valueArguments.indexOfFirst { refersToParameter(it, parameter) }
                if (argIndex >= 0) {
                    stored = isAddToField(node) || forwardsToStoringMethod(node, argIndex, depth)
                }
                return stored || super.visitCallExpression(node)
            }
        })
        return stored
    }

    private fun isAddToField(call: UCallExpression): Boolean =
        call.methodName in COLLECTION_ADDERS && isField(AndroidTypes.resolveRef(call.receiver))

    private fun forwardsToStoringMethod(call: UCallExpression, argIndex: Int, depth: Int): Boolean {
        if (depth >= MAX_DEPTH) return false
        val target = call.resolve() ?: return false
        if (!target.isConstructor && !isSetterLike(target)) return false
        val uTarget = target.toUElementOfType<UMethod>() ?: return false
        val paramIndex = (0 until target.parameterList.parametersCount).firstOrNull {
            call.getArgumentForParameter(it)?.sourcePsi == call.valueArguments[argIndex].sourcePsi
        } ?: argIndex
        val targetParam = parameterOf(uTarget, paramIndex) ?: return false
        return storesParameter(uTarget, targetParam, depth + 1)
    }

    private fun isSetterLike(method: PsiMethod) =
        method.name.startsWith("set") || method.name.startsWith("init") || method.name.startsWith("register")

    private fun storedByPropertyInitializer(uClass: UClass?, parameter: UParameter): Boolean =
        uClass?.fields?.any { refersToParameter(it.uastInitializer, parameter) } == true

    fun refersToParameter(expression: UExpression?, parameter: UElement): Boolean {
        val resolved = AndroidTypes.resolveRef(expression?.skipParenthesizedExprDown()) ?: return false
        return AndroidTypes.isSameDeclaration(resolved, parameter)
    }

    fun isField(resolved: PsiElement?): Boolean = when (resolved) {
        is PsiField -> true
        is KtProperty -> !resolved.isLocal
        is KtParameter -> resolved.hasValOrVar()
        else -> false
    }
}
