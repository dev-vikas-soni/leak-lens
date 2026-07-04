# LeakLens Privacy & AI Data Policy 🔒

LeakLens is built as **Developer Infrastructure**. We understand that code and heap dumps are
sensitive intellectual property. Our data policy follows a **Zero-Trust, Local-First** model.

## 1. Local-First Heap Analysis

* **Offline by Default**: 100% of the Shark analysis graph traversal is performed on your machine.
* **Zero File Uploads**: Your `.hprof` files never leave your workstation.
* **Ephemeral Storage**: Temporary files are cleared after analysis to prevent disk bloat.

## 2. AI Fix Assistant (Strictly Opt-In)

LeakLens uses AI to explain *why* a leak occurs. This feature is disabled by default.

### Data Sent (Only on request):

* **Anonymized Trace**: The reference chain (e.g., `ClassA -> FieldB -> ClassC`).
* **Contextual Snippet**: The specific line of code flagged by the inspection.

### Data NEVER Sent:

* **Object Values**: Strings, bitmaps, or sensitive user IDs from the heap are never extracted for
  AI.
* **Personal Data**: We do not collect telemetry, IDE usernames, or project paths.

## 3. Anonymization Engine

LeakLens includes a built-in anonymizer that runs **before** network transmission:

* **Package Stripping**: `com.yourcompany.SecretActivity` -> `app.pkg.SecretActivity`.
* **Library Genericization**: Known internal framework packages are labeled as `lib.pkg`.

## 4. GDPR & CCPA Compliance

Since LeakLens does not collect or store personal data (PII) on any central server, it is compliant
with GDPR and CCPA by design.

## 5. Offline Mode

If you work in a high-security environment, simply disable AI features in **Settings → LeakLens**.
The tool will function as a 100% offline static and runtime analysis engine.
