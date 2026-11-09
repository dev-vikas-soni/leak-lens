package com.github.devvikassoni.leaklens.inspections

import com.github.devvikassoni.leaklens.services.StaticAnalysisRunner
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import org.junit.Test

/** Compose, Hilt, WorkManager, batch runner and signature stability. */
class ArchitectureInspectionsTest : LeakInspectionTestBase() {

    @Test
    fun testComposeContextToViewModel() = assertFlagged(
        ComposeContextLeakInspection(),
        "Ui.kt",
        """
        import android.content.Context
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.platform.LocalContext
        import androidx.lifecycle.ViewModel
        class Vm : ViewModel() {
            private var ctx: Context? = null
            fun bind(context: Context) { ctx = context }
            fun toast(context: Context) { }
        }
        @Composable fun Screen(vm: Vm) {
            val context = LocalContext.current
            vm.bind(context)
            vm.bind(context.applicationContext)
            vm.toast(context)
        }
        fun notComposable(vm: Vm, context: Context) { vm.bind(context) }
        """,
        "context",
    )

    @Test
    fun testComposeContextFix() {
        val result = applyFix(
            ComposeContextLeakInspection(),
            "Ui.kt",
            """
            import android.content.Context
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.platform.LocalContext
            import androidx.lifecycle.ViewModel
            class Vm : ViewModel() {
                private var ctx: Context? = null
                fun bind(context: Context) { ctx = context }
            }
            @Composable fun Screen(vm: Vm) { vm.bind(LocalContext.curr<caret>ent) }
            """,
            "Pass applicationContext",
        )
        assertContainsAll(result, "vm.bind(LocalContext.current.applicationContext)")
    }

    @Test
    fun testHiltScopes() = assertFlagged(
        HiltScopeMismatchInspection(),
        "Di.kt",
        """
        import javax.inject.Inject
        import javax.inject.Provider
        import javax.inject.Singleton
        import dagger.hilt.android.scopes.ActivityScoped
        import dagger.hilt.android.scopes.FragmentScoped
        import dagger.hilt.android.scopes.ActivityRetainedScoped
        @ActivityScoped class Navigator @Inject constructor()
        @ActivityRetainedScoped class Session @Inject constructor()
        @Singleton class Analytics @Inject constructor(private val navigator: Navigator)
        @Singleton class Lazyish @Inject constructor(private val navigator: Provider<Navigator>)
        @Singleton class Fine @Inject constructor(private val analytics: Analytics)
        @FragmentScoped class FragmentThing @Inject constructor(private val navigator: Navigator, private val session: Session)
        @ActivityScoped class ActivityThing @Inject constructor(private val session: Session)
        """,
        "private val navigator: Navigator",
        "private val navigator: Provider<Navigator>",
    )

    @Test
    fun testWorkerContext() = assertFlagged(
        WorkerContextLeakInspection(),
        "Sync.kt",
        """
        import android.content.Context
        import androidx.work.Worker
        import androidx.work.WorkerParameters
        class SyncWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params)
        class CleanWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
            fun run() = applicationContext
        }
        """,
        "ctx",
    )

    @Test
    fun testWorkerFixKotlin() {
        val result = applyFix(
            WorkerContextLeakInspection(),
            "Sync.kt",
            """
            import android.content.Context
            import androidx.work.Worker
            import androidx.work.WorkerParameters
            class SyncWorker(private val c<caret>tx: Context, params: WorkerParameters) : Worker(ctx, params) {
                fun run(): Context = ctx
            }
            """,
            "Replace 'ctx' with applicationContext",
        )
        assertContainsAll(
            result,
            "class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params)",
            "fun run(): Context = applicationContext"
        )
    }

    @Test
    fun testWorkerFixJava() {
        val result = applyFix(
            WorkerContextLeakInspection(),
            "SyncWorker.java",
            """
            import android.content.Context; import androidx.work.Worker; import androidx.work.WorkerParameters;
            public class SyncWorker extends Worker {
                private final Context con<caret>text;
                public SyncWorker(Context c, WorkerParameters p) { super(c, p); this.context = c; }
                Context get() { return context; }
            }
            """,
            "Replace 'context' with applicationContext",
        )
        assertFalse(result.contains("private final Context context"))
        assertContainsAll(result, "return getApplicationContext();")
    }

    @Test
    fun testBatchRunnerMatchesEditorAndSignaturesAreLineIndependent() {
        val source = """
            import android.app.Activity;
            public class Holder { static Activity sActivity; }
        """.trimIndent()
        val file = myFixture.configureByText("Holder.java", source)
        val tool = StaticActivityReferenceInspection()
        val first = signatures(tool, file)
        assertEquals(listOf("LeakLensStaticActivityReference:Holder#sActivity"), first)

        val shifted = myFixture.configureByText("Holder.java", "\n\n// moved\n$source")
        val second = signatures(tool, shifted)
        assertEquals(first, second)

        val result = StaticAnalysisRunner(project).analyze(listOf(shifted.virtualFile), null)
        assertEquals(1, result.issuesFound)
    }

    private fun signatures(tool: LeakLensInspectionBase, file: PsiFile): List<String> =
        ReadAction.compute<List<String>, RuntimeException> { tool.collectIssues(file).map { it.signature } }

    @Test
    fun testRegistryHasUniqueShortNames() {
        val names = LeakLensInspectionRegistry.createAll().map { it.shortName }
        assertEquals(12, names.size)
        assertEquals(names.size, names.toSet().size)
    }
}
