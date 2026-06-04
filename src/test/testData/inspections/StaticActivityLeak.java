import android.app.Activity;

public class StaticActivityLeak {
    public static Activity <warning descr="LeakLens: Static field 'mActivity' holds Activity/Fragment reference. This will cause a memory leak.">mActivity</warning>;
}
