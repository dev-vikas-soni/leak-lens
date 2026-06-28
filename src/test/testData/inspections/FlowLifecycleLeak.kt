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

class FlowLifecycleLeak : Activity() {

    fun observeFlows(myFlow: Flow<String>) {
        // Bad: Collecting directly in launch
        GlobalScope.launch {
            myFlow.< error descr =
                "LeakLens: Unsafe collection of Flow in UI. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks." > collect < / error > { value ->
                    println(value)
                }
        }

        // Good: Using repeatOnLifecycle
        GlobalScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                myFlow.collect { value ->
                    println(value)
                }
            }
        }

        // Good: Using flowWithLifecycle
        GlobalScope.launch {
            myFlow.flowWithLifecycle(null, Lifecycle.State.STARTED).collect {
                println(it)
            }
        }
    }
}
