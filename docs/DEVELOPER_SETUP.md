# Developer Setup

Follow these steps to contribute to LeakLens.

## Prerequisites

* **JDK 21**: Recommended distributions are Zulu or JetBrains Runtime.
* **IntelliJ IDEA**: Latest stable version.
* **Android SDK**: ADB must be in your system PATH.

## Building the Project

1. **Clone the repository**:
   ```bash
   git clone https://github.com/dev-vikas-soni/leak-lens.git
   cd leak-lens
   ```
2. **Import as Gradle Project**: Open IntelliJ IDEA and select the `build.gradle.kts` file.
3. **Run the Sandbox**: Execute the following task to launch a sandboxed IDE with LeakLens
   installed:
   ```bash
   ./gradlew runIde
   ```

## Running Tests

* **Unit Tests**: `./gradlew test`
* **Plugin Verification**: `./gradlew verifyPlugin`
* **Static Analysis**: `./gradlew detekt`

## Contributing a New Inspection

1. Define the rule in `src/main/kotlin/.../inspections/`.
2. Register the inspection in `src/main/resources/META-INF/plugin.xml`.
3. Add test cases in `src/test/testData/inspections/`.
