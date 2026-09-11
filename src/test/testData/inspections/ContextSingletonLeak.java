import android.content.Context;
import javax.inject.Singleton;

@Singleton
class ContextSingletonLeak {
    private static ContextSingletonLeak instance;
    private Context context;

    public static ContextSingletonLeak init(Context context) {
        if (instance == null) {
            instance = new ContextSingletonLeak();
            instance.context = <error descr="LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (SINGLETON -> ACTIVITY)">context</error>;
        }
        return instance;
    }

    public static void safeInit(Context context) {
        // Good: Storing application context (ideally we check variable name or source)
        // For now, if we pass getApplicationContext() it should be safe.
    }
}

class MyActivity extends android.app.Activity {
    void start() {
        ContextSingletonLeak.init(<error descr="LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (SINGLETON -> ACTIVITY)">this</error>);

        // Good: application context
        ContextSingletonLeak.init(getApplicationContext());
    }
}
