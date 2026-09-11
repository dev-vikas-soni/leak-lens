import androidx.lifecycle.ViewModel
import android.content.Context
import android.app.Activity
import android.view.View

class MyLeakyViewModel : ViewModel() {
    // Bad: UI Context
    private var <error descr =
        "LeakLens: Storing Context in a ViewModel will cause a memory leak. (VIEWMODEL -> ACTIVITY)" > myContext < / error >: Context? = null
    val <error descr =
        "LeakLens: Storing Activity in a ViewModel will cause a memory leak. (VIEWMODEL -> ACTIVITY)" > myActivity < / error >: Activity? = null
    var <error descr =
        "LeakLens: Storing View in a ViewModel will cause a memory leak. (VIEWMODEL -> VIEW)" > myView < / error >: View? = null

    // Good: Application context (explicitly named or source)
    private var applicationContext: Context? = null
    private var mySafeString: String = "test"
}
