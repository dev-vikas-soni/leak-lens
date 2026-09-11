package com.github.devvikassoni.leaklens.model

/**
 * Represents the semantic lifetime of an object or scope in Android.
 */
enum class Lifetime(val priority: Int) {
    /** The entire OS process. */
    PROCESS(100),

    /** The Application instance. */
    APPLICATION(90),

    /** A Singleton or object scoped to the application. */
    SINGLETON(80),

    /** A long-lived background worker. */
    WORKER(70),

    /** An Android Service. */
    SERVICE(60),

    /** A Jetpack ViewModel. */
    VIEWMODEL(50),

    /** An Android Activity. */
    ACTIVITY(40),

    /** An Android Fragment. */
    FRAGMENT(30),

    /** A Jetpack Compose Composition. */
    COMPOSITION(25),

    /** A UI View. */
    VIEW(20),

    /** A single method call or short-lived local scope. */
    LOCAL(10),

    /** Lifetime cannot be determined. */
    UNKNOWN(0);

    /**
     * Returns true if this lifetime is potentially longer than the [other] lifetime.
     */
    fun canOutlive(other: Lifetime): Boolean {
        if (this == UNKNOWN || other == UNKNOWN) return false
        return this.priority > other.priority
    }
}

/**
 * Describes the relationship between an owner and a retained object.
 */
data class LifetimeRelationship(
    val ownerLifetime: Lifetime,
    val referencedLifetime: Lifetime,
    val evidence: String,
    val confidence: Confidence,
    val riskExplanation: String? = null
) {
    val isRisky: Boolean
        get() = ownerLifetime.canOutlive(referencedLifetime)
}
