import android.app.Activity
import android.os.Handler

class MissingRemoveCallbacksLeakKt : Activity() {
    // Bad: Handler field without cleanup
    private val <warning descr =
        "LeakLens: Handler 'mHandler' may cause a leak. Call removeCallbacks in onDestroy()." > mHandler < / warning > =
            Handler()

    // Good: Handled in onDestroy
    private val mSafeHandler = Handler()

    override fun onDestroy() {
        super.onDestroy()
        mSafeHandler.removeCallbacksAndMessages(null)
    }
}
