package com.github.devvikassoni.leaklens.fix

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * IntelliJ IntentionAction (Quick Fix) that inserts cleanup code for common leak patterns.
 * Shows in the Alt+Enter menu when the cursor is on a leak-related class.
 */
class LeakFixIntentionAction(
    private val fixDescription: String,
    private val fixCode: String
) : IntentionAction {

    override fun getText(): String = "LeakLens: $fixDescription"

    override fun getFamilyName(): String = "LeakLens Fix Suggestions"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        // Insert a comment with the fix suggestion at cursor position
        val document = editor.document
        val offset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(offset)
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        val comment = "\n    // TODO LeakLens Fix: $fixDescription\n    // $fixCode"
        document.insertString(lineEndOffset, comment)
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix: Add removeCallbacksAndMessages(null) in onDestroy.
 */
class AddRemoveCallbacksFixAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "LeakLens: Add handler.removeCallbacksAndMessages(null) in onDestroy"

    override fun getFamilyName(): String = "LeakLens Fix Suggestions"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        // Available in Activity/Fragment classes
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return false
        return isActivityOrFragment(containingClass)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java) ?: return
        val factory = JavaPsiFacade.getElementFactory(project)

        // Check if onDestroy exists
        val onDestroy = containingClass.findMethodsByName("onDestroy", false).firstOrNull()

        if (onDestroy != null) {
            // Add statement to existing onDestroy
            val body = onDestroy.body ?: return
            val statement = factory.createStatementFromText(
                "handler.removeCallbacksAndMessages(null);", body
            )
            val superCall = body.statements.find { it.text.contains("super.onDestroy") }
            if (superCall != null) {
                body.addBefore(statement, superCall)
            } else {
                body.addBefore(statement, body.rBrace)
            }
        } else {
            // Create onDestroy method
            val method = factory.createMethodFromText(
                """
                @Override
                protected void onDestroy() {
                    handler.removeCallbacksAndMessages(null);
                    super.onDestroy();
                }
                """.trimIndent(),
                containingClass
            )
            containingClass.add(method)
        }
    }

    private fun isActivityOrFragment(psiClass: PsiClass): Boolean {
        val superName = psiClass.superClass?.qualifiedName ?: ""
        return superName.contains("Activity") || superName.contains("Fragment")
    }
}

/**
 * Quick fix: Null out binding in onDestroyView.
 */
class NullBindingFixAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "LeakLens: Null out _binding in onDestroyView"

    override fun getFamilyName(): String = "LeakLens Fix Suggestions"

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
            val statement = factory.createStatementFromText("_binding = null;", body)
            val superCall = body.statements.find { it.text.contains("super.onDestroyView") }
            if (superCall != null) {
                body.addBefore(statement, superCall)
            } else {
                body.addBefore(statement, body.rBrace)
            }
        } else {
            val method = factory.createMethodFromText(
                """
                @Override
                public void onDestroyView() {
                    _binding = null;
                    super.onDestroyView();
                }
                """.trimIndent(),
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

    override fun getFamilyName(): String = "LeakLens Fix Suggestions"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return element.text.contains("context", ignoreCase = true)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val document = editor.document
        val text = document.text
        val offset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = text.substring(lineStartOffset, lineEndOffset)

        // Replace 'context' with 'context.applicationContext'
        val newLine = lineText.replace("context", "context.applicationContext")
        document.replaceString(lineStartOffset, lineEndOffset, newLine)
    }
}

