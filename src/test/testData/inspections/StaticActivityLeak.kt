import android.app.Activity
import android.content.Context

class StaticActivityLeakKt {
    companion object {
        // Bad: Static field in companion holds Activity
        var <error descr =
            "LeakLens: Static field 'mActivity' holds an Activity/Fragment reference. This causes a memory leak as static fields outlive the Activity lifecycle." > mActivity < / error >: Activity? = null

        // Good: Static non-UI type
        var mAppVersion: String = "1.0"
    }

    // Good: Instance field
    var mInstanceActivity: Activity? = null
}
