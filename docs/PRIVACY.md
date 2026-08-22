# Data Handling & Privacy

LeakLens is designed with a local-first architecture to protect source code and sensitive
application data.

## 1. Local Heap Analysis

* **Offline Traversal**: Heap analysis (via the Shark engine) is performed locally on your machine.
  **No HPROF files are ever uploaded or transmitted.**
* **Storage**: Temporary heap dumps are stored in the system's `/tmp` directory. LeakLens enforces a
  retention policy (default: 5 snapshots) to prevent disk bloat.

## 2. AI Fix Assistant

AI interactions are strictly user-initiated and governed by transparency controls:

* **Manual Activation**: No data is sent to AI models automatically. Communication only occurs when
  you manually click "Ask Gemini" or "Ask AI."
* **Anonymization**: Before a leak trace is processed, it is passed through a regex-based engine
  that strips internal package names (e.g., `com.company.project` → `app.pkg`).
* **Data Control**: LeakLens does not have background upload capabilities. You maintain full
  visibility into the information being processed.

## 3. Telemetry

The plugin collects **zero telemetry**, usage analytics, or project metadata.
