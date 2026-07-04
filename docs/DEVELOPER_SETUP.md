# Developer Setup Guide 🛠️

This guide will help you set up your environment to contribute to LeakLens.

## 📋 Prerequisites

1. **JDK 21**: Required for building the plugin. We recommend
   using [Zulu](https://www.azul.com/downloads/?package=jdk#zulu)
   or [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime).
2. **IntelliJ IDEA**: Use the latest stable version of IntelliJ IDEA (Community or Ultimate).
3. **Android SDK**: Ensure `platform-tools` (ADB) is installed and available in your PATH.

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/dev-vikas-soni/leak-lens.git
cd leak-lens
```

### 2. Open in IntelliJ IDEA

- Import as a **Gradle Project**.
- Wait for the project sync to finish.

### 3. Run the Sandbox

- Open the **Gradle Tool Window**.
- Execute `Tasks -> intellij -> runIde`.
- This will launch a sandboxed version of Android Studio with LeakLens installed.

## 🧪 Running Tests

We use a combination of UAST unit tests and Shark integration tests.

- **Run all tests**: `./gradlew test`
- **Plugin Verification**: `./gradlew verifyPlugin`

## 🎨 Code Style & Quality

We enforce strict Kotlin standards via **Detekt** and **EditorConfig**.

- **Check style**: `./gradlew detekt`
- **Auto-fix style**: `./gradlew detekt --auto-correct`

## 🛠️ Modifying Inspections

1. Locate the inspection in `src/main/kotlin/com/github/devvikassoni/leaklens/inspections/`.
2. If adding a new rule, register it in `src/main/resources/META-INF/plugin.xml`.
3. Add a test case in
   `src/test/kotlin/com/github/devvikassoni/leaklens/inspections/UastInspectionsTest.kt` with a
   corresponding file in `src/test/testData/inspections/`.
