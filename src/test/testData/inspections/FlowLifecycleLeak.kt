import android.app.Activity
import android.content.Context

interface LifecycleOwner
interface Lifecycle
enum class State { STARTED, RESUMED }

fun LifecycleOwner.repeatOnLifecycle(state: State, block: suspend () -> Unit) {
    println(state)
    println(block)
}

interface Flow<T> {
    suspend fun collect(collector: (T) -> Unit)
}

fun <T> Flow<T>.flowWithLifecycle(
    @Suppress("UNUSED_PARAMETER") lifecycle: Lifecycle,
    @Suppress("UNUSED_PARAMETER") state: State
): Flow<T> = this

fun <T> Flow<T>.collectAsState(@Suppress("UNUSED_PARAMETER") initial: T): T = initial
fun <T> Flow<T>.collectAsStateWithLifecycle(@Suppress("UNUSED_PARAMETER") initial: T): T = initial

class FlowLifecycleLeak : Activity(), LifecycleOwner {
    val myFlow: Flow<String> = object : Flow<String> {
        override suspend fun collect(collector: (String) -> Unit) {}
    }
    val lifecycle: Lifecycle = object : Lifecycle {}

    suspend fun setup() {
        // Bad: unsafe collect
        myFlow.< error descr =
            "LeakLens: Unsafe Flow collection. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks. (ACTIVITY -> LOCAL)" > collect < / error > { }

        // Good: repeatOnLifecycle
        repeatOnLifecycle(State.STARTED) {
            myFlow.collect { }
        }

        // Good: flowWithLifecycle
        myFlow.flowWithLifecycle(lifecycle, State.STARTED).collect { }
    }
}

annotation class Composable

@Composable
fun MyComposable(myFlow: Flow<String>) {
    // Bad: unsafe collectAsState
    val state = myFlow.< error descr =
        "LeakLens: Unsafe use of collectAsState(). Use collectAsStateWithLifecycle() for better memory management in Compose. (COMPOSITION -> LOCAL)" > collectAsState < / error >(
            ""
        )
    println(state)

    // Good: collectAsStateWithLifecycle
    val safeState = myFlow.collectAsStateWithLifecycle("")
    println(safeState)
}

fun println(@Suppress("UNUSED_PARAMETER") any: Any) {}
