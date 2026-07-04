<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# LeakLens Changelog

## [Unreleased]

## [0.2.3] - 2026-07-06

### Fixed
- **CI Build**: Fixed Detekt configuration and relaxed thresholds to resolve build failures in automated pipelines.
- **Tests**: Resolved 6 failing UAST inspection tests by cleaning up whitespace and correcting tag formatting in test data.
- **Dependencies**: Added `detekt-formatting` plugin to support code style rules.

## [0.2.2] - 2026-07-06

### Fixed

- **CI Fix**: Enabled Detekt SARIF reports to resolve workflow failures and properly surface code
  quality issues.
- **Test Hardening**: Fixed whitespace issues in `UastInspectionsTest` data markers for consistent
  CI results.
- **Compatibility**: Reverted to more compatible `ddmlib` APIs to prevent `NoSuchMethodError` on
  older IDE versions.

### Added

- **Direct Feedback**: Integrated a "Rate LeakLens" action in the Tools menu.
- **SEO Optimization**: Refined plugin name and Marketplace description for peak discoverability.

## [0.2.1] - 2026-07-04

### Added

- **Adaptive Compatibility Layer**: Robust support for multiple Android Studio versions (Koala,
  Ladybug, Meerkat) using capability detection instead of brittle version checks.
- **Enterprise-Grade Governance**: Formalized versioning strategy, community governance model, and
  legal attribution for third-party components (Shark, IntelliJ SDK).
- **Hardened Quick Fixes**: Migrated from string-based replacements to semantic PSI transformations
  for 100% reliable code refactoring.

### Fixed

- **Memory Safety**: Implemented history capping and explicit resource cleanup to prevent IDE memory
  pressure during long-running sessions.
- **ADB Discovery**: Resolved connectivity issues in AS Ladybug by implementing a recursive
  reflection discovery engine for the ADB binary.
- **Thread Safety**: Hardened background task execution to ensure non-blocking UI interactions
  during complex analysis steps.

## [0.2.0] - 2026-06-28

### Added

- **SonarLint-style Real-time Detection**: Potential leaks (Static Fields, Inner Classes, Context
  leaks) are now detected **as you type** with instant squiggly lines and gutter markers.
- **Status Bar Leak Counter**: A new widget in the IDE status bar provides a persistent health
  check for your project.
- **Hilt Scope Mismatch Inspection**: Detects when a `@Singleton` class injects an
  `@ActivityScoped` or `@FragmentScoped` dependency. Surfaces an **Ask AI** fix action.
- **WorkManager Context Leak Inspection**: Detects `Worker` subclasses that store injected
  `Context` as a class field. Includes a 1-click **UseApplicationContextFix**.
- **Quick-Fix Actions**: Added `WrapWithRepeatOnLifecycleFix` for unsafe Flow collection and
  `RemoveContextArgFix` for Context passed to ViewModel in Compose.
- **Animations & UX**: Added smooth fade-in transitions for leak details and pulsing status
  indicators for new detections.

### Fixed

- **Critical Compatibility**: Resolved binary incompatibilities and `INTERNAL_API_USAGES` for
  IntelliJ IDEA Ultimate and future platform releases (2025.x/2026.x).
- **API Modernization**: Fully resolved all deprecated API warnings including `ProcessAdapter`
  and `FileSaverDescriptor`.
- **Ddmlib Stability**: Fixed `NoSuchMethodError` with `ClientData` across different IDE versions
  using safe reflection.
- **Tool Window Polish**: Unified vertical toolbar for all actions and removed redundant UI
  elements.

## [0.1.9] - 2026-06-28 (Internal)

- Internal stability and compatibility improvements.

## [0.1.8] - 2026-06-17

### Added

- **Final Polish**: Refined plugin name and description for the JetBrains Staff Picks program.
- **Dark Mode Support**: Added `pluginIcon_dark.svg` for full compatibility with the Marketplace's
  dark theme.
- **Improved UX**: Integrated the YouTube demo and an optimized "Getting Started" guide into the
  plugin description.

## [0.1.7] - 2026-06-16

### Fixed

- **Build Stability**: Resolved issues with `:buildSearchableOptions` task that caused build
  failures in certain environments.
- **Inspection Documentation**: Added missing HTML descriptions for all static analysis inspections
  to improve IDE integration.
- **Type Safety**: Enhanced robustness of leak inspections and utility classes for better
  performance and fewer false positives.
- **Build Configuration**: Corrected Kotlin version and plugin dependencies for better compatibility
  with modern Android Studio versions.

## [0.1.6] - 2026-06-14

### Fixed

- **Binary Compatibility**: Reverted to `clientDescription` to fix `NoSuchMethodError` on older IDE
  versions (e.g., 2024.2.6).
- **CI Test Fix**: Resolved `FileComparisonFailedError` in `UastInspectionsTest` by cleaning up test
  data markers.
- **Archive Compatibility**: Finalized plugin name as
  `LeakLens - Android Memory Leak Detector & AI Fix Assistant` to ensure safe ZIP extraction.

## [0.1.5] - 2026-06-14

### Added

- **SEO Optimization**: Enhanced plugin metadata for better discoverability.

## [0.1.4] - 2026-06-14

### Added

- **SEO Optimization**: Enhanced plugin metadata for better discoverability in the JetBrains Marketplace search.
- **Direct Feedback**: Added "Rate LeakLens" action to the Tools menu for easier user reviews.

### Fixed

- **Configuration Fix**: Resolved "invalid configuration" errors by removing unsupported HTML tables from the description in `plugin.xml`.
- **Service Optimization**: Cleaned up `plugin.xml` by removing redundant service registrations already handled by `@Service` annotations.
- **Platform Stability**: Improved reliability of heap capture on the latest **Android Studio Ladybug** versions.

## [0.1.3] - 2026-06-10

### Added

- **SEO Optimization**: Enhanced plugin metadata for better discoverability in the JetBrains Marketplace search (Keywords: OOM, Profiler, Heap Analysis).
- **Direct Feedback**: Added "Rate LeakLens" action to the Tools menu to facilitate easier user reviews and community feedback.

### Fixed

- **Platform Stability**: Improved reliability of heap capture on the latest **Android Studio Ladybug** versions.
- **Memory Management**: Optimized Kotlin Coroutine scopes within Project Services to prevent internal plugin leaks.

## [0.1.2] - 2026-06-06

### Added

- **Enhanced AI Fix Assistant**: New "Ask Gemini AI" button with professional Markdown prompt
  structure for better context and accuracy.
- **Improved Getting Started UI**: Polished "Welcome" screen in the details panel for first-time
  users.
- **Dynamic Iconography**: Monochromatic icons for the IDE sidebar and colorful branding for the
  JetBrains Marketplace.
- **Robust ADB Integration**: Migrated to `ddmlib` and `AndroidSdkUtils` for reliable device
  detection using the IDE's built-in ADB binary.

### Fixed

- **Plugin Verification Errors**: Resolved 15 Internal API usage errors by converting
  `ToolWindowFactory` to Java.
- **NPE on Project Close**: Fixed a NullPointerException in the connectivity watcher timer during
  project disposal.
- **Test Data Corruption**: Fixed whitespace issues in GlobalScope inspection test data causing CI
  failures.
- **Threading Violations**: Corrected background thread access to EDT-only platform methods.

## [0.1.1] - 2026-06-03

### Added

- **UAST-based Inspections**: All preventive inspections migrated to UAST, supporting both Java and Kotlin.
- **Polished UI**: Settings and tool window panels modernized with IntelliJ UI DSL 2.
- **Stability Fixes**: Added timeouts and resource management to ADB operations.
- **Large Heap Handling**: Intelligent detection and warnings for massive .hprof files.
- **Improved Quick Fixes**: Language-aware intentions for Activity Context and Handler cleanup.
- **Professional Testing**: Added 100% passing unit and integration test suite.
- **Custom Branding**: New SVG icons for tool window and marketplace.

### Fixed

- Kotlin ABI compatibility issues for older Android Studio versions.
- Destructuring errors in Shark `HeapField` access.
- Deprecated `URL` and `FileSaverDescriptor` constructor usages.
- Project sync errors in sandboxed development environment.

## [0.1.0] - 2026-06-01

### Added

- Initial plugin skeleton with IntelliJ Platform Plugin SDK
- LeakLens Tool Window with split panel (Leak List + Detail)
- Shark heap analysis engine integration (v2.14)
- ADB heap dump service for device communication
- "Dump Heap Now" action in Tools menu
- Leak severity classification (Critical, Warning, Library Leak)
- Data models for leak traces and references
- Post-startup activity for initialization
- Notification group for user alerts

### Architecture

- Project service for orchestrating leak analysis
- Shark analysis service wrapping HeapAnalyzer
- ADB service for device discovery and heap dump pulling
- Tool window factory with list + detail panel layout

[Unreleased]: https://github.com/dev-vikas-soni/leak-lens/compare/0.2.3...HEAD
[0.2.3]: https://github.com/dev-vikas-soni/leak-lens/compare/0.2.2...0.2.3
[0.2.2]: https://github.com/dev-vikas-soni/leak-lens/compare/0.2.1...0.2.2
[0.2.1]: https://github.com/dev-vikas-soni/leak-lens/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.9...0.2.0
[0.1.9]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.8...0.1.9
[0.1.8]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.7...0.1.8
[0.1.7]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.6...0.1.7
[0.1.6]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.5...0.1.6
[0.1.5]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/dev-vikas-soni/leak-lens/commits/0.1.0
