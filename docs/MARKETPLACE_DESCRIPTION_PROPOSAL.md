# Proposed JetBrains Marketplace Listing Description

> **Note**: Copy and paste the text below into the JetBrains Marketplace Publisher Portal to update the LeakLens plugin listing.

---

## Short Description (Tagline)

Android memory analysis plugin combining host-side Shark heap analysis via ADB with lifecycle-aware UAST static inspections.

---

## Full Description

**LeakLens** brings end-to-end memory leak debugging directly into Android Studio and IntelliJ IDEA. It combines local, host-side heap dump analysis using the LeakCanary **Shark** engine with real-time **UAST static analysis** to detect, explain, and remediate Android memory leaks early in the development cycle.

### Key Capabilities

- **Zero-SDK Host-Side Heap Analysis**: Capture HPROF dumps from debuggable Android devices via ADB and analyze them on your workstation's JVM using Shark 2.14. No SDK or runtime code required in your production APK.
- **Semantic Lifetime Engine**: Static inspections model object lifetime hierarchies (Application, Singleton, ViewModel, Activity, Fragment, Compose Composition, View) to highlight memory retention risks while you write code.
- **Modern Android & Kotlin Inspections**: Built-in rules for Jetpack Compose context capture, Coroutine scope misuse (`GlobalScope`), unsafe Flow collection (`repeatOnLifecycle`), Hilt scope mismatches, and ViewModel/Worker context retention.
- **Periodic Memory Monitoring**: Monitor device JVM heap usage via `adb shell dumpsys meminfo` at periodic intervals with configurable auto-dump thresholds.
- **ProGuard / R8 Deobfuscation**: Load `mapping.txt` files to map obfuscated class, method, and field names in heap traces back to original source symbols.
- **AI-Assisted Remediation (Optional)**: Generate contextual fix suggestions using OpenAI or Google Gemini API integrations, or copy formatted analysis prompts directly to your clipboard for use with Android Studio's built-in AI tools.

---

### Technical Workflows

#### 1. Static Analysis (Author-Time)
Inspects Kotlin and Java files using Universal Abstract Syntax Tree (UAST) visitors and compares relative lifetime priorities (e.g., a `@Singleton` holding an `Activity` reference) to flag potential leaks before runtime.

#### 2. Heap Analysis (Run-Time)
Triggers `am dumpheap` over ADB, pulls the binary `.hprof` file to your local system, parses the object graph using Shark on the host JVM, and displays retention paths in a unified IDE tool window.

---

### Privacy & Data Safety

- **Local Heap Processing**: Heap dumps are processed entirely on your workstation. HPROF files are never uploaded or transmitted to external servers.
- **Opt-In AI Features**: AI integrations are disabled by default. When enabled, requests include leak details and reference chains. Package anonymization filters common package patterns on a best-effort basis.

---

### Prerequisites & Compatibility

- **IDE**: Android Studio 2023.3+ (Ladybug / Jellyfish / Iguana) or IntelliJ IDEA Ultimate / Community.
- **Target App**: Any Android debuggable application (API 21+). No application build modifications required.
- **ADB**: Standard Android SDK Platform-Tools `adb` installed and configured in PATH or Android SDK location.

---

### Technical Talk & Resources

Learn more about the architecture and design patterns behind host-side memory analysis:
- **DroidCon India 2026 Session**: *"Beyond Leak Detection: Building an End-to-End Memory Debugging Workflow Inside Android Studio"* by Vikas Soni ([Speaker Profile](https://india.droidcon.com/droidcon-2026-speakers/vikas-soni))
- **Source Code & Documentation**: [GitHub Repository](https://github.com/dev-vikas-soni/leak-lens)
