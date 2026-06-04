import android.app.Fragment
import android.view.View

class ViewReferenceLeak : Fragment() {
    var <warning descr="LeakLens: View/Binding field 'myView' is not nulled in onDestroyView(). When Fragment goes on back stack, the view is destroyed but the field retains it, causing a leak. Set myView = null in onDestroyView().">myView</warning>: View? = null

    override fun onDestroyView() {
        super.onDestroyView()
        // Missing myView = null
    }
}
