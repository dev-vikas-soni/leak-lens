package com.github.devvikassoni.leaklens.ai

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.datatransfer.StringSelection

object AiUtils {
    fun askGemini(project: Project, leak: LeakInfo): String {
        val prompt =
            if (leak.signature.startsWith("static_scan") || leak.signature.contains("leak")) {
                buildStaticAnalysisPrompt(leak)
            } else {
                buildHeapAnalysisPrompt(leak)
            }

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

    private fun buildHeapAnalysisPrompt(leak: LeakInfo): String {
        return """
            # Android Memory Leak Analysis Request (Runtime Heap Leak)

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
            1. Provide an idiomatic Kotlin solution.
            2. Explain the root cause of the leak in 2 sentences.
            3. Show the "Before" (leaking) and "After" (fixed) code snippets.
        """.trimIndent()
    }

    private fun buildStaticAnalysisPrompt(leak: LeakInfo): String {
        val semanticContext = if (leak.ownerLifetime != null && leak.referencedLifetime != null) {
            """
            ## Semantic Evidence
            * **Owner Lifetime**: ${leak.ownerLifetime}
            * **Referenced Lifetime**: ${leak.referencedLifetime}
            * **Evidence**: ${leak.evidence ?: "N/A"}
            * **Risk Explanation**: ${leak.riskExplanation ?: "N/A"}
            """.trimIndent()
        } else ""

        return """
            # Android Memory Leak Analysis Request (Static Analysis Finding)

            LeakLens has detected a potential memory retention hazard during static analysis.

            ## Finding Details
            * **Rule**: `${leak.shortDescription}`
            * **Location**: `${leak.retainedObjectClassName}`
            * **Description**: ${leak.leakTrace}

            $semanticContext

            ## Detected Hazard
            The pattern identified suggests that an object (e.g., Activity, Context, or View) may be retained longer than its intended lifecycle, preventing garbage collection.

            Please suggest the best way to fix this.
            **Requirements:**
            1. Provide an idiomatic Kotlin solution (e.g., using `WeakReference`, `Lifecycle` observers, `repeatOnLifecycle`, or `rememberUpdatedState`).
            2. Explain WHY this pattern is dangerous in Android.
            3. Show the "Before" (leaking) and "After" (fixed) code snippets.
            4. If the evidence shows a specific lifetime mismatch (e.g. SINGLETON -> ACTIVITY), prioritize fixes that respect these lifecycles (e.g. using applicationContext).
        """.trimIndent()
    }
}
