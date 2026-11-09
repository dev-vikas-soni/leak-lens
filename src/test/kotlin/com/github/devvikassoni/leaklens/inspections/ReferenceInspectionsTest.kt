package com.github.devvikassoni.leaklens.inspections

import org.junit.Test

/** Static fields, singletons, anonymous classes, handlers, views and ViewModels. */
class ReferenceInspectionsTest : LeakInspectionTestBase() {

    @Test
    fun testStaticFieldsJava() = assertFlagged(
        StaticActivityReferenceInspection(),
        "Holder.java",
        """
        import android.app.Activity; import android.app.Application; import android.content.Context; import android.view.View;
        public class Holder {
            static Activity sActivity;
            static View sView;
            static Context sContext;
            static Application sApp;
            static String sName;
            Activity instanceField;
        }
        """,
        "sActivity",
        "sContext",
        "sView",
    )

    @Test
    fun testStaticFieldsKotlin() = assertFlagged(
        StaticActivityReferenceInspection(),
        "Holder.kt",
        """
        import android.app.Activity
        import android.content.Context
        import dagger.hilt.android.qualifiers.ApplicationContext
        class Holder {
            var instance: Activity? = null
            companion object {
                var current: Activity? = null
                @ApplicationContext lateinit var appContext: Context
            }
        }
        object Cache { var view: android.view.View? = null }
        """,
        "current",
        "view",
    )

    @Test
    fun testStaticFieldSuppressed() = assertFlagged(
        StaticActivityReferenceInspection(),
        "Suppressed.java",
        """
        import android.app.Activity;
        public class Suppressed {
            @SuppressWarnings("LeakLensStaticActivityReference")
            static Activity sActivity;
        }
        """,
    )

    @Test
    fun testStaticFieldSuppressedKotlin() = assertFlagged(
        StaticActivityReferenceInspection(),
        "Suppressed.kt",
        """
        import android.app.Activity
        object Cache {
            @Suppress("LeakLensStaticActivityReference")
            var activity: Activity? = null
        }
        """,
    )

    @Test
    fun testAnonymousClassJava() = assertFlagged(
        AnonymousInnerClassLeakInspection(),
        "Main.java",
        """
        import android.app.Activity; import android.os.Handler; import java.util.concurrent.Executor;
        public class Main extends Activity {
            Handler handler = new Handler();
            Executor executor;
            void start() {
                new Thread(new Runnable() { public void run() {} }).start();
                handler.postDelayed(new Runnable() { public void run() {} }, 1000);
                executor.execute(new Runnable() { public void run() {} });
                handler.post(new Runnable() { public void run() {} });
                Runnable local = new Runnable() { public void run() {} };
                local.run();
            }
            static void staticContext(Handler h) {
                h.postDelayed(new Runnable() { public void run() {} }, 1000);
            }
        }
        class NotAComponent {
            void start() { new Thread(new Runnable() { public void run() {} }).start(); }
        }
        """,
        "Runnable",
        "Runnable",
        "Runnable",
    )

    @Test
    fun testContextToSingletonJava() = assertFlagged(
        ContextPassedToSingletonInspection(),
        "Main.java",
        """
        import android.app.Activity; import android.content.Context;
        class Repo {
            private static Repo instance;
            private Context context;
            static Repo get() { return instance; }
            void init(Context c) { this.context = c; }
            void use(Context c) { c.getApplicationContext(); }
        }
        public class Main extends Activity {
            void start() {
                Repo.get().init(this);
                Repo.get().init(getApplicationContext());
                Repo.get().use(this);
            }
        }
        """,
        "this",
    )

    @Test
    fun testContextToSingletonKotlinObject() = assertFlagged(
        ContextPassedToSingletonInspection(),
        "Main.kt",
        """
        import android.app.Activity
        import android.content.Context
        object Tracker {
            private var ctx: Context? = null
            fun init(context: Context) { ctx = context }
            fun log(context: Context) { }
        }
        class Main : Activity() {
            fun start() {
                Tracker.init(this)
                Tracker.init(applicationContext)
                Tracker.log(this)
            }
        }
        """,
        "this",
    )

    @Test
    fun testContextToSingletonFix() {
        val result = applyFix(
            ContextPassedToSingletonInspection(),
            "Main.java",
            """
            import android.app.Activity; import android.content.Context;
            class Repo {
                private static Repo instance;
                private Context context;
                static Repo get() { return instance; }
                void init(Context c) { context = c; }
            }
            public class Main extends Activity {
                void start() { Repo.get().init(th<caret>is); }
            }
            """,
            "Use applicationContext",
        )
        assertContainsAll(result, "Repo.get().init(getApplicationContext());")
    }

    @Test
    fun testMissingRemoveCallbacks() = assertFlagged(
        MissingRemoveCallbacksInspection(),
        "Main.java",
        """
        import android.app.Activity; import android.os.Handler;
        public class Main extends Activity {
            private final Handler leaky = new Handler();
            private final Handler cleaned = new Handler();
            private final Handler immediate = new Handler();
            void start() {
                leaky.postDelayed(() -> {}, 1000);
                cleaned.postDelayed(() -> {}, 1000);
                immediate.post(() -> {});
            }
            @Override protected void onDestroy() {
                cleaned.removeCallbacksAndMessages(null);
                super.onDestroy();
            }
        }
        """,
        "leaky",
    )

    @Test
    fun testMissingRemoveCallbacksFixKotlin() {
        val result = applyFix(
            MissingRemoveCallbacksInspection(),
            "Main.kt",
            """
            import android.app.Activity
            import android.os.Handler
            class Main : Activity() {
                private val hand<caret>ler = Handler()
                fun start() { handler.postDelayed({ }, 1000) }
            }
            """,
            "Call handler.removeCallbacksAndMessages",
        )
        assertContainsAll(
            result,
            "override fun onDestroy() {",
            "handler.removeCallbacksAndMessages(null)",
            "super.onDestroy()"
        )
    }

    @Test
    fun testViewReferenceHeld() = assertFlagged(
        ViewReferenceHeldInspection(),
        "MyFragment.kt",
        """
        import android.view.View
        import androidx.fragment.app.Fragment
        import androidx.viewbinding.ViewBinding
        class MyFragment : Fragment() {
            private var leakyView: View? = null
            private var binding: ViewBinding? = null
            private var cleared: View? = null
            override fun onDestroyView() {
                super.onDestroyView()
                cleared = null
            }
        }
        class NotAFragment { private var view: View? = null }
        """,
        "binding",
        "leakyView",
    )

    @Test
    fun testViewReferenceHeldFix() {
        val result = applyFix(
            ViewReferenceHeldInspection(),
            "MyFragment.kt",
            """
            import android.view.View
            import androidx.fragment.app.Fragment
            class MyFragment : Fragment() {
                private var lea<caret>ky: View? = null
            }
            """,
            "Set 'leaky' to null",
        )
        assertContainsAll(result, "override fun onDestroyView() {", "leaky = null")
    }

    @Test
    fun testViewModelFields() = assertFlagged(
        ViewModelContextLeakInspection(),
        "Vm.kt",
        """
        import android.app.Activity
        import android.app.Application
        import android.content.Context
        import android.view.View
        import androidx.lifecycle.ViewModel
        import androidx.lifecycle.AndroidViewModel
        import dagger.hilt.android.qualifiers.ApplicationContext
        class LeakyVm(private val activity: Activity, private val context: Context) : ViewModel() {
            var view: View? = null
        }
        class SafeVm(@ApplicationContext private val context: Context, private val app: Application) : ViewModel()
        class AndroidVm(app: Application) : AndroidViewModel(app) {
            private val ctx: Context = getApplication<Application>().applicationContext
            var view: View? = null
        }
        """,
        "activity",
        "context",
        "view",
        "view",
    )
}
