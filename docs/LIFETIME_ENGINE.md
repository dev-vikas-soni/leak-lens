# Semantic Lifetime Engine

The **Lifetime Engine** is the core reasoning component of LeakLens' static analysis. It determines
whether a code pattern is risky by comparing the relative "lifetimes" of the objects involved.

## 1. The Lifetime Hierarchy

LeakLens assigns every object or scope a priority based on its expected duration in an Android
process. A leak is likely if a higher-priority object (longer-lived) retains a lower-priority
object (shorter-lived).

| Lifetime        | Priority | Examples                             |
|:----------------|:---------|:-------------------------------------|
| **PROCESS**     | 100      | `GlobalScope`, Static variables      |
| **APPLICATION** | 90       | `android.app.Application`            |
| **SINGLETON**   | 80       | `@Singleton` (Hilt), Kotlin `object` |
| **WORKER**      | 70       | `ListenableWorker`                   |
| **SERVICE**     | 60       | `android.app.Service`                |
| **VIEWMODEL**   | 50       | `androidx.lifecycle.ViewModel`       |
| **ACTIVITY**    | 40       | `ComponentActivity`                  |
| **FRAGMENT**    | 30       | `androidx.fragment.app.Fragment`     |
| **COMPOSITION** | 25       | `remember { ... }` blocks            |
| **VIEW**        | 20       | `android.view.View`                  |
| **LOCAL**       | 10       | Local variables, Method parameters   |

## 2. Risk Calculation

The engine analyzes the **Relationship** between an **Owner** and a **Referenced** object.

* **Risk Condition**: `Owner.priority > Referenced.priority`
* **Confidence**:
    * **HIGH**: Major mismatches (e.g., SINGLETON → ACTIVITY).
    * **MEDIUM**: Adjacent mismatches or common leak patterns.
    * **LOW**: Ambiguous lifetimes.

## 3. Detection Logic

### Android Component Resolution

The engine uses UAST and `InheritanceUtil` to identify standard Android classes. It specifically
handles:

* `Context` resolution (pessimistically assuming `ACTIVITY` unless identified as
  `applicationContext`).
* `Drawable` detection (often holding a `VIEW` reference).

### DI Scope Resolution

Supports JSR-330 and Hilt annotations:

* `@Singleton` → `SINGLETON`
* `@ActivityScoped` → `ACTIVITY`
* `@ViewScoped` → `VIEW`

### Async & Coroutine Scopes

The engine resolves lifetimes for asynchronous constructs:

* `viewModelScope` → `VIEWMODEL`
* `lifecycleScope` → `ACTIVITY` (default)
* `rememberCoroutineScope()` → `COMPOSITION`
* `GlobalScope` → `PROCESS`

## 4. Why this matters

By reasoning about lifetimes semantically, LeakLens can explain *why* something is a leak. Instead
of saying "You have a static field," it says **"Owner (SINGLETON) can outlive referenced object (
ACTIVITY)."**

This semantic evidence is passed to the **AI Fix Assistant**, enabling it to suggest specific fixes
like `WeakReference` or moving the logic to a safer scope.
