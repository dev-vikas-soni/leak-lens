import android.app.Fragment;
import android.view.View;

public class ViewReferenceLeak extends Fragment {
    private View <warning descr="LeakLens: View/Binding field 'myView' is not nulled in onDestroyView(). When Fragment goes on back stack, the view is destroyed but the field retains it, causing a leak. Set myView = null in onDestroyView().">myView</warning>;

    public void onDestroyView() {
        super.onDestroyView();
        // TODO: should null out myView here
    }
}
