# LeakLens — Architecture & Technical Documentation

## 1. System Overview
**LeakLens** is a high-performance memory leak detection suite for Android Studio. Unlike standard tools that only offer runtime analysis, LeakLens provides a **Dual-Layer Detection Strategy**:

1.  **Static Layer (SonarLint-style)**: Real-time UAST inspections that catch common leak patterns while the developer is typing.
2.  **Runtime Layer (Shark-powered)**: On-demand heap dump capture via ADB and deep analysis using LeakCanary's Shark engine.

---

## 2. Core Components

### 🧠 Service Layer
*   **`LeakLensProjectService`**: The central state manager. It uses a **Map-of-Maps** architecture to store findings from multiple inspections across various files without data loss. It provides `leaks` and `liveIssues` StateFlows for reactive UI updates.
*   **`LeakAnalysisCoordinator`**: Orchestrates the runtime analysis pipeline: Trigger → Capture → Pull → Shark Analysis → Deobfuscation → AI Enrichment → Baseline Filtering → UI Update.
*   **`AdbHeapDumpService`**: A robust ADB wrapper with built-in timeouts and connection monitoring to ensure IDE stability.

### 🛡️ Inspection Engine
*   **UAST Inspections**: 6 specialized tools that work across Java and Kotlin. They leverage `LeakLensInspectionUtils` for unified supertype resolution and live reporting.
*   **Live Analysis Bridge**: Inspections automatically feed findings into the tool window during "on-the-fly" checks, creating a living dashboard of the project's health.

### 🎨 Presentation Layer
*   **IntelliJ UI DSL 2**: All configuration and tool window components use modern DSL 2 for native theme integration and accessibility.
*   **Clickable Trace Engine**: A styled `JTextPane` that converts class names into actionable links, jumping to the **exact line number** in the source code.

---

## 3. The "SDK-Free" Workflow
LeakLens eliminates the need for the LeakCanary SDK in the Android app:
*   **ADB Monitoring**: Uses `dumpsys meminfo` to watch the device's Java Heap in real-time.
*   **Auto-Trigger**: Automatically triggers a heap dump when the heap exceeds the user's defined threshold.
*   **Host-Side Analysis**: All heavy Shark processing is offloaded to the IDE, keeping the Android app fast and small.

---

## 4. AI & Collaboration
*   **Ask Gemini**: A free, integrated action that copies leak traces and instructions to the clipboard for discussion with the built-in Android Studio AI assistant.
*   **VCS Baselines**: `leak-baseline.json` allows teams to acknowledge and suppress legacy leaks, ensuring the dashboard only shows new issues.
