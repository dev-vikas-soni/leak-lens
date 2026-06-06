import android.content.Context;
import android.app.Activity;

public class ContextSingletonLeak {
    private static ContextSingletonLeak instance;
    private Context context;

    public static void init(Context context) {
        if (instance == null) {
            instance = new ContextSingletonLeak();
            instance.context = context;
        }
    }
}

class MyActivity extends Activity {
    public void start() {
        ContextSingletonLeak.init(<warning descr="LeakLens: Passing Activity Context to a Singleton will cause a memory leak.">this</warning>);
    }
}
