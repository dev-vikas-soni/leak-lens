import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

class MyWorkerJava extends Worker {
    public MyWorkerJava(Context context, WorkerParameters params) {
        super(context, params);
    }

    // Bad: Storing raw Context
    private Context <warning descr="LeakLens: WorkManager Worker stores a Context in a field. Use applicationContext instead to avoid leaks.">mContext</warning>;

    @Override
    public Result doWork() {
        return Result.success();
    }
}
