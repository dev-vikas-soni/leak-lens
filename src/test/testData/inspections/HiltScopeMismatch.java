import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
class MyActivityHelperJava {
    @Inject public MyActivityHelperJava() {}
}

@Singleton
class UserRepositoryJava {
    @Inject public UserRepositoryJava() {}

    // Bad: Singleton injects ActivityScoped
    @Inject MyActivityHelperJava <error descr="LeakLens: A wider-scoped Hilt component (e.g., @Singleton) injects a narrower-scoped dependency (e.g., @ActivityScoped). (SINGLETON -> ACTIVITY)">helper</error>;
}
