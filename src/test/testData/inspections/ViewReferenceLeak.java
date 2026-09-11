import android.app.Fragment;
import android.view.View;

public class ViewReferenceLeak extends Fragment {
    // Bad: Not nulled in onDestroyView
    private View <error descr="LeakLens: View field 'myView' is not nulled in onDestroyView().">myView</error>;

    // Good: Nulled in onDestroyView
    private View mySafeView;

    public void onDestroyView() {
        super.onDestroyView();
        mySafeView = null;
    }
}
