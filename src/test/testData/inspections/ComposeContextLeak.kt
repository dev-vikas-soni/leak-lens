import android.content.Context
import androidx.lifecycle.ViewModel

annotation class Composable

class MyViewModel : ViewModel() {
    @Suppress("UNUSED_PARAMETER")
    fun setContext(context: Context) {
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel, context: Context) {
    // Bad: Passing context to ViewModel inside Composable
    viewModel.setContext(<error descr="LeakLens: Passing Context/Activity to a ViewModel in Compose causes leaks. Use LocalContext only for UI operations.">context</error>)

    // Bad: Capturing context in remember
    remember {
        MyHelper(<error descr="LeakLens: Context captured in remember { } can outlive Activity. Use rememberUpdatedState or pass Context as a key.">context</error>)
    }
}

class MyHelper(val context: Context)

@Suppress("UNUSED_PARAMETER")
fun <T> remember(calculation: () -> T): T = calculation()
