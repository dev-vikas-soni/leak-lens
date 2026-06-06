import android.app.Activity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GlobalScopeLeak : Activity() {
    fun doSomething() {
        GlobalScope.< warning descr =
            "LeakLens: GlobalScope.launch may cause a memory leak. Use lifecycleScope." > launch < / warning > {
            // no-op
        }
    }
}
