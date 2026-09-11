# User Manual

LeakLens provides Android memory leak detection directly within the IDE via static analysis and
runtime heap processing.

## 1. Setup

### Requirements

* **IDE**: Android Studio Iguana (2023.2) or newer.
* **JDK**: 17+.
* **Device**: Physical device or emulator with USB debugging enabled.
* **Memory**: 4GB+ free RAM recommended for heap analysis.

### Installation

1. Open **Settings → Plugins**.
2. Search for **LeakLens**.
3. Click **Install** and restart the IDE.

## 2. Features Guide

### Heap Analysis

1. Connect a debuggable Android device.
2. Open the **LeakLens** tool window (bottom-right stripe).
3. **Onboarding**: When a project is first opened, LeakLens will display a notification offering to
   **Start Memory Monitor**. This is the recommended way to begin profiling.
4. **Manual Start**: You can also click **Dump Heap Now** or **Start Memory Monitor** from the
   LeakLens action group in the Tools menu.
5. Select the target process. The plugin will pull the `.hprof` file via ADB and begin analysis.

### Leak Dashboard

* **Unified View**: Displays both static analysis findings and runtime heap leaks in one list.
* **Risk Explanation**: Detailed reasoning for static findings based on lifetime analysis.
* **Reference Chain**: A visual map for heap leaks showing the retention path to the GC Root.
* **Source Navigation**: Click any finding to jump directly to the problematic code.

### AI Fix Assistant

1. Select a leak in the dashboard.
2. Click **Ask Gemini AI**.
3. The plugin generates a prompt containing the **Semantic Evidence** (lifetimes involved) and the
   reference trace.
4. Paste the prompt into the **Gemini** panel in Android Studio for context-aware refactoring
   suggestions.

### Static Inspections

LeakLens highlights potential leaks as you type using its **Semantic Lifetime Engine**. Supported
detection categories include:

* **Jetpack Compose**: Capturing `Context` or `Activity` inside `remember` or `@Composable`.
* **Coroutines & Flow**: Unsafe `Flow` collection, `GlobalScope` usage, and deprecated lifecycle
  scopes.
* **DI (Hilt)**: Scope mismatches where long-lived components retain short-lived dependencies.
* **Architecture**: ViewModels and Workers retaining Activity/View references.
* **Classic Leaks**: Static Activity/Fragment fields, anonymous inner classes, and Handler cleanup.

## 3. Configuration

Settings are managed via **Settings → Tools → LeakLens**.

| Setting               | Default | Description                                                       |
|:----------------------|:--------|:------------------------------------------------------------------|
| **Auto-detect**       | On      | Polls logcat for external LeakCanary dumps to pull automatically. |
| **Show Gutter Icons** | On      | Toggles 🚫/⚠️ markers in the editor gutter.                       |
| **Auto Heap Dump**    | 256MB   | Triggers a dump when Java heap exceeds this threshold.            |
| **Max History**       | 50      | Number of past analyses to retain in history.                     |

> [!NOTE]
> The **First-Run Onboarding** notification status is stored at the application level and will not
> be shown again once acted upon or dismissed, even if you open a different project.

## 4. Troubleshooting

* **No device connected**: Verify `adb devices` shows your device. Ensure no other tool is locking
  the JDWP port.
* **Analysis OOM**: If the IDE crashes during analysis, increase the `-Xmx` memory in **Help →
  Change Memory Settings**.
* **Obfuscated Traces**: If the trace shows generic names (a.b.c), click **Link mapping.txt** in the
  leak detail panel to provide an R8/ProGuard mapping file.
