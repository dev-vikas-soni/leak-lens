package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Detects Flow collection in Activities/Fragments/Compose that keeps running while the UI is in the
 * background:
 * - `collect`/`collectLatest` inside `lifecycleScope.launch { }` without `repeatOnLifecycle`
 * - `launchIn(lifecycleScope)` without `flowWithLifecycle`
 * - `collectAsState()` in Compose (prefer `collectAsStateWithLifecycle()`)
 */
class FlowLifecycleInspection : LeakLensInspectionBase() {

    override fun getDisplayName() = "Unsafe collection of Flow in UI"
    override fun getShortName() = "LeakLensFlowLifecycleLeak"

    override fun buildLeakVisitor(reporter: LeakReporter): PsiElementVisitor =
        UastHintedVisitorAdapter.create(
            reporter.file.language,
            object : AbstractUastNonRecursiveVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    when (node.methodName) {
                        "collect", "collectLatest" -> checkCollect(node, reporter)
                        "launchIn" -> checkLaunchIn(node, reporter)
                        "collectAsState" -> checkCollectAsState(node, reporter)
                    }
                    return false
                }
            },
            arrayOf(UCallExpression::class.java)
        )

    private fun checkCollect(node: UCallExpression, reporter: LeakReporter) {
        if (!AndroidTypes.isFlow(AndroidTypes.receiverTypeOf(node)) || chainHasFlowWithLifecycle(node.receiver)) return
        if (!AndroidTypes.isLifecycleComponent(node.getContainingUClass())) return
        val launch = enclosingUnsafeLaunch(node) ?: return
        val owner = DeprecatedLifecycleScopeInspection.ownerPrefix(launch.receiver?.sourcePsi?.text)
        val fix = owner?.let { prefix ->
            WrapWithRepeatOnLifecycleFix(prefix).takeIf { statementOf(node) != null }
        }
        report(
            node,
            reporter,
            "LeakLens: Flow is collected in lifecycleScope.launch without repeatOnLifecycle; collection " +
                "continues while the UI is in the background.",
            "Wrap the collection in repeatOnLifecycle(Lifecycle.State.STARTED) { ... }.",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            fix,
        )
    }

    private fun checkLaunchIn(node: UCallExpression, reporter: LeakReporter) {
        if (!AndroidTypes.isFlow(AndroidTypes.receiverTypeOf(node)) || chainHasFlowWithLifecycle(node.receiver)) return
        val scopeArg = node.valueArguments.firstOrNull() ?: return
        if (!AndroidTypes.inherits(scopeArg.getExpressionType(), AndroidTypes.LIFECYCLE_COROUTINE_SCOPE)) return
        if (hasSafeAncestor(node)) return
        val scopeText = scopeArg.sourcePsi?.text
        val fix = DeprecatedLifecycleScopeInspection.ownerPrefix(scopeText)
            ?.let { InsertFlowWithLifecycleFix("${it}lifecycle") }
            ?.takeIf { node.sourcePsi is KtCallExpression }
        report(
            node,
            reporter,
            "LeakLens: launchIn(lifecycleScope) keeps collecting while the UI is in the background. " +
                "Add flowWithLifecycle before launchIn.",
            "Insert .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED) before launchIn().",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            fix,
        )
    }

    private fun checkCollectAsState(node: UCallExpression, reporter: LeakReporter) {
        if (!AndroidTypes.isFlow(AndroidTypes.receiverTypeOf(node))) return
        if (!isComposableContext(node)) return
        report(
            node,
            reporter,
            "LeakLens: collectAsState() keeps collecting while the app is in the background. " +
                "Use collectAsStateWithLifecycle().",
            "Replace collectAsState() with collectAsStateWithLifecycle() (lifecycle-runtime-compose).",
            ProblemHighlightType.WEAK_WARNING,
            CollectAsStateWithLifecycleFix().takeIf { node.sourcePsi is KtCallExpression },
        )
    }

    @Suppress("LongParameterList")
    private fun report(
        node: UCallExpression,
        reporter: LeakReporter,
        message: String,
        suggestion: String,
        highlight: ProblemHighlightType,
        fix: LocalQuickFix?,
    ) {
        val anchor = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return
        val owner = node.getContainingUClass()
        val ownerName = owner?.name ?: node.getContainingUMethod()?.name ?: "UI"
        reporter.report(
            anchor,
            message,
            Finding(
                key = LeakReporter.memberKey(node, "${node.methodName}:${receiverText(node)}"),
                title = "Flow collected without lifecycle awareness",
                location = "${node.methodName}() in $ownerName",
                retainedClass = owner?.qualifiedName ?: ownerName,
                suggestedFix = suggestion,
            ),
            *listOfNotNull(fix).toTypedArray(),
            highlightType = highlight,
        )
    }

    private fun receiverText(node: UCallExpression) =
        node.receiver?.sourcePsi?.text?.take(MAX_KEY_TEXT)?.replace(Regex("\\s+"), "") ?: ""

    /** Returns the enclosing `lifecycleScope.launch/async` call if no lifecycle-safe wrapper is in between. */
    private fun enclosingUnsafeLaunch(node: UElement): UCallExpression? {
        var parent = node.uastParent
        while (parent != null && parent !is UMethod && parent !is UClass) {
            if (parent is UCallExpression) {
                val name = parent.methodName
                if (name in SAFE_WRAPPERS) return null
                if (name == "launch" || name == "async") {
                    return parent.takeIf {
                        AndroidTypes.inherits(AndroidTypes.receiverTypeOf(it), AndroidTypes.LIFECYCLE_COROUTINE_SCOPE)
                    }
                }
            }
            parent = parent.uastParent
        }
        return null
    }

    private fun hasSafeAncestor(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null && parent !is UMethod && parent !is UClass) {
            if (parent is UCallExpression && parent.methodName in SAFE_WRAPPERS) return true
            parent = parent.uastParent
        }
        return false
    }

    private fun chainHasFlowWithLifecycle(receiver: UExpression?): Boolean {
        var current: UElement? = receiver
        while (current != null) {
            current = when (current) {
                is UQualifiedReferenceExpression -> {
                    if ((current.selector as? UCallExpression)?.methodName == FLOW_WITH_LIFECYCLE) return true
                    current.receiver
                }
                is UCallExpression -> {
                    if (current.methodName == FLOW_WITH_LIFECYCLE) return true
                    current.receiver
                }
                else -> null
            }
        }
        return false
    }

    private fun isComposableContext(node: UElement): Boolean {
        val method = node.getContainingUMethod()
        if (method != null && method.javaPsi.hasAnnotation(AndroidTypes.COMPOSABLE)) return true
        var parent = node.uastParent
        while (parent != null && parent !is UClass) {
            if (parent is UCallExpression && parent.methodName == "setContent") return true
            parent = parent.uastParent
        }
        return false
    }

    companion object {
        private const val FLOW_WITH_LIFECYCLE = "flowWithLifecycle"
        private const val MAX_KEY_TEXT = 80
        private val SAFE_WRAPPERS = setOf(
            "repeatOnLifecycle", FLOW_WITH_LIFECYCLE,
            "launchWhenStarted", "launchWhenResumed", "launchWhenCreated",
            "whenStarted", "whenResumed", "whenCreated", "withStarted", "withResumed", "withCreated",
        )

        /** The statement-level Kotlin expression containing the collect call, or null. */
        internal fun statementOf(node: UCallExpression): KtQualifiedExpression? {
            val call = node.sourcePsi as? KtCallExpression ?: return null
            val qualified = call.parent as? KtQualifiedExpression
            return qualified?.takeIf { it.selectorExpression == call && it.parent is KtBlockExpression }
        }
    }
}
