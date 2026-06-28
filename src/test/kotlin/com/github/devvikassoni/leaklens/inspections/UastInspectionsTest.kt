package com.github.devvikassoni.leaklens.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class UastInspectionsTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/testData/inspections"
    }

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("android/app/Activity.java", "package android.app; public class Activity extends android.content.Context { protected void onDestroy() {} }")
        myFixture.addFileToProject("android/app/Fragment.java", "package android.app; public class Fragment { public void onDestroyView() {} protected void onDestroy() {} }")
        myFixture.addFileToProject("android/content/Context.java", "package android.content; public class Context {}")
        myFixture.addFileToProject("android/os/Handler.java", "package android.os; public class Handler { public void postDelayed(Runnable r, long d) {} public void removeCallbacksAndMessages(Object o) {} }")
        myFixture.addFileToProject("android/view/View.java", "package android.view; public class View {}")
        myFixture.addFileToProject("java/lang/Runnable.java", "package java.lang; public interface Runnable { void run(); }")
        myFixture.addFileToProject("java/lang/Thread.java", "package java.lang; public class Thread { public Thread(Runnable r) {} public void start() {} }")
        myFixture.addFileToProject("java/lang/Object.java", "package java.lang; public class Object {}")
        myFixture.addFileToProject("java/lang/String.java", "package java.lang; public class String {}")
        myFixture.addFileToProject(
            "androidx/lifecycle/ViewModel.java",
            "package androidx.lifecycle; public abstract class ViewModel {}"
        )
        
        // Mock Kotlin coroutines for GlobalScopeWithContextInspection
        myFixture.addFileToProject(
            "kotlinx/coroutines/GlobalScope.kt",
            """
            package kotlinx.coroutines
            object GlobalScope {}
            fun GlobalScope.launch(block: () -> Unit) {}
            """.trimIndent()
        )

        // Mock WorkManager
        myFixture.addFileToProject(
            "androidx/work/ListenableWorker.java",
            "package androidx.work; public abstract class ListenableWorker {}"
        )
        myFixture.addFileToProject(
            "androidx/work/Worker.java",
            "package androidx.work; public abstract class Worker extends ListenableWorker {}"
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
    }

    @Test
    fun testStaticActivityReferenceInspection() {
        myFixture.enableInspections(StaticActivityReferenceInspection())
        myFixture.testHighlighting(true, false, false, "StaticActivityLeak.java")
    }

    @Test
    fun testAnonymousInnerClassLeakInspection() {
        myFixture.enableInspections(AnonymousInnerClassLeakInspection())
        myFixture.testHighlighting(true, false, false, "AnonymousInnerClassLeak.java")
    }

    @Test
    fun testContextPassedToSingletonInspection() {
        myFixture.enableInspections(ContextPassedToSingletonInspection())
        myFixture.testHighlighting(true, false, false, "ContextSingletonLeak.java")
    }

    @Test
    fun testMissingRemoveCallbacksInspection() {
        myFixture.enableInspections(MissingRemoveCallbacksInspection())
        myFixture.testHighlighting(true, false, false, "MissingRemoveCallbacksLeak.java")
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
    }

    @Test
    fun testViewModelContextLeakInspection() {
        myFixture.enableInspections(ViewModelContextLeakInspection())
        myFixture.testHighlighting(true, false, false, "ViewModelContextLeak.kt")
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
        myFixture.testHighlighting(true, false, false, "ComposeContextLeak.kt")
    }

    @Test
    fun testHiltScopeMismatchInspection() {
        myFixture.enableInspections(HiltScopeMismatchInspection())
        myFixture.testHighlighting(true, false, false, "HiltScopeMismatch.kt")
    }

    @Test
    fun testWorkerContextLeakInspection() {
        myFixture.enableInspections(WorkerContextLeakInspection())
        myFixture.testHighlighting(true, false, false, "WorkerContextLeak.kt")
    }
}
