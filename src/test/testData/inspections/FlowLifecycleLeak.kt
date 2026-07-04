package testData.inspections

import android.app.Activity

// Mock classes
object Lifecycle {
    enum class State { STARTED }
}

interface CoroutineScope
object GlobalScope : CoroutineScope

@Suppress("UNUSED_PARAMETER")
fun CoroutineScope.launch(block: suspend CoroutineScope.() -> Unit) {
}

interface Flow<out T> {
    suspend fun collect(action: suspend (value: T) -> Unit)
}

val lifecycleScope = GlobalScope

@Suppress("UNUSED_PARAMETER")
fun <T> Flow<T>.flowWithLifecycle(lifecycle: Any?, minActiveState: Any = ""): Flow<T> = this

@Suppress("UNUSED_PARAMETER")
fun Activity.repeatOnLifecycle(state: Any, block: suspend () -> Unit) {
}

@Suppress("UNUSED_PARAMETER")
fun println(message: Any?) {
}

annotation class Composable

@Suppress("UNUSED_PARAMETER")
fun <T> Flow<T>.collectAsState(initial: T): Any = this

@Suppress("UNUSED_PARAMETER")
fun <T> Flow<T>.collectAsStateWithLifecycle(initial: T): Any = this

@Suppress("UNUSED_PARAMETER")
fun <T> Flow<T>.launchIn(scope: CoroutineScope): Any = this

class FlowLifecycleLeak : Activity() {

    fun observeFlows(myFlow: Flow<String>) {
        // Bad: Collecting directly in launch
        GlobalScope.launch {
            myFlow.<error descr="LeakLens: Unsafe Flow collection. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks.">collect</error> { value ->
                println(value)
            }
        }

        // Bad: launchIn without lifecycle awareness
        myFlow.<error descr="LeakLens: Unsafe Flow collection. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks.">launchIn</error>(
            GlobalScope
        )

        // Good: Using repeatOnLifecycle
        GlobalScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                myFlow.collect { value ->
                    println(value)
                }
            }
        }
    }

    @Composable
    fun MyComposable(myFlow: Flow<String>) {
        // Bad: collectAsState in Compose
        myFlow.<error descr="LeakLens: Unsafe use of collectAsState(). Use collectAsStateWithLifecycle() for better memory management in Compose.">collectAsState</error>(
            ""
        )

        // Good: collectAsStateWithLifecycle
        myFlow.collectAsStateWithLifecycle("")
    }
}
