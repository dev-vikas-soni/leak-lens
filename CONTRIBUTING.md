# Contributing to LeakLens 🚀

Thank you for your interest in improving LeakLens! We welcome contributions from the community to
help make Android memory management effortless.

## 🛠️ Development Environment

LeakLens is built using the **IntelliJ Platform Gradle Plugin**. To set up your environment:

1. **Android Studio / IntelliJ IDEA**: Use the latest stable version of Android Studio or IntelliJ
   IDEA (Community or Ultimate).
2. **JDK 21**: Ensure you have JDK 21 installed and configured as your project SDK.
3. **Clone & Build**:
   ```bash
   git clone https://github.com/dev-vikas-soni/leak-lens.git
   cd leak-lens
   ./gradlew buildPlugin
   ```
4. **Run with Plugin**:
   ```bash
   ./gradlew runIde
   ```
   This will launch a sandboxed instance of Android Studio with the plugin installed.

## 🧪 Testing

We take stability seriously. Please ensure all tests pass before submitting a PR:

* **Unit & Integration Tests**: `./gradlew test`
* **Plugin Verification**: `./gradlew verifyPlugin` (checks for internal API usage and compatibility
  across IDE versions).

If you are adding a new inspection, please include a test case in
`src/test/kotlin/com/github/devvikassoni/leaklens/inspections/`.

## 🏗️ Architecture for Contributors

Before diving into the code, please review
the [Architectural Overview](docs/ARCHITECTURAL_OVERVIEW.md).

### Key Components:

- **`AdbHeapDumpService`**: Handles all communication with the Android Debug Bridge.
- **`SharkAnalysisService`**: The bridge to the Shark heap analysis engine.
- **`LeakLensProjectService`**: The central state manager (StateFlow) for all detected leaks.
- **`inspections/`**: Contains UAST-based static analysis rules.

## 📬 Pull Request Review Process

To maintain the stability of LeakLens, all PRs undergo a technical review:

1. **Automated Checks**: PRs must pass the Build, Detekt, and Plugin Verification workflows.
2. **Manual Review**: At least one maintainer will review the code for architectural alignment.
3. **Verification**:
    * **Inspections**: New inspections must include a test data file.
    * **UI/ADB**: UI changes or ADB refactors must be accompanied by a screen recording or
      verification log from a physical device/emulator.

## ✍️ Creating a New Inspection

To maintain our sub-15ms typing latency on 1M LOC projects, all new inspections **must** follow the
hinted visitor pattern.

1. **Define the Problem**: Create a class in `inspections/` inheriting from `LocalInspectionTool`.
2. **Use Hinted Visitors**: Do not visit every PSI element. Use `UastHintedVisitorAdapter` to target
   only specific nodes (e.g., `UField` or `UCallExpression`).
3. **Semantic Resolution**: Use `com.intellij.psi.util.InheritanceUtil` with fully qualified names.
   Avoid `.text.contains()` which leads to false positives.
4. **Register**: Add the tool to `src/main/resources/META-INF/plugin.xml`.
5. **Test**: Add a `.kt` sample to `src/test/testData/inspections/` and verify highlighting in
   `UastInspectionsTest`.

## 🎨 Coding Standards

- **Structured Concurrency**: Use `scope.launch` within project services; never use `GlobalScope`.
- **Non-blocking IO**: All ADB/Shell commands must be run via `AdbFacade` on a background thread.
- **PSI Transformations**: Quick fixes must use `KtPsiFactory` or `PsiElementFactory`. Direct string
  replacement in documents is prohibited.

## 🐞 Reporting Issues

Found a bug or have a feature request?
Please [open an issue](https://github.com/dev-vikas-soni/leak-lens/issues) with:

* A clear description of the problem.
* Steps to reproduce.
* Your IDE version and OS.
* (If applicable) The `.hprof` file or leak trace.

---
Thank you for being part of the LeakLens journey! 🧠💧
