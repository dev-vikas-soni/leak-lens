import android.content.Context
import javax.inject.Singleton

@Singleton
class MySingleton {
    var context: Context? = null

    companion object {
        val INSTANCE = MySingleton()
    }
}

class MyActivity : android.app.Activity() {
    fun start() {
        // Bad: Passing 'this' to singleton
        MySingleton.INSTANCE.context = < error descr =
            "LeakLens: Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared. (SINGLETON -> ACTIVITY)" > this < / error >

        // Good: application context
        MySingleton.INSTANCE.context = applicationContext
    }
}
