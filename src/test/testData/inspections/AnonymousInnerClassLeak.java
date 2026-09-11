import android.app.Activity;
import android.os.Handler;

class AnonymousInnerClassLeak extends Activity {
    void runTask() {
        // Bad: Anonymous inner class captures outer activity
        new Handler().postDelayed(<warning descr="LeakLens: Anonymous inner class holds an implicit reference to AnonymousInnerClassLeak. If this object outlives the Activity, it will prevent GC.">new Runnable() {
            public void run() {
                // no-op
            }
        }</warning>, 1000);
    }

    // Good: Static inner class does not capture outer
    static class MySafeRunnable implements Runnable {
        public void run() {}
    }

    void runSafeTask() {
        new Handler().post(new MySafeRunnable());
    }
}
