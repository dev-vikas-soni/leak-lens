package com.github.devvikassoni.leaklens.fix

import com.github.devvikassoni.leaklens.model.LeakInfo

/**
 * Static rule engine that matches leak traces against known patterns
 * and produces fix suggestions. Covers ~80% of common Android leak patterns.
 */
class FixSuggestionEngine {

    private val rules: List<LeakFixRule> = listOf(
        StaticFieldActivityRule(),
        AnonymousInnerClassRule(),
        HandlerActivityRule(),
        ViewModelContextRule(),
        CoroutineScopeNotCancelledRule(),
        SingletonActivityContextRule(),
        LiveDataObserverRule(),
        UnregisteredReceiverRule(),
        ViewReferenceRule(),
        InputMethodManagerRule(),
        AnimatorLeakRule()
    )

    /**
     * Analyze a leak and return the best fix suggestion.
     */
    fun suggest(leak: LeakInfo): FixSuggestion? {
        for (rule in rules) {
            val suggestion = rule.match(leak)
            if (suggestion != null) return suggestion
        }
        return null
    }

    /**
     * Analyze a list of leaks and attach fix suggestions.
     */
    fun enrichWithFixes(leaks: List<LeakInfo>): List<LeakInfo> {
        return leaks.map { leak ->
            val fix = suggest(leak)
            if (fix != null) {
                leak.copy(suggestedFix = "${fix.explanation}\n\n${fix.codeSnippet ?: ""}")
            } else {
                leak
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// RULE IMPLEMENTATIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Activity/Fragment stored in a static field or companion object.
 */
class StaticFieldActivityRule : LeakFixRule {
    override val name = "Static Field Holding Activity/Fragment"
    override val description = "A static field or companion object holds a reference to an Activity or Fragment"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasStaticRef = leak.referenceChain.any { ref ->
            ref.referenceType == "STATIC_FIELD"
        }
        val leaksActivityOrFragment = leak.retainedObjectClassName.let {
            it.contains("Activity") || it.contains("Fragment")
        }

        if (hasStaticRef && leaksActivityOrFragment) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A static/companion object field holds a reference to an Activity or Fragment.
                    Since static fields live for the entire app lifecycle, the Activity/Fragment cannot be garbage collected after being destroyed.
                    
                    ✅ Fix Options:
                    1. Remove the static reference entirely
                    2. Use WeakReference<Activity> if you must keep a reference
                    3. Use a lifecycle-aware component (LifecycleObserver)
                    4. Clear the reference in onDestroy()
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - static reference keeps Activity alive
                    companion object {
                        var activity: MainActivity? = null  // LEAK!
                    }
                    
                    // ✅ GOOD - WeakReference allows GC
                    companion object {
                        var activityRef: WeakReference<MainActivity>? = null
                    }
                    
                    // ✅ BETTER - no static reference at all
                    // Pass Activity as parameter where needed, use Application context for singletons
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Anonymous inner class holding outer Activity reference.
 */
class AnonymousInnerClassRule : LeakFixRule {
    override val name = "Anonymous Inner Class Holding Activity"
    override val description = "An anonymous inner class (Runnable, Listener, etc.) implicitly holds the outer Activity"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasAnonymousClass = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("\$") && ref.referenceName == "this\$0"
        }
        val leaksActivityOrFragment = leak.retainedObjectClassName.let {
            it.contains("Activity") || it.contains("Fragment")
        }

        if (hasAnonymousClass && leaksActivityOrFragment) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: An anonymous inner class (lambda, Runnable, Listener) implicitly holds a reference
                    to the outer Activity/Fragment via 'this${'$'}0'. If this object outlives the Activity (e.g., posted
                    to a Handler, registered as a callback), the Activity leaks.
                    
                    ✅ Fix Options:
                    1. Convert to a static inner class with a WeakReference to the Activity
                    2. Use viewLifecycleOwner-scoped callbacks in Fragments
                    3. Remove callbacks/unregister in onDestroy()/onDestroyView()
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - anonymous Runnable holds Activity reference
                    handler.postDelayed({
                        // 'this' refers to Activity implicitly
                        updateUI()
                    }, 5000)
                    
                    // ✅ GOOD - static inner class with WeakReference
                    private class UpdateTask(activity: MainActivity) : Runnable {
                        private val activityRef = WeakReference(activity)
                        override fun run() {
                            activityRef.get()?.updateUI()
                        }
                    }
                    
                    // ✅ ALSO GOOD - remove callbacks in onDestroy
                    override fun onDestroy() {
                        handler.removeCallbacksAndMessages(null)
                        super.onDestroy()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Handler holding Activity reference.
 */
class HandlerActivityRule : LeakFixRule {
    override val name = "Handler Holding Activity Reference"
    override val description = "A Handler implicitly holds a reference to the Activity"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasHandler = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("Handler") || ref.referenceName.contains("handler", ignoreCase = true)
        }
        val leaksActivity = leak.retainedObjectClassName.contains("Activity")

        if (hasHandler && leaksActivity) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A Handler (often a non-static inner class) holds a reference to the Activity.
                    Messages in the Handler's queue keep the Handler (and thus the Activity) alive.
                    
                    ✅ Fix Options:
                    1. Use a static Handler with WeakReference<Activity>
                    2. Call handler.removeCallbacksAndMessages(null) in onDestroy()
                    3. Use lifecycleScope.launch { delay(...) } instead of Handler.postDelayed
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - inner Handler class holds Activity
                    private val handler = object : Handler(Looper.getMainLooper()) {
                        override fun handleMessage(msg: Message) {
                            updateUI() // implicit Activity reference
                        }
                    }
                    
                    // ✅ GOOD - static Handler with WeakReference
                    private class SafeHandler(activity: MainActivity) : Handler(Looper.getMainLooper()) {
                        private val activityRef = WeakReference(activity)
                        override fun handleMessage(msg: Message) {
                            activityRef.get()?.updateUI()
                        }
                    }
                    
                    // ✅ BETTER - use coroutines
                    lifecycleScope.launch {
                        delay(5000)
                        updateUI()
                    }
                    
                    // ✅ Always clean up in onDestroy
                    override fun onDestroy() {
                        handler.removeCallbacksAndMessages(null)
                        super.onDestroy()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * ViewModel holding View or Context reference.
 */
class ViewModelContextRule : LeakFixRule {
    override val name = "ViewModel Holding View/Context"
    override val description = "A ViewModel holds a reference to a View or Activity Context"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasViewModel = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("ViewModel")
        }
        val leaksViewOrContext = leak.retainedObjectClassName.let {
            it.contains("Activity") || it.contains("View") || it.contains("Context")
        } || leak.referenceChain.any { ref ->
            ref.referenceName.contains("context", ignoreCase = true) ||
            ref.referenceName.contains("view", ignoreCase = true)
        }

        if (hasViewModel && leaksViewOrContext) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A ViewModel holds a reference to a View, Activity, or Activity Context.
                    ViewModels survive configuration changes, so they outlive Activities/Views.
                    
                    ✅ Fix Options:
                    1. Never store View or Activity Context in ViewModel
                    2. Use Application context via AndroidViewModel if context is needed
                    3. Pass data via LiveData/StateFlow (observed by the View layer)
                    4. Use SavedStateHandle for data persistence
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - ViewModel holds Activity context
                    class MyViewModel(private val context: Context) : ViewModel() { }
                    
                    // ✅ GOOD - use AndroidViewModel for Application context
                    class MyViewModel(application: Application) : AndroidViewModel(application) {
                        private val appContext = application.applicationContext
                    }
                    
                    // ✅ BETTER - expose data via StateFlow, let View observe
                    class MyViewModel : ViewModel() {
                        private val _uiState = MutableStateFlow(UiState())
                        val uiState: StateFlow<UiState> = _uiState.asStateFlow()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Coroutine scope not cancelled.
 */
class CoroutineScopeNotCancelledRule : LeakFixRule {
    override val name = "Coroutine Scope Not Cancelled"
    override val description = "A CoroutineScope is not cancelled when the lifecycle ends"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasCoroutine = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("Coroutine") ||
            ref.owningClassName.contains("Job") ||
            ref.owningClassName.contains("Continuation") ||
            ref.referenceName.contains("scope", ignoreCase = true)
        }

        if (hasCoroutine) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A coroutine or scope holds a reference to a destroyed component.
                    The coroutine was not cancelled when the Activity/Fragment was destroyed.
                    
                    ✅ Fix Options:
                    1. Use viewModelScope (auto-cancelled in onCleared)
                    2. Use lifecycleScope (auto-cancelled on DESTROYED)
                    3. Use viewLifecycleOwner.lifecycleScope in Fragments
                    4. Cancel custom CoroutineScope in onDestroy/onCleared
                    5. Never use GlobalScope with UI references
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - GlobalScope keeps reference alive
                    GlobalScope.launch {
                        val data = fetchData()
                        updateUI(data) // Activity may be destroyed!
                    }
                    
                    // ✅ GOOD - lifecycleScope auto-cancels
                    lifecycleScope.launch {
                        val data = fetchData()
                        updateUI(data)
                    }
                    
                    // ✅ GOOD - viewModelScope auto-cancels in onCleared
                    viewModelScope.launch {
                        val data = repository.fetchData()
                        _state.value = data
                    }
                    
                    // ✅ If using custom scope, cancel it
                    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                    override fun onDestroy() {
                        scope.cancel()
                        super.onDestroy()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Singleton holding Activity context.
 */
class SingletonActivityContextRule : LeakFixRule {
    override val name = "Singleton Holding Activity Context"
    override val description = "A Singleton holds a reference to an Activity Context instead of Application Context"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasSingleton = leak.referenceChain.any { ref ->
            ref.referenceType == "STATIC_FIELD" || ref.referenceName.contains("instance", ignoreCase = true) ||
            ref.referenceName.contains("INSTANCE", ignoreCase = true)
        }
        val leaksActivity = leak.retainedObjectClassName.contains("Activity")
        val hasContextRef = leak.referenceChain.any { ref ->
            ref.referenceName.contains("context", ignoreCase = true) ||
            ref.referenceName.contains("mContext", ignoreCase = true)
        }

        if (hasSingleton && (leaksActivity || hasContextRef)) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A Singleton (object with static lifecycle) holds an Activity Context.
                    The Singleton lives for the entire app lifecycle, preventing the Activity from being GC'd.
                    
                    ✅ Fix: Use Application Context instead of Activity Context in singletons.
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - Singleton holds Activity context
                    object MySingleton {
                        lateinit var context: Context  // Activity context passed here = LEAK
                        fun init(ctx: Context) { context = ctx }
                    }
                    
                    // ✅ GOOD - Use Application context
                    object MySingleton {
                        lateinit var context: Context
                        fun init(ctx: Context) { 
                            context = ctx.applicationContext  // Safe!
                        }
                    }
                    
                    // ✅ BETTER - Use dependency injection with proper scoping
                    @Singleton
                    class MySingleton @Inject constructor(
                        @ApplicationContext private val context: Context
                    )
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * LiveData observed with wrong lifecycle owner.
 */
class LiveDataObserverRule : LeakFixRule {
    override val name = "LiveData Observer With Wrong Lifecycle"
    override val description = "LiveData observed with Fragment 'this' instead of viewLifecycleOwner"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasLiveData = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("LiveData") || ref.referenceName.contains("observer", ignoreCase = true)
        }
        val leaksFragment = leak.retainedObjectClassName.contains("Fragment")

        if (hasLiveData && leaksFragment) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: LiveData is observed using the Fragment itself ('this') as the lifecycle owner.
                    In Fragments, the view can be destroyed while the Fragment instance is still alive (e.g., on back stack).
                    This causes observers to accumulate and the old view to be retained.
                    
                    ✅ Fix: Use viewLifecycleOwner instead of 'this' when observing LiveData in Fragments.
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - using 'this' in a Fragment
                    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                        viewModel.data.observe(this) { data ->  // WRONG lifecycle!
                            updateUI(data)
                        }
                    }
                    
                    // ✅ GOOD - use viewLifecycleOwner
                    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                        viewModel.data.observe(viewLifecycleOwner) { data ->
                            updateUI(data)
                        }
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Unregistered BroadcastReceiver or Listener.
 */
class UnregisteredReceiverRule : LeakFixRule {
    override val name = "Unregistered BroadcastReceiver/Listener"
    override val description = "A BroadcastReceiver or Listener was registered but never unregistered"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasReceiver = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("Receiver") || ref.owningClassName.contains("Listener") ||
            ref.owningClassName.contains("CallbackRecord") || ref.owningClassName.contains("Observer") ||
            ref.referenceName.contains("receiver", ignoreCase = true) ||
            ref.referenceName.contains("listener", ignoreCase = true)
        }

        if (hasReceiver) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A BroadcastReceiver, Listener, or Callback was registered but never unregistered.
                    The system holds a reference to the receiver/listener, which in turn holds the Activity/Fragment.
                    
                    ✅ Fix: Always unregister in the corresponding lifecycle method.
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - registered but never unregistered
                    override fun onResume() {
                        super.onResume()
                        registerReceiver(myReceiver, intentFilter)
                    }
                    // Missing: unregisterReceiver in onPause!
                    
                    // ✅ GOOD - symmetric register/unregister
                    override fun onResume() {
                        super.onResume()
                        registerReceiver(myReceiver, intentFilter)
                    }
                    override fun onPause() {
                        unregisterReceiver(myReceiver)
                        super.onPause()
                    }
                    
                    // ✅ For listeners:
                    override fun onStart() {
                        super.onStart()
                        sensorManager.registerListener(this, sensor, SENSOR_DELAY_NORMAL)
                    }
                    override fun onStop() {
                        sensorManager.unregisterListener(this)
                        super.onStop()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * View reference held beyond lifecycle.
 */
class ViewReferenceRule : LeakFixRule {
    override val name = "View Reference Held Beyond Lifecycle"
    override val description = "A View reference is stored in a field that outlives the view lifecycle"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val leaksView = leak.retainedObjectClassName.let {
            it.contains("View") || it.contains("Binding")
        }
        val hasFragmentInChain = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("Fragment")
        }

        if (leaksView && hasFragmentInChain) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: A Fragment holds a reference to a View or ViewBinding after onDestroyView().
                    When a Fragment goes on the back stack, its view is destroyed but the Fragment instance remains.
                    
                    ✅ Fix: Null out view references in onDestroyView().
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - binding reference kept after view destroyed
                    class MyFragment : Fragment() {
                        private var _binding: FragmentMyBinding? = null
                        private val binding get() = _binding!!
                        
                        override fun onDestroyView() {
                            super.onDestroyView()
                            // Missing: _binding = null
                        }
                    }
                    
                    // ✅ GOOD - null out binding in onDestroyView
                    class MyFragment : Fragment() {
                        private var _binding: FragmentMyBinding? = null
                        private val binding get() = _binding!!
                        
                        override fun onDestroyView() {
                            super.onDestroyView()
                            _binding = null  // Allow GC!
                        }
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

/**
 * Known Android framework leak: InputMethodManager.
 */
class InputMethodManagerRule : LeakFixRule {
    override val name = "InputMethodManager Framework Leak"
    override val description = "Known Android framework leak via InputMethodManager"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasIMM = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("InputMethodManager")
        }

        if (hasIMM || leak.leakTrace.contains("InputMethodManager")) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ℹ️ Known Android Framework Leak: InputMethodManager holds a reference to a View/Activity.
                    This is a known issue in the Android framework that LeakCanary's Plumber auto-patches at runtime.
                    
                    ✅ This leak is NOT caused by your code. It's handled automatically by LeakCanary's Plumber library.
                    No action required from the developer.
                """.trimIndent(),
                codeSnippet = """
                    // No code fix needed - this is a framework bug.
                    // If you want to work around it manually:
                    override fun onDestroy() {
                        // Clear focus to help InputMethodManager release the view
                        currentFocus?.clearFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                        super.onDestroy()
                    }
                """.trimIndent(),
                confidence = FixSuggestion.Confidence.HIGH
            )
        }
        return null
    }
}

/**
 * Animator/Animation leak.
 */
class AnimatorLeakRule : LeakFixRule {
    override val name = "Animator Not Cancelled"
    override val description = "An Animator or Animation holds a View reference after Activity is destroyed"

    override fun match(leak: LeakInfo): FixSuggestion? {
        val hasAnimator = leak.referenceChain.any { ref ->
            ref.owningClassName.contains("Animator") || ref.owningClassName.contains("Animation") ||
            ref.referenceName.contains("animator", ignoreCase = true) ||
            ref.referenceName.contains("animation", ignoreCase = true)
        }

        if (hasAnimator) {
            return FixSuggestion(
                ruleName = name,
                explanation = """
                    ❌ Problem: An Animator or Animation was not cancelled when the Activity/Fragment was destroyed.
                    Running animations hold references to their target Views and listeners.
                    
                    ✅ Fix: Cancel all animators in onDestroy()/onDestroyView().
                """.trimIndent(),
                codeSnippet = """
                    // ❌ BAD - animator not cancelled
                    private val animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                        duration = 5000
                        start()
                    }
                    
                    // ✅ GOOD - cancel in lifecycle
                    override fun onDestroyView() {
                        animator.cancel()
                        // or for all animators on a view:
                        view.animate().cancel()
                        super.onDestroyView()
                    }
                """.trimIndent()
            )
        }
        return null
    }
}

