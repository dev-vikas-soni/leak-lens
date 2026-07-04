// Mock lifecycle functions
@Suppress("UNUSED_PARAMETER")
fun launchWhenStarted(block: suspend () -> Unit) {
}

@Suppress("UNUSED_PARAMETER")
fun launchWhenResumed(block: suspend () -> Unit) {
}

class MyActivity {
    fun setupObservers() {
        // This should be flagged
        <warning descr="LeakLens: launchWhenStarted is deprecated and can cause memory/resource leaks in the background. Use repeatOnLifecycle instead.">launchWhenStarted</warning> {
            // flow.collect()
        }

        <warning descr="LeakLens: launchWhenResumed is deprecated and can cause memory/resource leaks in the background. Use repeatOnLifecycle instead.">launchWhenResumed</warning> {
            // flow.collect()
        }
    }
}
