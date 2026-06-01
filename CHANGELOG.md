<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# LeakLens Changelog

## [Unreleased]

## [1.0.0] - 2026-06-02
### Added - Phase 6: Real-Time Device Monitoring
- **DeviceMemoryMonitor** service - periodically queries `dumpsys meminfo` for live heap stats
- **MemoryGraphPanel** - real-time graph showing Java Heap, Native Heap, Total PSS over time
- **Auto-trigger** heap dump when Java heap exceeds configurable threshold (default 256MB)
- **Memory tab** in tool window showing live graph with legend and current values
- **MonitorMemoryAction** - start/stop monitoring from Tools menu
- Push notifications (balloon) when threshold exceeded

### Added - Phase 7: Collaboration & CI Integration
- **ReportExporter** - export analysis as HTML, JSON, or SARIF format
- **SARIF support** - compatible with GitHub Code Scanning and SonarQube
- **LeakBaselineManager** - store suppressed leaks in `leak-baseline.json` (VCS-tracked)
- **Baseline filtering** - new leaks are flagged, baseline leaks silently suppressed
- **ExportReportAction** - export from Tools menu with file chooser
- **SaveBaselineAction** - save current leaks as accepted baseline
- **DeobfuscationService** - R8/ProGuard mapping.txt support
  - Auto-detects mapping from `app/build/outputs/mapping/release/mapping.txt`
  - Deobfuscates class names and leak traces before display
  - Integrated into analysis pipeline

### Added - Phase 8: Polish & Settings
- Extended settings: monitoring threshold, baseline toggle, deobfuscation auto-detect
- All 3 new services registered in plugin.xml
- 3 new actions: Monitor Memory, Export Report, Save Baseline


## [0.6.0] - 2026-06-02
### Added - Steps 6, 7, 8: Custom Inspectors, Persistent History, AI Analysis
- **Custom Shark ObjectInspectors** (`LeakLensObjectInspectors`):
  - `ViewModelContextInspector` - detects ViewModel holding View/Context/Activity
  - `SingletonContextInspector` - detects singletons holding Activity Context
  - `CoroutineScopeInspector` - flags retained CoroutineScope/StandaloneCoroutine
  - `ComposableLeakInspector` - detects Compose RecomposeScope holding Activity
  - `WorkManagerLeakInspector` - detects Worker/CoroutineWorker holding Activity
  - `NavigationLeakInspector` - flags retained NavBackStackEntry
- **Persistent History** (project-level XML storage):
  - `LeakLensSettingsState` with `@State` persistence in `leaklens.xml`
  - History survives IDE restarts (configurable max entries)
  - Load on project open, save on each analysis
- **AI-Assisted Analysis** (optional, opt-in):
  - `AiAnalysisService` with OpenAI GPT-4o-mini and Google Gemini support
  - Disabled by default — zero network calls unless explicitly enabled
  - Anonymizes package names before sending (configurable)
  - AI suggestions clearly marked with 🤖 badge
  - Only used for leaks not matched by static rules
- **Settings UI** (`LeakLensConfigurable`):
  - Settings → Tools → LeakLens configuration page
  - General: auto-detect toggle, gutter icons toggle
  - History: persistence toggle, max entries
  - AI: enable/disable, provider selection, API key, anonymization

## [0.5.0] - 2026-06-02
### Added - Phase 5: Static Analysis Layer - Preventive Inspections
- **6 LocalInspectionTool implementations** that detect leak-prone patterns at write-time:
  1. `StaticActivityReferenceInspection` - Activity/Fragment stored in static/companion field
  2. `AnonymousInnerClassLeakInspection` - Anonymous inner class holding outer Activity reference
  3. `ContextPassedToSingletonInspection` - Activity Context passed to a Singleton
  4. `MissingRemoveCallbacksInspection` - Handler without removeCallbacksAndMessages in onDestroy
  5. `GlobalScopeWithContextInspection` - GlobalScope.launch in Activity/Fragment
  6. `ViewReferenceHeldInspection` - View/Binding fields not nulled in onDestroyView
- All inspections enabled by default at WARNING level
- Grouped under "LeakLens" inspection group in IDE settings
- Quick fix for static Activity reference (suggests WeakReference)
- Inspections work before runtime — catch leaks at write-time

## [0.4.0] - 2026-06-02
### Added - Phase 4: Fix Suggestion Engine
- **Static Rule Engine** with 11 pattern-matching rules covering ~80% of Android leaks:
  - Activity/Fragment stored in static field → WeakReference / remove reference
  - Anonymous inner class holding outer Activity → static class + WeakReference
  - Handler holding Activity → static Handler / removeCallbacksAndMessages
  - ViewModel holding View/Context → AndroidViewModel / StateFlow
  - Coroutine scope not cancelled → lifecycleScope / viewModelScope
  - Singleton holding Activity context → applicationContext
  - LiveData observer with wrong lifecycle → viewLifecycleOwner
  - Unregistered BroadcastReceiver/Listener → symmetric register/unregister
  - View reference held beyond lifecycle → null out in onDestroyView
  - InputMethodManager framework leak → known issue, no fix needed
  - Animator not cancelled → cancel in onDestroyView
- **Fix suggestions auto-attached** to leaks during analysis pipeline
- **Quick Fix IntentionActions** (Alt+Enter menu):
  - Add `handler.removeCallbacksAndMessages(null)` in onDestroy
  - Null out `_binding` in onDestroyView
  - Replace context with applicationContext
- **FixSuggestion model** with confidence levels (HIGH/MEDIUM/LOW)
- **LeakFixRule interface** for extensible pattern matching

## [0.3.0] - 2026-06-02
### Added - Phase 3: Tool Window UI - Leak Dashboard
- **Leak Tree Panel**: Grouped tree view by severity (Critical/Warning/Library) with retained size badges
- **Clickable Leak Traces**: Click any class name in the detail panel to navigate to source code
- **Source Navigation Service**: Uses `JavaPsiFacade.findClass()` to resolve and navigate to classes/fields/methods
- **Editor Gutter Icons**: `LeakGutterLineMarkerProvider` places 🔴/🟡/🟢 icons on classes appearing in leak traces
- **History/Timeline Tab**: Tabbed interface with past analysis entries showing timestamp and summary stats
- **Reactive UI**: Tool window auto-updates via coroutine StateFlow subscription
- **Severity Header**: Color-coded header in detail panel showing class, size, and object count
- **Styled Trace Display**: JTextPane with styled text (blue underlined links, gray comments, indented chain)

## [0.2.0] - 2026-06-02
### Added - Phase 2: Heap Dump Capture & Analysis Pipeline
- **Auto-capture mode**: `LogcatHeapDumpListener` monitors logcat for LeakCanary's `HEAP DUMP` tag
- **Manual capture**: `DumpHeapAction` discovers devices, lists debuggable processes, triggers heap dump via `am dumpheap`
- **Import .hprof**: `ImportHprofAction` allows importing local .hprof files for analysis
- **Toggle Auto-Detect**: `ToggleAutoDetectAction` starts/stops logcat monitoring
- **Analysis Coordinator**: `LeakAnalysisCoordinator` orchestrates the full pipeline (pull → analyze → display → notify)
- **Background analysis**: All Shark analysis runs via `Task.Backgroundable` to avoid UI freeze
- **Analysis history**: `LeakLensProjectService` tracks past analyses with timestamps and summary stats
- **Device process listing**: `AdbHeapDumpService.listDebuggableProcesses()` resolves JDWP PIDs to package names
- **Remote file cleanup**: Auto-deletes heap dumps from device after pulling
- All new services registered in `plugin.xml`

## [0.1.0] - 2026-06-01
### Added
- Initial plugin skeleton with IntelliJ Platform Plugin SDK
- LeakLens Tool Window with split panel (Leak List + Detail)
- Shark heap analysis engine integration (v2.14)
- ADB heap dump service for device communication
- "Dump Heap Now" action in Tools menu
- Leak severity classification (Critical, Warning, Library Leak)
- Data models for leak traces and references
- Post-startup activity for initialization
- Notification group for user alerts

### Architecture
- Project service for orchestrating leak analysis
- Shark analysis service wrapping HeapAnalyzer
- ADB service for device discovery and heap dump pulling
- Tool window factory with list + detail panel layout
