# LeakLens Sample Application & Test Suite 🧪

A Living Test Suite containing deterministic memory leaks for validating the LeakLens analysis
pipeline.

## Structure

- `/scenarios`: Isolated Android modules, each demonstrating one leak pattern.
- `/golden`: Pre-captured `.hprof` files and expected analysis results (`.json`).
- `/app`: Dashboard to trigger leaks manually.

## How to use

1. **Manual Verification**: Run `:app` and follow in-app instructions to trigger leaks.
2. **Regression Testing**: Run `./gradlew test` in the root project to verify Shark analysis against
   files in `/golden`.

## Adding a Scenario

1. Create a new module in `/scenarios`.
2. Implement the `LeakScenario` interface.
3. Capture a heap dump from the `:app`.
4. Create a folder in `verification/golden/<id>/`.
5. Save the heap dump as `input.hprof` in that folder.
6. Run the verification engine to generate and save `expected.json`.
