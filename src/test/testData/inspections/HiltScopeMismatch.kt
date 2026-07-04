import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.scopes.ActivityScoped

@ActivityScoped
class MyActivityHelper @Inject constructor()

@Singleton
class UserRepository @Inject constructor(
    // Bad: Singleton injecting an ActivityScoped dependency
    <error descr="LeakLens: Scope mismatch. A javax.inject.Singleton class cannot inject a dagger.hilt.android.scopes.ActivityScoped dependency. This will leak the narrower scope.">val helper: MyActivityHelper</error>
)
