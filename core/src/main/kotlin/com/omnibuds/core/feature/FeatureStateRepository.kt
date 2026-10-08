package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single authoritative owner of feature control state.
 *
 * The repository holds the current [FeatureState] per feature as an immutable
 * map behind a [StateFlow], so UI consumers observe changes reactively while the
 * engine remains the only writer. There is exactly one state owner per feature:
 * capability truth lives in the capability layer, transport truth in the
 * transport layer, and *control* truth lives here (ARCH-LAYER-003).
 *
 * This is a runtime state holder, not a persistence repository: nothing here is
 * stored across sessions, and Phase 9 adds no saved-device history (that is
 * Phase 17's work). The name mirrors the domain language — "repository" as in
 * "where the state lives" — not a database.
 *
 * All mutations are serialized through a mutex and validated against
 * [FeatureStateTransitions]: an illegal transition is a programming error and
 * is refused with an exception, because a state machine that can be driven into
 * nonsense cannot be trusted to report hardware truth.
 */
interface FeatureStateRepository {

    /** The current control state per feature; `null` (absent) means never tracked. */
    val states: StateFlow<Map<FeatureId, FeatureState>>

    /** The current state of [feature], or null when the repository never tracked it. */
    fun stateOf(feature: FeatureId): FeatureState?

    /**
     * Atomically replaces [feature]'s state with the result of [transition].
     *
     * The transition runs under the repository's lock and receives the current
     * state (or null); its result must be a legal move per
     * [FeatureStateTransitions], otherwise the update is refused.
     */
    suspend fun update(feature: FeatureId, transition: (FeatureState?) -> FeatureState)

    /** Drops every tracked state. Used when the owning session is torn down. */
    suspend fun reset()
}

/**
 * The in-memory [FeatureStateRepository].
 *
 * Thread-safe through a single mutex: concurrent readers see immutable
 * snapshots via the [StateFlow], and concurrent writers serialize. No hidden
 * mutable global state — the map lives in this instance, and instances are
 * handed to the engine that owns them.
 */
class InMemoryFeatureStateRepository : FeatureStateRepository {

    private val mutex = Mutex()
    private val backing = MutableStateFlow<Map<FeatureId, FeatureState>>(emptyMap())

    override val states: StateFlow<Map<FeatureId, FeatureState>> = backing.asStateFlow()

    override fun stateOf(feature: FeatureId): FeatureState? = backing.value[feature]

    override suspend fun update(feature: FeatureId, transition: (FeatureState?) -> FeatureState) {
        mutex.withLock {
            val current = backing.value[feature]
            val next = transition(current)
            require(FeatureStateTransitions.isLegal(current, next)) {
                "refusing illegal feature-state transition for ${feature.qualifiedName}: " +
                    "${describe(current)} -> ${describe(next)}"
            }
            backing.value = backing.value + (feature to next)
        }
    }

    override suspend fun reset() {
        mutex.withLock {
            backing.value = emptyMap()
        }
    }

    private fun describe(state: FeatureState?): String =
        state?.let { it::class.simpleName } ?: "<untracked>"
}
