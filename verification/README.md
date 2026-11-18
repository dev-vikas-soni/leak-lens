# LeakLens Verification Platform 🛡️

The `:verification` module is a standalone JVM regression testing framework designed to validate Shark heap analysis output against pre-recorded golden fixtures.

---

## Technical Concept

Rather than testing Shark against live devices during CI, the verification suite treats binary HPROF files as inputs and compares the **normalized semantic signature** (class chain, field names, leak cause) against canonical expectation files (`expected.json`).

---

## Directory Layout

```text
verification/
├── build.gradle.kts
├── src/main/kotlin/.../verification/
│   ├── GoldenComparator.kt
│   ├── LeakNormalizer.kt
│   ├── Models.kt
│   └── VerificationRunner.kt
└── golden/
    ├── activity_leak/
    │   ├── input.hprof
    │   └── expected.json
    ├── bitmap_leak/
    ├── compose_leak/
    ├── flow_leak/
    ├── fragment_leak/
    ├── singleton_leak/
    └── workmanager_leak/
```

---

## Usage

### Run Full Verification Suite
```bash
./gradlew :verification:verify
```

### Run Specific Scenario
```bash
./gradlew :verification:verify -Pscenario=activity_leak
```

---

## Status & Known Verification Notes

> [!WARNING]
> **Active Investigation**: The verification runner evaluates Shark 2.14 heap traversal against stored `.hprof` fixtures. In recent test runs, certain golden scenarios failed comparison due to fixture normalization mismatches or Shark object inspector filtering updates.

- The presence of the verification framework should **not** be interpreted as a claim that all golden fixtures currently pass without issue.
- Fixtures and expectation schemas are being updated to match current Shark 2.14 inspection outputs.
