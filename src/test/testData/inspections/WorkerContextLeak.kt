import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class MyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    // Bad: Storing raw Context in a field
    private var <warning descr =
        "LeakLens: WorkManager Worker stores a Context in a field. Use applicationContext instead to avoid leaks." > mContext < / warning >: Context? = null

    // Good: Using applicationContext (inherited) or correctly named safe variable
    private val mySafeString = "safe"

    override fun doWork(): Result {
        return Result.success()
    }
}
