import androidx.lifecycle.ViewModel;
import android.content.Context;
import android.app.Activity;
import android.view.View;

class MyLeakyViewModelJava extends ViewModel {
    // Bad: UI Context
    private Context <error descr="LeakLens: Storing Context in a ViewModel will cause a memory leak. (VIEWMODEL -> ACTIVITY)">myContext</error>;
    private Activity <error descr="LeakLens: Storing Activity in a ViewModel will cause a memory leak. (VIEWMODEL -> ACTIVITY)">myActivity</error>;
    private View <error descr="LeakLens: Storing View in a ViewModel will cause a memory leak. (VIEWMODEL -> VIEW)">myView</error>;

    // Good: Application context
    private Context applicationContext;
    private String mySafeString = "test";
}
