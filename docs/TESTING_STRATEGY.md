# Testing & Verification Strategy

LeakLens relies on a multi-tiered testing strategy to ensure static analysis accuracy, plugin stability, and heap analysis regression tracking.

---

## 1. Automated Testing Architecture

### A. UAST Inspection Unit Tests
- **Location**: `src/test/kotlin/com/github/devvikassoni/leaklens/inspections/`
- **Framework**: `BasePlatformTestCase` (IntelliJ Light Code Insight Fixture Test Case).
- **Scope**: Validates static inspection highlights and quick-fix intentions against Kotlin and Java code snippets.
- **Test Scenarios**:
  - **Positive Cases**: Confirms that leaky code patterns (e.g., storing Activity in a static field or passing Activity to a Singleton) trigger visual diagnostics.
  - **Negative Cases**: Confirms that safe patterns (e.g., using `applicationContext` or wrapping Flow collection in `repeatOnLifecycle`) produce zero warnings.

### B. Standalone Verification Engine (`:verification`)
- **Location**: `verification/` subproject.
- **Framework**: JVM standalone test runner wrapping Shark 2.14.
- **Purpose**: Runs Shark against pre-captured binary HPROF heap dumps (`golden/<scenario>/input.hprof`), normalizes the output, and compares the resulting leak trace structure against expected JSON fixtures (`golden/<scenario>/expected.json`).

```bash
# Run golden verification task
./gradlew :verification:verify

# Run specific scenario
./gradlew :verification:verify -Pscenario=activity_leak
```

> [!IMPORTANT]
> **Current Verification Suite Status**: The golden verification suite provides a deterministic regression framework for Shark integration. During recent validation audits, test runs observed failures (e.g., `No leaks found in heap` or normalized signature mismatches). Fixture compatibility and Shark object inspector matching are under active investigation. The verification platform is documented as a regression testing tool, not as evidence that all golden scenarios currently pass out-of-the-box.

### C. IntelliJ Plugin Verification (`verifyPlugin`)
- **Gradle Task**: `./gradlew verifyPlugin`
- **Purpose**: Runs the JetBrains Plugin Verifier tool to check binary compatibility across supported IDE versions (Android Studio and IntelliJ IDEA) and flag deprecated API usages.

---

## 2. Manual Verification Workflow

To test host-side heap capture and analysis manually:
1. Deploy the sample application (`:sample-app`) to a connected Android device or emulator.
2. Trigger a memory leak scenario in the sample app (e.g., navigating to the Activity Leak or Singleton Leak screen).
3. Open the **LeakLens** tool window in Android Studio and click **Dump Heap Now**.
4. Verify that LeakLens successfully pulls the `.hprof` file over ADB, parses the graph using Shark, and displays the reference chain in the UI.
