package com.github.devvikassoni.leaklens.inspections

/** Single source of truth for every LeakLens inspection (used by batch analysis and tests). */
object LeakLensInspectionRegistry {
    fun createAll(): List<LeakLensInspectionBase> = listOf(
        StaticActivityReferenceInspection(),
        AnonymousInnerClassLeakInspection(),
        ContextPassedToSingletonInspection(),
        MissingRemoveCallbacksInspection(),
        GlobalScopeWithContextInspection(),
        ViewReferenceHeldInspection(),
        ViewModelContextLeakInspection(),
        DeprecatedLifecycleScopeInspection(),
        FlowLifecycleInspection(),
        ComposeContextLeakInspection(),
        HiltScopeMismatchInspection(),
        WorkerContextLeakInspection(),
    )
}
