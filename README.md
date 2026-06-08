# LeakLens: Android Memory Leak Detector & AI Fix Assistant 🧠💧

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-LeakLens-blue?style=for-the-badge&logo=jetbrains)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![Version](https://img.shields.io/jetbrains/plugin/v/32079.svg?style=for-the-badge)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![Build Status](https://img.shields.io/github/actions/workflow/status/dev-vikas-soni/leak-lens/build.yml?branch=main&style=for-the-badge)](https://github.com/dev-vikas-soni/leak-lens/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=for-the-badge)](LICENSE)

**LeakLens** is a high-performance, professional-grade memory leak detection and fix assistant for
Android Studio. It bridges the gap between static code analysis and runtime heap debugging,
providing a seamless "detect-analyze-fix" workflow without ever leaving your IDE.

---

### 📺 Watch LeakLens in Action

> [!TIP]
> https://www.youtube.com/watch?v=BcKYf34Jr1Q

---

## 🚀 The LeakLens Advantage

Most tools wait for your app to crash. **LeakLens catches leaks before they are even compiled.**

### 1. Static Analysis Layer (SonarLint-Style)

Using a high-performance **UAST (Universal Abstract Syntax Tree)** engine, LeakLens scans your
Kotlin and Java code in real-time.

* **Write-Time Prevention**: 6+ specialized inspections flag leaks (like static Activity references
  or uncancelled coroutines) as you type.
* **Editor Gutter Icons**: Visual markers (🚫, ⚠️) indicate leaking classes directly in the code
  gutter.
* **One-Click Fixes**: Use `Alt + Enter` to automatically apply industry-standard patterns like
  `WeakReference` wrapping or lifecycle cleanup.

> <img width="1471" height="835" alt="leak-canary-sample-leak-view" src="https://github.com/user-attachments/assets/150093dd-df5b-4c42-8664-9a58ec585343" />

### 2. Runtime Precision (Shark-Powered)

Integrates the **Shark Heap Analysis engine** (used by LeakCanary) directly into the IDE.

* **SDK-Free Capture**: No need to add dependencies to your `build.gradle`. LeakLens works entirely
  via ADB and host-side analysis.
* **Live Memory Graph**: Monitor Java Heap, Native Heap, and PSS in real-time with an integrated
  lite profiler.
* **Auto-Trigger Safeguards**: Configure thresholds to automatically dump the heap when your app
  hits critical memory levels.

> <img width="1471" height="835" alt="leak-detail-suggestion-view" src="https://github.com/user-attachments/assets/8aaa0054-f520-4ee6-b660-64984c2dd102" />

### 3. AI Fix Assistant (Gemini Integrated)

Stop guessing. Discuss complex leak traces with Android Studio’s built-in AI.

* **Context-Aware Prompts**: One click generates a professional Markdown analysis request for
  Gemini.
* **Actionable Solutions**: Get code-specific fix suggestions for complex library or framework
  leaks.

---

## 📊 Why LeakLens? (Comparison)

| Feature | **LeakLens** | **Android Studio Profiler** | **LeakCanary** |
| :--- | :---: | :---: | :---: |
| **IDE Integrated** | ✅ Full | ✅ Partial | ❌ External App |
| **AI Fix Assistant** | ✅ Yes (Gemini/GPT) | ❌ No | ❌ No |
| **Zero Code Changes** | ✅ Yes (ADB based) | ✅ Yes | ❌ Requires SDK |
| **Static Analysis** | ✅ Real-time (UAST) | ❌ No | ❌ No |
| **One-Click Fixes** | ✅ Yes | ❌ No | ❌ No |

---

## ✨ Key Features

|         Feature          | Technical Detail                                                                   |
|:------------------------:|:-----------------------------------------------------------------------------------|
|  **Universal Support**   | Native support for **Java**, **Kotlin**, and **Jetpack Compose**.                  |
| **Precision Navigation** | Click any class in a leak trace to jump to the **exact line of code**.             |
|    **VCS Baselines**     | Commit `leak-baseline.json` to suppress legacy leaks and focus on new regressions. |
|     **CI/CD Ready**      | Export professional reports in **HTML**, **JSON**, or **SARIF** formats.           |
|    **Deobfuscation**     | Automatic R8/ProGuard mapping resolution for production heap dumps.                |

---

## 🛠️ Installation

1. **Marketplace**: Search for `LeakLens` in **Settings → Plugins → Marketplace**.
2. **Plugin Window**: Open the **LeakLens** tab at the bottom of Android Studio.
3. **ADB Ready**: Connect your device/emulator and click the **Dump Heap** icon to begin.

---

## 🤝 Contributing

We welcome contributions from the Android community! Please see
our [Contributing Guide](CONTRIBUTING.md) to get started with the IntelliJ Platform SDK.

---

## ⚖️ License

LeakLens is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

---
Built with ❤️ by [Vikas Soni](https://github.com/dev-vikas-soni)
