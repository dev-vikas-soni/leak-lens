# LeakLens 🧠💧

> **Professional Android Memory Leak Detection & Fix Assistant for Android Studio.**

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-32079-blue)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![Version](https://img.shields.io/jetbrains/plugin/v/32079-leaklens.svg)](https://plugins.jetbrains.com/plugin/32079-leaklens)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**LeakLens** is a high-performance, standalone IDE plugin designed to make Android memory leak management proactive and effortless. It combines the power of the **Shark heap analysis engine** with **SonarLint-style real-time inspections**, allowing you to find and fix leaks before they ever reach your users.

---

## 🚀 Why LeakLens?

*   **SDK-Free Detection**: No more adding `leakcanary-android` to your `build.gradle`. LeakLens works entirely via ADB and IDE-side analysis, keeping your app binary clean and fast.
*   **Write-Time Prevention**: 6 specialized UAST inspections detect leak-prone patterns (like static Activity references or uncancelled coroutines) while you type.
*   **Jetpack Compose Support**: Tailored analysis for Compose recomposition scopes and state-related leaks.
*   **Free AI Discussions**: Integrated "Ask Gemini" button to discuss complex leaks with Android Studio's built-in AI for free.
*   **One-Click Fixes**: Real code refactoring intentions (Alt+Enter) to automatically wrap fields in `WeakReference` or add cleanup logic.

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **Instant Analysis** | captures heap dumps via ADB, pulls, and analyzes with Shark in the background. |
| **SonarLint Workflow** | Manual "Analyze Current File" and "Analyze Project" actions to audit your code health. |
| **Live Memory Graph** | Real-time monitoring of Java Heap, Native Heap, and PSS with auto-trigger safeguards. |
| **Precision Navigation** | Click a leak trace to jump to the **exact line** in your source code. |
| **Team Baselines** | VCS-tracked `leak-baseline.json` to suppress legacy leaks and focus on new ones. |
| **CI-Ready Reports** | Export findings as HTML, JSON, or SARIF for GitHub Code Scanning/SonarQube. |

---

## 🛠️ Getting Started

1.  **Install**: Search for `LeakLens` in **Settings → Plugins → Marketplace**.
2.  **Open**: Click the **LeakLens** tab at the bottom of Android Studio.
3.  **Audit**: Click the ▶️ icon to audit your current file or 🔁 to scan the entire project.
4.  **Analyze**: Connect a device and click 📥 to capture a deep runtime heap dump.

---

## 🤝 Support & Marketplace

LeakLens is free and open-source. For a live rating and download count, you can embed this card on your tools page:

```html
<script src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
<script>
  MarketplaceWidget.setupMarketplaceWidget('card', 32079, "#leaklens-card");
</script>
```

---

## ⚖️ License

Licensed under the [Apache License, Version 2.0](LICENSE).
