import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.scopes.ActivityScoped

@ActivityScoped
class MyActivityHelper @Inject constructor()

@Singleton
class UserRepository @Inject constructor() {
    // Bad: Singleton injects ActivityScoped
    @Inject
    val <error descr =
        "LeakLens: A wider-scoped Hilt component (e.g., @Singleton) injects a narrower-scoped dependency (e.g., @ActivityScoped). (SINGLETON -> ACTIVITY)" > helper < / error >: MyActivityHelper? = null
}

@Singleton
class SafeRepository @Inject constructor() {
    // Good: Singleton injects Singleton (implicitly or explicitly)
    @Inject
    val otherRepo: UserRepository? = null
}
