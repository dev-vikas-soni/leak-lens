package com.github.devvikassoni.leaklens.fix

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import org.junit.Assert.*
import org.junit.Test

class FixSuggestionEngineTest {

    private val engine = FixSuggestionEngine()

    private fun createLeakInfo(
        retainedClassName: String,
        referenceChain: List<LeakTraceReference>,
        leakTrace: String = ""
    ): LeakInfo {
        return LeakInfo(
            signature = "test_signature",
            shortDescription = "Test Leak",
            retainedObjectClassName = retainedClassName,
            retainedByteSize = 1024,
            retainedObjectCount = 1,
            leakTrace = leakTrace,
            severity = LeakSeverity.WARNING,
            isLibraryLeak = false,
            referenceChain = referenceChain,
            suggestedFix = null
        )
    }

    @Test
    fun testStaticFieldActivityRule() {
        val leak = createLeakInfo(
            retainedClassName = "com.example.MainActivity",
            referenceChain = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.SomeClass",
                    referenceName = "activity",
                    referenceType = "STATIC_FIELD"
                )
            )
        )
        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("Static Field Holding Activity/Fragment", suggestion?.ruleName)
    }

    @Test
    fun testAnonymousInnerClassRule() {
        val leak = createLeakInfo(
            retainedClassName = "com.example.MyFragment",
            referenceChain = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MyFragment$1",
                    referenceName = "this$0",
                    referenceType = "INSTANCE_FIELD"
                )
            )
        )
        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("Anonymous Inner Class Holding Activity", suggestion?.ruleName)
    }

    @Test
    fun testViewModelContextRule() {
        val leak = createLeakInfo(
            retainedClassName = "android.content.Context",
            referenceChain = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MyViewModel",
                    referenceName = "context",
                    referenceType = "INSTANCE_FIELD"
                )
            )
        )
        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("ViewModel Holding View/Context", suggestion?.ruleName)
    }

    @Test
    fun testNoMatchReturnsNull() {
        val leak = createLeakInfo(
            retainedClassName = "java.lang.String",
            referenceChain = listOf(
                LeakTraceReference(
                    owningClassName = "java.lang.Thread",
                    referenceName = "someVar",
                    referenceType = "LOCAL"
                )
            )
        )
        val suggestion = engine.suggest(leak)
        assertNull(suggestion)
    }
}
