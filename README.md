# LeakLens 🧠💧

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-LeakLens-blue)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**LeakLens** is an IntelliJ Platform plugin for Android memory leak detection. It integrates the *
*Shark (LeakCanary)** heap analysis engine into the IDE and provides real-time static analysis via *
*UAST**.

### ⚡ Project at a Glance

* **Languages**: Kotlin, Java
* **Frameworks**: Jetpack Compose, Hilt, Coroutines, Flow, WorkManager
* **Analysis**: Static (UAST) + Runtime (Shark)
* **Integration**: Zero-SDK (captured via ADB)

---

## 🚀 Features

### 1. Kotlin-Specific Intelligence

Detects memory-unsafe patterns unique to Kotlin syntax:

* **Coroutines & Flows**: Identifies unsafe `StateFlow` collection outside lifecycle-aware scopes.
* **Jetpack Compose**: Flags `Context` capture in `@Composable` and `remember` blocks.
* **Property Delegates**: Analyzes `by viewModels()` and lazy delegates for escaping references.

### 2. UAST Static Inspections

Real-time IDE highlighting for common leak patterns:

* Static Activity/Fragment references.
* Anonymous inner classes in long-lived scopes.
* Missing callback removal in `onDestroy`.
* View reference retained in Fragment after `onDestroyView`.
* Activity context passed to Singletons or Worker fields.
* Hilt scope mismatches.

### 3. Host-Side Runtime Analysis

Analyzes heap dumps without app modifications:

* **Zero-SDK**: No dependencies added to the production APK.
* **Host-Side Processing**: Graph traversal is performed by the IDE, avoiding device-side resource
  exhaustion.
* **AI Fix Assistant**: Generates idiomatic Kotlin refactoring suggestions from leak traces.

## 📊 Comparison: Why LeakLens?

| Feature             | LeakLens           | Android Profiler | LeakCanary |
|:--------------------|:-------------------|:-----------------|:-----------|
| **Primary Goal**    | Prevention/Fixing  | Deep Debugging   | Alerting   |
| **Feedback Loop**   | Real-time (Typing) | Manual           | Runtime    |
| **IDE Integration** | Native             | Partial          | None       |
| **Zero-SDK**        | Yes                | Yes              | No         |

---

## 🧪 Verification & Testing

LeakLens includes a **Deterministic Verification Platform** to ensure analysis precision:

* [Sample App & Scenarios](sample-app/README.md): 7+ deterministic leak implementations.
* [Verification Engine](verification/README.md): Standalone JVM tool for semantic Golden HPROF
  comparison.
* **Golden Fixtures**: Pre-captured heap dumps and normalized JSON expectations in
  `verification/golden/`.

## 🛠️ Usage

1. Search for **LeakLens** in **Settings → Plugins → Marketplace**.
2. Open the **LeakLens** tool window.
3. Connect a device and click **Dump Heap Now**.

## 📚 Documentation

* [Architecture Deep-Dive](docs/ARCHITECTURE.md)
* [Performance Benchmarks](docs/PERFORMANCE.md)
* [How LeakLens analyzes Kotlin](docs/KOTLIN_SMARTS.md)

## 🌐 Community

* **Official Page
  **: [DroidUnplugged](https://www.droidunplugged.com/p/leaklens-android-memory-leak-detector.html)
* **Author**: [Vikas Soni](https://www.droidunplugged.com/p/about-us.html)

---
Licensed under [Apache 2.0](LICENSE).
