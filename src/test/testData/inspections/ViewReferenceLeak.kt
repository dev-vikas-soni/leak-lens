import android.app.Fragment
import android.view.View

class ViewReferenceLeakKt : Fragment() {
    // Bad: Not nulled in onDestroyView
    private var <error descr =
        "LeakLens: View field 'myView' is not nulled in onDestroyView()." > myView < / error >: View? = null

    // Good: Nulled in onDestroyView
    private var mySafeView: View? = null

    override fun onDestroyView() {
        super.onDestroyView()
        mySafeView = null
    }
}
