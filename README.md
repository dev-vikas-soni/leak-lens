# LeakLens - Memory Leak Detector & AI Assistant 🧠💧

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-LeakLens-blue?style=for-the-badge&logo=jetbrains)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![Version](https://img.shields.io/jetbrains/plugin/v/32079.svg?style=for-the-badge)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![Build Status](https://img.shields.io/github/actions/workflow/status/dev-vikas-soni/leak-lens/build.yml?branch=main&style=for-the-badge)](https://github.com/dev-vikas-soni/leak-lens/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=for-the-badge)](LICENSE)

**LeakLens** is a high-performance, professional-grade memory leak detection and fix assistant for
Android Studio. It bridges the gap between static code analysis and runtime heap debugging,
providing a seamless "detect-analyze-fix" workflow without ever leaving your IDE.

<img width="1498" height="535" alt="latets-leallense-dashboard" src="https://github.com/user-attachments/assets/f8dc09c5-5010-4c12-bdfb-e113545419b9" />

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

* **Write-Time Prevention**: 12 specialized inspections flag leaks (static Activity refs,
  uncancelled coroutines, Hilt scope mismatches, WorkManager context leaks, and more) as you type.
* **Editor Gutter Icons**: Visual markers (🚫, ⚠️) indicate leaking classes directly in the code
  gutter.
* **One-Click Fixes**: Use `Alt + Enter` to apply fixes like `WeakReference` wrapping, lifecycle
  cleanup, `repeatOnLifecycle` wrapping, or `applicationContext` substitution.
* **AI Fix Assistant**: Every inspection surfaces an "Ask AI" action that builds a structured
  Gemini prompt pre-loaded with the leak context.

<img width="1498" height="780" alt="static-analysis-leaklens" src="https://github.com/user-attachments/assets/74ba35bf-0b14-4993-90b8-0b2fc99bd302" />

### 2. Runtime Precision (Shark-Powered)

Integrates the **Shark Heap Analysis engine** (used by LeakCanary) directly into the IDE.

* **SDK-Free Capture**: No need to add dependencies to your `build.gradle`. LeakLens works entirely
  via ADB and host-side analysis.
* **Live Memory Graph**: Monitor Java Heap, Native Heap, and PSS in real-time with an integrated
  lite profiler.
* **Auto-Trigger Safeguards**: Configure thresholds to automatically dump the heap when your app
  hits critical memory levels.

<img width="1498" height="535" alt="leaklens-memory-graph" src="https://github.com/user-attachments/assets/64927cb8-6ab2-4f49-8e1b-901860b1e2f7" />

### 3. AI Fix Assistant (Gemini Integrated)

Stop guessing. Discuss complex leak traces with Android Studio’s built-in AI.

* **Context-Aware Prompts**: One click generates a professional Markdown analysis request for
  Gemini.
* **Actionable Solutions**: Get code-specific fix suggestions for complex library or framework
  leaks.

  <img width="1498" height="780" alt="ask_ai-leaklens" src="https://github.com/user-attachments/assets/c1512879-493a-41bb-8cc9-9a8cf75b8204" />

---

## 📊 Why LeakLens? (Comparison)

| Feature | **LeakLens** | **Android Studio Profiler** | **LeakCanary** |
| :---                  | :---                | :---      | :---            |
| **IDE Integrated**    | ✅ Full             | ✅ Partial | ❌ External App |
| **AI Fix Assistant**  | ✅ Yes (Gemini/GPT) | ❌ No      | ❌ No           |
| **Zero Code Changes** | ✅ Yes (ADB based)  | ✅ Yes     | ❌ Requires SDK |
| **Static Analysis**   | ✅ Real-time (UAST) | ❌ No      | ❌ No           |
| **One-Click Fixes**   | ✅ Yes              | ❌ No      | ❌ No           |

---

## ✨ Key Features

| Feature                  | Technical Detail                                                                            |
|:-------------------------|:--------------------------------------------------------------------------------------------|
| **Universal Support**    | Native support for **Java**, **Kotlin**, and **Jetpack Compose**.                           |
| **12 UAST Inspections**  | Covers ViewModel, Handler, Coroutine, Flow, Compose, Hilt DI, WorkManager and more.         |
| **1-Click Quick Fixes**  | `WrapWithRepeatOnLifecycle`, `UseApplicationContext`, `RemoveContextArg` fixes built-in.    |
| **Tool Window Toolbar**  | All actions (Dump, Monitor, Export, Baseline) unified in the side panel — always reachable. |
| **Precision Navigation** | Click any class in a leak trace to jump to the **exact line of code**.                      |
| **VCS Baselines**        | Commit `leak-baseline.json` to suppress legacy leaks and focus on new regressions.          |
| **CI/CD Ready**          | Export professional reports in **HTML**, **JSON**, or **SARIF** formats.                    |
| **"Show in Files" CTA**  | After export, one click reveals the report in Finder/Explorer.                              |
| **Deobfuscation**        | Automatic R8/ProGuard mapping resolution for production heap dumps.                         |

---

## 🛠️ Installation

1. **Marketplace**: Search for `LeakLens` in **Settings → Plugins → Marketplace**.
2. **Plugin Window**: Open the **LeakLens** tab at the bottom of Android Studio.
3. **ADB Ready**: Connect your device/emulator and click the **Dump Heap** icon to begin.

<img width="983" height="723" alt="plugin-setting-leallens" src="https://github.com/user-attachments/assets/76cd01f0-e7ea-4561-bd2b-e3e1dd26eeb6" />

---

## 🤝 Contributing

We welcome contributions from the Android community! Please see
our [Contributing Guide](CONTRIBUTING.md) to get started with the IntelliJ Platform SDK.

---

## ⚖️ License

LeakLens is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

---
Built with ❤️ by [Vikas Soni](https://github.com/dev-vikas-soni)
