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
    viewModel.setContext(< error descr =
        "LeakLens: Passing Context or Activity to a ViewModel from a @Composable can cause memory leaks." > context < / error >)
}
