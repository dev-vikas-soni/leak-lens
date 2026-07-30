# Data Handling & Privacy

LeakLens follows a local-first architecture to ensure the security of your source code and heap
dumps.

## 1. Local Heap Analysis

* **Offline Analysis**: All heap analysis (Shark engine) is performed locally within the IDE
  process. No `.hprof` files are ever uploaded or transmitted.
* **Ephemeral Storage**: Temporary heap dumps are stored in the system's temporary directory and are
  managed by the configured retention policy (default: 5 snapshots).

## 2. AI Fix Assistant

LeakLens uses a clipboard-based interaction model for AI assistance:

* **Manual Trigger**: No data is sent to AI providers automatically. Communication is only initiated
  when you manually click "Ask Gemini."
* **Anonymization**: Before copying the leak trace to the clipboard, LeakLens strips package names (
  e.g., `com.mycompany.app` → `app.pkg`) to protect intellectual property.
* **Zero Background Uploads**: The plugin does not make direct network calls to LLM APIs. You
  maintain full control over the prompt before pasting it into your AI tool.

## 3. Telemetry

LeakLens collects **zero** telemetry, project metadata, or usage analytics.
