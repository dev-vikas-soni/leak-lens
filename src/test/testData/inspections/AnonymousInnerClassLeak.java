import android.app.Activity;
import android.os.Handler;

public class AnonymousInnerClassLeak extends Activity {
    public void runTask() {
        new Handler().postDelayed(<warning descr="LeakLens: Anonymous inner class holds an implicit reference to AnonymousInnerClassLeak. If this object outlives the Activity, it will prevent GC.">new Runnable() {
            public void run() {
                // no-op
            }
        }</warning>, 1000);
    }
}
