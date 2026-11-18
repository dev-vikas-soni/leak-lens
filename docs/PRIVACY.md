# Data Handling, AI Integrations & Privacy Specification

LeakLens is designed with a local-first analysis model. However, optional AI features involve data exchange with external services. This document provides a detailed specification of data handling practices, AI request construction, and privacy limitations.

---

## 1. Local Heap & Code Analysis

### Heap Dumps (HPROF)
- **Local Processing Only**: All binary HPROF heap dumps captured over ADB are processed locally on the developer's workstation using the embedded Shark engine.
- **File Storage**: Captured `.hprof` files are saved temporarily to the operating system's temporary directory (`/tmp` or system equivalent) and deleted after analysis completes.
- **Zero Remote Uploads**: Binary heap dump files are **never** uploaded, backed up, or transmitted to any remote server or service.

### Static Code Analysis
- **Local AST Traversal**: Universal Abstract Syntax Tree (UAST) static inspections execute entirely within the local IDE process. Source code is not transmitted externally for static analysis.

---

## 2. AI-Assisted Remediation Workflows

LeakLens offers two distinct mechanisms for AI-assisted leak remediation: **Direct API Integrations** and **Clipboard Workflow**. Both workflows are opt-in and user-initiated.

### A. Direct API Integrations (`AiAnalysisService.kt`)

- **Supported Providers**: OpenAI (`https://api.openai.com/v1/chat/completions`) and Google Gemini (`https://generativelanguage.googleapis.com/v1beta/models/...`).
- **Default State**: Disabled by default (`aiEnabled = false`). Network calls occur **only** if the user explicitly enables AI features and configures an API key.
- **API Key Handling**: API keys are stored using the IDE's secure state mechanism (`AiCredentials.kt`) and sent strictly in standard HTTP authorization headers (`Authorization: Bearer <key>` for OpenAI, `x-goog-api-key: <key>` for Gemini).
- **Data Transmitted**:
  - Leaking class name (e.g. `com.example.MainActivity`).
  - Leak severity level and retained byte size.
  - Library leak boolean status.
  - Formatted reference chain (owning class names, reference field names, and reference types).
  - Full Shark stack trace or static inspection trace string.
  - *Note*: Source code files are **not** attached directly, but code references contained within stack traces are transmitted.

### B. Clipboard Workflow (`AiUtils.kt`)

- **Mechanism**: Triggered via "Ask Gemini" actions. LeakLens constructs a formatted markdown prompt containing the leak summary, reference chain, and trace string, and copies it to the system clipboard (`CopyPasteManager`).
- **User Interaction**: LeakLens displays an informational dialog instructing the user to paste the prompt into Android Studio's built-in Gemini tool window or another AI interface.
- **Response Handling**: LeakLens does **not** automatically receive or process the response from the clipboard workflow. The interaction takes place entirely within the target AI chat UI.

---

## 3. Anonymization Engine & Privacy Limitations

### Package Anonymization Mechanism (`aiAnonymizePackageNames`)
When package anonymization is enabled in LeakLens settings, the plugin passes class names and traces through regex replacement filters prior to prompt construction:
- `com.\w+.\w+` $\rightarrow$ `app.pkg`
- `org.\w+.\w+` $\rightarrow$ `lib.pkg`
- Class prefixes $\rightarrow$ `app.package.ClassName`

### Privacy Limitations & Disclaimers

> [!WARNING]
> **Best-Effort Anonymization**: Package sanitization uses basic regex patterns. It does **not** guarantee total anonymization. Domain-specific package names, custom class names, parameter string values, or stack trace identifiers may still be visible in transmitted prompts.

> [!IMPORTANT]
> - LeakLens does **not** claim complete data anonymization or zero data exposure when AI features are enabled.
> - Users working with proprietary, sensitive, or regulated codebases should review generated prompts before transmitting them to external AI APIs or pasting them into external AI interfaces.

---

## 4. Telemetry & Analytics

- **Zero Telemetry**: LeakLens collects **no** usage metrics, telemetry, crash reports, analytics, or project metadata.
- **Zero Usage Tracking**: The plugin does not report user activity to JetBrains, Google, OpenAI, or any third party.
