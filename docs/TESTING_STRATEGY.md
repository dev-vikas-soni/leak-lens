# LeakLens — Testing Strategy & Manual Testing Guide

## Table of Contents
1. [Testing Philosophy](#testing-philosophy)
2. [Test Environment Setup](#test-environment-setup)
3. [Manual Testing Checklist](#manual-testing-checklist)
4. [End-to-End Test Scenarios](#end-to-end-test-scenarios)
5. [Sample Leak App](#sample-leak-app)
6. [Regression Testing](#regression-testing)
7. [Performance Testing](#performance-testing)

---

## 1. Testing Philosophy

LeakLens testing follows a **pyramid approach**:
- **Unit Tests**: Shark integration, rule engine, data models
- **Integration Tests**: ADB communication, plugin service wiring
- **UI Tests**: Tool window rendering, navigation
- **E2E Manual Tests**: Full flow from device → analysis → fix

For manual testing by the developer, this document focuses on **E2E scenarios** that validate the complete user experience.

---

## 2. Test Environment Setup

### Prerequisites
```bash
# 1. Android Studio Iguana+ installed
# 2. Android SDK with platform-tools (adb)
# 3. Physical device or emulator (API 26+)
# 4. Plugin built and installed

# Build the plugin
cd /Users/vikas.soni/AndroidStudioProjects/leak-lens
./gradlew buildPlugin

# Install: Settings → Plugins → ⚙ → Install from Disk → build/distributions/*.zip
```

### Sample Leak App Setup
Create a simple Android app with intentional leaks (see Section 5).

---

## 3. Manual Testing Checklist

### Phase 1: Plugin Installation & Startup
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 1.1 | Plugin installs | Install zip from disk, restart | No errors, plugin appears in Plugins list | ☐ |
| 1.2 | Tool window visible | View → Tool Windows → LeakLens | LeakLens panel appears at bottom | ☐ |
| 1.3 | Startup log | Check idea.log for "LeakLens: Plugin initialized" | Log message present | ☐ |
| 1.4 | Settings page | Settings → Tools → LeakLens | Settings panel renders correctly | ☐ |
| 1.5 | Tools menu | Tools menu bar | LeakLens submenu with all actions visible | ☐ |

### Phase 2: Device Connection & Heap Dump
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 2.1 | Device detection | Connect device, Tools → LeakLens → Dump Heap Now | Device serial shown in selection | ☐ |
| 2.2 | Process listing | After device selection | Debuggable processes listed | ☐ |
| 2.3 | Heap dump trigger | Select process, confirm | Progress bar, then results in tool window | ☐ |
| 2.4 | Import hprof | Tools → LeakLens → Import .hprof | File chooser opens, analysis runs | ☐ |
| 2.5 | No device error | Disconnect device, Dump Heap Now | "No connected devices" notification | ☐ |
| 2.6 | Auto-detect | Start Auto-Detect, trigger leak in app | Plugin auto-captures and analyzes | ☐ |

### Phase 3: UI & Navigation
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 3.1 | Leak tree | After analysis with leaks | Tree grouped by 🔴🟡🟢 severity | ☐ |
| 3.2 | Leak detail | Click a leak in tree | Right panel shows trace + fix | ☐ |
| 3.3 | Source navigation | Click blue class name in detail | Editor opens at that class | ☐ |
| 3.4 | Gutter icons | Open a class that's in a leak trace | 🔴 or ⚠️ icon in gutter | ☐ |
| 3.5 | History tab | Switch to History tab | Past analyses listed with timestamps | ☐ |
| 3.6 | Memory tab | Start monitoring, view Memory tab | Live graph updating | ☐ |
| 3.7 | Empty state | Clear all leaks | "No leaks detected" message shown | ☐ |

### Phase 4: Fix Suggestions
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 4.1 | Static rule match | Leak with Activity in static field | Shows "Remove static reference" fix | ☐ |
| 4.2 | Code snippet | View fix suggestion | Before/After code example shown | ☐ |
| 4.3 | Quick fix (Alt+Enter) | Cursor on Handler field in Activity | "Add removeCallbacksAndMessages" appears | ☐ |
| 4.4 | AI suggestion | Enable AI in settings, analyze novel leak | 🤖 badge on AI-generated suggestion | ☐ |
| 4.5 | No fix available | Library/framework leak | "No fix suggestion available" shown | ☐ |

### Phase 5: Static Inspections
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 5.1 | Static field warning | Write `static Activity activity;` | Yellow squiggly underline | ☐ |
| 5.2 | GlobalScope warning | Write `GlobalScope.launch{}` in Activity | Warning highlight | ☐ |
| 5.3 | Handler warning | Add Handler field without cleanup | Warning on field name | ☐ |
| 5.4 | Inspection settings | Settings → Editor → Inspections → LeakLens | All 6 inspections listed | ☐ |
| 5.5 | Disable inspection | Uncheck one inspection | No longer shows warning | ☐ |

### Phase 6: Memory Monitoring
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 6.1 | Start monitor | Tools → LeakLens → Start Memory Monitor | "Monitoring..." notification | ☐ |
| 6.2 | Graph updates | View Memory tab while app runs | Lines moving on graph | ☐ |
| 6.3 | Stop monitor | Tools → LeakLens → Stop Memory Monitor | Graph freezes, "stopped" msg | ☐ |
| 6.4 | Auto-trigger | Set threshold to 50MB, allocate memory in app | Auto heap dump notification | ☐ |

### Phase 7: Reports & Baselines
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 7.1 | Export HTML | Tools → LeakLens → Export Report → .html | HTML file with leak table + traces | ☐ |
| 7.2 | Export JSON | Export as .json | Valid JSON with all leak data | ☐ |
| 7.3 | Export SARIF | Export as .sarif | Valid SARIF 2.1.0 schema | ☐ |
| 7.4 | Save baseline | Tools → LeakLens → Save as Baseline | leak-baseline.json created in project root | ☐ |
| 7.5 | Baseline filtering | Re-analyze after baseline | Baseline leaks suppressed, notification shows "(X suppressed)" | ☐ |

### Phase 8: Settings & Configuration
| # | Test Case | Steps | Expected Result | Pass? |
|---|-----------|-------|----------------|-------|
| 8.1 | Change threshold | Set to 100MB, apply | Setting persisted across restart | ☐ |
| 8.2 | Toggle AI | Enable AI, set provider + key | AI suggestions appear on next analysis | ☐ |
| 8.3 | Disable gutter | Uncheck "Show gutter icons" | Gutter markers disappear | ☐ |
| 8.4 | Deobfuscation | Import obfuscated hprof with mapping.txt | Class names shown deobfuscated | ☐ |

---

## 4. End-to-End Test Scenarios

### Scenario A: "Happy Path - Leak Detected and Fixed"
```
1. Launch sample app with intentional Activity leak on emulator
2. Navigate away from leaked Activity (press Back)
3. Tools → LeakLens → Dump Heap Now
4. Select emulator → select app process
5. Wait for analysis (~10-20s)
6. VERIFY: Tool window shows 🔴 Critical leak
7. Click the leak → VERIFY: Detail panel shows Activity class name
8. Click class name → VERIFY: Editor opens Activity.kt
9. VERIFY: "Suggested Fix" panel shows "Remove static reference"
10. Apply fix in code
11. Re-run app, dump heap again
12. VERIFY: Leak no longer appears
```

### Scenario B: "Auto-Detection Flow"
```
1. Add LeakCanary to sample app (debugImplementation)
2. Tools → LeakLens → Start Auto-Detect
3. Launch app, trigger a leak (rotate device with Activity leak)
4. Wait for LeakCanary to dump (check logcat for "D/LeakCanary")
5. VERIFY: LeakLens auto-pulls and analyzes (notification appears)
6. VERIFY: Tool window updates with new leaks
```

### Scenario C: "CI Report Workflow"
```
1. Run analysis (import or dump)
2. Tools → LeakLens → Export Report → choose .sarif
3. VERIFY: File created, valid JSON
4. Open in text editor → VERIFY: has "tool.driver.name": "LeakLens"
5. VERIFY: Each leak has ruleId, level, message, location
6. Validate with: https://sarifweb.azurewebsites.net/Validation
```

### Scenario D: "Baseline Suppression"
```
1. Analyze heap with 3 leaks
2. Tools → LeakLens → Save as Baseline
3. VERIFY: leak-baseline.json exists with 3 signatures
4. Re-analyze the same heap
5. VERIFY: "All 3 leak(s) are in baseline. No new leaks!"
6. Add a NEW leak to the app, dump again
7. VERIFY: Only the new leak shows (3 suppressed by baseline)
```

---

## 5. Sample Leak App

Create this Activity to generate intentional leaks for testing:

```kotlin
// app/src/main/java/com/example/leaktest/LeakyActivity.kt

package com.example.leaktest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class LeakyActivity : AppCompatActivity() {

    // LEAK 1: Static reference to Activity
    companion object {
        var leakedActivity: LeakyActivity? = null
    }

    // LEAK 2: Handler holding Activity (inner class)
    private val handler = object : Handler(Looper.getMainLooper()) {}

    // LEAK 3: Anonymous Runnable posted with delay
    private val leakyRunnable = Runnable {
        // This holds implicit reference to LeakyActivity
        title = "Leaked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaky)

        // Store static reference
        leakedActivity = this

        // Post delayed runnable (will outlive Activity if not removed)
        handler.postDelayed(leakyRunnable, 60000)
    }

    // Intentionally NO cleanup in onDestroy
}
```

```kotlin
// app/src/main/java/com/example/leaktest/SingletonLeak.kt
package com.example.leaktest

import android.content.Context

// LEAK 4: Singleton holding Activity context
object AppManager {
    lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx // Should be ctx.applicationContext!
    }
}
```

**Test flow:**
1. Launch LeakyActivity
2. Press Back (Activity destroyed but leaked)
3. Trigger heap dump → LeakLens should find 3-4 leaks

---

## 6. Regression Testing

After any code change, run through these minimum tests:

### Smoke Test (2 minutes)
1. Build plugin: `./gradlew buildPlugin` — passes
2. Install in IDE — no errors
3. Tool window opens — no crash
4. Import a known .hprof — leaks displayed

### Core Flow Test (5 minutes)
1. Connect device
2. Dump Heap Now → analysis completes
3. Click a leak → source navigation works
4. Export HTML report → file valid

### Full Regression (15 minutes)
- Run the complete Phase 1-8 checklist above

---

## 7. Performance Testing

### Benchmarks to Track

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Analysis time (50MB hprof) | < 10s | Time from "Running Shark..." to results |
| Analysis time (200MB hprof) | < 30s | Same as above |
| Analysis time (500MB hprof) | < 60s | Same as above |
| UI responsiveness during analysis | No freeze | IDE should remain responsive |
| Memory usage during analysis | < 1GB additional | Monitor IDE process via Activity Monitor |
| Tool window render time | < 100ms | Time from data ready to visible |
| Source navigation latency | < 500ms | Click to file open |

### How to Test Performance
```bash
# Generate a large heap dump for testing
adb shell am dumpheap <pid> /data/local/tmp/large_heap.hprof
adb pull /data/local/tmp/large_heap.hprof ~/Desktop/

# Time the analysis
# In IDE: Import the file, note the progress bar duration
```

### Memory Stress Test
1. Open a large project (100+ classes)
2. Run 5 analyses in sequence
3. Check IDE memory (Help → Diagnostic Tools → Memory)
4. Verify no OOM, no significant memory growth after GC

---

## Test Results Template

```
Date: ____________________
Tester: __________________
Plugin Version: __________
IDE Version: _____________
Device: __________________

Phase 1: ☐ Pass  ☐ Fail  Notes: ___________
Phase 2: ☐ Pass  ☐ Fail  Notes: ___________
Phase 3: ☐ Pass  ☐ Fail  Notes: ___________
Phase 4: ☐ Pass  ☐ Fail  Notes: ___________
Phase 5: ☐ Pass  ☐ Fail  Notes: ___________
Phase 6: ☐ Pass  ☐ Fail  Notes: ___________
Phase 7: ☐ Pass  ☐ Fail  Notes: ___________
Phase 8: ☐ Pass  ☐ Fail  Notes: ___________

E2E Scenario A: ☐ Pass  ☐ Fail
E2E Scenario B: ☐ Pass  ☐ Fail
E2E Scenario C: ☐ Pass  ☐ Fail
E2E Scenario D: ☐ Pass  ☐ Fail

Overall: ☐ RELEASE READY  ☐ NEEDS FIXES
```

