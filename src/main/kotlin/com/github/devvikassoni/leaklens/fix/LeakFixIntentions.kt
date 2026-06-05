package com.github.devvikassoni.leaklens.fix

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Quick fix: Add removeCallbacksAndMessages(null) in onDestroy.
 */
class AddRemoveCallbacksFixAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "LeakLens: Add handler.removeCallbacksAndMessages(null) in onDestroy"
    override fun getFamilyName(): String = "LeakLens Quick Fixes"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return false
        val superName = containingClass.superClass?.qualifiedName ?: ""
        return superName.contains("Activity") || superName.contains("Fragment")
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
        val factory = JavaPsiFacade.getElementFactory(project)

        val onDestroy = containingClass.findMethodsByName("onDestroy", false).firstOrNull()
        if (onDestroy != null) {
            val body = onDestroy.body ?: return
            val statement = factory.createStatementFromText("handler.removeCallbacksAndMessages(null);", body)
            body.addBefore(statement, body.rBrace)
        } else {
            val method = factory.createMethodFromText(
                "@Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }",
                containingClass
            )
            containingClass.add(method)
        }
    }
}

/**
 * Quick fix: Null out binding in onDestroyView.
 */
class NullBindingFixAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "LeakLens: Null out _binding in onDestroyView"
    override fun getFamilyName(): String = "LeakLens Quick Fixes"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return false
        return containingClass.superClass?.qualifiedName?.contains("Fragment") == true
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
        val factory = JavaPsiFacade.getElementFactory(project)

        val onDestroyView = containingClass.findMethodsByName("onDestroyView", false).firstOrNull()
        if (onDestroyView != null) {
            val body = onDestroyView.body ?: return
            body.addBefore(factory.createStatementFromText("_binding = null;", body), body.rBrace)
        } else {
            val method = factory.createMethodFromText(
                "@Override public void onDestroyView() { _binding = null; super.onDestroyView(); }",
                containingClass
            )
            containingClass.add(method)
        }
    }
}

/**
 * Quick fix: Replace context with applicationContext in singletons.
 */
class UseApplicationContextFixAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "LeakLens: Use applicationContext instead of Activity context"
    override fun getFamilyName(): String = "LeakLens Quick Fixes"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val text = element.text
        return text == "context" || text == "mContext" || text == "activity"
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val isKotlin = element.language.id.equals("kotlin", ignoreCase = true)
        val suffix = if (isKotlin) ".applicationContext" else ".getApplicationContext()"
        val newText = "${element.text}$suffix"
        
        val document = editor.document
        document.replaceString(element.textRange.startOffset, element.textRange.endOffset, newText)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
