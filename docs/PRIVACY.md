# Data Handling, AI Integrations & Privacy Specification

LeakLens is designed with a local-first analysis model. However, optional AI features involve data processing or external API calls. This document provides an explicit specification of data handling practices, AI request construction, and privacy limitations.

---

## 1. Local Heap & Code Analysis

### Heap Dumps (HPROF)
- **Local Processing Only**: All binary HPROF heap dumps captured over ADB are processed locally on the developer's workstation using the embedded Shark 2.14 engine.
- **File Storage**: Captured `.hprof` files are saved temporarily to the operating system's temporary directory (`/tmp` or system equivalent) and deleted after analysis completes.
- **Zero Remote Uploads**: Binary heap dump files are **never** uploaded, backed up, or transmitted to any remote server or service.

### Static Code Analysis
- **Local AST Traversal**: Universal Abstract Syntax Tree (UAST) static inspections execute entirely within the local IDE process. Source code is not transmitted externally for static analysis.

---

## 2. AI Remediation Workflows

LeakLens offers two distinct mechanisms for AI-assisted leak remediation:

### A. Local Clipboard Workflow (`AiUtils.kt`)
- **Mechanism**: Triggered via "Ask Gemini" actions. LeakLens constructs a pre-formatted markdown prompt containing leak details and reference chains, and copies it directly to the system clipboard (`CopyPasteManager`).
- **Network Footprint**: **Zero network requests.** The clipboard workflow operates entirely locally on your machine.
- **User Interaction**: The developer pastes the copied prompt into Android Studio's Gemini tool window or any external chat UI.
- **Response Handling**: LeakLens does **not** receive, parse, or track responses from clipboard-based prompts.

### B. Direct API Mode (`AiAnalysisService.kt`)
- **Supported Providers**: OpenAI (`https://api.openai.com/v1/chat/completions`) and Google Gemini (`https://generativelanguage.googleapis.com/v1beta/models/...`).
- **Opt-In Execution**: Disabled by default (`aiEnabled = false`). Direct API calls occur **only** if the user explicitly enables AI features and configures an API key.
- **Transmitted Metadata**:
  - Leaking class name (e.g. `com.example.MainActivity`).
  - Leak severity level and retained byte size.
  - Retained reference chain (owning class names, reference field names, and reference types).
  - Full Shark stack trace or static inspection trace string.
- **Source Code Handling**: Source code files (`.kt` / `.java`) are **never** attached or transmitted directly by LeakLens.
- **API Key Security**: API keys are stored securely in IDE service storage (`AiCredentials.kt`) and passed exclusively in standard HTTP request headers (`Authorization: Bearer <key>` for OpenAI, `x-goog-api-key: <key>` for Gemini).

---

## 3. Anonymization Engine & Privacy Limitations

### Package Anonymization Mechanism (`aiAnonymizePackageNames`)
When package anonymization is enabled in LeakLens settings, the plugin passes class names and traces through regex replacement filters prior to prompt construction:
- `com.\w+.\w+` $\rightarrow$ `app.pkg`
- `org.\w+.\w+` $\rightarrow$ `lib.pkg`
- Class prefixes $\rightarrow$ `app.package.ClassName`

### Privacy Limitations & Disclaimers

> [!WARNING]
> **Best-Effort Anonymization**: Package sanitization uses basic regex patterns. It does **not** guarantee complete anonymization or zero data exposure. Custom class names, package structures, parameter string values, or stack trace identifiers may still be visible in transmitted prompts.

> [!IMPORTANT]
> - LeakLens does **not** claim complete data anonymization or zero privacy risk when direct API features are enabled.
> - Users working with proprietary, sensitive, or regulated codebases should review generated prompts before transmitting them to external AI APIs or pasting them into external AI interfaces.

---

## 4. Telemetry & Analytics

- **Zero Telemetry**: LeakLens collects **no** usage metrics, telemetry, crash reports, analytics, or project metadata.
- **Zero Usage Tracking**: The plugin does not report user activity to JetBrains, Google, OpenAI, or any third party.
