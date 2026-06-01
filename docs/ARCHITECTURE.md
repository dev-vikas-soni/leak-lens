# LeakLens — Architecture & Technical Documentation

## Table of Contents
1. [System Architecture](#system-architecture)
2. [Package Structure](#package-structure)
3. [Component Details](#component-details)
4. [Data Flow](#data-flow)
5. [Plugin Extension Points](#plugin-extension-points)
6. [Threading Model](#threading-model)
7. [Persistence Strategy](#persistence-strategy)
8. [Security & Privacy](#security--privacy)
9. [API Reference](#api-reference)

---

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Android Studio (IntelliJ Platform)                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    PRESENTATION LAYER                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐ │   │
│  │  │ Tool Window │  │   Gutter    │  │   Inspections        │ │   │
│  │  │ (Leaks +   │  │   Markers   │  │   (6 preventive      │ │   │
│  │  │  Memory +  │  │   (PSI)     │  │    checks)           │ │   │
│  │  │  History)  │  │             │  │                      │ │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────────┬───────────┘ │   │
│  └─────────┼────────────────┼─────────────────────┼─────────────┘   │
│            │                │                     │                   │
│  ┌─────────▼────────────────▼─────────────────────▼─────────────┐   │
│  │                     SERVICE LAYER                              │   │
│  │  ┌────────────────┐  ┌───────────────┐  ┌─────────────────┐ │   │
│  │  │ LeakLens       │  │ Analysis      │  │ Source          │ │   │
│  │  │ ProjectService │  │ Coordinator   │  │ Navigation      │ │   │
│  │  │ (State/History)│  │ (Pipeline)    │  │ Service         │ │   │
│  │  └────────────────┘  └───────┬───────┘  └─────────────────┘ │   │
│  └──────────────────────────────┼───────────────────────────────┘   │
│                                 │                                     │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                     ENGINE LAYER                               │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │   │
│  │  │  Shark   │ │   Fix    │ │   AI     │ │  Deobfuscation │  │   │
│  │  │ Analysis │ │  Engine  │ │  Service │ │  Service       │  │   │
│  │  │          │ │ (11rules)│ │(opt-in)  │ │  (mapping.txt) │  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                   INFRASTRUCTURE LAYER                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │   │
│  │  │   ADB    │ │  Logcat  │ │ Memory   │ │   Baseline     │  │   │
│  │  │  Heap    │ │ Listener │ │ Monitor  │ │   Manager      │  │   │
│  │  │  Dump    │ │          │ │          │ │                │  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                   PERSISTENCE LAYER                            │   │
│  │  ┌─────────────────────┐  ┌───────────────────────────────┐  │   │
│  │  │ LeakLensSettingsState│  │ leak-baseline.json (VCS)      │  │   │
│  │  │ (leaklens.xml)      │  │                               │  │   │
│  │  └─────────────────────┘  └───────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
         │
         │ ADB (USB/TCP)
         ▼
┌─────────────────────┐
│  Android Device     │
│  ┌───────────────┐  │
│  │ Debug App     │  │
│  │ (+ LeakCanary)│  │
│  └───────────────┘  │
└─────────────────────┘
```

---

## 2. Package Structure

```
com.github.devvikassoni.leaklens/
├── LeakLensBundle.kt                    # i18n resource bundle
├── actions/                             # AnAction implementations
│   ├── DumpHeapAction.kt               # Manual heap dump + analysis
│   ├── ImportHprofAction.kt            # Import local .hprof
│   ├── ToggleAutoDetectAction.kt       # Start/stop logcat listener
│   ├── MonitorMemoryAction.kt          # Start/stop memory polling
│   ├── ExportReportAction.kt           # Export HTML/JSON/SARIF
│   └── SaveBaselineAction.kt           # Save leak-baseline.json
├── ai/                                  # AI suggestion layer
│   └── AiAnalysisService.kt           # OpenAI/Gemini integration
├── baseline/                            # Baseline management
│   └── LeakBaselineManager.kt         # VCS-tracked suppression
├── deobfuscation/                       # R8/ProGuard support
│   └── DeobfuscationService.kt        # mapping.txt parsing
├── fix/                                 # Fix suggestion engine
│   ├── LeakFixRule.kt                  # Rule interface + FixSuggestion model
│   ├── FixSuggestionEngine.kt         # 11 static rules
│   └── LeakFixIntentions.kt           # IntentionAction quick fixes
├── gutter/                              # Editor gutter markers
│   └── LeakGutterLineMarkerProvider.kt # Line markers on leak classes
├── inspections/                         # LocalInspectionTool (preventive)
│   ├── StaticActivityReferenceInspection.kt
│   ├── AnonymousInnerClassLeakInspection.kt
│   ├── ContextPassedToSingletonInspection.kt
│   ├── MissingRemoveCallbacksInspection.kt
│   ├── GlobalScopeWithContextInspection.kt
│   └── ViewReferenceHeldInspection.kt
├── model/                               # Data models
│   ├── LeakInfo.kt                     # Core leak data + trace
│   ├── LeakSeverity.kt                # CRITICAL/WARNING/LIBRARY_LEAK
│   └── AnalysisHistoryEntry.kt        # History record
├── monitoring/                          # Real-time device monitoring
│   ├── DeviceMemoryMonitor.kt         # dumpsys meminfo polling
│   └── MemoryGraphPanel.kt            # Custom Swing graph
├── reporting/                           # Report generation
│   └── ReportExporter.kt              # HTML/JSON/SARIF export
├── services/                            # Core services
│   ├── LeakLensProjectService.kt      # State management + history
│   ├── AdbHeapDumpService.kt          # ADB commands wrapper
│   ├── SharkAnalysisService.kt        # Shark HeapAnalyzer
│   ├── LeakAnalysisCoordinator.kt     # Orchestration pipeline
│   ├── LogcatHeapDumpListener.kt      # Logcat monitoring
│   └── SourceNavigationService.kt     # PSI-based navigation
├── settings/                            # Configuration
│   ├── LeakLensSettingsState.kt       # PersistentStateComponent
│   └── LeakLensConfigurable.kt       # Settings UI
├── shark/                               # Custom Shark extensions
│   └── LeakLensObjectInspectors.kt   # 6 custom ObjectInspectors
├── startup/                             # Post-startup hooks
│   └── LeakLensStartupActivity.kt    # Initialization
└── toolWindow/                          # UI components
    ├── LeakLensToolWindowFactory.kt   # Factory (Leaks + Memory tabs)
    ├── LeakLensMainPanel.kt           # Tabbed container
    ├── LeakTreePanel.kt               # Severity-grouped tree
    ├── LeakListPanel.kt               # Flat list (compat)
    ├── LeakDetailPanel.kt             # Trace + fix display
    └── HistoryPanel.kt                # Past analyses
```

---

## 3. Component Details

### Analysis Pipeline (LeakAnalysisCoordinator)

```
Input (.hprof) → Shark Analysis → Deobfuscation → Fix Engine → AI (opt-in) → Baseline Filter → UI Update
```

Each step has a progress indicator fraction so users see real-time feedback.

### Fix Suggestion Engine Rules

| # | Rule | Pattern Matched | Confidence |
|---|------|----------------|-----------|
| 1 | StaticFieldActivityRule | STATIC_FIELD + Activity/Fragment | HIGH |
| 2 | AnonymousInnerClassRule | `this$0` reference + Activity | HIGH |
| 3 | HandlerActivityRule | Handler in chain + Activity leak | HIGH |
| 4 | ViewModelContextRule | ViewModel + Context/View in chain | HIGH |
| 5 | CoroutineScopeNotCancelledRule | Coroutine/Job/Continuation in chain | MEDIUM |
| 6 | SingletonActivityContextRule | STATIC_FIELD + context reference | HIGH |
| 7 | LiveDataObserverRule | LiveData + Fragment leak | HIGH |
| 8 | UnregisteredReceiverRule | Receiver/Listener/Callback in chain | MEDIUM |
| 9 | ViewReferenceRule | View/Binding + Fragment in chain | HIGH |
| 10 | InputMethodManagerRule | InputMethodManager in trace | HIGH |
| 11 | AnimatorLeakRule | Animator/Animation in chain | MEDIUM |

### Custom Shark ObjectInspectors

| Inspector | Detects |
|-----------|---------|
| ViewModelContextInspector | ViewModel fields holding Activity/View/Context |
| SingletonContextInspector | Objects with context/mContext fields holding Activity |
| CoroutineScopeInspector | Retained CoroutineScopeImpl or StandaloneCoroutine |
| ComposableLeakInspector | RecomposeScope holding Activity/Fragment |
| WorkManagerLeakInspector | Worker/CoroutineWorker holding Activity |
| NavigationLeakInspector | Retained NavBackStackEntry |

---

## 4. Data Flow

### Heap Dump → Results Flow
```
User clicks "Dump Heap Now"
    → DumpHeapAction.actionPerformed()
    → AdbHeapDumpService.listDevices()
    → AdbHeapDumpService.listDebuggableProcesses(device)
    → User selects process
    → LeakAnalysisCoordinator.triggerAndAnalyze(device, package)
        → AdbHeapDumpService.triggerHeapDump() [am dumpheap]
        → Thread.sleep(3000) [wait for dump]
        → AdbHeapDumpService.pullHeapDump() [adb pull]
        → AdbHeapDumpService.deleteRemoteFile() [cleanup]
        → SharkAnalysisService.analyzeHprof()
            → HeapAnalyzer.analyze() [Shark engine]
            → convertToLeakInfoList()
        → DeobfuscationService.deobfuscateTrace() [if mapping loaded]
        → FixSuggestionEngine.enrichWithFixes()
        → AiAnalysisService.enrichWithAiSuggestions() [if enabled]
        → LeakBaselineManager.filterNewLeaks() [if baseline exists]
        → LeakLensProjectService.updateLeaks()
        → LeakLensProjectService.addToHistory()
        → Notification to user
    → StateFlow triggers UI update
    → LeakTreePanel.updateLeaks()
```

### Reactive UI Update Flow
```
LeakLensProjectService._leaks (MutableStateFlow)
    → LeakLensToolWindowFactory (coroutine collectLatest)
    → LeakLensMainPanel.refreshLeaks()
    → LeakTreePanel.updateLeaks()
    → LeakListPanel.updateLeaks()
```

---

## 5. Plugin Extension Points (plugin.xml)

| Extension | Implementation | Purpose |
|-----------|---------------|---------|
| `toolWindow` | LeakLensToolWindowFactory | Bottom panel |
| `projectService` (×9) | All services | Scoped to project lifecycle |
| `projectConfigurable` | LeakLensConfigurable | Settings → Tools → LeakLens |
| `codeInsight.lineMarkerProvider` (×2) | LeakGutterLineMarkerProvider | Java + Kotlin gutter |
| `notificationGroup` | "LeakLens Notifications" | Balloon notifications |
| `postStartupActivity` | LeakLensStartupActivity | Post-open init |
| `intentionAction` (×3) | Fix actions | Alt+Enter quick fixes |
| `localInspection` (×6) | Inspection classes | Write-time warnings |

---

## 6. Threading Model

| Operation | Thread | Mechanism |
|-----------|--------|-----------|
| Heap analysis | Background | `Task.Backgroundable` with ProgressIndicator |
| ADB commands | Background | ProcessBuilder (within Backgroundable) |
| UI updates | EDT | `ApplicationManager.invokeLater` / Coroutine Dispatchers.Main |
| Memory monitoring | Daemon Timer | `Timer("LeakLens-MemoryMonitor", daemon=true)` |
| Logcat listening | Daemon Thread | `Thread(daemon=true)` |
| State observation | Coroutine | `CoroutineScope(Dispatchers.Main)` |
| PSI access | Read action | `ApplicationManager.runReadAction {}` |
| Document modification | Write action | `startInWriteAction = true` |

---

## 7. Persistence Strategy

| Data | Storage | Scope | Survives Restart |
|------|---------|-------|-----------------|
| Settings (all toggles) | `leaklens.xml` via `@State` | Project | ✅ |
| Analysis history (summary) | `leaklens.xml` (historyEntries list) | Project | ✅ |
| Full leak data | In-memory StateFlow | Session | ❌ |
| Baseline | `leak-baseline.json` (project root) | VCS | ✅ |
| Current leaks | MutableStateFlow | Session | ❌ |

---

## 8. Security & Privacy

| Concern | Mitigation |
|---------|-----------|
| AI data transmission | Disabled by default. Opt-in only. |
| Package name exposure | Anonymized by default before AI send |
| API key storage | Stored in project-level XML (not VCS) |
| Heap dump files | Temp directory, `deleteOnExit()` |
| Device access | Only reads via ADB (no writes except heap trigger) |
| Baseline file | No sensitive data (only signatures + class names) |

---

## 9. API Reference

### Key Public APIs for Extension

```kotlin
// Get project service
LeakLensProjectService.getInstance(project)
    .leaks          // StateFlow<List<LeakInfo>>
    .isAnalyzing    // StateFlow<Boolean>
    .history        // StateFlow<List<AnalysisHistoryEntry>>

// Trigger analysis programmatically
LeakAnalysisCoordinator.getInstance(project)
    .analyzeLocalHprof(file)
    .triggerAndAnalyze(deviceSerial, packageName)
    .analyzeFromDevice(deviceSerial, remotePath)

// Navigate to source
SourceNavigationService.getInstance(project)
    .navigateToClass(className)
    .navigateToReference(leakTraceReference)

// Custom fix rules (extend)
interface LeakFixRule {
    val name: String
    fun match(leak: LeakInfo): FixSuggestion?
}
```

