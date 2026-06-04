import android.app.Activity;
import android.os.Handler;

public class AnonymousInnerClassLeak extends Activity {
    public void runTask() {
        new Handler().postDelayed(<warning descr="LeakLens: Anonymous inner class holds implicit reference to AnonymousInnerClassLeak. If this object outlives the Activity/Fragment, it will cause a memory leak. Consider using a static inner class with WeakReference.">new Runnable() {
            public void run() {
                // no-op
            }
        }</warning>, 1000);
    }
}
