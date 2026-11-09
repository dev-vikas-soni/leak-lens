package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects Handler fields in an Activity/Fragment/View that schedule delayed work but are never
 * cleaned up (`removeCallbacks*` / `removeMessages`) in a teardown callback.
 */
class MissingRemoveCallbacksInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Missing removeCallbacksAndMessages in onDestroy"
    override fun getShortName() = "LeakLensMissingRemoveCallbacks"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    val psi = node.javaPsi
                    val kind = componentKind(psi) ?: return false
                    val handlers = node.fields.filter { AndroidTypes.isHandler(it.type) }
                    if (handlers.isEmpty()) return false

                    val (posting, cleaned) = analyzeUsage(node, handlers)
                    for (field in handlers) {
                        if (field !in posting || field in cleaned) continue
                        val anchor = field.uastAnchor?.sourcePsi ?: field.sourcePsi ?: continue
                        reporter.report(
                            anchor,
                            "LeakLens: Handler '${field.name}' schedules delayed work but is never cleared. " +
                                "Pending messages keep ${node.name} alive; call " +
                                "removeCallbacksAndMessages(null) in ${kind.teardown}().",
                            Finding(
                                key = LeakReporter.memberKey(field, field.name),
                                title = "Handler '${field.name}' not cleared",
                                location = "Handler field ${field.name}",
                                retainedClass = AndroidTypes.HANDLER,
                                suggestedFix = "Call ${field.name}.removeCallbacksAndMessages(null) " +
                                    "in ${kind.teardown}().",
                            ),
                            AddRemoveCallbacksFix(field.name, kind, isNullable(field)),
                        )
                    }
                    return false
                }
            },
            arrayOf(UClass::class.java)
        )

    private fun analyzeUsage(node: UClass, handlers: List<UField>): Pair<Set<UField>, Set<UField>> {
        val posting = mutableSetOf<UField>()
        val cleaned = mutableSetOf<UField>()
        node.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName
                if (name in DELAYED_METHODS || name in CLEANUP_METHODS) {
                    val field = handlers.firstOrNull { AndroidTypes.refersTo(node.receiver, it) }
                    if (field != null) {
                        if (name in DELAYED_METHODS) posting += field
                        if (name in CLEANUP_METHODS && node.getContainingUMethod()?.name in TEARDOWN_METHODS) {
                            cleaned += field
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return posting to cleaned
    }

    internal enum class ComponentKind(val teardown: String, val javaVisibility: String) {
        ACTIVITY("onDestroy", "protected"),
        FRAGMENT("onDestroyView", "public"),
        VIEW("onDetachedFromWindow", "protected"),
    }

    private fun componentKind(psi: PsiClass): ComponentKind? = when {
        AndroidTypes.isActivity(psi) -> ComponentKind.ACTIVITY
        AndroidTypes.isFragment(psi) -> ComponentKind.FRAGMENT
        AndroidTypes.isView(psi) -> ComponentKind.VIEW
        else -> null
    }

    private fun isNullable(field: UField): Boolean {
        val typeText = when (val src = field.sourcePsi) {
            is KtProperty -> src.typeReference?.text ?: if (src.initializer?.text == "null") "?" else ""
            is KtParameter -> src.typeReference?.text.orEmpty()
            else -> return false
        }
        return typeText.endsWith("?")
    }

    internal class AddRemoveCallbacksFix(
        private val fieldName: String,
        private val kind: ComponentKind,
        private val nullable: Boolean,
    ) : LocalQuickFix {
        override fun getName() = "Call $fieldName.removeCallbacksAndMessages(null) in ${kind.teardown}()"
        override fun getFamilyName() = "LeakLens: Clear Handler callbacks"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            if (LeakFixUtils.isKotlin(element)) {
                val ktClass = PsiTreeUtil.getParentOfType(element, KtClassOrObject::class.java) ?: return
                val call = "$fieldName${if (nullable) "?." else "."}removeCallbacksAndMessages(null)"
                LeakFixUtils.addToKotlinOverride(ktClass, kind.teardown, call, atStart = true)
            } else {
                val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
                LeakFixUtils.addToJavaOverride(
                    psiClass,
                    kind.teardown,
                    kind.javaVisibility,
                    "$fieldName.removeCallbacksAndMessages(null);",
                    atStart = true
                )
            }
        }
    }

    private companion object {
        val DELAYED_METHODS = setOf(
            "postDelayed",
            "postAtTime",
            "sendMessageDelayed",
            "sendEmptyMessageDelayed",
            "sendMessageAtTime",
            "sendEmptyMessageAtTime",
        )
        val CLEANUP_METHODS = setOf("removeCallbacksAndMessages", "removeCallbacks", "removeMessages")
        val TEARDOWN_METHODS = setOf(
            "onDestroy",
            "onDestroyView",
            "onStop",
            "onPause",
            "onDetach",
            "onDetachedFromWindow",
        )
    }
}
