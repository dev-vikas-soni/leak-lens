# LeakLens 🧠💧

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-LeakLens-blue)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**LeakLens** is an Android memory leak detector for the IntelliJ Platform. It runs the **Shark (
LeakCanary)** engine host-side to analyze heap dumps via ADB and provides real-time static analysis
using **UAST**.

### Technical Stack

* **Runtime**: Shark 2.14 (LeakCanary engine)
* **Static Analysis**: UAST (Universal Abstract Syntax Tree)
* **Target**: Kotlin, Java
* **Integrations**: Compose, Hilt, Coroutines, WorkManager
* **Connectivity**: ADB (Zero-SDK / No app modifications)

---

## Features

### 1. Static Leak Detection (Author-time)

Catch leaks while writing code, before you even hit "Run". LeakLens uses UAST inspections to
highlight problematic signatures:

* **Jetpack Compose**: `Context` capture in `@Composable` or `remember` blocks.
* **Coroutines**: Unsafe `Flow` collection outside of `repeatOnLifecycle` or `flowWithLifecycle`.
* **DI**: Hilt scope mismatches (e.g., Singleton injecting Activity-scoped components).
* **Classic Patterns**: Static Activity refs, anonymous inner classes, and missing `onDestroy`
  cleanup.

### 2. Host-Side Heap Analysis (Zero-SDK)

Analyze your app's memory without adding any dependencies to your production APK.

* **ADB Offloading**: Triggers `am dumpheap` and pulls the HPROF to your machine.
* **Desktop Performance**: Graph traversal and reachability analysis are performed using your
  computer's resources, avoiding device OOMs during analysis.
* **AI Fix Assistant**: Maps complex reference chains to idiomatic Kotlin remediation steps using
  LLMs (Gemini/OpenAI).

### 3. Closed-Loop Verification

Once a fix is applied, re-verify it instantly.

* **1-Click Verify**: Triggers a fresh heap dump to confirm the leak is resolved.
* **Baseline Manager**: Track known issues and focus only on new regressions.

## Comparison

| Feature             | LeakLens            | Android Profiler | LeakCanary |
|:--------------------|:--------------------|:-----------------|:-----------|
| **Feedback Loop**   | Authoring/Real-time | Manual/Debugging | Runtime    |
| **IDE Integration** | Native Tool Window  | Partial          | None       |
| **APK Impact**      | Zero                | Zero             | ~300KB+    |
| **Remediation**     | AI-Powered Fixes    | Manual           | Trace only |

---

## Verification & Precision

LeakLens uses a **Deterministic Verification Platform** to ensure analysis accuracy:

* **Golden Fixtures**: Pre-captured heaps and JSON expectations in `verification/golden/`.
* **Regression Engine**: Standalone tool that validates Shark output against historical "ground
  truth" data.

## Usage

1. Install **LeakLens** from the **JetBrains Marketplace**.
2. Open the **LeakLens** tool window (Bottom Tab).
3. Connect a debuggable device and click **Dump Heap Now**.

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
