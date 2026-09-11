# Static Analysis Rules Reference

LeakLens implements 12 core rules covering classic Android memory leaks and modern framework
pitfalls.

## Context Retention

| ID                                 | Title                       | Severity | Description                                                                                      |
|:-----------------------------------|:----------------------------|:---------|:-------------------------------------------------------------------------------------------------|
| **LL-STATIC-ACTIVITY-REFERENCE**   | Static Activity Reference   | HIGH     | Static field holds an Activity/Fragment reference. Static fields outlive the Activity lifecycle. |
| **LL-CONTEXT-PASSED-TO-SINGLETON** | Context passed to Singleton | HIGH     | Activity Context passed to a Singleton or long-lived object.                                     |
| **LL-ANONYMOUS-INNER-CLASS-LEAK**  | Anonymous Inner Class Leak  | MEDIUM   | Anonymous inner class holds an implicit reference to an outer Activity/Fragment.                 |

## Modern Android

| ID                             | Title                   | Severity | Description                                                                              |
|:-------------------------------|:------------------------|:---------|:-----------------------------------------------------------------------------------------|
| **LL-COMPOSE-CONTEXT-CAPTURE** | Compose Context Capture | HIGH     | Context/Activity captured inside a `remember` block without proper lifecycle management. |
| **LL-HILT-SCOPE-MISMATCH**     | Hilt Scope Mismatch     | HIGH     | A wider-scoped Hilt component (e.g., `@Singleton`) injects a narrower-scoped dependency. |
| **LL-VIEWMODEL-CONTEXT-LEAK**  | ViewModel Context Leak  | HIGH     | ViewModel holds a reference to an Activity, Context, or View.                            |
| **LL-WORKER-CONTEXT-LEAK**     | Worker Context Leak     | MEDIUM   | `ListenableWorker` stores a Context in a field.                                          |

## Asynchronous & Lifecycle

| ID                                | Title                      | Severity | Description                                                                     |
|:----------------------------------|:---------------------------|:---------|:--------------------------------------------------------------------------------|
| **LL-COROUTINE-GLOBAL-SCOPE**     | GlobalScope Capture        | HIGH     | `GlobalScope` coroutine captures an Activity or Context.                        |
| **LL-FLOW-LIFECYCLE-LEAK**        | Unsafe Flow Collection     | HIGH     | Flow collected in UI without `repeatOnLifecycle` or `flowWithLifecycle`.        |
| **LL-DEPRECATED-LIFECYCLE-SCOPE** | Deprecated Lifecycle Scope | MEDIUM   | Usage of `launchWhenStarted`, `launchWhenResumed`, etc.                         |
| **LL-MISSING-REMOVE-CALLBACKS**   | Missing Callback Removal   | MEDIUM   | Handler has callbacks but no `removeCallbacksAndMessages(null)` in `onDestroy`. |
| **LL-VIEW-REFERENCE-HELD**        | Fragment View Retention    | HIGH     | View reference held in a Fragment field but not cleared in `onDestroyView`.     |

---

## Severity Levels

* **HIGH**: Definitive leak that will likely cause an OOM or significant memory pressure.
* **MEDIUM**: Potential leak depending on object usage and duration.
* **LOW**: Information or "code smell" that could lead to leaks in the future.

## Detection Engine

All rules are implemented using **UAST** and the **Lifetime Engine**, ensuring support for both *
*Kotlin** and **Java** source files.
