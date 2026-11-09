package com.github.devvikassoni.leaklens.inspections.registry

import com.github.devvikassoni.leaklens.model.Confidence
import com.github.devvikassoni.leaklens.model.IssueCategory
import com.github.devvikassoni.leaklens.model.Severity

/**
 * Metadata for a static analysis rule.
 */
data class LeakLensRule(
    val id: String,
    val title: String,
    val description: String,
    val category: IssueCategory,
    val defaultSeverity: Severity,
    val defaultConfidence: Confidence,
    val hasQuickFix: Boolean = false,
    val hasAiFix: Boolean = true
)

object RuleRegistry {
    val STATIC_ACTIVITY_REFERENCE = LeakLensRule(
        id = "LL-STATIC-ACTIVITY-REFERENCE",
        title = "Activity/Fragment stored in static field",
        description = "Static field holds an Activity/Fragment reference. This causes a memory leak as static fields outlive the Activity lifecycle.",
        category = IssueCategory.CONTEXT_RETENTION,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH,
        hasQuickFix = true
    )

    val ANONYMOUS_INNER_CLASS_LEAK = LeakLensRule(
        id = "LL-ANONYMOUS-INNER-CLASS-LEAK",
        title = "Anonymous inner class may leak outer Activity/Fragment",
        description = "Anonymous inner class holds an implicit reference to an Activity/Fragment. If this object outlives the Activity, it will prevent GC.",
        category = IssueCategory.CALLBACK,
        defaultSeverity = Severity.MEDIUM,
        defaultConfidence = Confidence.MEDIUM
    )

    val CONTEXT_PASSED_TO_SINGLETON = LeakLensRule(
        id = "LL-CONTEXT-PASSED-TO-SINGLETON",
        title = "Activity Context passed to Singleton",
        description = "Activity Context passed to a Singleton or long-lived object. This will leak the Activity if not cleared.",
        category = IssueCategory.CONTEXT_RETENTION,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val MISSING_REMOVE_CALLBACKS = LeakLensRule(
        id = "LL-MISSING-REMOVE-CALLBACKS",
        title = "Missing removeCallbacksAndMessages in onDestroy",
        description = "Handler has callbacks but no removeCallbacksAndMessages(null) call in onDestroy/onStop. This may leak the Activity.",
        category = IssueCategory.HANDLER,
        defaultSeverity = Severity.MEDIUM,
        defaultConfidence = Confidence.HIGH
    )

    val GLOBAL_SCOPE_WITH_CONTEXT = LeakLensRule(
        id = "LL-COROUTINE-GLOBAL-SCOPE",
        title = "GlobalScope coroutine may leak Activity/Context",
        description = "GlobalScope is used to launch a coroutine that captures an Activity or Context. GlobalScope lives as long as the application.",
        category = IssueCategory.COROUTINE,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val VIEW_REFERENCE_HELD = LeakLensRule(
        id = "LL-VIEW-REFERENCE-HELD",
        title = "View reference held beyond lifecycle in Fragment",
        description = "View reference is held in a Fragment field but not cleared in onDestroyView. This leaks the View hierarchy.",
        category = IssueCategory.VIEW,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH,
        hasQuickFix = true
    )

    // Modern Android Rules
    val COMPOSE_CONTEXT_CAPTURE = LeakLensRule(
        id = "LL-COMPOSE-CONTEXT-CAPTURE",
        title = "Context/Activity captured by remembered object",
        description = "An Activity or Context is captured inside a remember block or a Composable state without proper lifecycle management.",
        category = IssueCategory.COMPOSE,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val FLOW_LIFECYCLE_LEAK = LeakLensRule(
        id = "LL-FLOW-LIFECYCLE-LEAK",
        title = "Unsafe collection of Flow in UI",
        description = "Flow is collected in the UI without repeatOnLifecycle or flowWithLifecycle, leading to leaks when the app is in background.",
        category = IssueCategory.FLOW,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val HILT_SCOPE_MISMATCH = LeakLensRule(
        id = "LL-HILT-SCOPE-MISMATCH",
        title = "Hilt Scope Mismatch",
        description = "A wider-scoped Hilt component (e.g., @Singleton) injects a narrower-scoped dependency (e.g., @ActivityScoped).",
        category = IssueCategory.HILT,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val VIEWMODEL_CONTEXT_LEAK = LeakLensRule(
        id = "LL-VIEWMODEL-CONTEXT-LEAK",
        title = "ViewModel holds Activity/Context/View reference",
        description = "ViewModel holds a reference to an Activity, Context, or View, which outlives the Activity lifecycle.",
        category = IssueCategory.VIEWMODEL,
        defaultSeverity = Severity.HIGH,
        defaultConfidence = Confidence.HIGH
    )

    val DEPRECATED_LIFECYCLE_SCOPE = LeakLensRule(
        id = "LL-DEPRECATED-LIFECYCLE-SCOPE",
        title = "Deprecated lifecycleScope.launchWhenX used",
        description = "launchWhenStarted, launchWhenResumed, etc. are deprecated and can lead to resource leaks. Use repeatOnLifecycle instead.",
        category = IssueCategory.LIFECYCLE,
        defaultSeverity = Severity.MEDIUM,
        defaultConfidence = Confidence.HIGH
    )

    val WORKER_CONTEXT_LEAK = LeakLensRule(
        id = "LL-WORKER-CONTEXT-LEAK",
        title = "Worker stores Context in a field",
        description = "WorkManager Worker stores a Context in a field. Use applicationContext instead to avoid leaks.",
        category = IssueCategory.BACKGROUND_WORK,
        defaultSeverity = Severity.MEDIUM,
        defaultConfidence = Confidence.HIGH
    )
}
