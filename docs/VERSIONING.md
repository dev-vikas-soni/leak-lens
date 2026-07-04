# Versioning Strategy

LeakLens follows [Semantic Versioning 2.0.0](https://semver.org/).

## Version Format

`MAJOR.MINOR.PATCH`

### Major Version (`1.0.0`)

* Significant architectural shifts (e.g., migrating from pure Android support to KMP).
* Removing support for a major IntelliJ Platform baseline (e.g., dropping 2023.x).
* Breaking changes to the CLI or SARIF export format.

### Minor Version (`0.2.0`)

* New leak inspections.
* New AI fix rules.
* Integration with new Android Studio features (e.g., Ladybug, Meerkat).
* Performance optimizations.

### Patch Version (`0.1.1`)

* Bug fixes in inspections (reducing false positives).
* Documentation updates.
* Metadata/SEO improvements for JetBrains Marketplace.

## Platform Compatibility

LeakLens versions are tested against specific IntelliJ Platform ranges. The supported range is
always declared in the `plugin.xml` and validated by the `verifyPlugin` CI task.
