import android.app.Activity;
import android.os.Handler;

public class MissingRemoveCallbacksLeak extends Activity {
    private Handler <warning descr="LeakLens: Handler 'handler' may cause a leak. Call removeCallbacks in onDestroy.">handler</warning> = new Handler();

    public void doWork() {
        handler.postDelayed(null, 1000);
    }

    protected void onDestroy() {
        super.onDestroy();
    }
}
