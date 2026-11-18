# Static Analysis Rules Reference

LeakLens includes 12 UAST-based static code inspections targeting common Android memory leak patterns and lifecycle mismatches in Kotlin and Java source code.

---

## Rule Inventory

### 1. Context Retention Rules

| Rule ID | Name | Severity | Description | Target Constructs |
|:---|:---|:---:|:---|:---|
| **LL-STATIC-ACTIVITY-REFERENCE** | Static Activity Reference | `HIGH` | Companion object or static field holds an `Activity`, `Fragment`, or `View` reference. | Static fields, Kotlin companion object fields |
| **LL-CONTEXT-PASSED-TO-SINGLETON** | Context Passed to Singleton | `HIGH` | Passing an `Activity` context into a `@Singleton` or static `INSTANCE` method/constructor. | Constructor calls, method invocations |
| **LL-ANONYMOUS-INNER-CLASS-LEAK** | Anonymous Inner Class Leak | `MEDIUM` | Non-static anonymous inner class (e.g. `Handler`, `Runnable`) holds an implicit outer `Activity`/`Fragment` reference. | Anonymous class instantiations |

---

### 2. Modern Android Framework Rules

| Rule ID | Name | Severity | Description | Target Constructs |
|:---|:---|:---:|:---|:---|
| **LL-COMPOSE-CONTEXT-CAPTURE** | Compose Context Leak | `HIGH` | UI `Context` or `Activity` passed from `@Composable` code into a `ViewModel`, `ViewModelProvider.Factory`, or long-lived object that stores it. (*Note: `remember { context }` is safe and not flagged*). | Compose call expressions & ViewModel invocations |
| **LL-HILT-SCOPE-MISMATCH** | Hilt Scope Mismatch | `HIGH` | A longer-lived Hilt component (e.g. `@Singleton`) injects a narrower-scoped dependency (e.g. `@ActivityScoped`). | DI field/constructor injections |
| **LL-VIEWMODEL-CONTEXT-LEAK** | ViewModel Context Leak | `HIGH` | `ViewModel` class stores an `Activity`, `Context`, or `View` in a field. | `ViewModel` fields |
| **LL-WORKER-CONTEXT-LEAK** | Worker Context Leak | `MEDIUM` | `ListenableWorker` subclass stores an `Activity` or `View` context in a field. | `ListenableWorker` fields |

---

### 3. Asynchronous & Coroutine Rules

| Rule ID | Name | Severity | Description | Target Constructs |
|:---|:---|:---:|:---|:---|
| **LL-COROUTINE-GLOBAL-SCOPE** | GlobalScope Capture | `HIGH` | Coroutine launched in `GlobalScope` captures an `Activity`, `Fragment`, or `View` reference. | `GlobalScope.launch` / `async` |
| **LL-FLOW-LIFECYCLE-LEAK** | Unsafe Flow Collection | `HIGH` | Kotlin `Flow` collected in UI without `repeatOnLifecycle` or `flowWithLifecycle`. | `Flow.collect` call expressions |
| **LL-DEPRECATED-LIFECYCLE-SCOPE** | Deprecated Lifecycle Scope | `MEDIUM` | Usage of deprecated lifecycle coroutine launchers (`launchWhenStarted`, `launchWhenResumed`). | Lifecycle owner coroutine builders |
| **LL-MISSING-REMOVE-CALLBACKS** | Missing Callback Removal | `MEDIUM` | `Handler` callbacks posted without corresponding `removeCallbacksAndMessages(null)` cleanup in `onDestroy`. | `Handler.post` calls |
| **LL-VIEW-REFERENCE-HELD** | Fragment View Retention | `HIGH` | `Fragment` retains a `View` or binding reference in a field without clearing it in `onDestroyView`. | `Fragment` fields |

---

## Inspection Engine Behavior

- **UAST-Based Parsing**: All rules inspect code using Universal Abstract Syntax Tree (UAST) interfaces (`UClass`, `UCallExpression`, `UField`), providing identical enforcement across Kotlin and Java files.
- **Lifetime Engine Integration**: Rules query `LifetimeEngine` to evaluate whether an owner object's priority exceeds the referenced object's priority before raising a diagnostic.
- **Quick-Fix Intentions**: Select inspections provide automated IDE quick-fixes (e.g. wrapping Flow collection with `repeatOnLifecycle`, converting `context` to `applicationContext`, or offering AI remediation).
