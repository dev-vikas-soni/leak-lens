# LeakLens 🧠💧

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-LeakLens-blue)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**LeakLens** is a unified Android memory profiling and analysis tool for the IntelliJ Platform. It
combines deep **Host-Side Heap Analysis** with real-time **Semantic Static Analysis** to catch,
explain, and fix memory leaks.

### Technical Stack

* **Runtime**: Shark 2.14 (LeakCanary engine)
* **Static Analysis**: UAST (Universal Abstract Syntax Tree) + **Semantic Lifetime Engine**
* **Target**: Kotlin, Java
* **Integrations**: Compose, Hilt, Coroutines, Flow, WorkManager, ViewModels
* **Connectivity**: ADB (Zero-SDK / No app modifications)

---

## Features

### 1. Semantic Static Analysis (Author-time)

Catch leaks while writing code, before you even hit "Run". LeakLens uses a specialized **Lifetime
Engine** to analyze object relationships and highlight risky signatures:

* **Jetpack Compose**: `Context` capture in `@Composable` scopes or `remember` blocks.
* **Coroutines & Flow**: Unsafe `Flow` collection (missing `repeatOnLifecycle`) and `GlobalScope`
  usage.
* **Hilt / DI**: Scope mismatches (e.g., `@Singleton` injecting `@ActivityScoped` dependencies).
* **Modern Android**: ViewModel and Worker `Context` retention, deprecated `launchWhenX` scopes.
* **Classic Patterns**: Static Activity refs, anonymous inner classes, and missing `onDestroy`
  cleanup.

### 2. Host-Side Heap Analysis (Zero-SDK)

Analyze your app's memory without adding any dependencies to your production APK.

* **ADB Offloading**: Triggers `am dumpheap` and pulls the HPROF to your machine.
* **Desktop Performance**: Graph traversal and reachability analysis are performed using your
  workstation's resources, avoiding device OOMs.
* **Real-time Memory Monitor**: An integrated memory graph to track JVM Heap usage with configurable
  auto-dump thresholds.
* **AI Fix Assistant**: Transforms complex reference chains and lifetime evidence into idiomatic
  Kotlin remediation steps using LLMs (Gemini/OpenAI).

### 3. Unified Findings Dashboard

All leaks—whether caught by static analysis or detected in a heap dump—are consolidated into a
single **Unified Dashboard**.

* **Proactive Onboarding**: Proactively introduces the Memory Monitor when you open a project,
  ensuring you never miss a leak.
* **Risk Explanation**: Clear reasoning for why a particular pattern is risky (e.g., "ViewModel
  outlives Activity").
* **1-Click Verify**: Re-verify fixes instantly by triggering a fresh heap dump.
* **Fingerprinting**: Deduplicates and tracks issues across analysis runs.

## Comparison

| Feature                | LeakLens                     | Android Profiler | LeakCanary |
|:-----------------------|:-----------------------------|:-----------------|:-----------|
| **Feedback Loop**      | **Authoring + Real-time**    | Manual/Debugging | Runtime    |
| **Lifetime Awareness** | **Semantic (UAST)**          | None             | None       |
| **IDE Integration**    | Native Unified Tool Window   | Partial          | None       |
| **APK Impact**         | **Zero**                     | Zero             | ~300KB+    |
| **Remediation**        | **Evidence-Driven AI Fixes** | Manual           | Trace only |

---

## Verification & Precision

LeakLens ensures analysis accuracy through a **Deterministic Verification Platform**:

* **Golden Fixtures**: Pre-captured heaps and JSON expectations in `verification/golden/`.
* **Benchmark Corpus**: Extensive suite of positive and negative cases for modern Android patterns.

## Usage

1. Install **LeakLens** from the **JetBrains Marketplace**.
2. Open the **LeakLens** tool window (Bottom Tab).
3. Connect a debuggable device. LeakLens will proactively offer to **Start Memory Monitor** when a
   project is opened.
4. Click **Dump Heap Now** to capture and analyze your first leak manually.

## 📚 Documentation

* [Architecture Deep-Dive](docs/ARCHITECTURE.md)
* [Semantic Lifetime Engine](docs/LIFETIME_ENGINE.md)
* [Static Analysis Rules Reference](docs/STATIC_ANALYSIS_RULES.md)
* [Testing & Verification Strategy](docs/TESTING_STRATEGY.md)
* [Performance Benchmarks](docs/PERFORMANCE.md)

---
Licensed under [Apache 2.0](LICENSE).
