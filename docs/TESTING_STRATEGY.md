# Testing Strategy

LeakLens uses a multi-layered testing approach to ensure stability across different IntelliJ
Platform versions.

## 1. Automated Testing Layers

### UAST Unit Tests

Located in `src/test/kotlin/.../inspections/`. These tests use the IntelliJ Platform testing
framework to verify that static inspections correctly identify and highlight leaking patterns in
Kotlin and Java source code.

### Shark Integration Tests

Verify the host-side heap analysis engine. We use a set of golden `.hprof` files to ensure that the
Shark bridge correctly identifies signatures and builds reference chains.

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
