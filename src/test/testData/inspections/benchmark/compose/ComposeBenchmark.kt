@file:Suppress(
    "UNUSED_VARIABLE",
    "UNUSED_PARAMETER",
    "UNUSED_ANONYMOUS_PARAMETER",
    "UNUSED_LAMBDA_EXPRESSION"
)

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel

@Composable
fun MyComposable(activity: Activity, context: Context) {
    // NEGATIVE: remember Activity is safe (Activity outlives Composition)
    val a = remember { activity }

    // NEGATIVE: remember Context is safe
    val c = remember { context }

    // NEGATIVE: remember applicationContext is safe
    val app = remember { context.applicationContext }

    // POSITIVE: Passing Activity to a Singleton from Compose
    remember {
        MySingleton.leaks(< error descr =
            "LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (SINGLETON -> ACTIVITY)" > activity < / error >)
    }
}

object MySingleton {
    fun leaks(context: Context) {
        // ...
    }
}

@Composable
fun ViewModelLeak(viewModel: MyViewModel, activity: Activity) {
    // POSITIVE: Passing Activity to ViewModel in Compose
    Button(onClick = {
        viewModel.init(< error descr =
            "LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (VIEWMODEL -> ACTIVITY)" > activity < / error >)
    })
}

class MyViewModel : ViewModel() {
    fun init(activity: Activity) {}
}

@Composable
fun Button(onClick: () -> Unit) {
}
