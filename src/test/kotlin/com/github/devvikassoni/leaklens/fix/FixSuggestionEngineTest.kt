package com.github.devvikassoni.leaklens.fix

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import org.junit.Assert.*
import org.junit.Test

class FixSuggestionEngineTest {

    private val engine = FixSuggestionEngine()

    @Test
    fun `test StaticFieldActivityRule matches static activity reference`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MainActivity",
                    referenceName = "instance",
                    referenceType = "STATIC_FIELD"
                )
            )
        )

        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("Static Field Holding Activity/Fragment", suggestion?.ruleName)
        assertTrue(suggestion?.explanation?.contains("static/companion object field holds a reference") == true)
    }

    @Test
    fun `test AnonymousInnerClassRule matches anonymous class leak`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MainActivity$1",
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
    fun `test HandlerActivityRule matches handler leak`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "android.os.Handler",
                    referenceName = "mCallback",
                    referenceType = "INSTANCE_FIELD"
                )
            )
        )

        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("Handler Holding Activity Reference", suggestion?.ruleName)
    }

    @Test
    fun `test ViewModelContextRule matches ViewModel leaking Activity`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
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
    fun `test ViewReferenceRule matches Fragment leaking View`() {
        val leak = createLeak(
            retainedClass = "android.widget.TextView",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MyFragment",
                    referenceName = "mTextView",
                    referenceType = "INSTANCE_FIELD"
                )
            )
        )

        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("View Reference Held Beyond Lifecycle", suggestion?.ruleName)
    }

    @Test
    fun `test InputMethodManagerRule matches framework leak`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "android.view.inputmethod.InputMethodManager",
                    referenceName = "mCurRootView",
                    referenceType = "INSTANCE_FIELD"
                )
            )
        )

        val suggestion = engine.suggest(leak)
        assertNotNull(suggestion)
        assertEquals("InputMethodManager Framework Leak", suggestion?.ruleName)
    }

    @Test
    fun `test enrichWithFixes attaches suggestion to LeakInfo`() {
        val leak = createLeak(
            retainedClass = "com.example.MainActivity",
            references = listOf(
                LeakTraceReference(
                    owningClassName = "com.example.MainActivity",
                    referenceName = "instance",
                    referenceType = "STATIC_FIELD"
                )
            )
        )

        val enriched = engine.enrichWithFixes(listOf(leak))
        assertNotNull(enriched[0].suggestedFix)
        assertTrue(enriched[0].suggestedFix?.contains("Static Field Holding Activity/Fragment") == true)
        assertTrue(enriched[0].suggestedFix?.startsWith("Fix Suggestion:") == true)
    }

    private fun createLeak(
        retainedClass: String,
        references: List<LeakTraceReference>
    ): LeakInfo {
        return LeakInfo(
            signature = "sig",
            shortDescription = "desc",
            leakTrace = "trace",
            retainedObjectClassName = retainedClass,
            retainedByteSize = 1024L,
            retainedObjectCount = 1,
            severity = LeakSeverity.CRITICAL,
            referenceChain = references
        )
    }
}
