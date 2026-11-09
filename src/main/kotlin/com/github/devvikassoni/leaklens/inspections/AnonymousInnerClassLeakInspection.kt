package com.github.devvikassoni.leaklens.inspections

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects anonymous classes created inside an Activity/Fragment that capture the outer instance and
 * are handed to something that outlives it: delayed Handler messages, executors, timers, threads or
 * static fields.
 *
 * Java anonymous classes always capture `this`; Kotlin object expressions only when they use outer members.
 */
class AnonymousInnerClassLeakInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Anonymous inner class may leak outer Activity/Fragment"
    override fun getShortName() = "LeakLensAnonymousInnerClassLeak"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
                    val outer = node.getContainingUClass() ?: return false
                    if (!AndroidTypes.isLifecycleComponent(outer)) return false
                    if (node.getContainingUMethod()?.isStatic == true) return false
                    val sink = longLivedSink(node) ?: return false
                    if (!capturesOuter(node, outer)) return false

                    val anchor = anchorOf(node) ?: return false
                    val outerName = outer.name ?: "the outer class"
                    reporter.report(
                        anchor,
                        "LeakLens: Anonymous class implicitly references $outerName and is passed to $sink, " +
                            "which can outlive $outerName.",
                        Finding(
                            key = LeakReporter.memberKey(
                                node,
                                "anonymous:${node.declaration.javaPsi.superClass?.name}"
                            ),
                            title = "Anonymous class retains $outerName",
                            location = "Anonymous class passed to $sink",
                            retainedClass = outer.qualifiedName ?: outerName,
                            suggestedFix = "Use a static nested class holding a WeakReference to $outerName, " +
                                "or cancel/remove the callback in onDestroy().",
                        ),
                    )
                    return false
                }
            },
            arrayOf(UObjectLiteralExpression::class.java)
        )

    private fun anchorOf(node: UObjectLiteralExpression): PsiElement? =
        node.classReference?.sourcePsi
            ?: (node.sourcePsi as? PsiNewExpression)?.anonymousClass?.baseClassReference
            ?: (node.sourcePsi as? KtObjectLiteralExpression)?.objectDeclaration?.getObjectKeyword()
            ?: node.sourcePsi

    /** Returns a human-readable description of the long-lived sink, or null if none. */
    private fun longLivedSink(node: UObjectLiteralExpression): String? {
        val declaration = node.declaration.javaPsi
        if (AndroidTypes.inherits(declaration, AndroidTypes.THREAD)) return "a background Thread"

        return when (val parent = skipParenthesizedExprUp(node.uastParent)) {
            is UCallExpression -> callSink(parent)
            is UField -> "static field '${parent.name}'".takeIf { parent.isStatic }
            is UBinaryExpression -> assignmentSink(parent)
            else -> null
        }
    }

    private fun callSink(call: UCallExpression): String? {
        if (call.kind == UastCallKind.CONSTRUCTOR_CALL) {
            val created = call.classReference?.resolve() as? PsiClass ?: AndroidTypes.classOf(call.returnType)
            return "a background Thread".takeIf { AndroidTypes.inherits(created, AndroidTypes.THREAD) }
        }
        val method = call.resolve() ?: return null
        val owner = method.containingClass
        val name = method.name
        return when {
            AndroidTypes.inherits(owner, AndroidTypes.HANDLER) && name in HANDLER_DELAYED ->
                "Handler.$name (delayed message)"
            AndroidTypes.inherits(owner, AndroidTypes.EXECUTOR) && name in EXECUTOR_METHODS ->
                "an Executor"
            AndroidTypes.inherits(owner, AndroidTypes.TIMER) && name.startsWith("schedule") ->
                "a Timer"
            else -> null
        }
    }

    private fun assignmentSink(assignment: UBinaryExpression): String? {
        if (assignment.operator != UastBinaryOperator.ASSIGN) return null
        val target = AndroidTypes.resolveRef(assignment.leftOperand) ?: return null
        val isStatic = (target as? PsiField)?.hasModifierProperty(PsiModifier.STATIC)
            ?: target.toUElementOfType<UField>()?.isStatic
            ?: false
        return "a static field".takeIf { isStatic }
    }

    private fun capturesOuter(node: UObjectLiteralExpression, outer: UClass): Boolean {
        if (!LeakFixUtils.isKotlin(node.sourcePsi ?: return true)) return true
        val outerPsi = outer.javaPsi
        val anonymous = node.declaration.javaPsi
        var captures = false
        node.declaration.accept(object : AbstractUastVisitor() {
            override fun visitThisExpression(node: UThisExpression): Boolean {
                if (node.label != null && node.label == outer.name) captures = true
                return captures
            }

            override fun visitElement(node: org.jetbrains.uast.UElement): Boolean {
                if (captures) return true
                val isReference = node is UCallExpression || node is org.jetbrains.uast.USimpleNameReferenceExpression
                if (node is UResolvable && isReference) {
                    val isImplicit = (node as? UCallExpression)?.receiver == null
                    if (isImplicit && isOuterInstanceMember(node.resolve(), outerPsi, anonymous)) captures = true
                }
                return captures
            }
        })
        return captures
    }

    private fun isOuterInstanceMember(resolved: PsiElement?, outer: PsiClass, anonymous: PsiClass): Boolean {
        val member = (resolved as? PsiMember)
            ?: resolved?.toUElementOfType<UField>()?.javaPsi as? PsiMember
            ?: return false
        if (member.hasModifierProperty(PsiModifier.STATIC)) return false
        val owner = member.containingClass ?: return false
        if (InheritanceUtil.isInheritorOrSelf(anonymous, owner, true)) return false
        return InheritanceUtil.isInheritorOrSelf(outer, owner, true)
    }

    private companion object {
        val HANDLER_DELAYED = setOf("postDelayed", "postAtTime", "sendMessageDelayed", "sendMessageAtTime")
        val EXECUTOR_METHODS = setOf("execute", "submit", "schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay")
    }
}
