# LeakLens — Architecture & Technical Documentation

## 1. System Overview

**LeakLens** is a high-performance memory leak detection suite for Android Studio. It employs a *
*Dual-Layer Detection Strategy**:

1. **Static Layer**: Real-time UAST (Universal Abstract Syntax Tree) inspections that catch common
   leak patterns during code composition.
2. **Runtime Layer**: On-demand heap dump capture via ADB and deep analysis using the Shark engine (
   v2.14).

---

## 2. Core Components

### 🧠 Service Layer

* **`LeakLensProjectService`**: The central state manager. It uses a reactive architecture (Kotlin
  Flow) and a **Map-of-Maps** storage pattern to aggregate findings from multiple inspections across
  various files without data loss.
*   **`LeakAnalysisCoordinator`**: Orchestrates the runtime analysis pipeline: Trigger → Capture → Pull → Shark Analysis → Deobfuscation → AI Enrichment → Baseline Filtering → UI Update.
* **`AdbHeapDumpService`**: A robust wrapper around `ddmlib` and `AndroidSdkUtils`. It leverages the
  IDE's built-in ADB binary to ensure path-independent device discovery and communication.

### 🛡️ Inspection Engine (UAST)

* **Preventive Logic**: 6+ specialized tools targeting common Android pitfalls (e.g., static
  Activity fields, uncancelled coroutines, unregistered listeners).
* **Multi-Language Support**: UAST allows the same logic to detect issues in both **Java** and *
  *Kotlin** with zero performance overhead.
* **Live Reporting**: Findings are streamed in real-time to the Tool Window, providing a "living
  dashboard" of the project's health.

### 🎨 Presentation Layer

* **Modern UI**: Built using **IntelliJ UI DSL 2**, ensuring full support for the New UI, native
  themes (Darcula/Light), and accessibility.
* **Java-Kotlin Hybrid**: The `ToolWindowFactory` is implemented in Java to maintain binary
  compatibility with the IntelliJ platform's internal APIs (specifically avoiding bridge method
  verification errors in 2024.2+).
* **Lifecycle Management**: Strict adherence to the `Disposable` pattern for all background
  listeners, timers, and coroutine scopes ensures the plugin is "Dynamic-Ready" (can be
  installed/uninstalled without IDE restart).
*   **Clickable Trace Engine**: A styled `JTextPane` that converts class names into actionable links, jumping to the **exact line number** in the source code.

---

## 3. The "SDK-Free" Workflow

LeakLens eliminates the need for the LeakCanary SDK in your application binary:

* **Host-Side Analysis**: All heavy Shark processing is offloaded to the IDE's JVM, keeping the
  Android app binary clean and fast.
* **ADB Monitoring**: Uses `dumpsys meminfo` to track Java Heap usage in real-time.
* **Auto-Trigger**: Optionally triggers a heap dump when the heap exceeds a user-defined threshold.

---

## 4. AI & Collaboration

* **Ask Gemini**: A free, integrated action that generates a structured Markdown prompt containing
  the leak context, optimized for Android Studio's built-in AI assistant.
* **VCS Baselines**: A `leak-baseline.json` file allows teams to track and suppress known legacy
  leaks, ensuring new regressions are immediately visible.
