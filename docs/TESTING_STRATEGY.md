# Testing Strategy

LeakLens uses a multi-layered testing approach to ensure stability across different IntelliJ
Platform versions.

## 1. Automated Testing Layers

### UAST Unit Tests

Located in `src/test/kotlin/.../inspections/`. These tests use `BasePlatformTestCase` to verify that
static inspections correctly identify and highlight leaking patterns in both Kotlin and Java.

* **Positive Cases**: Verify that leaks are highlighted at the correct line.
* **Negative Cases**: Verify that safe patterns (e.g., using `applicationContext`) do NOT trigger
  warnings.

### Static Analysis Benchmarks

A specialized **Benchmark Corpus** (`src/test/testData/inspections/benchmark/`) is used to verify
performance and accuracy across complex modern Android scenarios:

* Nested Compose `remember` blocks.
* Multi-layered Hilt dependency graphs.
* Complex Coroutine/Flow lifecycle interactions.

### Deterministic Verification Platform

The `:verification` module provides a standalone regression engine that validates Shark output
against historical "ground truth" data:

* **Golden Fixtures**: Pre-captured heaps and expected JSON results.
* **Leak Normalizer**: Ensures signatures are stable across different runtime environments.

### Plugin Verification

The `verifyPlugin` task is run in CI to ensure binary compatibility with the target IDE range and to
check for usage of internal/deprecated APIs.

## 2. Manual Verification

To verify full device-to-IDE flow, use the following patterns in a sample Android app:

```kotlin
// Static reference leak
companion object { var leakedActivity: Activity? = null }

// Anonymous inner class leak
private val handler = object : Handler(Looper.getMainLooper()) {}
```

## 3. Performance Benchmarks

Any code change impacting the analysis pipeline must be measured against these targets:

* **Scanning Latency**: < 500ms on 100k LOC.
* **Memory Footprint**: < 1GB additional IDE heap usage during 500MB hprof analysis.
* **UI Thread Impact**: No observable lag in the Event Dispatch Thread (EDT) during typing.
