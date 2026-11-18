# Semantic Lifetime Engine Specification

The **Lifetime Engine** (`com.github.devvikassoni.leaklens.inspections.engine.LifetimeEngine`) is the core static reasoning component of LeakLens. It evaluates whether a code pattern presents a memory retention hazard by modeling and comparing the relative lifetimes of objects and scopes.

---

## 1. Lifetime Priority Hierarchy

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
| `COMPOSITION` | 25 | Jetpack Compose `remember { ... }` blocks, `produceState`, `derivedStateOf`, `*Effect` |
| `VIEW` | 20 | `android.view.View`, `Drawable` |
| `LOCAL` | 10 | Local method variables, parameters |
| `UNKNOWN` | 0 | Unresolved or dynamic type |

---

## 2. Risk Calculation & Confidence

The engine evaluates the relationship between an **Owner** scope and a **Referenced** object:

$$\text{isRisky} = \text{Owner.priority} > \text{Referenced.priority}$$

### Confidence Matrix
- **HIGH Confidence**:
  - `PROCESS` scope retaining `ACTIVITY` reference.
  - `SINGLETON` scope retaining `ACTIVITY` reference.
  - `VIEWMODEL` scope retaining `ACTIVITY` reference.
  - `WORKER` scope retaining `ACTIVITY` reference.
- **MEDIUM Confidence**:
  - General case where `Owner.priority > Referenced.priority`.
- **LOW Confidence**:
  - Equal priority or ambiguous/unresolved lifetimes.

---

## 3. Scope & Construct Resolution Rules

### A. DI Scopes (Hilt / Dagger)
- Checks annotations on class declarations:
  - `javax.inject.Singleton` or `ActivityRetainedScoped` $\rightarrow$ `SINGLETON` (80)
  - `ActivityScoped` $\rightarrow$ `ACTIVITY` (40)
  - `FragmentScoped` $\rightarrow$ `FRAGMENT` (30)
  - `ViewScoped` $\rightarrow$ `VIEW` (20)

### B. Android Framework Components
- Uses `InheritanceUtil.isInheritor` to resolve base classes:
  - `Application` $\rightarrow$ `APPLICATION` (90)
  - `ViewModel` $\rightarrow$ `VIEWMODEL` (50)
  - `Activity` / `ComponentActivity` $\rightarrow$ `ACTIVITY` (40)
  - `Fragment` $\rightarrow$ `FRAGMENT` (30)
  - `View` / `Drawable` $\rightarrow$ `VIEW` (20)
  - `ListenableWorker` $\rightarrow$ `WORKER` (70)
  - `Service` $\rightarrow$ `SERVICE` (60)
  - `Context` $\rightarrow$ `ACTIVITY` (pessimistic fallback)

### C. Jetpack Compose Constructs
- `getComposeConstructLifetime` inspects UAST call expressions:
  - Methods starting with `remember` $\rightarrow$ `COMPOSITION` (25)
  - Methods ending with `Effect` (`LaunchedEffect`, `DisposableEffect`) $\rightarrow$ `COMPOSITION` (25)
  - `produceState`, `derivedStateOf` $\rightarrow$ `COMPOSITION` (25)

### D. Coroutine Scopes
- `getScopeLifetime` inspects CoroutineScope expressions:
  - `GlobalScope` $\rightarrow$ `PROCESS` (100)
  - `viewModelScope` $\rightarrow$ `VIEWMODEL` (50)
  - `lifecycleScope` $\rightarrow$ `ACTIVITY` (40)
  - `viewLifecycleOwner.lifecycleScope` $\rightarrow$ `FRAGMENT` (30)
  - `rememberCoroutineScope()` $\rightarrow$ `COMPOSITION` (25)

---

## 4. Architectural Heuristics & Boundaries

> [!NOTE]
> Static lifetime resolution is **heuristic**. While the Lifetime Engine effectively catches structural mismatches (such as storing an Activity context in a ViewModel field or capturing Context in a Compose `remember` block), it does not execute code or track complex dynamic state transitions. Host-side heap dump analysis (Shark) serves as the runtime verification counterpart.
