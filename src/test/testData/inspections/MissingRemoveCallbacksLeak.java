import android.app.Activity;
import android.os.Handler;

class MissingRemoveCallbacksLeak extends Activity {
    // Bad: Handler field without cleanup
    private Handler <warning descr="LeakLens: Handler 'mHandler' may cause a leak. Call removeCallbacks in onDestroy().">mHandler</warning> = new Handler();

    // Good: Handled in onDestroy
    private Handler mSafeHandler = new Handler();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSafeHandler.removeCallbacksAndMessages(null);
    }
}
