<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# LeakLens Changelog

## [Unreleased]

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
