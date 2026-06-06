# Security Policy

## Supported Versions

We take the security of your development environment and your application data seriously. Currently, we provide security updates for the latest stable release of LeakLens.

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

## Reporting a Vulnerability

If you discover a potential security vulnerability within LeakLens, please **do not open a public issue**. Instead, follow the process below:

1.  **Email**: Send a detailed report to **vikasacsoni9211@gmail.com**.
2.  **Details**: Include a description of the vulnerability, steps to reproduce, and the potential impact.
3.  **Response**: You can expect an initial acknowledgement within **48 hours**.
4.  **Disclosure**: We request that you do not disclose the vulnerability publicly until we have had the opportunity to address it and release a fix.

## Our Commitment to Privacy

LeakLens is designed with a **Local-First** security model:

*   **Heap Analysis**: All Shark heap analysis and `.hprof` processing occur locally on your workstation. No heap data is ever sent to our servers.
*   **AI Feature (Opt-in)**: Leak traces are only sent to Google Gemini or OpenAI if you explicitly click the "Ask Gemini" button or enable background automation. 
*   **Anonymization**: By default, LeakLens anonymizes package names (e.g., `com.mycompany.app` becomes `app.package`) before providing data to AI models to protect your intellectual property.

## JetBrains Marketplace Security

LeakLens is regularly scanned by the **JetBrains Plugin Verifier** and marketplace security tools to ensure it adheres to the latest security standards for IntelliJ-based IDEs.
