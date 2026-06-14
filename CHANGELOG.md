<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# LeakLens Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.4...HEAD
[0.1.4]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/dev-vikas-soni/leak-lens/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/dev-vikas-soni/leak-lens/commits/0.1.0
