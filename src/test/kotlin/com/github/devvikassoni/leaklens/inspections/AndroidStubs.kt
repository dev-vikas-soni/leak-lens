package com.github.devvikassoni.leaklens.inspections

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/** Minimal Android / AndroidX / coroutines API stubs so inspections can resolve types in light tests. */
object AndroidStubs {

    private val JAVA = mapOf(
        "java/lang/Object.java" to "package java.lang; public class Object {}",
        "java/lang/String.java" to "package java.lang; public final class String {}",
        "java/lang/SuppressWarnings.java" to
            "package java.lang; public @interface SuppressWarnings { String[] value(); }",
        "java/lang/Runnable.java" to "package java.lang; public interface Runnable { void run(); }",
        "java/lang/Thread.java" to
            "package java.lang; public class Thread implements Runnable { public Thread() {} " +
            "public Thread(Runnable r) {} public void start() {} public void run() {} }",
        "java/util/concurrent/Executor.java" to
            "package java.util.concurrent; public interface Executor { void execute(Runnable r); }",
        "android/content/Context.java" to
            "package android.content; public abstract class Context { " +
            "public Context getApplicationContext() { return null; } }",
        "android/app/Application.java" to
            "package android.app; public class Application extends android.content.Context {}",
        "android/app/Activity.java" to
            "package android.app; public class Activity extends android.content.Context " +
            "implements androidx.lifecycle.LifecycleOwner { protected void onCreate(Object b) {} " +
            "protected void onDestroy() {} public androidx.lifecycle.Lifecycle getLifecycle() { return null; } }",
        "android/app/Dialog.java" to "package android.app; public class Dialog {}",
        "android/app/Fragment.java" to
            "package android.app; public class Fragment { public void onDestroyView() {} }",
        "androidx/fragment/app/Fragment.java" to
            "package androidx.fragment.app; public class Fragment implements androidx.lifecycle.LifecycleOwner { " +
            "public void onDestroyView() {} public android.content.Context requireContext() { return null; } " +
            "public android.app.Activity requireActivity() { return null; } " +
            "public androidx.lifecycle.LifecycleOwner getViewLifecycleOwner() { return null; } " +
            "public androidx.lifecycle.Lifecycle getLifecycle() { return null; } }",
        "android/view/View.java" to
            "package android.view; public class View { public View(android.content.Context c) {} " +
            "protected void onDetachedFromWindow() {} }",
        "android/widget/TextView.java" to
            "package android.widget; public class TextView extends android.view.View { " +
            "public TextView(android.content.Context c) { super(c); } }",
        "android/os/Handler.java" to
            "package android.os; public class Handler { public boolean post(Runnable r) { return true; } " +
            "public boolean postDelayed(Runnable r, long d) { return true; } " +
            "public void removeCallbacksAndMessages(Object token) {} }",
        "androidx/lifecycle/LifecycleOwner.java" to
            "package androidx.lifecycle; public interface LifecycleOwner { Lifecycle getLifecycle(); }",
        "androidx/lifecycle/ViewModel.java" to "package androidx.lifecycle; public abstract class ViewModel {}",
        "androidx/lifecycle/AndroidViewModel.java" to
            "package androidx.lifecycle; public class AndroidViewModel extends ViewModel { " +
            "public AndroidViewModel(android.app.Application a) {} " +
            "public <T extends android.app.Application> T getApplication() { return null; } }",
        "androidx/viewbinding/ViewBinding.java" to
            "package androidx.viewbinding; public interface ViewBinding { android.view.View getRoot(); }",
        "androidx/work/WorkerParameters.java" to "package androidx.work; public final class WorkerParameters {}",
        "androidx/work/ListenableWorker.java" to
            "package androidx.work; public abstract class ListenableWorker { " +
            "public ListenableWorker(android.content.Context c, WorkerParameters p) {} " +
            "public final android.content.Context getApplicationContext() { return null; } }",
        "androidx/work/Worker.java" to
            "package androidx.work; public abstract class Worker extends ListenableWorker { " +
            "public Worker(android.content.Context c, WorkerParameters p) { super(c, p); } }",
        "javax/inject/Inject.java" to "package javax.inject; public @interface Inject {}",
        "javax/inject/Singleton.java" to "package javax.inject; public @interface Singleton {}",
        "javax/inject/Provider.java" to "package javax.inject; public interface Provider<T> { T get(); }",
        "dagger/Lazy.java" to "package dagger; public interface Lazy<T> { T get(); }",
        "dagger/hilt/android/scopes/ActivityScoped.java" to
            "package dagger.hilt.android.scopes; public @interface ActivityScoped {}",
        "dagger/hilt/android/scopes/ActivityRetainedScoped.java" to
            "package dagger.hilt.android.scopes; public @interface ActivityRetainedScoped {}",
        "dagger/hilt/android/scopes/FragmentScoped.java" to
            "package dagger.hilt.android.scopes; public @interface FragmentScoped {}",
        "dagger/hilt/android/scopes/ViewModelScoped.java" to
            "package dagger.hilt.android.scopes; public @interface ViewModelScoped {}",
        "dagger/hilt/android/lifecycle/HiltViewModel.java" to
            "package dagger.hilt.android.lifecycle; public @interface HiltViewModel {}",
        "dagger/hilt/android/qualifiers/ApplicationContext.java" to
            "package dagger.hilt.android.qualifiers; public @interface ApplicationContext {}",
    )

    private val KOTLIN = mapOf(
        "kotlinx/coroutines/Coroutines.kt" to """
            package kotlinx.coroutines
            interface CoroutineScope
            interface Job
            object GlobalScope : CoroutineScope
            fun CoroutineScope.launch(block: suspend CoroutineScope.() -> Unit): Job = null!!
            fun <T> CoroutineScope.async(block: suspend CoroutineScope.() -> T): Job = null!!
        """,
        "kotlinx/coroutines/flow/Flow.kt" to """
            package kotlinx.coroutines.flow
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Job
            interface Flow<out T>
            fun <T> flowOf(value: T): Flow<T> = null!!
            suspend fun <T> Flow<T>.collect(action: suspend (T) -> Unit) {}
            suspend fun <T> Flow<T>.collectLatest(action: suspend (T) -> Unit) {}
            fun <T> Flow<T>.onEach(action: suspend (T) -> Unit): Flow<T> = this
            fun <T> Flow<T>.launchIn(scope: CoroutineScope): Job = null!!
        """,
        "androidx/lifecycle/Lifecycle.kt" to """
            package androidx.lifecycle
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.Job
            import kotlinx.coroutines.flow.Flow
            abstract class Lifecycle { enum class State { CREATED, STARTED, RESUMED } }
            abstract class LifecycleCoroutineScope : CoroutineScope {
                fun launchWhenCreated(block: suspend CoroutineScope.() -> Unit): Job = null!!
                fun launchWhenStarted(block: suspend CoroutineScope.() -> Unit): Job = null!!
                fun launchWhenResumed(block: suspend CoroutineScope.() -> Unit): Job = null!!
            }
            val LifecycleOwner.lifecycleScope: LifecycleCoroutineScope get() = null!!
            val ViewModel.viewModelScope: CoroutineScope get() = null!!
            suspend fun LifecycleOwner.repeatOnLifecycle(state: Lifecycle.State, block: suspend CoroutineScope.() -> Unit) {}
            fun <T> Flow<T>.flowWithLifecycle(lifecycle: Lifecycle, minActiveState: Lifecycle.State): Flow<T> = this
        """,
        "androidx/compose/runtime/Compose.kt" to """
            package androidx.compose.runtime
            import kotlinx.coroutines.flow.Flow
            annotation class Composable
            interface State<out T> { val value: T }
            @Composable fun <T> Flow<T>.collectAsState(initial: T): State<T> = null!!
        """,
        "androidx/compose/ui/platform/LocalContext.kt" to """
            package androidx.compose.ui.platform
            object LocalContext { val current: android.content.Context get() = null!! }
        """,
        "androidx/lifecycle/compose/Lifecycle.kt" to """
            package androidx.lifecycle.compose
            import kotlinx.coroutines.flow.Flow
            import androidx.compose.runtime.State
            @androidx.compose.runtime.Composable
            fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> = null!!
        """,
    )

    fun install(fixture: CodeInsightTestFixture) {
        JAVA.forEach { (path, text) -> fixture.addFileToProject(path, text) }
        KOTLIN.forEach { (path, text) -> fixture.addFileToProject(path, text.trimIndent()) }
    }
}
