import android.app.Activity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GlobalScopeLeak : Activity() {
    fun doSomething() {
        GlobalScope.<warning descr="LeakLens: GlobalScope.launch in an Activity/Fragment may cause a memory leak. Use lifecycleScope or viewModelScope instead, which auto-cancel on lifecycle end.">launch</warning> {
            // no-op
        }
    }
}
