package com.github.devvikassoni.leaklens.actions

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.ui.Messages
import com.intellij.icons.AllIcons

/**
 * Action to leverage the built-in Gemini Assistant in Android Studio.
 * Copies the leak trace and provides instructions on how to use the free AI chat.
 */
class AskGeminiAction(private val leak: LeakInfo) : AnAction("Ask Gemini", "Discuss this leak with Android Studio's built-in AI assistant", AllIcons.Actions.GC) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val prompt = """
            I have a memory leak in my Android app. Please analyze this leak trace and suggest a fix.
            
            Leaking Class: ${leak.retainedObjectClassName}
            Short Description: ${leak.shortDescription}
            
            Leak Trace:
            ${leak.leakTrace}
        """.trimIndent()

        // Copy to clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(prompt))

        Messages.showInfoMessage(
            project,
            "Leak analysis prompt copied to clipboard!\n\n" +
            "Steps to use free Gemini:\n" +
            "1. Open the 'Gemini' tool window (usually on the right side).\n" +
            "2. Paste the prompt and press Enter.\n\n" +
            "This uses your built-in Android Studio AI quota for free.",
            "Ask Gemini - LeakLens"
        )
    }
}
