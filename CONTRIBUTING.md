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

## 📬 Pull Request Process

1. **Branching**: Create a feature branch from `main` (e.g., `feature/awesome-new-detector`).
2. **Code Style**: Follow standard Kotlin/Java coding conventions.
3. **Commits**: Use clear, descriptive commit messages.
4. **Documentation**: Update `CHANGELOG.md` and any relevant docs if your change adds or modifies
   user-facing features.
5. **Submit**: Open a PR against the `main` branch. Provide screenshots or videos for UI changes.

## 🐞 Reporting Issues

Found a bug or have a feature request?
Please [open an issue](https://github.com/dev-vikas-soni/leak-lens/issues) with:

* A clear description of the problem.
* Steps to reproduce.
* Your IDE version and OS.
* (If applicable) The `.hprof` file or leak trace.

---
Thank you for being part of the LeakLens journey! 🧠💧
