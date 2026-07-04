# LeakLens: Technical Architectural Overview

This document provides a deep dive into how LeakLens bridges the gap between static analysis and
runtime heap debugging using pure Kotlin and the IntelliJ Platform SDK.

## 1. Zero-SDK "Host-Side" Analysis

Unlike LeakCanary or similar tools, LeakLens follows a **Zero-SDK** philosophy.

### Why this matters for the Grant:

- **APK Integrity**: No code or dependencies are added to the production APK.
- **Resource Efficiency**: Heap analysis is a CPU and memory-intensive task. By performing this on
  the **Host (Developer's Machine)** rather than the mobile device, we preserve device battery and
  performance.
- **Shark Engine**: We utilize the **Shark** engine (the Kotlin-pure heap analysis engine from
  LeakCanary) directly within the IDE process.

## 2. Real-time Prevention via UAST

LeakLens leverages **UAST (Universal Abstract Syntax Tree)** to provide SonarLint-style real-time
feedback.

### Key Innovations:

- **Language Agnostic**: UAST allows our inspections to work across both Kotlin and Java, though
  they are semantically optimized for Kotlin's unique features like Coroutines, Flows, and Jetpack
  Compose.
- **Context-Aware Inspections**:
    - **Compose Leaks**: Recursively scans `@Composable` functions to detect `Context` capture in
      `remember { }` blocks.
    - **Lifecycle Safety**: Analyzes Flow collection chains (`.collect`, `.launchIn`) to ensure they
      are wrapped in `repeatOnLifecycle` or `flowWithLifecycle`.

## 3. Asynchronous Performance Model

To ensure the IDE remains responsive, LeakLens is built entirely on **Kotlin Coroutines and Flow**.

- **Non-blocking ADB**: All device interactions (JDWP, Shell, Pull) are offloaded from the UI
  Thread.
- **Streaming UI**: The Memory Graph uses `StateFlow` to provide real-time updates from the
  `DeviceMemoryMonitor` service without lag.

## 4. Resilience Architecture

Due to the rapid evolution of the Android Plugin for IntelliJ, LeakLens uses an **Adaptive
Reflection Layer**. This allows the plugin to remain compatible across different IDE builds (
IntelliJ IDEA Ultimate, Android Studio Ladybug, etc.) even when internal package names like
`com.android.ddmlib` shift.

## 5. AI-Assisted Education

The AI Assistant is designed to be **Educational**. Instead of just providing a patch, the prompt
engineering in `AiUtils.kt` forces the AI to explain the root cause and provide **Idiomatic Kotlin**
solutions (e.g., suggesting `WeakReference` delegates or `LifecycleObserver` instead of just nulling
out fields).
