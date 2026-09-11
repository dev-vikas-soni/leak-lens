package testData.inspections

import android.app.Activity

interface CoroutineScope
object GlobalScope : CoroutineScope

@Suppress("UNUSED_PARAMETER")
fun CoroutineScope.launchWhenStarted(block: suspend () -> Unit) {
}
@Suppress("UNUSED_PARAMETER")
fun CoroutineScope.launchWhenResumed(block: suspend () -> Unit) {
}

val lifecycleScope = GlobalScope

class DeprecatedLifecycleScopeLeak : Activity() {

    fun setupObservers() {
        // Bad: launchWhenStarted
        lifecycleScope.< warning descr =
            "LeakLens: launchWhenStarted, launchWhenResumed, etc. are deprecated and can lead to resource leaks. Use repeatOnLifecycle instead. (ACTIVITY -> ACTIVITY)" > launchWhenStarted < / warning > {
                // no-op
        }

        // Bad: launchWhenResumed
        lifecycleScope.< warning descr =
            "LeakLens: launchWhenStarted, launchWhenResumed, etc. are deprecated and can lead to resource leaks. Use repeatOnLifecycle instead. (ACTIVITY -> ACTIVITY)" > launchWhenResumed < / warning > {
                // no-op
        }
    }
}
