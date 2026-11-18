# Performance & Latency Documentation

This document outlines the internal performance goals, historical benchmark measurements, and measurement methodology for LeakLens across various project sizes.

> **Important Disclosure**: The numbers presented below represent **historical internal measurements** recorded during early development on a single hardware configuration. They are retained for architectural context and baseline comparison, but have not been independently validated across all project setups. LeakLens does not guarantee specific performance numbers on arbitrary hardware or codebases.

---

## 1. Historical Internal Measurements

The following figures reflect historical internal test runs evaluating static inspection latency, Event Dispatch Thread (EDT) impact, and host-side Shark heap processing speed.

| Project Scale | LOC Range | Static Inspection Latency (Historical) | UI Thread Impact (Historical) | Host Heap Analysis (500 MB HPROF) |
|:---|:---|:---:|:---:|:---:|
| **Tier 1 (Small)** | ~100k LOC | ~280 ms | < 5 ms | ~12 s |
| **Tier 2 (Medium)** | ~500k LOC | ~640 ms | < 8 ms | ~18 s |
| **Tier 3 (Large)** | ~1M LOC | ~1.2 s | < 12 ms | ~24 s |

*Note: These measurements were recorded under internal development conditions and depend heavily on host system specs, JVM memory allocation, and project module structure.*

---

## 2. Test Environment Profile

Historical internal measurements were performed on the following hardware and software configuration:

- **CPU**: Apple M2 Pro (10 cores)
- **Memory**: 32 GB RAM (IDE Heap `-Xmx4g`)
- **Host OS**: macOS Sonoma / Sequoia
- **IDE**: Android Studio Ladybug (2024.2.1)
- **JDK**: JetBrains Runtime (JBR) 17 / 21

---

## 3. Architectural Scaling Design

To minimize overhead on large projects and prevent UI freezing, LeakLens incorporates several explicit engineering practices:

1. **Non-Blocking UAST Resolution**: Type resolution and class hierarchy checks (`InheritanceUtil`) are performed via `ReadAction.nonBlocking` off the main thread where applicable.
2. **UAST Visitor Filtering**: Static inspections filter expressions at the syntax tree node level (e.g., targeting specific annotations or call expressions) rather than performing full file PSI traversals.
3. **O(1) Gutter Marker Lookup**: Gutter icon providers query cached issue lists maintained by `LeakLensProjectService` rather than running live inspections during editor repaints.
4. **Host-Side Heap Processing**: Shark graph processing runs entirely within a background `Task.Backgroundable` thread in the IDE JVM, avoiding CPU contention on the target mobile device.

---

## 4. Reproducing Performance Measurements

Developers or maintainers wishing to benchmark LeakLens on their local machines can execute the following verification steps:

### Benchmarking Static Inspections
1. Open a target test project in Android Studio.
2. Enable IDE performance logging by adding `#com.github.devvikassoni.leaklens` to **Help $\rightarrow$ Diagnostic Tools $\rightarrow$ Debug Log Settings**.
3. Trigger a re-inspection of Kotlin/Java files and inspect `idea.log` for inspection execution times.

### Benchmarking Heap Analysis
1. Place a target `.hprof` file in the project directory.
2. Run the standalone verification task:
   ```bash
   ./gradlew :verification:verify
   ```
3. Observe execution times reported by Shark in the console output.

---

## 5. Known Measurement Limitations

- **System Variability**: Benchmark figures vary significantly based on CPU single-core speed, available JVM heap (`-Xmx`), disk I/O, and background Gradle sync activity.
- **Project Structure**: Multi-module projects with complex Dagger/Hilt dependency graphs or large synthetic Compose generated code can exhibit higher static inspection latency than flat codebases of equal line counts.
- **Heap Dump Size**: Large heap dumps (>1 GB) require sufficient host RAM allocation; analysis time scales non-linearly if JVM heap exhaustion triggers frequent garbage collection cycles.
