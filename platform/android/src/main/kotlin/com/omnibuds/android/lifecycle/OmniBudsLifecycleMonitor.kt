package com.omnibuds.android.lifecycle

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import com.omnibuds.core.lifecycle.LifecycleEvent
import com.omnibuds.core.lifecycle.LifecycleStateMachine
import com.omnibuds.core.lifecycle.LifecycleTransition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android process-lifecycle monitor.
 *
 * Phase 28 (OB-P28-REQ-016): uses Application callbacks (no androidx
 * dependency). Publishes typed events; the state machine decides
 * transitions. Best-effort: Android may kill the process without
 * callbacks, so recovery never depends on TERMINATING being observed.
 */
class OmniBudsLifecycleMonitor(
    private val stateMachine: LifecycleStateMachine = LifecycleStateMachine(),
) : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    private val _events = MutableSharedFlow<LifecycleEvent>(extraBufferCapacity = 64)
    /** Typed lifecycle events. */
    val events: SharedFlow<LifecycleEvent> = _events.asSharedFlow()

    private var startedActivities = 0

    /**
     * Attach to the application. Call once at process start.
     */
    fun attach(application: Application, previousDeath: Boolean) {
        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(this)
        emit(LifecycleEvent.ProcessStarted(previousDeath))
    }

    /**
     * Detach. Best-effort; the process may die without this.
     */
    fun detach(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        application.unregisterComponentCallbacks(this)
        emit(LifecycleEvent.ProcessTerminating)
    }

    /** Current phase. */
    fun phase() = stateMachine.phase

    private fun emit(event: LifecycleEvent) {
        stateMachine.apply(event)
        _events.tryEmit(event)
    }

    // ---- ActivityLifecycleCallbacks ----

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) {
            emit(LifecycleEvent.ForegroundEntered)
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            emit(LifecycleEvent.BackgroundEntered)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    // ---- ComponentCallbacks2 ----

    override fun onTrimMemory(level: Int) {
        // TRIM_MEMORY_UI_HIDDEN is a reliable background signal; the
        // activity counter above is primary, this is a backstop.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && startedActivities == 0) {
            emit(LifecycleEvent.BackgroundEntered)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = Unit
}
