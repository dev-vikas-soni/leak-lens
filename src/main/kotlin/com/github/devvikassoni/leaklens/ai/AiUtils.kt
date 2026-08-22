package com.github.devvikassoni.leaklens.ai

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.datatransfer.StringSelection

object AiUtils {
    fun askGemini(project: Project, leak: LeakInfo): String {
        val prompt = """
            # Android Memory Leak Analysis Request

            I have detected a memory leak using LeakCanary Shark. Please analyze the following details and provide a specific fix.

            ## Leak Details
            * **Leaking Class**: `${leak.retainedObjectClassName}`
            * **Description**: ${leak.shortDescription}
            * **Retained Size**: ${leak.retainedByteSize / 1024} KB

            ## Reference Chain
            ${
            leak.referenceChain.joinToString(
                "\n"
            ) { "↓ ${it.owningClassName}.${it.referenceName} (${it.referenceType})" }
        }

            ## Full Leak Trace
            ```
            ${leak.leakTrace}
            ```

            Please suggest the best way to fix this.
            **Requirements:**
            1. Provide an idiomatic Kotlin solution (e.g., using `weakReference`, `Lifecycle` observers, `repeatOnLifecycle`, or `autoCleared` delegates).
            2. Explain the root cause of the leak in 2 sentences.
            3. Show the "Before" (leaking) and "After" (fixed) code snippets.
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

        return prompt
    }
}
