import androidx.lifecycle.ViewModel
import android.content.Context
import android.app.Activity
import android.view.View

class MyLeakyViewModel : ViewModel() {
    private var <warning descr="LeakLens: Storing Context in a ViewModel will cause a memory leak.">myContext</warning>: Context? = null
    val <warning descr="LeakLens: Storing Activity in a ViewModel will cause a memory leak.">myActivity</warning>: Activity? = null
    var <warning descr="LeakLens: Storing View in a ViewModel will cause a memory leak.">myView</warning>: View? = null
    var myString: String = "test"
}
