import android.app.Activity
import android.os.Handler

class AnonymousInnerClassLeakKt : Activity() {
    fun runTask() {
        // Bad: Object literal captures outer activity
        Handler().postDelayed(< warning descr =
            "LeakLens: Anonymous inner class holds an implicit reference to AnonymousInnerClassLeakKt. If this object outlives the Activity, it will prevent GC." > object :
                Runnable {
                override fun run() {
                    // no-op
                }
            } < / warning >, 1000)
    }

    // Good: Nested class (static by default)
    class MySafeRunnable : Runnable {
        override fun run() {}
    }

    fun runSafeTask() {
        Handler().post(MySafeRunnable())
    }
}
