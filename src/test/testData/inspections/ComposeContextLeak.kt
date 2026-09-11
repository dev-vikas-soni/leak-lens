import android.content.Context
import androidx.lifecycle.ViewModel

annotation class Composable

class MyViewModel : ViewModel() {
    fun setContext(@Suppress("UNUSED_PARAMETER") context: Context) {
    }
}

val Context.applicationContext: Context get() = this

@Composable
fun MyScreen(viewModel: MyViewModel, context: Context) {
    // Bad: Passing context to ViewModel inside Composable
    viewModel.setContext(< error descr =
        "LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (VIEWMODEL -> ACTIVITY)" > context < / error >)

    // Safe: Activity outlives Composition
    remember {
        MyHelper(context)
    }

    // Good: application context
    remember {
        MyHelper(context.applicationContext)
    }
}

class MyHelper(@Suppress("UNUSED_PARAMETER") val context: Context)

fun <T> remember(calculation: () -> T): T = calculation()
fun LaunchedEffect(@Suppress("UNUSED_PARAMETER") key: Any, block: suspend () -> Unit) {
    // avoid unused parameter
    println(block)
}

@Composable
fun EffectTest(context: Context) {
    // Safe: capture in LaunchedEffect (cancelled when disposed)
    LaunchedEffect(Unit) {
        println(context)
    }
}

fun println(@Suppress("UNUSED_PARAMETER") any: Any) {}
