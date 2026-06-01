# LeakLens 🧠💧

> Android Studio IDE Plugin for Memory Leak Detection & Fix Suggestions

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-blue)](https://plugins.jetbrains.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Phase](https://img.shields.io/badge/Status-v1.0.0-brightgreen)](CHANGELOG.md)

---

## Overview

**LeakLens** is a standalone Android Studio/IntelliJ plugin (like SonarLint) that integrates LeakCanary's heap analysis engine (Shark) directly into the IDE. It provides:

- 🔍 **Runtime leak detection** — Shark-powered heap analysis
- 🛡️ **Preventive static analysis** — 6 inspections catching leaks at write-time
- 💡 **Fix suggestions** — 11 pattern rules + optional AI
- 📊 **Real-time monitoring** — Live memory graph + auto-trigger
- 🎯 **One-click navigation** — Leak trace → source code
- 📋 **CI-ready reports** — HTML, JSON, SARIF export
- 🤝 **Team collaboration** — VCS-tracked baselines

---

## Documentation

| Document | Description |
|----------|-------------|
| [User Manual](docs/USER_MANUAL.md) | Complete usage guide with all features |
| [Testing Strategy](docs/TESTING_STRATEGY.md) | Manual testing checklist + E2E scenarios |
| [Architecture](docs/ARCHITECTURE.md) | Technical deep-dive, data flow, threading |
| [Changelog](CHANGELOG.md) | Version history with all changes |

---

## Quick Start

```bash
# Build
git clone https://github.com/dev-vikas-soni/leak-lens.git
cd leak-lens
./gradlew buildPlugin

# Install: Settings → Plugins → ⚙ → Install from Disk → build/distributions/*.zip
# Then: View → Tool Windows → LeakLens
```

**First analysis:**
1. Connect device/emulator with debug app
2. **Tools → LeakLens → Dump Heap Now**
3. View results in LeakLens tool window

---

## Features at a Glance

| Feature | Description |
|---------|-------------|
| Heap dump capture | Manual trigger, auto-detect via LeakCanary logcat, .hprof import |
| Shark analysis | Full LeakCanary heap analyzer running in IDE process |
| Leak dashboard | Tree view grouped by severity (🔴🟡🟢) |
| Source navigation | Click class names in trace → jumps to source |
| Gutter icons | Warning markers on leak-related classes |
| Fix suggestions | Explanation + before/after code for 11 common patterns |
| Quick fixes | Alt+Enter one-click fix application |
| AI suggestions | Optional OpenAI/Gemini for novel patterns (🤖 marked) |
| Static inspections | 6 write-time checks (like Lint) |
| Memory monitor | Live Java Heap/Native/PSS graph |
| Auto-trigger | Heap dump when threshold exceeded |
| Deobfuscation | R8/ProGuard mapping.txt support |
| Report export | HTML, JSON, SARIF (GitHub/SonarQube) |
| Baselines | VCS-tracked `leak-baseline.json` |
| Persistent history | Analysis history survives IDE restart |
| Settings UI | Full configuration page (Settings → Tools → LeakLens) |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│              Android Studio (IntelliJ Platform)       │
├─────────────────────────────────────────────────────┤
│  PRESENTATION   │  Inspections  │  Gutter Markers   │
│  (Tool Window)  │  (6 checks)   │  (Line icons)     │
├─────────────────┴───────────────┴───────────────────┤
│  SERVICES: ProjectService, Coordinator, Navigation   │
├─────────────────────────────────────────────────────┤
│  ENGINES: Shark, FixRules(11), AI, Deobfuscation     │
├─────────────────────────────────────────────────────┤
│  INFRA: ADB, Logcat, MemoryMonitor, Baseline         │
├─────────────────────────────────────────────────────┤
│  PERSISTENCE: leaklens.xml, leak-baseline.json       │
└──────────────────────────┬──────────────────────────┘
                           │ ADB
                    ┌──────▼──────┐
                    │   Device    │
                    └─────────────┘
```

---

## Project Structure (44 files, 13 packages)

```
src/main/kotlin/com/github/devvikassoni/leaklens/
├── actions/         (6)  # DumpHeap, Import, AutoDetect, Monitor, Export, Baseline
├── ai/              (1)  # AI suggestion service (OpenAI/Gemini)
├── baseline/        (1)  # VCS-tracked leak suppression
├── deobfuscation/   (1)  # R8/ProGuard mapping support
├── fix/             (3)  # 11 rules + 3 quick-fix intentions
├── gutter/          (1)  # Editor line markers
├── inspections/     (6)  # Preventive static analysis
├── model/           (3)  # Data models
├── monitoring/      (2)  # Real-time memory graph
├── reporting/       (1)  # HTML/JSON/SARIF export
├── services/        (6)  # Core services + coordinator
├── settings/        (2)  # Persistent config + UI
├── shark/           (1)  # Custom ObjectInspectors
├── startup/         (1)  # Post-startup init
└── toolWindow/      (6)  # UI panels + factory
```

---

## Roadmap

- [x] Phase 1: Plugin skeleton & Shark integration
- [x] Phase 2: Heap dump capture & full analysis pipeline
- [x] Phase 3: Tool window UI with source navigation
- [x] Phase 4: Fix suggestion engine (static rules + optional AI)
- [x] Phase 5: Static analysis inspections (preventive)
- [x] Phase 6: Real-time device monitoring (memory graph, auto-trigger)
- [x] Phase 7: Collaboration & CI (reports, SARIF, baselines, deobfuscation)
- [x] Phase 8: Polish & marketplace release

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Platform | IntelliJ Platform SDK (2023.2+) |
| Language | Kotlin 100% |
| Build | Gradle (Kotlin DSL) + IntelliJ Platform Gradle Plugin |
| Analysis | Shark 2.14 (LeakCanary heap analyzer) |
| ADB | ProcessBuilder-based adb commands |
| UI | Swing (JTree, JTextPane, custom Canvas) |
| State | Kotlin Coroutines StateFlow |
| Persistence | PersistentStateComponent (XML) |
| AI | OpenAI GPT-4o-mini / Google Gemini (opt-in) |

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run `./gradlew buildPlugin` to verify
4. Submit a Pull Request

See [Testing Strategy](docs/TESTING_STRATEGY.md) for the full test checklist.

---

## License

Apache 2.0 — see [LICENSE](LICENSE) for details.
