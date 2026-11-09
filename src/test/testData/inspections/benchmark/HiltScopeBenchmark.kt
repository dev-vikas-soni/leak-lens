import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.scopes.ActivityScoped

@Singleton
class GlobalManager @Inject constructor(
    // POSITIVE: Singleton depends on ActivityScoped
    private val <error
    descr = "LeakLens: A wider-scoped Hilt component (e.g., @Singleton) injects a narrower-scoped dependency (e.g., @ActivityScoped). (SINGLETON -> ACTIVITY)" > activityScoped < / error >
    : ActivityScopedDep
)

@ActivityScoped
class ActivityScopedDep @Inject constructor()

@ActivityScoped
class ActivityManager @Inject constructor(
    // NEGATIVE: ActivityScoped depends on Singleton (Safe)
    private val global: GlobalDep
)

@Singleton
class GlobalDep @Inject constructor()
