package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Wraps `flow.collect { }` in `[owner.]repeatOnLifecycle(Lifecycle.State.STARTED) { }` and adds imports.
 * [ownerPrefix] matches the LifecycleOwner of the enclosing scope (e.g. `viewLifecycleOwner.`).
 */
class WrapWithRepeatOnLifecycleFix(private val ownerPrefix: String = "") : LocalQuickFix {

    override fun getName(): String = "Wrap with ${ownerPrefix}repeatOnLifecycle(STARTED)"
    override fun getFamilyName(): String = "LeakLens: Wrap with repeatOnLifecycle"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtCallExpression::class.java) ?: return
        val statement = (call.parent as? KtQualifiedExpression)
            ?.takeIf { it.selectorExpression == call && it.parent is KtBlockExpression } ?: return
        val wrapped = "${ownerPrefix}repeatOnLifecycle(Lifecycle.State.STARTED) {\n${statement.text}\n}"
        val replaced = statement.replace(KtPsiFactory(project).createExpression(wrapped))
        LeakFixUtils.addKotlinImports(
            replaced.containingFile,
            "androidx.lifecycle.Lifecycle",
            "androidx.lifecycle.repeatOnLifecycle"
        )
        LeakFixUtils.reformat(project, replaced)
    }
}

/** `flow.launchIn(scope)` → `flow.flowWithLifecycle(owner.lifecycle, Lifecycle.State.STARTED).launchIn(scope)`. */
class InsertFlowWithLifecycleFix(private val lifecycleExpression: String) : LocalQuickFix {

    override fun getName(): String = "Insert flowWithLifecycle($lifecycleExpression) before launchIn"
    override fun getFamilyName(): String = "LeakLens: Insert flowWithLifecycle"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtCallExpression::class.java) ?: return
        val qualified = (call.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == call } ?: return
        val receiver = qualified.receiverExpression.text
        val operator = qualified.operationSign.value
        val text = "$receiver${operator}flowWithLifecycle($lifecycleExpression, Lifecycle.State.STARTED)" +
            "$operator${call.text}"
        val replaced = qualified.replace(KtPsiFactory(project).createExpression(text))
        LeakFixUtils.addKotlinImports(
            replaced.containingFile,
            "androidx.lifecycle.Lifecycle",
            "androidx.lifecycle.flowWithLifecycle"
        )
    }
}

/** `collectAsState(initial = x)` → `collectAsStateWithLifecycle(initialValue = x)` plus import. */
class CollectAsStateWithLifecycleFix : LocalQuickFix {

    override fun getName(): String = "Replace with collectAsStateWithLifecycle()"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtCallExpression::class.java, false) ?: return
        val factory = KtPsiFactory(project)
        call.calleeExpression?.replace(factory.createExpression("collectAsStateWithLifecycle"))
        call.valueArgumentList?.arguments
            ?.firstOrNull { it.getArgumentName()?.asName?.asString() == "initial" }
            ?.let { renameArgument(factory, it, "initialValue") }
        LeakFixUtils.addKotlinImports(call.containingFile, "androidx.lifecycle.compose.collectAsStateWithLifecycle")
    }

    private fun renameArgument(factory: KtPsiFactory, argument: KtValueArgument, newName: String) {
        val value = argument.getArgumentExpression()?.text ?: return
        val list = factory.createCallArguments("($newName = $value)")
        argument.replace(list.arguments.single())
    }
}
