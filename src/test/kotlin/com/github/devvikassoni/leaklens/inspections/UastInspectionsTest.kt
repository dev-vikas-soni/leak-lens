package com.github.devvikassoni.leaklens.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class UastInspectionsTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/testData/inspections"
    }

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "java/lang/Override.java",
            "package java.lang; public @interface Override {}"
        )
        myFixture.addFileToProject(
            "android/app/Activity.java",
            """
            package android.app;
            import android.content.Context;
            public class Activity extends Context {
                protected void onDestroy() {}
                public Context getApplicationContext() { return this; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "android/app/Fragment.java",
            "package android.app; public class Fragment { public void onDestroyView() {} protected void onDestroy() {} }"
        )
        myFixture.addFileToProject(
            "android/content/Context.java",
            "package android.content; public class Context { public Context getApplicationContext() { return this; } }"
        )
        myFixture.addFileToProject(
            "android/os/Handler.java",
            """
            package android.os;
            public class Handler {
                public void postDelayed(Runnable r, long d) {}
                public void post(Runnable r) {}
                public void removeCallbacksAndMessages(Object o) {}
                public void removeCallbacks(Runnable r) {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject("android/view/View.java", "package android.view; public class View {}")
        myFixture.addFileToProject(
            "java/lang/Runnable.java",
            "package java.lang; public interface Runnable { void run(); }"
        )
        myFixture.addFileToProject(
            "java/lang/Thread.java",
            "package java.lang; public class Thread { public Thread(Runnable r) {} public void start() {} }"
        )
        myFixture.addFileToProject("java/lang/Object.java", "package java.lang; public class Object {}")
        myFixture.addFileToProject("java/lang/String.java", "package java.lang; public class String {}")
        myFixture.addFileToProject(
            "androidx/lifecycle/ViewModel.java",
            "package androidx.lifecycle; public abstract class ViewModel {}"
        )

        // Mock kotlinx.coroutines
        myFixture.addFileToProject(
            "kotlinx/coroutines/GlobalScope.kt",
            """
            package kotlinx.coroutines
            object GlobalScope : CoroutineScope {
                override val coroutineContext: java.lang.Object = object : java.lang.Object() {}
            }
            interface CoroutineScope {
                val coroutineContext: Any
            }
            fun CoroutineScope.launch(block: suspend () -> Unit) {}
            interface Flow<T> {
                suspend fun collect(collector: (T) -> Unit)
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "androidx/lifecycle/Lifecycle.kt",
            """
            package androidx.lifecycle
            import kotlinx.coroutines.CoroutineScope
            interface LifecycleOwner {
                val lifecycle: Lifecycle
            }
            interface Lifecycle
            val LifecycleOwner.lifecycleScope: CoroutineScope get() = object : CoroutineScope { override val coroutineContext = Any() }
            val ViewModel.viewModelScope: CoroutineScope get() = object : CoroutineScope { override val coroutineContext = Any() }

            enum class State { STARTED, RESUMED }
            suspend fun LifecycleOwner.repeatOnLifecycle(state: State, block: suspend () -> Unit) {}
            """.trimIndent()
        )

        // Mock WorkManager
        myFixture.addFileToProject(
            "androidx/work/ListenableWorker.java",
            """
            package androidx.work;
            import android.content.Context;
            public abstract class ListenableWorker {
                public ListenableWorker(Context context, WorkerParameters params) {}
                public abstract Result doWork();
                public static class Result {
                    public static Result success() { return new Result(); }
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "androidx/work/Worker.java",
            """
            package androidx.work;
            import android.content.Context;
            public abstract class Worker extends ListenableWorker {
                public Worker(Context context, WorkerParameters params) { super(context, params); }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "androidx/work/WorkerParameters.java",
            "package androidx.work; public class WorkerParameters {}"
        )

        // Mock Hilt / Dagger
        myFixture.addFileToProject(
            "javax/inject/Inject.java",
            "package javax.inject; public @interface Inject {}"
        )
        myFixture.addFileToProject(
            "javax/inject/Singleton.java",
            "package javax.inject; public @interface Singleton {}"
        )
        myFixture.addFileToProject(
            "dagger/hilt/android/scopes/ActivityScoped.java",
            "package dagger.hilt.android.scopes; public @interface ActivityScoped {}"
        )

        // Mock Compose
        myFixture.addFileToProject(
            "androidx/compose/runtime/Composable.kt",
            """
            package androidx.compose.runtime
            annotation class Composable
            fun <T> remember(calculation: () -> T): T = calculation()
            """.trimIndent()
        )
    }

    @Test
    fun testStaticActivityReferenceInspection() {
        myFixture.enableInspections(StaticActivityReferenceInspection())
        myFixture.testHighlighting(true, false, false, "StaticActivityLeak.java")
        myFixture.testHighlighting(true, false, false, "StaticActivityLeak.kt")
    }

    @Test
    fun testAnonymousInnerClassLeakInspection() {
        myFixture.enableInspections(AnonymousInnerClassLeakInspection())
        myFixture.testHighlighting(true, false, false, "AnonymousInnerClassLeak.java")
        myFixture.testHighlighting(true, false, false, "AnonymousInnerClassLeak.kt")
    }

    @Test
    fun testContextPassedToSingletonInspection() {
        myFixture.enableInspections(ContextPassedToSingletonInspection())
        myFixture.testHighlighting(true, false, false, "ContextSingletonLeak.java")
        myFixture.testHighlighting(true, false, false, "ContextSingletonLeak.kt")
    }

    @Test
    fun testMissingRemoveCallbacksInspection() {
        myFixture.enableInspections(MissingRemoveCallbacksInspection())
        myFixture.testHighlighting(true, false, false, "MissingRemoveCallbacksLeak.java")
        myFixture.testHighlighting(true, false, false, "MissingRemoveCallbacksLeak.kt")
    }

    @Test
    fun testGlobalScopeWithContextInspection() {
        myFixture.enableInspections(GlobalScopeWithContextInspection())
        myFixture.testHighlighting(true, false, false, "GlobalScopeLeak.kt")
    }

    @Test
    fun testViewReferenceHeldInspection() {
        myFixture.enableInspections(ViewReferenceHeldInspection())
        myFixture.testHighlighting(true, false, false, "ViewReferenceLeak.java")
        myFixture.testHighlighting(true, false, false, "ViewReferenceLeak.kt")
    }

    @Test
    fun testViewModelContextLeakInspection() {
        myFixture.enableInspections(ViewModelContextLeakInspection())
        myFixture.testHighlighting(true, false, false, "ViewModelContextLeak.kt")
        myFixture.testHighlighting(true, false, false, "ViewModelContextLeak.java")
    }

    @Test
    fun testDeprecatedLifecycleScopeInspection() {
        myFixture.enableInspections(DeprecatedLifecycleScopeInspection())
        myFixture.testHighlighting(true, false, false, "DeprecatedLifecycleScopeLeak.kt")
    }

    @Test
    fun testFlowLifecycleInspection() {
        myFixture.enableInspections(FlowLifecycleInspection())
        myFixture.testHighlighting(true, false, false, "FlowLifecycleLeak.kt")
    }

    @Test
    fun testComposeContextLeakInspection() {
        myFixture.enableInspections(ComposeContextLeakInspection())
        myFixture.enableInspections(ContextPassedToSingletonInspection())
        myFixture.testHighlighting(true, false, false, "ComposeContextLeak.kt")
    }

    @Test
    fun testHiltScopeMismatchInspection() {
        myFixture.enableInspections(HiltScopeMismatchInspection())
        myFixture.testHighlighting(true, false, false, "HiltScopeMismatch.kt")
        myFixture.testHighlighting(true, false, false, "HiltScopeMismatch.java")
    }

    @Test
    fun testWorkerContextLeakInspection() {
        myFixture.enableInspections(WorkerContextLeakInspection())
        myFixture.testHighlighting(true, false, false, "WorkerContextLeak.kt")
        myFixture.testHighlighting(true, false, false, "WorkerContextLeak.java")
    }

    @Test
    fun testBenchmarkSingletonContext() {
        myFixture.enableInspections(ContextPassedToSingletonInspection())
        myFixture.testHighlighting(true, false, false, "benchmark/SingletonContextBenchmark.kt")
    }

    @Test
    fun testBenchmarkViewModelContext() {
        myFixture.enableInspections(ViewModelContextLeakInspection())
        myFixture.testHighlighting(true, false, false, "benchmark/ViewModelContextBenchmark.kt")
    }

    @Test
    fun testBenchmarkHiltScope() {
        myFixture.enableInspections(HiltScopeMismatchInspection())
        myFixture.testHighlighting(true, false, false, "benchmark/HiltScopeBenchmark.kt")
    }

    @Test
    fun testBenchmarkCoroutineFlow() {
        myFixture.enableInspections(GlobalScopeWithContextInspection())
        myFixture.enableInspections(FlowLifecycleInspection())
        myFixture.testHighlighting(true, false, false, "benchmark/CoroutineFlowBenchmark.kt")
    }

    @Test
    fun testBenchmarkCompose() {
        myFixture.enableInspections(ComposeContextLeakInspection())
        myFixture.enableInspections(ContextPassedToSingletonInspection())
        myFixture.testHighlighting(true, false, false, "benchmark/compose/ComposeBenchmark.kt")
    }
}
