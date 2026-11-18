# Semantic Lifetime Engine Specification

The **Lifetime Engine** (`com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine`) is the core static reasoning component of LeakLens. It evaluates whether a code pattern presents a structural memory retention hazard by modeling and comparing the relative lifetimes of objects and scopes.

---

## 1. Lifetime Priority Hierarchy (Static Heuristic)

LeakLens assigns every detected object or scope an explicit priority based on its expected lifespan within an Android application process. Priorities are defined in `com.github.devvikassoni.leaklens.model.Lifetime`:

| Lifetime Enum | Priority | Categorization & Triggers |
|:---|:---:|:---|
| `PROCESS` | 100 | Operating system process lifecycle, `GlobalScope`, static fields |
| `APPLICATION` | 90 | `android.app.Application`, `applicationContext` |
| `SINGLETON` | 80 | `@Singleton` (Hilt/JSR-330), `@ActivityRetainedScoped`, Kotlin `object` / static `INSTANCE` |
| `WORKER` | 70 | `androidx.work.ListenableWorker` |
| `SERVICE` | 60 | `android.app.Service` |
| `VIEWMODEL` | 50 | `androidx.lifecycle.ViewModel` |
| `ACTIVITY` | 40 | `android.app.Activity`, `androidx.activity.ComponentActivity`, raw `Context` parameters |
| `FRAGMENT` | 30 | `android.app.Fragment`, `androidx.fragment.app.Fragment` |
| `COMPOSITION` | 25 | Jetpack Compose `@Composable` scope, `remember { ... }` blocks, `produceState` |
| `VIEW` | 20 | `android.view.View`, `Drawable` |
| `LOCAL` | 10 | Local method variables, parameters |
| `UNKNOWN` | 0 | Unresolved or dynamic type |

---

## 2. Risk Calculation & Confidence

The engine evaluates the relationship between an **Owner** scope and a **Referenced** object:

```text
isRisky = Owner.priority > Referenced.priority
```

### Illustrative Priority Example
If a class annotated with `@Singleton` (Priority 80) holds a field reference to an `Activity` (Priority 40):

```text
Priority(SINGLETON) > Priority(ACTIVITY)  -->  Structural Retention Hazard
```

> [!NOTE]
> **Static Analysis Heuristic Disclosure**: The Lifetime Engine operates on static code structures. Storing an object of lower priority represents a structural code hazard, but does **not** constitute definitive proof of an active runtime memory leak (as references may be cleared or managed dynamically at runtime).

---

## 3. Scope & Construct Resolution Rules

### A. DI Scopes (Hilt / Dagger)
- Checks annotations on class declarations:
  - `javax.inject.Singleton` or `ActivityRetainedScoped` -> `SINGLETON` (80)
  - `ActivityScoped` -> `ACTIVITY` (40)
  - `FragmentScoped` -> `FRAGMENT` (30)
  - `ViewScoped` -> `VIEW` (20)

### B. Android Framework Components
- Uses `InheritanceUtil.isInheritor` to resolve base classes:
  - `Application` -> `APPLICATION` (90)
  - `ViewModel` -> `VIEWMODEL` (50)
  - `Activity` / `ComponentActivity` -> `ACTIVITY` (40)
  - `Fragment` -> `FRAGMENT` (30)
  - `View` / `Drawable` -> `VIEW` (20)
  - `ListenableWorker` -> `WORKER` (70)
  - `Service` -> `SERVICE` (60)
  - `Context` -> `ACTIVITY` (pessimistic fallback)

### C. Jetpack Compose Constructs
- `getComposeConstructLifetime` inspects UAST call expressions:
  - Methods starting with `remember` -> `COMPOSITION` (25)
  - Methods ending with `Effect` (`LaunchedEffect`, `DisposableEffect`) -> `COMPOSITION` (25)
- **Compose Retention Heuristic**:
  - `remember { context }` is treated as **safe** because `ACTIVITY` (40) > `COMPOSITION` (25) (Activity outlives Composition).
  - Passing an `Activity` / UI `Context` from Compose to a `ViewModel` or `@Singleton` is flagged as a structural hazard.

### D. Coroutine Scopes
- `getScopeLifetime` inspects CoroutineScope expressions:
  - `GlobalScope` -> `PROCESS` (100)
  - `viewModelScope` -> `VIEWMODEL` (50)
  - `lifecycleScope` -> `ACTIVITY` (40)
  - `viewLifecycleOwner.lifecycleScope` -> `FRAGMENT` (30)
  - `rememberCoroutineScope()` -> `COMPOSITION` (25)
