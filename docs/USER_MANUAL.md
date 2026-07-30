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
3. Click **Dump Heap Now**.
4. Select the target process. The plugin will pull the `.hprof` file via ADB and begin analysis.

### Leak Dashboard

* **Leak Tree**: Grouped by Severity (Critical, Warning, Information).
* **Reference Chain**: A visual map showing the retention path to the GC Root.
* **Source Navigation**: Click any class or field name in the trace to jump to the corresponding
  source code.

### AI Fix Assistant

1. Select a leak in the dashboard.
2. Click **Ask Gemini AI**.
3. The plugin generates a prompt and copies it to your clipboard.
4. Paste the prompt into the **Gemini** panel in Android Studio for refactoring suggestions.

### Static Inspections

LeakLens highlights potential leaks as you type. Examples include:

* Static Activity/Fragment fields.
* Uncancelled coroutines in `GlobalScope`.
* Missing `removeCallbacks` for Handlers.

## 3. Configuration

Settings are managed via **Settings → Tools → LeakLens**.

| Setting               | Default | Description                                                       |
|:----------------------|:--------|:------------------------------------------------------------------|
| **Auto-detect**       | On      | Polls logcat for external LeakCanary dumps to pull automatically. |
| **Show Gutter Icons** | On      | Toggles 🚫/⚠️ markers in the editor gutter.                       |
| **Auto Heap Dump**    | 256MB   | Triggers a dump when Java heap exceeds this threshold.            |
| **Max History**       | 50      | Number of past analyses to retain in history.                     |

## 4. Troubleshooting

* **No device connected**: Verify `adb devices` shows your device. Ensure no other tool is locking
  the JDWP port.
* **Analysis OOM**: If the IDE crashes during analysis, increase the `-Xmx` memory in **Help →
  Change Memory Settings**.
* **Obfuscated Traces**: If the trace shows generic names (a.b.c), click **Link mapping.txt** in the
  leak detail panel to provide an R8/ProGuard mapping file.
