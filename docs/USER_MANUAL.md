# LeakLens User Manual

LeakLens provides Android memory leak detection directly within Android Studio and IntelliJ IDEA through author-time static analysis and host-side runtime heap processing.

---

## 1. Prerequisites & Setup

### Requirements
- **IDE**: Android Studio 2023.3+ (Ladybug, Jellyfish, Iguana) or IntelliJ IDEA Ultimate/Community.
- **JDK**: JDK 17 or JDK 21.
- **Target Device**: Physical Android device or emulator with USB debugging enabled.
- **Host RAM**: 4 GB+ free RAM recommended for analyzing large HPROF files on the host machine.

### Installation
1. Open **Settings/Preferences** -> **Plugins** -> **Marketplace**.
2. Search for **LeakLens**.
3. Click **Install** and restart the IDE.

---

## 2. Features & Workflow Guide

### A. Static Code Inspections (Author-Time)
LeakLens inspects Kotlin and Java files automatically as you type. Detections are highlighted directly in the code editor with warning annotations and gutter markers:
- **Jetpack Compose**: Flags UI `Context` or `Activity` passed from Compose to a `ViewModel` or `@Singleton`. (*Note: `remember { context }` is safe and not flagged because Activity outlives Composition*).
- **Coroutines & Flow**: Flags `GlobalScope` usage and `Flow` collections missing `repeatOnLifecycle`.
- **Hilt / DI**: Flags scope mismatches (e.g. `@Singleton` retaining an `@ActivityScoped` reference).
- **ViewModels & Workers**: Flags `Context`, `Activity`, or `View` fields in `ViewModel` or `ListenableWorker` classes.

### B. Host-Side Heap Analysis (Run-Time)
1. Connect a debuggable Android device or emulator.
2. Open the **LeakLens** tool window at the bottom of the IDE.
3. Click **Start Memory Monitor** or **Dump Heap Now**.
4. LeakLens triggers `am dumpheap` over ADB, pulls the binary `.hprof` file to your host machine's `/tmp` directory, and parses the object graph using **Shark** on the host JVM.
5. If a large heap dump file is detected (`fileSize > maxMemory * 0.8`), LeakLens displays a memory warning notification to alert you of high host JVM memory usage.

### C. Unified Issue Tool Window
- **Unified Findings**: View both static analysis diagnostics and runtime heap traces in one place.
- **Retention Paths**: Inspect reference chains leading from GC Roots to leaking objects.
- **Deobfuscation**: Load a ProGuard/R8 `mapping.txt` file to deobfuscate class, method, and field names in heap traces.

### D. AI Fix Assistant & Privacy
- **Clipboard Workflow**: Click **Ask Gemini** to copy a pre-formatted prompt containing leak details and reference chains to the clipboard. Paste the prompt directly into Android Studio's Gemini tool window. This operates **100% locally** without making network calls.
- **Direct API Workflow**: Optionally enable direct AI API calls (OpenAI or Gemini) in settings. LeakLens transmits metadata (class names, retained size, reference chains, traces) to the selected provider. Source `.kt`/`.java` files are **never** transmitted.
- **Privacy Controls**: An optional package anonymization regex (`com.example.app` -> `app.pkg`) is applied prior to request construction. Anonymization is best-effort and does not guarantee complete privacy.

---

## 3. Tool Ecosystem Positioning

| Dimension | LeakLens | Android Profiler | LeakCanary |
|:---|:---|:---|:---|
| **Primary Focus** | Author-time static analysis + Zero-SDK host heap dumps | Live memory graphs & allocation tracking | Automated in-app leak detection during testing |
| **APK Impact** | Zero SDK (Host-side via ADB) | Zero SDK (Built-in IDE profiler) | Debug build dependency |
| **Analysis Host** | Host Workstation JVM (Shark 2.14) | Host Workstation IDE | Target Device JVM |

---

## 4. Configuration Options

Settings are managed in **Settings/Preferences** -> **Tools** -> **LeakLens**:

| Setting | Default | Description |
|:---|:---:|:---|
| **Auto Heap Dump Threshold** | `256 MB` | Automatically triggers a heap dump when Java heap exceeds this value during memory monitoring. |
| **Enable AI Analysis** | `false` | Opt-in toggle to enable direct API requests to external LLM providers. |
| **AI Provider** | `none` | Choice of provider (`openai`, `gemini`, or `none`). |
| **Anonymize Package Names** | `true` | Applies best-effort regex anonymization to package prefixes before sending AI requests. |

---

## 5. Troubleshooting

- **No Device Found**: Ensure `adb devices` lists your device and USB debugging is authorized.
- **Large Heap Dump Warning**: If you receive a large heap dump warning, consider increasing host IDE memory in **Help -> Change Memory Settings** (`-Xmx4g` or higher recommended).
- **Verification Runner**: To run regression tests against ground-truth heap fixtures, execute `./gradlew :verification:verify`. Note that fixture expectations are under active investigation.
