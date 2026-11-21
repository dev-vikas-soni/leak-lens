# LeakLens Roadmap: Android Memory Intelligence & Engineering Platform

> **LeakLens** connects static memory risk analysis, runtime observability, heap investigation, AI-assisted root-cause analysis, fix verification, and CI/CD regression prevention into a unified developer tool for Android engineers, platform teams, and enterprise codebases.

---

## 1. Product Direction & Core Principles

LeakLens bridges the gap between compile-time code authoring and runtime heap evidence. Rather than operating as a standalone leak detector or duplicating Android Studio Profiler, LeakLens provides:
1. **Static Memory Risk Analysis**: Catching structural retention hazards in source code before execution.
2. **Zero-SDK Host-Side Heap Analysis**: Offloading HPROF parsing via ADB to host JVM using Shark 2.14.
3. **Runtime & CI Observability**: Transitioning from IDE-only troubleshooting to automated regression prevention and CI gating.

### Strategic Product Principles
- **Evidence over assumptions**: Distinguish static retention risk, runtime observation, heap evidence, and AI hypotheses.
- **Explainable analysis**: Every inspection provides clear reasoning, ownership boundaries, confidence levels, and false-positive considerations.
- **Accuracy over inspection count**: Prioritize context-aware analysis and low false-positive rates over broad rule volume.
- **Complement the Android ecosystem**: Integrate seamlessly with Android Studio, LeakCanary, Shark, and Perfetto.
- **Privacy-first AI**: Local clipboard workflows and transparent, opt-in direct API integration with explicit data controls (no source or heap upload by default).

---

## 2. Status Categories

- `DONE` — Implemented and verified in the repository.
- `IN PROGRESS` — Implementation actively exists but is incomplete.
- `NEXT` — Highest-priority upcoming work.
- `PLANNED` — Accepted product direction.
- `RESEARCH` — Requires feasibility investigation or validation.
- `EXPERIMENTAL` — Prototype or uncertain capability.
- `DEFERRED` — Deliberately postponed.
- `OUT OF SCOPE` — Explicitly outside LeakLens's direction.

---

## 3. Current Capabilities (Verified Baseline)

| Capability | Status | Implementation Reference | Description |
|:---|:---:|:---|:---|
| **Semantic Lifetime Engine** | `DONE` | `com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine` | Hierarchical lifetime scoping (Process, Application, Singleton, Worker, Service, ViewModel, Activity, Fragment, Composition, View, Local). |
| **Static Memory Inspections** | `DONE` | `com.github.devvikassoni.leaklens.inspections.*` | 12+ UAST inspections covering static activity references, singleton leaks, Compose context captures, coroutine scopes, Hilt scope mismatches, and missing callbacks. |
| **Zero-SDK Host Heap Analysis** | `DONE` | `com.github.devvikassoni.leaklens.services.AdbHeapDumpService`, `SharkAnalysisService` | Captures HPROF via ADB (`am dumpheap`) and parses reachability using Shark 2.14 on host JVM with zero runtime SDK dependency. |
| **R8 / ProGuard Deobfuscation** | `DONE` | `com.github.devvikassoni.leaklens.deobfuscation.DeobfuscationService` | Parses `mapping.txt` build artifacts to deobfuscate class, method, and field names in heap reference chains. |
| **Periodic Memory Monitoring** | `DONE` | `com.github.devvikassoni.leaklens.monitoring.DeviceMemoryMonitor` | Polls device memory statistics (`dumpsys meminfo`) and renders historical memory trends (`MemoryGraphPanel`). |
| **AI Remediation & Clipboard** | `DONE` | `com.github.devvikassoni.leaklens.ai.AiAnalysisService`, `AskGeminiFix` | Local clipboard markdown generation and optional direct API integration (OpenAI/Gemini) with best-effort package anonymization. |
| **Baseline Management** | `DONE` | `com.github.devvikassoni.leaklens.baseline.LeakBaselineManager` | Deduplicates and tracks known issues against baseline snapshots. |
| **Unified Tool Window & Gutter** | `DONE` | `LeakLensToolWindowFactory`, `LeakGutterLineMarkerProvider` | IDE tool window aggregating static findings and heap traces with inline gutter markers. |

---

## 4. Roadmap at a Glance

| Priority | Initiative | Status | Complexity | Dependencies | Target Value |
|:---|:---|:---:|:---:|:---|:---|
| **P0** | **Data-Flow Escaping Reference Tracking** | `NEXT` | M | Lifetime Engine, UAST Indexing | Track object escaping local/composition scope boundaries across class calls. |
| **P0** | **Golden Fixture Verification Stability** | `IN PROGRESS` | M | `:verification` module | Stabilize regression tests for Shark object inspection against reference hprofs. |
| **P1** | **Analysis Core Extraction & Headless API** | `PLANNED` | L | IDE-specific module decoupling | Separate UAST/lifetime engine from IntelliJ plugin framework for CLI usage. |
| **P1** | **LeakLens CLI & JSON/SARIF Reporting** | `PLANNED` | L | Analysis Core Extraction | Enable headless project scanning and standard SARIF report generation. |
| **P1** | **GitHub Actions PR Integration** | `PLANNED` | M | LeakLens CLI | Automated pull request comment annotations and baseline checking in CI. |
| **P2** | **Differential Heap Analysis** | `PLANNED` | L | Shark Engine, Session Store | Compare baseline vs. candidate HPROF snapshots to isolate object growth. |
| **P2** | **Scenario-Based Memory Sessions** | `RESEARCH` | M | Device Monitor, ADB | Correlate user navigation actions with memory spikes and automated heap captures. |
| **P3** | **Platform Apps & AOSP Analysis Track** | `RESEARCH` | XL | Platform Lifetime Model | Adapt lifetime heuristics for `system_server`, SystemUI, and Binder reference chains. |
| **P3** | **Evidence-Based AI Investigation Agent** | `PLANNED` | L | Evidence Model, AI Service | Multi-turn reasoning correlating static findings, heap paths, and fix verification. |

---

## 5. Detailed Roadmap Phases

### Phase 1 — Analysis Quality & Trust (Foundation)
*Focus: Enhancing static analysis precision, reducing false positives, and solidifying verification.*

- **Data-Flow Escaping Reference Tracking (`NEXT`)**:
  - *Problem*: Current static inspections evaluate lexical scope but miss references escaping via method returns or builder patterns.
  - *Proposed*: UAST data-flow tracking for variable assignments and method returns crossing lifetime boundaries.
  - *Success Criteria*: Detects hidden leaks where transient objects escape into singleton or static caches.
- **Golden Verification Suite Stabilization (`IN PROGRESS`)**:
  - *Problem*: The `:verification` subproject fixture suite requires alignment with updated Shark 2.14 inspector schemas.
  - *Proposed*: Fix test harness fixtures, update JSON expectation schemas, and enforce green local runs in CI.

### Phase 2 — Heap Intelligence & Investigation
*Focus: Deepening host-side heap analytics and comparative object tracking.*

- **Interactive Retention Path Explorer (`PLANNED`)**:
  - *Problem*: Linear reference chain views lack context on object dominator trees and GC root paths.
  - *Proposed*: Tree-based visual navigator for Shark reference chains with retained size breakdown.
- **Differential Heap Snapshot Comparison (`PLANNED`)**:
  - *Problem*: Single heap dumps show current state but cannot isolate allocations caused by specific user interactions.
  - *Proposed*: Capture baseline and candidate HPROF dumps, compute class histogram growth and persistent Activity/View counts.

### Phase 3 — Analysis Core & CLI
*Focus: Decoupling analysis logic from the IntelliJ platform to enable headless execution.*

- **Analysis Core Module Extraction (`PLANNED`)**:
  - *Problem*: Static inspection rules and lifetime models are bound to IntelliJ plugin APIs (`com.intellij.*`).
  - *Proposed*: Extract a pure Kotlin `leak-lens-core` module containing UAST/PSI abstractions, lifetime rules, and Shark analysis runners.
- **LeakLens CLI (`PLANNED`)**:
  - *Problem*: Developers cannot run LeakLens rules outside of Android Studio.
  - *Proposed*: Standalone CLI tool (`leaklens-cli`) executing static scans and heap analysis with CLI arguments, configuration files, and deterministic exit codes.

### Phase 4 — CI/CD & PR Integration
*Focus: Automating memory checks in enterprise pull request pipelines.*

- **SARIF & JSON Report Generation (`PLANNED`)**:
  - *Problem*: CI systems require standardized artifact formats.
  - *Proposed*: Output findings in SARIF (Static Analysis Results Interchange Format) and structured JSON for CI ingestion.
- **GitHub Actions & Baseline Enforcement (`PLANNED`)**:
  - *Problem*: Unfiltered static rules create noise on legacy codebases.
  - *Proposed*: Official GitHub Action supporting baseline suppression files, failing builds only on new violations.

### Phase 5 — Runtime & Memory Regression Testing
*Focus: Session-based observability and scenario regression detection.*

- **Scenario-Based Memory Sessions (`RESEARCH`)**:
  - *Problem*: Periodic polling lacks workload context.
  - *Proposed*: Session recorder linking user navigation steps or UI automation markers with memory graphs and automatic heap snapshots.
- **Build-to-Build Memory Regression Tracking (`RESEARCH`)**:
  - *Problem*: Memory regressions are discovered late during QA or production releases.
  - *Proposed*: Track peak RSS/PSS and retained object counts across test runs and CI benchmarks.

### Phase 6 — Native Memory Intelligence
*Focus: Expanding heap analysis into JNI and native allocations.*

- **Native Allocation Monitoring (`RESEARCH`)**:
  - *Problem*: Java/Kotlin heap analysis misses JNI global refs, Direct ByteBuffers, and native bitmap leaks.
  - *Proposed*: Integration hooks for `heapprofd` and Perfetto native heap profiles on debuggable builds.

### Phase 7 — Platform Apps & AOSP Research Track
*Focus: Exploring adaptation for system applications and AOSP components.*

- **Platform Lifetime Model (`RESEARCH`)**:
  - *Problem*: App-level lifetimes (`Activity`, `Fragment`) do not apply to system server, SystemUI, or framework services.
  - *Proposed*: Define platform lifetime domains (Boot, Process, `system_server`, User Session, Window) and Binder `DeathRecipient` callback rules.
- **SystemUI & Framework Inspection Profile (`RESEARCH`)**:
  - *Proposed*: Custom UAST inspection rules targeting QS tile controllers, notification handlers, and Binder proxy leaks.

### Phase 8 — Evidence-Based AI Investigation Agent
*Focus: Transforming AI from static prompt generation into structured root-cause reasoning.*

- **Correlated Evidence Context Model (`PLANNED`)**:
  - *Problem*: LLM prompts lack multi-source context (static hazard + heap path + lifecycle).
  - *Proposed*: Assemble a structured diagnostic bundle (inspection finding, Shark retention path, deobfuscated symbols) for AI reasoning.
- **Closed-Loop AI Fix Verification (`PLANNED`)**:
  - *Proposed*: 1-click re-testing of applied fixes against fresh static scans and heap dumps to verify resolution.

---

## 6. Long-Term Vision & Enterprise Governance

- **Team Memory Dashboards**: Centralized metrics tracking memory trends, active leakage risks, and baseline debt across monorepos.
- **Module-Level Ownership Policies**: Enforcing custom severity policies and blocking merge requests based on architectural domain ownership.

---

## 7. Research & Validation Track

| Topic | Key Questions | Validation Method |
|:---|:---|:---|
| **CI/CD Demand & Scope** | Do enterprise teams want static PR gating, heap analysis in CI, or both? | Developer surveys and pilot testing with enterprise CI pipelines. |
| **AOSP / SystemUI Feasibility** | Can static analysis scale to multi-gigabyte AOSP module trees without breaking IDE indexing? | Prototype indexing performance on AOSP framework source trees. |

---

## 8. Deferred & Out of Scope

- **Runtime Target SDK Integration**: LeakLens remains zero-SDK on the target app. We explicitly reject shipping an on-device runtime leak detector library (such as an embedded LeakCanary SDK) to preserve zero-footprint host analysis.
- **Replacing Android Studio Profiler**: LeakLens complements Android Profiler by providing author-time static intelligence and zero-SDK heap graph analysis; we do not aim to rebuild real-time CPU/GPU memory profilers.
- **Kotlin Native / iOS Support**: Out of scope. LeakLens is tightly coupled to Android lifecycle semantics, UAST, and JVM heap structures (Shark).

---

## 9. Contribution Opportunities

Contributors are welcome to pick up issues tagged with `good first issue` or `help wanted` on GitHub:
- Adding new UAST inspection rules for modern Jetpack lifecycle APIs.
- Improving test coverage in `ReferenceInspectionsTest.kt` and `CoroutineInspectionsTest.kt`.
- Enhancing Shark object inspector rules in `LeakLensObjectInspectors.kt`.

---

## 10. Non-Goals

- LeakLens will not execute arbitrary code on target devices.
- LeakLens will not upload raw HPROF heap dumps to cloud AI services.
- LeakLens will not attempt to auto-patch complex source code without developer review and verification.
