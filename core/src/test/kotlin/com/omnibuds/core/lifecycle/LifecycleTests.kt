package com.omnibuds.core.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifecycleStateMachineTest {

    @Test
    fun `starts unknown`() {
        assertEquals(LifecyclePhase.UNKNOWN, LifecycleStateMachine().phase)
    }

    @Test
    fun `process start transitions to starting`() {
        val m = LifecycleStateMachine()
        val t = m.apply(LifecycleEvent.ProcessStarted(false))
        assertTrue(t is LifecycleTransition.Applied)
        assertEquals(LifecyclePhase.STARTING, m.phase)
    }

    @Test
    fun `double process start is rejected`() {
        val m = LifecycleStateMachine()
        m.apply(LifecycleEvent.ProcessStarted(false))
        val t = m.apply(LifecycleEvent.ProcessStarted(false))
        assertTrue(t is LifecycleTransition.Rejected)
        assertEquals(LifecyclePhase.STARTING, m.phase)
    }

    @Test
    fun `foreground then background`() {
        val m = LifecycleStateMachine()
        m.apply(LifecycleEvent.ProcessStarted(false))
        assertTrue(m.apply(LifecycleEvent.ForegroundEntered) is LifecycleTransition.Applied)
        assertTrue(m.apply(LifecycleEvent.BackgroundEntered) is LifecycleTransition.Applied)
        assertEquals(LifecyclePhase.BACKGROUND, m.phase)
    }

    @Test
    fun `background without foreground is rejected`() {
        val m = LifecycleStateMachine()
        m.apply(LifecycleEvent.ProcessStarted(false))
        val t = m.apply(LifecycleEvent.BackgroundEntered)
        assertTrue(t is LifecycleTransition.Rejected)
    }

    @Test
    fun `signal events do not change phase`() {
        val m = LifecycleStateMachine()
        m.apply(LifecycleEvent.ProcessStarted(false))
        m.apply(LifecycleEvent.ForegroundEntered)
        val t = m.apply(LifecycleEvent.AdapterDisabled)
        assertTrue(t is LifecycleTransition.Ignored)
        assertEquals(LifecyclePhase.FOREGROUND, m.phase)
    }

    @Test
    fun `terminating is terminal`() {
        val m = LifecycleStateMachine()
        m.apply(LifecycleEvent.ProcessStarted(false))
        m.apply(LifecycleEvent.ProcessTerminating)
        assertEquals(LifecyclePhase.TERMINATING, m.phase)
        assertTrue(m.apply(LifecycleEvent.ProcessTerminating) is LifecycleTransition.Rejected)
    }
}

class ReconnectPolicyTest {

    private val policy = ReconnectPolicy(maxAttempts = 3)

    private fun context(
        trigger: ReconnectTrigger = ReconnectTrigger.PLATFORM_EVENT,
        attempts: Int = 0,
        bluetooth: Boolean? = true,
        permission: Boolean? = true,
        unpaired: Boolean? = false,
    ) = ReconnectContext(trigger, attempts, bluetooth, permission, unpaired)

    @Test
    fun `platform event allows reconnect`() {
        val d = policy.shouldAttempt(context())
        assertTrue(d is ReconnectDecision.Allowed)
    }

    @Test
    fun `periodic timer never eligible`() {
        val d = policy.shouldAttempt(context(trigger = ReconnectTrigger.PERIODIC_TIMER))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `unknown trigger never eligible`() {
        val d = policy.shouldAttempt(context(trigger = ReconnectTrigger.UNKNOWN))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `bluetooth disabled denies`() {
        val d = policy.shouldAttempt(context(bluetooth = false))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `permission revoked denies`() {
        val d = policy.shouldAttempt(context(permission = false))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `unpaired device denies`() {
        val d = policy.shouldAttempt(context(unpaired = true))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `max attempts enforced`() {
        val d = policy.shouldAttempt(context(attempts = 3))
        assertTrue(d is ReconnectDecision.Denied)
    }

    @Test
    fun `user-initiated has no backoff`() {
        val d = policy.shouldAttempt(
            context(trigger = ReconnectTrigger.USER_REQUEST).copy(userInitiated = true),
        )
        assertTrue(d is ReconnectDecision.Allowed)
        assertEquals(0L, (d as ReconnectDecision.Allowed).backoffMillis)
    }

    @Test
    fun `backoff grows and is bounded`() {
        val d1 = policy.shouldAttempt(context(attempts = 0)) as ReconnectDecision.Allowed
        val d2 = policy.shouldAttempt(context(attempts = 1)) as ReconnectDecision.Allowed
        assertTrue(d2.backoffMillis > d1.backoffMillis)
        assertTrue(d2.backoffMillis <= 60_000L)
    }
}

class ProcessRecoveryPlannerTest {

    private val planner = ProcessRecoveryPlanner()

    private fun input(
        liveProof: Boolean = false,
        age: ObservationAge = ObservationAge.UNKNOWN,
        interrupted: List<String> = emptyList(),
        rediscover: Boolean = false,
    ) = RecoveryInput("d1", liveProof, age, interrupted, rediscover)

    @Test
    fun `no proof invalidates the session`() {
        val plan = planner.plan(input())
        assertTrue(plan.actions.any { it is RecoveryAction.InvalidateSession })
        assertTrue(plan.actions.none { it is RecoveryAction.KeepSession })
    }

    @Test
    fun `live proof keeps the session`() {
        val plan = planner.plan(input(liveProof = true, age = ObservationAge.FRESH))
        assertTrue(plan.actions.any { it is RecoveryAction.KeepSession })
    }

    @Test
    fun `stale observations are marked`() {
        val plan = planner.plan(input(age = ObservationAge.STALE))
        assertTrue(plan.actions.any { it is RecoveryAction.MarkStale })
    }

    @Test
    fun `fresh observations are not marked stale`() {
        val plan = planner.plan(input(liveProof = true, age = ObservationAge.FRESH))
        assertTrue(plan.actions.none { it is RecoveryAction.MarkStale })
    }

    @Test
    fun `interrupted operations are marked never replayed`() {
        val plan = planner.plan(input(interrupted = listOf("anc")))
        val marked = plan.actions.filterIsInstance<RecoveryAction.MarkInterrupted>()
        assertEquals(1, marked.size)
        assertEquals("anc", marked[0].featureId)
    }

    @Test
    fun `capability rediscovery only when required`() {
        assertTrue(planner.plan(input()).actions.none { it is RecoveryAction.RediscoverCapabilities })
        assertTrue(
            planner.plan(input(rediscover = true)).actions
                .any { it is RecoveryAction.RediscoverCapabilities },
        )
    }
}

class ResourceLifecycleRegistryTest {

    @Test
    fun `release removes the resource`() {
        val registry = ResourceLifecycleRegistry()
        var released = false
        registry.register("o1", OwnedResource.Handle { released = true })
        assertTrue(registry.has("o1"))
        registry.release("o1")
        assertTrue(released)
    }

    @Test
    fun `replace releases the old resource`() {
        val registry = ResourceLifecycleRegistry()
        var released = 0
        registry.register("o1", OwnedResource.Handle { released++ })
        registry.register("o1", OwnedResource.Handle { released++ })
        assertEquals(1, released)
    }

    @Test
    fun `release all clears everything`() {
        val registry = ResourceLifecycleRegistry()
        registry.register("o1", OwnedResource.Handle {})
        registry.register("o2", OwnedResource.Handle {})
        registry.releaseAll()
        assertEquals(0, registry.size())
    }

    @Test
    fun `release is idempotent`() {
        val registry = ResourceLifecycleRegistry()
        var released = 0
        val r = OwnedResource.Handle { released++ }
        registry.register("o1", r)
        registry.release("o1")
        registry.release("o1")
        assertEquals(1, released)
    }
}
