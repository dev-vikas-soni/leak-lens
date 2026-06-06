import android.app.Fragment;
import android.view.View;

public class ViewReferenceLeak extends Fragment {
    private View <warning descr="LeakLens: View field 'myView' is not nulled in onDestroyView().">myView</warning>;

    public void onDestroyView() {
        super.onDestroyView();
        // TODO: should null out myView here
    }
}
