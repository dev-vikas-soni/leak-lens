import android.app.Activity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface CoroutineScope

val Activity.lifecycleScope: CoroutineScope get() = object : CoroutineScope {}
fun CoroutineScope.launch(block: suspend () -> Unit) {
    // mock usage to avoid unused parameter warning
    println(block)
}

class GlobalScopeLeak : Activity() {
    fun doSomething() {
        // Bad: GlobalScope captures 'this'
        GlobalScope.launch {
            println(< error descr =
                "LeakLens: GlobalScope is used to launch a coroutine that captures an Activity or Context. GlobalScope lives as long as the application. (PROCESS -> ACTIVITY)" > this@GlobalScopeLeak < / error >)
        }

        // Good: lifecycleScope
        lifecycleScope.launch {
            println(this@GlobalScopeLeak)
        }
    }
}

fun println(@Suppress("UNUSED_PARAMETER") any: Any) {}
