package com.github.devvikassoni.leaklens.inspections

import org.junit.Test

/** GlobalScope, launchWhenX and Flow collection. */
class CoroutineInspectionsTest : LeakInspectionTestBase() {

    @Test
    fun testGlobalScope() = assertFlagged(
        GlobalScopeWithContextInspection(),
        "Main.kt",
        """
        import android.app.Activity
        import androidx.lifecycle.ViewModel
        import kotlinx.coroutines.GlobalScope
        import kotlinx.coroutines.launch
        class Main : Activity() { fun start() { GlobalScope.launch { } } }
        class Vm : ViewModel() { fun start() { GlobalScope.launch { } } }
        class Plain { fun start() { GlobalScope.launch { } } }
        """,
        "launch",
        "launch",
    )

    @Test
    fun testGlobalScopeFix() {
        val result = applyFix(
            GlobalScopeWithContextInspection(),
            "Main.kt",
            """
            import android.app.Activity
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.launch
            class Main : Activity() { fun start() { Global<caret>Scope.launch { } } }
            """,
            "Replace GlobalScope with lifecycleScope",
        )
        assertContainsAll(result, "import androidx.lifecycle.lifecycleScope", "lifecycleScope.launch { }")
    }

    @Test
    fun testDeprecatedLaunchWhen() = assertFlagged(
        DeprecatedLifecycleScopeInspection(),
        "Main.kt",
        """
        import androidx.fragment.app.Fragment
        import androidx.lifecycle.lifecycleScope
        class Screen : Fragment() {
            fun start() {
                lifecycleScope.launchWhenStarted { }
                viewLifecycleOwner.lifecycleScope.launchWhenResumed { }
            }
        }
        class Custom { fun launchWhenStarted(block: () -> Unit) {} }
        fun other(c: Custom) { c.launchWhenStarted { } }
        """,
        "launchWhenResumed",
        "launchWhenStarted",
    )

    @Test
    fun testDeprecatedLaunchWhenFix() {
        val result = applyFix(
            DeprecatedLifecycleScopeInspection(),
            "Main.kt",
            """
            import androidx.fragment.app.Fragment
            import androidx.lifecycle.lifecycleScope
            class Screen : Fragment() {
                fun start() {
                    viewLifecycleOwner.lifecycleScope.launchWhen<caret>Started { println() }
                }
            }
            """,
            "Migrate to launch",
        )
        assertContainsAll(
            result,
            "import androidx.lifecycle.repeatOnLifecycle",
            "viewLifecycleOwner.lifecycleScope.launch {",
            "viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {",
            "println()",
        )
    }

    @Test
    fun testFlowCollection() = assertFlagged(
        FlowLifecycleInspection(),
        "Main.kt",
        """
        import androidx.fragment.app.Fragment
        import androidx.lifecycle.Lifecycle
        import androidx.lifecycle.lifecycleScope
        import androidx.lifecycle.repeatOnLifecycle
        import androidx.lifecycle.flowWithLifecycle
        import kotlinx.coroutines.flow.Flow
        import kotlinx.coroutines.flow.collect
        import kotlinx.coroutines.flow.launchIn
        import kotlinx.coroutines.launch
        class Screen(private val data: Flow<Int>) : Fragment() {
            fun unsafe() {
                lifecycleScope.launch { data.collect { } }
                data.launchIn(lifecycleScope)
            }
            fun safe() {
                lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { data.collect { } } }
                lifecycleScope.launch { data.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { } }
            }
        }
        """,
        "collect",
        "launchIn",
    )

    @Test
    fun testCollectAsStateInComposable() = assertFlagged(
        FlowLifecycleInspection(),
        "Ui.kt",
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.collectAsState
        import kotlinx.coroutines.flow.Flow
        @Composable fun Screen(data: Flow<Int>) { val s = data.collectAsState(0) }
        fun notCompose(data: Flow<Int>) { }
        """,
        "collectAsState",
    )

    @Test
    fun testWrapWithRepeatOnLifecycleFix() {
        val result = applyFix(
            FlowLifecycleInspection(),
            "Main.kt",
            """
            import androidx.fragment.app.Fragment
            import androidx.lifecycle.lifecycleScope
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.collect
            import kotlinx.coroutines.launch
            class Screen(private val data: Flow<Int>) : Fragment() {
                fun start() { lifecycleScope.launch { data.col<caret>lect { } } }
            }
            """,
            "Wrap with repeatOnLifecycle",
        )
        assertContainsAll(
            result,
            "import androidx.lifecycle.repeatOnLifecycle",
            "repeatOnLifecycle(Lifecycle.State.STARTED) {"
        )
    }
}
