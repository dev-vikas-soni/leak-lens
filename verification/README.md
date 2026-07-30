# LeakLens Verification Platform 🛡️

A deterministic regression testing framework for the Shark analysis engine.

## Overview

This standalone JVM application verifies that LeakLens continues to identify memory leaks correctly
by comparing the output of the Shark engine against **Scenario Goldens**.

Instead of comparing binary files, we treat the HPROF as input and compare the **normalized semantic
signature** of the leak.

## Structure

- `/golden/<scenario_id>/`: A self-contained test scenario.
    - `input.hprof`: The binary heap dump fixture.
    - `expected.json`: The canonical normalized leak trace we expect the engine to produce.
- `/engine`: The Kotlin logic responsible for normalization and comparison.

## Usage

### Run all verifications

```bash
./gradlew :verification:verify
```

### Run a specific scenario

```bash
./gradlew :verification:verify -Pscenario=activity_leak
```

## How it Works

1. **Analyze**: The engine runs Shark against `input.hprof`.
2. **Normalize**: It strips volatile data (addresses, IDs) and builds a semantic signature (
   Class.field chain).
3. **Persist**: It saves the result to `verification/build/actual/<scenario>.actual.json`.
4. **Compare**: It performs a field-by-field comparison against `expected.json`.

## Adding a New Golden

1. Capture a heap dump from the `:sample-app`.
2. Create a folder `verification/golden/<my_scenario>/`.
3. Save the heap dump as `input.hprof` in that folder.
4. Run the verification. It will fail because `expected.json` is missing.
5. Copy the generated `verification/build/actual/<my_scenario>.actual.json` to
   `verification/golden/<my_scenario>/expected.json` after manual validation.
