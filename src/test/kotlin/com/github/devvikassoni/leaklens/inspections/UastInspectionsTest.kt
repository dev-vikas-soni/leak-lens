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
        
        // Mock Kotlin coroutines for GlobalScopeWithContextInspection
        myFixture.addFileToProject(
            "kotlinx/coroutines/GlobalScope.kt",
            """
            package kotlinx.coroutines
            object GlobalScope {}
            fun GlobalScope.launch(block: () -> Unit) {}
            """.trimIndent()
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
}
