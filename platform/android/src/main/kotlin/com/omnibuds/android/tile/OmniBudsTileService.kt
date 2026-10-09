package com.omnibuds.android.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OmniBuds Quick Settings tile.
 *
 * Phase 25: public TileService APIs only. The service is a thin shell —
 * all logic lives in [QuickSettingsCoordinator], [TileStateMapper],
 * [TileTargetResolver], and [TileActionDispatcher], which are unit-tested
 * without Android.
 *
 * Dependencies are provided through [TileDependencies] so tests can
 * substitute fakes. The default holder is process-scoped and replaceable.
 */
class OmniBudsTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val coordinator: QuickSettingsCoordinator
        get() = TileDependencies.coordinator()

    override fun onStartListening() {
        super.onStartListening()
        coordinator.startListening(serviceScope) { tileState ->
            render(tileState)
        }
    }

    override fun onStopListening() {
        coordinator.stopListening()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Tile clicks are fire-and-forget from the framework's perspective;
        // the coordinator reports outcomes through the next state render.
        // A real feature id/mode set comes from capability metadata; until
        // a controllable feature is registered this is a no-op that keeps
        // the tile honest (non-clickable states never reach here).
        serviceScope.launch {
            // No-op: clicks on non-clickable tiles are not delivered by the
            // system. Clickable tiles always have a bound action configured
            // by the host app through TileDependencies.
            TileDependencies.onTileClick?.invoke()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        coordinator.clearFailure()
    }

    override fun onTileRemoved() {
        coordinator.stopListening()
        super.onTileRemoved()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun render(state: TileState) {
        val tile = qsTile ?: return
        tile.state = when (state.kind) {
            TileKind.READY_WITH_ACTION -> Tile.STATE_ACTIVE
            TileKind.READY_NO_ACTION -> Tile.STATE_ACTIVE
            TileKind.OPERATION_PENDING -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        // UNAVAILABLE kinds render as inactive rather than the platform's
        // "unavailable" grey when the tile may become available soon; the
        // subtitle carries the honest reason.
        if (state.kind == TileKind.NO_DEVICE ||
            state.kind == TileKind.DISCONNECTED ||
            state.kind == TileKind.UNKNOWN
        ) {
            tile.state = Tile.STATE_UNAVAILABLE
        }
        tile.label = state.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state.subtitle
        }
        // Content description for accessibility carries the same honest text.
        tile.contentDescription = listOfNotNull(state.label, state.subtitle).joinToString(", ")
        tile.updateTile()
    }

    companion object {
        /**
         * Request a tile-state refresh, API 24+. Safe to call when the
         * service may not be listening — the platform ignores it.
         */
        fun requestUpdate(context: android.content.Context) {
            requestListeningState(
                context,
                android.content.ComponentName(context, OmniBudsTileService::class.java),
            )
        }
    }
}

/**
 * Process-scoped dependencies for the tile service.
 *
 * The host application sets these at startup. Tests replace them with fakes.
 */
object TileDependencies {
    private var coordinatorFactory: (() -> QuickSettingsCoordinator)? = null
    private var contextFactory: (() -> android.content.Context)? = null

    /** Invoked on tile click when the host app configures an action. */
    var onTileClick: (suspend () -> Unit)? = null

    fun install(
        coordinator: () -> QuickSettingsCoordinator,
        appContext: () -> android.content.Context,
    ) {
        coordinatorFactory = coordinator
        contextFactory = appContext
    }

    fun coordinator(): QuickSettingsCoordinator =
        coordinatorFactory?.invoke()
            ?: throw IllegalStateException(
                "TileDependencies not installed — the host app must call install() at startup",
            )

    fun appContext(): android.content.Context =
        contextFactory?.invoke()
            ?: throw IllegalStateException("TileDependencies not installed")

    /** Reset for tests. */
    fun resetForTests() {
        coordinatorFactory = null
        contextFactory = null
        onTileClick = null
    }
}
