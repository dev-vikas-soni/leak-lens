# LeakLens — User Manual

## Table of Contents
1. [Introduction](#introduction)
2. [System Requirements](#system-requirements)
3. [Installation](#installation)
4. [Getting Started](#getting-started)
5. [Features Guide](#features-guide)
6. [Configuration](#configuration)
7. [Troubleshooting](#troubleshooting)
8. [FAQ](#faq)

---

## 1. Introduction

**LeakLens** is an Android Studio plugin that brings advanced memory leak detection directly into
your development environment. It eliminates the need to context-switch between your IDE and a mobile
device by providing:

- **SDK-Free Analysis**: Captures heap dumps via ADB without modifying your app's code.
- **Write-Time Prevention**: Static code inspections for Java and Kotlin.
- **AI Fix Assistant**: Context-aware integration with Google Gemini.
- **Visual Reference Chains**: Clickable traces that navigate directly to source code.

---

## 2. System Requirements

| Requirement | Minimum                                                |
|-------------|--------------------------------------------------------|
| **IDE**     | Android Studio Iguana (2023.2) or newer                |
| **JDK**     | JDK 17+                                                |
| **Device**  | Physical device or emulator with USB debugging enabled |
| **RAM**     | 4GB+ free for heap analysis                            |

---

## 3. Installation

### From JetBrains Marketplace

1. Open Android Studio → **Settings → Plugins**.
2. Search for **"LeakLens"** in the Marketplace tab.
3. Click **Install** and restart.

---

## 4. Getting Started

### Your First Heap Dump

1. Open the **LeakLens Tool Window** (bottom-right stripe).
2. Connect a debuggable Android device or emulator.
3. Go to **Tools → LeakLens → Dump Heap Now**.
4. Select your process. The plugin will pull the `.hprof` file and start the Shark analysis engine
   automatically.

---

## 5. Features Guide

### 5.1 Leak Dashboard

* **Tree View**: group leaks by **🚫 Error**, **⚠️ Warning**, and **ℹ️ Information**.
* **Reference Chain**: A visual map showing how an object is being held in memory.
* **Trace Links**: Click any blue class name to open the file at the specific line being referenced.

### 5.2 AI Fix Assistant (Gemini)

When a complex leak is detected:

1. Click **"Ask Gemini AI"** in the details panel.
2. The plugin copies a professional analysis request to your clipboard and displays it in the
   suggestion area.
3. Paste the prompt into the **Gemini** side panel in Android Studio.
4. Follow the AI-suggested refactoring steps to resolve the leak.

### 5.3 Static Inspections

LeakLens identifies 6+ dangerous patterns while you type:

* **Activity/Fragment in Static Fields**: Prevents the entire UI hierarchy from being garbage
  collected.
* **Uncancelled Coroutines**: Flags `GlobalScope` usage that captures Activity references.
* **Missing Handler Cleanup**: Detects `postDelayed` calls without corresponding `removeCallbacks`.

### 5.4 Live Memory Graph

Monitor app performance in real-time via the **Memory** tab.

* Track **Java Heap**, **Native Heap**, and **Total PSS**.
* Configure thresholds in Settings to trigger a heap dump automatically when memory usage spikes.

### 5.5 Team Baselines

Avoid "Noise fatigue."

* Save existing leaks to `leak-baseline.json`.
* Commit this file to Git.
* Future analyses will only highlight **new** regressions, keeping your team focused on preventing
  new leaks.

---

## 6. Configuration

Manage settings via **Settings → Tools → LeakLens**.

| Setting               | Default | Description                                                   |
|-----------------------|---------|---------------------------------------------------------------|
| **Auto-detect**       | Off     | Polls logcat for external LeakCanary dumps.                   |
| **Show Gutter Icons** | On      | Toggles 🚫/⚠️ markers in the editor.                          |
| **Auto Heap Dump**    | 256MB   | Triggers a dump when Java heap exceeds this limit.            |
| **AI Anonymization**  | On      | Strips package names before copying to clipboard for privacy. |

---

## 7. Troubleshooting

* **"No device connected"**: Verify `adb devices` shows your device. Ensure the plugin is not being
  blocked by a firewall.
* **Analysis OOM**: If the IDE crashes during analysis, increase the `-Xmx` memory in **Help →
  Change Memory Settings**.
* **Icons Missing**: Static inspections require the IDE to be in "Analysis" mode (check the
  top-right corner of the editor).

---

## 8. FAQ

**Q: Is LeakLens free?**
A: Yes, LeakLens is open-source and free to use.

**Q: Does it support Jetpack Compose?**
A: Yes, it includes specific inspectors for Compose recomposition scopes and state leaks.

---
Built for Android Developers by [Vikas Soni](https://github.com/dev-vikas-soni).
