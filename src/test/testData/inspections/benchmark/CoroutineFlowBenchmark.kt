import android.app.Activity
import androidx.lifecycle.*
import kotlinx.coroutines.*

class MyActivity : Activity(), LifecycleOwner {
    override val lifecycle: Lifecycle get() = java.lang.Object() as Lifecycle

    fun leaks(flow: Flow<Int>) {
        // POSITIVE: GlobalScope capturing Activity
        GlobalScope.launch {
            consume(< error descr =
                "LeakLens: GlobalScope is used to launch a coroutine that captures an Activity or Context. GlobalScope lives as long as the application. (PROCESS -> ACTIVITY)" > this@MyActivity < / error >)
        }

        // POSITIVE: Unsafe Flow collection in Activity
        lifecycleScope.launch {
            flow.< error descr =
                "LeakLens: Unsafe Flow collection. Use repeatOnLifecycle or flowWithLifecycle to prevent background leaks. (ACTIVITY -> LOCAL)" > collect < / error > { }
        }
    }

    fun safe(flow: Flow<Int>) {
        // NEGATIVE: repeatOnLifecycle is safe
        lifecycleScope.launch {
            repeatOnLifecycle(State.STARTED) {
                flow.collect { }
            }
        }

        // NEGATIVE: capture in lifecycleScope is safe
        lifecycleScope.launch {
            consume(this@MyActivity)
        }
    }

    private fun consume(obj: Any) {
        obj.hashCode()
    }
}
