import android.content.Context
import javax.inject.Singleton

@Singleton
class MySingleton {
    var context: Context? = null
}

class MyActivity : android.app.Activity() {
    fun leaks() {
        val s = MySingleton()
        // POSITIVE: Activity context passed to Singleton
        s.context = < error descr =
            "LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (SINGLETON -> ACTIVITY)" > this < / error >
    }

    fun safe() {
        val s = MySingleton()
        // NEGATIVE: Application context is safe
        s.context = applicationContext
    }

    fun safeGet() {
        val s = MySingleton()
        // NEGATIVE: getApplicationContext() is safe
        s.context = getApplicationContext()
    }
}
