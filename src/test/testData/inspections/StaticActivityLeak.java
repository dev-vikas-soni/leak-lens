import android.app.Activity;
import android.content.Context;

class StaticActivityLeak {
    // Bad: Static field holds Activity
    private static Activity <error descr="LeakLens: Static field 'mActivity' holds an Activity/Fragment reference. This causes a memory leak as static fields outlive the Activity lifecycle.">mActivity</error>;

    // Good: Instance field
    private Activity mInstanceActivity;

    // Good: Static non-UI type
    private static String mAppVersion = "1.0";

    // Good: Static context that is explicitly named applicationContext (we assume safe for now)
    private static Context applicationContext;
}
