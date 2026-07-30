package com.github.devvikassoni.leaklens.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LeakDashboard()
                }
            }
        }
    }
}

data class Scenario(val name: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeakDashboard() {
    val scenarios = listOf(
        Scenario("Activity Leak", "Static singleton holding Activity listener"),
        Scenario("Compose Leak", "remember capturing LocalContext"),
        Scenario("Flow Leak", "Unsafe collection in Fragment"),
        Scenario("Fragment Leak", "View retained after onDestroyView"),
        Scenario("Singleton Leak", "Activity context stored in AppManager"),
        Scenario("Worker Leak", "Context stored in Worker field"),
        Scenario("Bitmap Leak", "High-res bitmaps in static list")
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("LeakLens Living Test Suite") }) }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(scenarios) { scenario ->
                ScenarioCard(scenario)
            }
        }
    }
}

@Composable
fun ScenarioCard(scenario: Scenario) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(scenario.name, style = MaterialTheme.typography.titleLarge)
            Text(scenario.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                when (scenario.name) {
                    "Activity Leak" -> {
                        val intent = android.content.Intent(
                            context,
                            com.github.devvikassoni.leaklens.sample.scenarios.activity.LeakyActivity::class.java
                        )
                        context.startActivity(intent)
                    }

                    "Singleton Leak" -> {
                        com.github.devvikassoni.leaklens.sample.scenarios.singleton.AppManager.context =
                            context
                        android.widget.Toast.makeText(
                            context,
                            "Context leaked to AppManager!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    "Bitmap Leak" -> {
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            1024,
                            1024,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        com.github.devvikassoni.leaklens.sample.scenarios.bitmap.BitmapCache.bitmaps.add(
                            bitmap
                        )
                        android.widget.Toast.makeText(
                            context,
                            "1MB Bitmap leaked to cache!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        android.widget.Toast.makeText(
                            context,
                            "Open this screen to trigger ${scenario.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }) {
                Text("Trigger Scenario")
            }
        }
    }
}
