package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.ai.AiUtils
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to leverage the built-in Gemini Assistant in Android Studio.
 * Copies the leak trace and provides instructions on how to use the free AI chat.
 */
class AskGeminiAction(private val leak: LeakInfo) : AnAction(
    "Ask Gemini",
    "Discuss this leak with Android Studio's built-in AI assistant",
    AllIcons.Actions.QuickfixBulb
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AiUtils.askGemini(project, leak)
    }
}
