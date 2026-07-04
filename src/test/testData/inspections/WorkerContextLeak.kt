import android.content.Context
import androidx.work.Worker

class MyWorker(
    // Bad: storing Context as a property using 'val'
    val <warning descr="LeakLens: 'context' stores a raw Context in a Worker. Use applicationContext (inherited from ListenableWorker) instead to avoid leaks.">context</warning>: Context
) : Worker() {

    // Bad: explicit Context field
    private var <warning descr="LeakLens: 'myContext' stores a raw Context in a Worker. Use applicationContext (inherited from ListenableWorker) instead to avoid leaks.">myContext</warning>: Context? = null
}
