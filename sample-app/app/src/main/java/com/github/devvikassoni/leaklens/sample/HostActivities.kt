package com.github.devvikassoni.leaklens.sample

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.fragment.app.Fragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.github.devvikassoni.leaklens.sample.scenarios.compose.LeakyComposeScreen
import com.github.devvikassoni.leaklens.sample.scenarios.flow.LeakyFlowFragment
import com.github.devvikassoni.leaklens.sample.scenarios.fragment.LeakyFragment

/**
 * Activity that hosts Fragment-based leak scenarios.
 */
class FragmentHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply {
            id = android.R.id.content
        }
        setContentView(container)

        if (savedInstanceState == null) {
            val scenario = intent.getStringExtra("EXTRA_SCENARIO")
            val fragment: Fragment = when (scenario) {
                "fragment_leak" -> LeakyFragment()
                "flow_leak" -> LeakyFlowFragment()
                else -> throw IllegalArgumentException("Unknown scenario: $scenario")
            }

            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit()
        }
    }
}

/**
 * Activity that hosts Compose-based leak scenarios.
 */
class ComposeHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    LeakyComposeScreen()
                }
            }
        }
    }
}

/**
 * Activity that hosts Singleton leak scenarios.
 */
class SingletonHostActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.github.devvikassoni.leaklens.sample.scenarios.singleton.AppManager.context = this
        android.widget.Toast.makeText(
            this,
            "Context leaked to AppManager!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        finish() // Exit immediately to trigger the leak
    }
}

/**
 * Activity that hosts Bitmap leak scenarios.
 */
class BitmapHostActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bitmap = android.graphics.Bitmap.createBitmap(
            1024,
            1024,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        com.github.devvikassoni.leaklens.sample.scenarios.bitmap.BitmapCache.bitmaps.add(bitmap)
        android.widget.Toast.makeText(
            this,
            "1MB Bitmap leaked to cache!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        finish()
    }
}

/**
 * Activity that hosts WorkManager leak scenarios.
 */
class WorkerHostActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val workRequest =
            androidx.work.OneTimeWorkRequestBuilder<com.github.devvikassoni.leaklens.sample.scenarios.workmanager.LeakyWorker>()
                .build()
        androidx.work.WorkManager.getInstance(this).enqueue(workRequest)
        android.widget.Toast.makeText(
            this,
            "Leaky Worker enqueued!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        finish()
    }
}
