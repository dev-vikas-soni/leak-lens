import android.app.Activity;

public class StaticActivityLeak {
    public static Activity <warning descr="LeakLens: Static field 'mActivity' holds an Activity/Fragment reference. This causes a memory leak as static fields outlive the Activity lifecycle.">mActivity</warning>;
}
