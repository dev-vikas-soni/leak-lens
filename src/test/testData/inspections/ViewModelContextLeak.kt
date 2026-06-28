import androidx.lifecycle.ViewModel
import android.content.Context
import android.app.Activity
import android.view.View

class MyLeakyViewModel : ViewModel() {
    // This should be flagged
    private var <warning descr =
        "LeakLens: Storing Context in a ViewModel will cause a memory leak." > myContext < / warning >: Context? = null

    // This should also be flagged
    val <warning descr =
        "LeakLens: Storing Activity in a ViewModel will cause a memory leak." > myActivity < / warning >: Activity? = null

    // View should be flagged
    var <warning descr =
        "LeakLens: Storing View in a ViewModel will cause a memory leak." > myView < / warning >: View? = null

    // This is fine
    var myString: String = "test"
}
