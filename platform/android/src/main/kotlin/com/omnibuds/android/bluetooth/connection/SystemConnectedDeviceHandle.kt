package com.omnibuds.android.bluetooth.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCsipSetCoordinator
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothHearingAid
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.PlatformRegistration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one class in this codebase permitted to touch Android's device-facing Bluetooth API.
 *
 * It holds four facts and nothing else: which profiles can be asked, what they answer, which
 * announcements the stack makes, and which devices this phone has paired. `BluetoothAdapter.getProfileProxy`
 * is the only route to a per-profile device list for the audio and human-interface profiles - `BluetoothAdapter`
 * exposes no `getConnectedDevices` and no `getSupportedProfiles` in the public API at all, so which profiles to
 * ask is a list this project carries rather than a question the phone answers (research sections 3.1, 3.4) -
 * and `BluetoothManager.getDevicesMatchingConnectionStates` answers for the attribute protocol without
 * any binding, which is why [ObservedProfile.GATT] never reaches the binder.
 *
 * Every framework member below was read from the compileSdk 35 platform data rather than recalled, and
 * the two claims that could not be confirmed are carried as unknowns instead of assumed:
 *  - `getProfileProxy(Context, BluetoothProfile.ServiceListener, int)` returns a bare `boolean` with no
 *    error code, and its shipped Javadoc names only five profiles while the reference implementation
 *    refuses any profile it has no constructor for. A `false` is therefore recorded as a refusal of
 *    *that profile* and never as an empty device list (research sections 3.3, 7.1; U-3).
 *  - `closeProfileProxy(int, BluetoothProfile)` documents two profiles where `getProfileProxy` documents
 *    five. That narrower text is why teardown closes exactly what this instance bound, and why a profile
 *    service that cannot be cleared is reported rather than assumed away (U-3).
 *
 * A bind still in flight is reported [ProfileAnswerability.AWAITING_CALLBACK] and nothing here waits for
 * the callback to arrive. The platform's own documentation promises no completion for a `true` return, so
 * blocking a snapshot on one would turn an UNVERIFIED promise into this project's own hang; the next
 * round asks again, which is the shape ADR-P3-008 chose over a retry loop.
 *
 * No method on this class can pair, connect, enable, discover, scan, open a socket, send anything over
 * the air or change anything at all. `getAddress`, `getName` and `getBondState` are reads of an object
 * the platform handed us, and `getBondedDevices` is a read of the phone's own stored pairing records: it
 * asks nothing of any device, sends nothing, and is the mechanism prompt section 10.B's collection exists
 * to report. The mutation-shaped look-alikes - `fetchUuidsWithSdp`, `createBond`,
 * `connectGatt`, the voice-recognition calls, `setPriorityPolicy` - are refused by name in
 * `DependencyDirectionTest` rule 7, which since this phase scans `src/androidTest` as well as
 * `src/main` so a test cannot reach one unnoticed (ADR-P3-007's named guard gap).
 */
class SystemConnectedDeviceHandle(
    private val context: Context,
    private val apiLevel: ApiLevelProvider,
) : ConnectedDeviceHandle {

    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = manager?.adapter

    /**
     * The channels currently listening, each owning its own receiver and its own bound proxies.
     *
     * Ownership is per channel rather than per handle so that a second open cannot overwrite the first
     * channel's receiver - which would leave a registered receiver nobody could unregister and let one
     * teardown unregister somebody else's listener. With one channel open (the normal case, and the one
     * [com.omnibuds.core.platform.ConnectedDeviceObserver]'s slot mutex keeps) the two views agree.
     */
    private val channels = CopyOnWriteArrayList<Channel>()

    override val adapterPresent: Boolean
        get() = adapter != null

    override fun answerability(profile: ObservedProfile): ProfileAnswerability {
        // The attribute protocol is answered by the manager object, which has no binding to wait for and
        // none to lose. A phone with no manager has nothing to answer with, and that is a statement about
        // the mechanism rather than about any device.
        if (profile == ObservedProfile.GATT) {
            return if (manager == null) ProfileAnswerability.REFUSED else ProfileAnswerability.ANSWERABLE
        }
        // The strongest answer any live channel gives, by the rank below rather than by declaration
        // order: a channel that was refused is a weaker claim than one still waiting, and a channel that
        // never asked is weaker still - collapsing them would let one careless open report the platform
        // as having declined something it never requested.
        val answers = channels.map { channel -> channel.answerability(profile) }
        return answers.maxByOrNull { answer -> answer.evidenceRank() } ?: ProfileAnswerability.NOT_REQUESTED
    }

    /**
     * Asks one mechanism for the devices it reports a link to.
     *
     * The per-device state is read from the same mechanism that produced the list, so a profile's report
     * of *its own* link is the platform's answer rather than an inference from which filter the device
     * happened to match (research section 2c: the adapter-level aggregate collapses several devices into
     * one number, and this avoids borrowing that lossiness).
     *
     * `MissingPermission` is suppressed rather than answered, and the reason is the phase's own mechanism:
     * lint asks for a `checkPermission` at the call site or a caught `SecurityException`, and this call
     * already has both, one level up. [AndroidConnectedDeviceSource] settles standing through
     * [com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider] before it reaches any
     * enumeration (ADR-P3-009), and it wraps every call here in a `runCatching` that turns a thrown
     * [SecurityException] into a refused round. Duplicating the check inside the handle would be a second
     * permission decision with a second owner - the failure mode `DependencyDirectionTest` and ADR-P2-009
     * exist to keep out of this module - and lint cannot see through the seam to know the first one ran.
     */
    @SuppressLint("MissingPermission")
    override fun enumerate(profile: ObservedProfile): ProfileEnumeration {
        if (profile == ObservedProfile.GATT) {
            val manager = this.manager ?: return ProfileEnumeration.Unanswered
            val constant = profile.platformConstant()
            return enumerationOf(
                manager.getDevicesMatchingConnectionStates(constant, LINKED_STATES),
            ) { device -> manager.getConnectionState(device, constant) }
        }
        val proxy = channels.firstNotNullOfOrNull { channel -> channel.proxyFor(profile) }
            ?: return ProfileEnumeration.Unanswered
        return enumerationOf(proxy.getDevicesMatchingConnectionStates(LINKED_STATES)) { device ->
            proxy.getConnectionState(device)
        }
    }

    /**
     * The phone's paired-device list, together with the two adapter facts that decide what an empty list
     * means.
     *
     * The adapter reading is taken first and reported as its own case, not folded into the set, because
     * the shipped Javadoc for `getBondedDevices()` says so in as many words: "If Bluetooth state is not
     * STATE_ON, this API will return an empty set" and "@return unmodifiable set of BluetoothDevice, or
     * null on error". A switched-off adapter therefore produces exactly the value that would otherwise
     * mean "this phone has paired nothing", which is the confusion ADR-P3-009 exists to close and which
     * the code comment in [AndroidConnectedDeviceSource.snapshot] has said all along that this call would
     * answer empty. So [BondedListing.AdapterNotOn] and [BondedListing.ReadFailed] are refusals this
     * method raises before the set can be mistaken for a census, and only a present, on, answering
     * adapter reaches [BondedListing.Reported].
     *
     * The local adapter-state read is the same fact Phase 2 reads through
     * `SystemBluetoothAdapterHandle`, and it appears here for one reason: the bond call's own
     * documentation makes its emptiness depend on that reading, so the reading belongs to this call
     * rather than to a cross-package dependency on another boundary's handle. Nothing is cached, no
     * receiver is registered here and there is no poll - one read, per round, on the caller's dispatcher.
     *
     * `MissingPermission` is suppressed for the reason [enumerate] states: standing for
     * `device.bonded-list-inspection` is settled one level up, before this method is reachable at all,
     * and [AndroidConnectedDeviceSource] wraps the call so a thrown [SecurityException] becomes a refused
     * round rather than an empty one.
     */
    @SuppressLint("MissingPermission")
    override fun bondedDevices(): BondedListing {
        val adapter = this.adapter ?: return BondedListing.AdapterAbsent
        val state = runCatching { adapter.state }.getOrNull() ?: return BondedListing.ReadFailed
        if (state != BluetoothAdapter.STATE_ON) return BondedListing.AdapterNotOn
        val devices = runCatching { adapter.bondedDevices }.getOrNull() ?: return BondedListing.ReadFailed
        return BondedListing.Reported(devices.map { device -> bondedReportOf(device) })
    }

    /**
     * Opens one channel, and unwinds it if starting failed part way.
     *
     * The channel joins [channels] before it starts, because a receiver that was registered and then
     * abandoned by a throw halfway through the bind sequence is a leak with no owner left who could
     * dispose it - the same hazard as a bound proxy outliving its observer, arriving one step earlier. The
     * caller sees the exception either way: [AndroidConnectedDeviceSource] turns it into a structured
     * refusal and hands back no registration, which is only true because nothing was left behind here.
     */
    override fun openAnnouncements(
        profiles: List<ObservedProfile>,
        emit: (DeviceAnnouncement) -> Unit,
    ): PlatformRegistration {
        val channel = Channel(profiles, emit)
        channels += channel
        try {
            channel.start()
        } catch (problem: Throwable) {
            channel.abort()
            throw problem
        }
        return channel
    }

    /**
     * One open listener: a receiver, the profiles it asked the platform for, and their bindings.
     *
     * [start] registers first and binds second, so an announcement the platform makes while a bind is
     * still queued has somewhere to land. [dispose] releases in the opposite order, so no announcement
     * can arrive for a proxy that has already been closed.
     */
    private inner class Channel(
        private val profiles: List<ObservedProfile>,
        private val emit: (DeviceAnnouncement) -> Unit,
    ) : PlatformRegistration {

        private val receiver = AnnouncementReceiver(emit)
        private val listener = BindingListener()
        private val bindings = LinkedHashMap<ObservedProfile, Binding>()
        private val open = AtomicBoolean(true)

        @Volatile
        private var receiverRegistered = false

        /**
         * What this channel knows about [profile]'s mechanism.
         *
         * [ProfileAnswerability.NOT_REQUESTED] covers "this channel never asked", which is a different
         * fact from the platform declining and is why the handle asks rather than assuming one open
         * channel owns the answer.
         */
        fun answerability(profile: ObservedProfile): ProfileAnswerability {
            val binding = bindingFor(profile) ?: return ProfileAnswerability.NOT_REQUESTED
            return when {
                binding.proxy != null -> ProfileAnswerability.ANSWERABLE
                !binding.settled -> ProfileAnswerability.AWAITING_CALLBACK
                else -> ProfileAnswerability.REFUSED
            }
        }

        /** The service handle this channel holds for [profile], or null while it has none. */
        fun proxyFor(profile: ObservedProfile): BluetoothProfile? = bindingFor(profile)?.proxy

        private fun bindingFor(profile: ObservedProfile): Binding? =
            synchronized(bindings) { bindings[profile] }

        /**
         * Registers for the announcements and requests one binding per profile in the union.
         *
         * A profile the platform will not bind is recorded as refused rather than retried. Retrying is
         * the tempting design and it is the wrong one twice over: the phase forbids a loop, and a
         * handset that refuses `HEALTH` or an unknown constant refuses it for the lifetime of the
         * process, so a retry becomes background work that never ends (research section 3.3).
         */
        fun start() {
            register(receiver, connectionFilter())
            receiverRegistered = true
            val adapter = this@SystemConnectedDeviceHandle.adapter
            for (profile in profiles) {
                if (profile == ObservedProfile.GATT) continue
                if (adapter == null) {
                    // No adapter means no service to bind. That is the platform declining the mechanism,
                    // and it is recorded per profile so the union reports the gap instead of inventing a
                    // device list to explain it.
                    record(Binding(profile, refused = true))
                    continue
                }
                val binding = Binding(profile)
                record(binding)
                // A throw here is treated as a refusal of this profile rather than as the failure of the
                // whole open: the receiver is already registered, so the caller needs a registration it
                // can dispose, and one unavailable profile must not cost the user every other one.
                val accepted = runCatching { adapter.getProfileProxy(context, listener, profile.platformConstant()) }
                    .getOrDefault(false)
                if (!accepted) binding.refused = true
            }
        }

        override val isActive: Boolean
            get() = open.get()

        /**
         * Releases everything this channel holds, once.
         *
         * Idempotence is a contract requirement rather than a convenience: the observer disposes in a
         * `finally` and a cancellation path may have disposed already, and Android throws at an
         * unregister it does not recognise. Both halves are attempted even if one throws, because a
         * teardown that stopped at the first failure would leave a bound profile service with no owner
         * left who could close it, which is the leak ADR-P3-008's consequence names. The first problem
         * seen is rethrown only after the rest has been tried, so the failure surfaces instead of being
         * traded for a tidy shutdown.
         */
        override suspend fun dispose() {
            if (!open.compareAndSet(true, false)) return
            var problem: Throwable? = null
            if (receiverRegistered) {
                problem = runCatching { context.unregisterReceiver(receiver) }.exceptionOrNull()
            }
            val outstanding = synchronized(bindings) {
                val held = bindings.values.toList()
                bindings.clear()
                held
            }
            val adapter = this@SystemConnectedDeviceHandle.adapter
            if (adapter != null) {
                for (binding in outstanding) {
                    val proxy = binding.proxy ?: continue
                    val cause = runCatching { adapter.closeProfileProxy(binding.profile.platformConstant(), proxy) }
                        .exceptionOrNull()
                    if (cause != null && problem == null) problem = cause
                }
            }
            channels -= this
            problem?.let { throw it }
        }

        /**
         * Releases what a half-started channel holds, without the suspension [dispose] needs.
         *
         * Called from the open path, which is not a coroutine, and best-effort by design: a teardown of a
         * registration that never completed has nothing to report to anyone but the platform, so every
         * step is attempted and none is rethrown. The caller is already receiving the exception that made
         * this necessary, and that one is the more informative of the two.
         */
        fun abort() {
            if (!open.compareAndSet(true, false)) return
            if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
            val held = synchronized(bindings) {
                val snapshot = bindings.values.toList()
                bindings.clear()
                snapshot
            }
            val adapter = this@SystemConnectedDeviceHandle.adapter
            if (adapter != null) {
                for (binding in held) {
                    val proxy = binding.proxy ?: continue
                    runCatching { adapter.closeProfileProxy(binding.profile.platformConstant(), proxy) }
                }
            }
            channels -= this
        }

        private fun record(binding: Binding) {
            synchronized(bindings) { bindings[binding.profile] = binding }
        }

        private fun connectionFilter(): IntentFilter {
            val filter = IntentFilter()
            for (action in ANNOUNCEMENT_ACTIONS) filter.addAction(action.action)
            return filter
        }

        /**
         * One profile's service handle, as this channel's callback last reported it.
         *
         * [proxy] is volatile because `onServiceConnected` is delivered on the main thread while
         * [enumerate] runs on the caller's dispatcher; a plain field would let a round read a binding
         * that had already been dropped.
         */
        private inner class Binding(
            val profile: ObservedProfile,
            @Volatile var proxy: BluetoothProfile? = null,
            @Volatile var refused: Boolean = false,
            @Volatile var connected: Boolean = false,
        ) {
            /** False while the platform still owes an answer, true once it has given one either way. */
            val settled: Boolean
                get() = refused || connected || proxy != null
        }

        /**
         * Bridges the platform's asynchronous service callback into this channel's binding table.
         *
         * The callback names a profile `int`, so the binding is matched to the request that made it
         * rather than to the class of the returned object: `BluetoothHearingAid` and `BluetoothLeAudio`
         * are separate classes today, and a lookup keyed on class name would break the first time a
         * handset returned one proxy for two profiles. A callback for a profile this channel never asked
         * for is ignored, because accepting it would credit a binding nobody owns and nobody could close.
         *
         * The returned object is used through [BluetoothProfile] and never cast: `getDevicesMatchingConnectionStates`
         * and `getConnectionState` are declared on the interface every public proxy implements, verified
         * against the API-35 stubs, so naming `BluetoothA2dp` or `BluetoothCsipSetCoordinator` here would
         * tie this file to four different introduction levels to ask the same question.
         */
        private inner class BindingListener : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                val binding = bindingOf(profile) ?: return
                if (proxy == null) {
                    binding.refused = true
                } else {
                    binding.proxy = proxy
                    binding.connected = true
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                val binding = bindingOf(profile) ?: return
                // The service went away, so this profile contributes no evidence from here on. Refusing
                // it rather than emptying it is what keeps the next union honest about the gap: an
                // unbound proxy is not a report that the user's earbuds disconnected (ADR-P3-008). No
                // rebind is attempted - the platform offers no way to re-arm a dropped proxy from here,
                // and a silent re-request would be the retry loop this phase forbids.
                binding.proxy = null
                binding.connected = false
                binding.refused = true
            }

            private fun bindingOf(profile: Int): Binding? =
                ObservedProfile.fromPlatformConstant(profile)?.let { bindingFor(it) }
        }
    }

    /** The announcements the stack makes about devices this app never asked to open. */
    private class AnnouncementReceiver(private val emit: (DeviceAnnouncement) -> Unit) : BroadcastReceiver() {

        @Suppress("DEPRECATION")
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val announcement = ANNOUNCEMENT_ACTIONS.firstOrNull { candidate -> candidate.action == action } ?: return
            // `getParcelableExtra(String)` is deprecated at API 33 in favour of an overload that does not
            // exist below it, and this module's floor is 26. One call path beats a version branch here:
            // the deprecated form still answers on every release this app can install on, and the
            // alternative is two paths for the same extra. A missing device reads as null and is never
            // guessed at, because a broadcast that named no device is not a report about one.
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (announcement.kind) {
                AnnouncementKind.LINK -> emit(
                    DeviceAnnouncement.Link(
                        reportedAddress = device?.address,
                        profile = announcement.profile,
                        link = rawLinkStateOf(
                            intent.getIntExtra(BluetoothProfile.EXTRA_STATE, RAW_EXTRA_UNREADABLE),
                            announcement.fixedState,
                        ),
                    ),
                )

                AnnouncementKind.BOND -> emit(
                    DeviceAnnouncement.Bond(
                        reportedAddress = device?.address,
                        bond = rawBondStateOf(
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, RAW_EXTRA_UNREADABLE),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Registers so the Bluetooth stack can reach this receiver: exported from API 33, flag-less below it
     * where the overload does not exist.
     *
     * ADR-P3-013 is the reason and it is not restated at length here. Being *protected* governs who may
     * send these actions; the export flag governs who may reach the receiver, and a not-exported filter is
     * documented to lose the traffic the Bluetooth app relays from its own UID. The alternative is a
     * receiver that never hears anything and a projection that reports an empty room forever while
     * looking correct - the failure this phase has spent three ADRs refusing.
     *
     * Both lint complaints on this body are the guarded pattern's own shadow and are suppressed rather
     * than answered with an `androidx.core` dependency the phase has no reason to take: `InlinedApi`
     * fires on constants read inside the guarded branch, and `UnspecifiedRegisterReceiverFlag` fires on
     * the flag-less branch because lint cannot inspect which actions the filter holds.
     */
    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (apiLevel.apiLevel() >= RECEIVER_FLAG_API_LEVEL) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        /**
         * The states one enumeration is asked for: a live link and the two transitions around it.
         *
         * `STATE_DISCONNECTED` is deliberately absent. This phase answers "which devices does the
         * platform report a link to", and asking for the disconnected set as well would put every device
         * a handset has ever kept a profile record for into the projection - a larger claim than was
         * asked for, and one that would read as a device census. Absence from the union becomes a
         * disconnect only when every profile in it answered, which is ADR-P3-015 rule 4; this list and
         * that rule have to be read together.
         *
         * The device the disconnected set would have supplied is now asked for and answered somewhere
         * else: [bondedDevices] reads the phone's own pairing records, which is a named source for "this
         * device exists and is paired" rather than a profile's leftover bookkeeping, and it arrives in the
         * projection's paired collection where a link claim is not available to be made (ADR-P3-017).
         * Widening this array is therefore still the wrong way to populate the disconnected set, and this
         * note is the reason it stays that way.
         */
        val LINKED_STATES = intArrayOf(
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING,
        )

        const val RECEIVER_FLAG_API_LEVEL = 33

        /** Marker for "the broadcast carries no readable value", chosen to sit outside every platform range. */
        const val RAW_EXTRA_UNREADABLE = -1

        /**
         * The actions this receiver listens for, each with the profile that speaks it.
         *
         * The link-layer pair names no service, and per ADR-P3-015 rule 3 the engine reads that absence as
         * ending every profile's claim. `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` is excluded on
         * purpose: it is an aggregate edge with no device extra, so a second earbud produces nothing and
         * its silence cannot be attributed to anybody (research section 2b).
         * `ACTION_ACL_DISCONNECT_REQUESTED` is excluded because a request is not a change of state, and
         * acting on it would report a disconnect the link never had. `ACTION_NAME_CHANGED`,
         * `ACTION_CLASS_CHANGED` and `ACTION_ALIAS_CHANGED` are excluded because they carry no link fact
         * and every descriptor is re-read at the next snapshot anyway - and the class one especially,
         * since a reader would be tempted to finish the inference prompt section 16 forbids. The
         * human-interface device profile has no public connection-state action in the compile SDK at all,
         * so it is enumerable and never announceable: a stated gap in this table rather than an oversight.
         */
        val ANNOUNCEMENT_ACTIONS = connectionAnnouncements()

        fun rawLinkStateOf(raw: Int, fixed: Int?): RawLinkState = when (fixed ?: raw) {
            BluetoothProfile.STATE_CONNECTED -> RawLinkState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> RawLinkState.CONNECTING
            BluetoothProfile.STATE_DISCONNECTING -> RawLinkState.DISCONNECTING
            BluetoothProfile.STATE_DISCONNECTED -> RawLinkState.DISCONNECTED
            else -> RawLinkState.UNREADABLE
        }

        fun rawBondStateOf(raw: Int): RawBondState = when (raw) {
            BluetoothDevice.BOND_BONDED -> RawBondState.BONDED
            BluetoothDevice.BOND_BONDING -> RawBondState.BONDING
            BluetoothDevice.BOND_NONE -> RawBondState.NONE
            else -> RawBondState.UNREADABLE
        }

        /**
         * One device, reduced to what this phase is authorised to read, with its link read from the
         * mechanism that listed it.
         *
         * Each descriptor is read separately and a failure leaves that field unread rather than ending the
         * enumeration. `getName()` and `getBondState()` are documented cache reads that answer null or a
         * sentinel when the handset has nothing cached, and research section 5.1 records that a cold cache
         * is the normal case for an app that does not scan - which is the common case here. A device the
         * platform named but cannot describe is reported as named-and-undescribed, which is what lets the
         * engine keep the fact and refuse the inference.
         *
         * `MissingPermission` is suppressed for the same reason `enumerate` states: standing is settled one
         * level up by [AndroidConnectedDeviceSource], and every read here is already inside a `runCatching`
         * that turns a thrown refusal into an unread field instead of a fabricated one.
         */
        @SuppressLint("MissingPermission")
        fun deviceReportOf(device: BluetoothDevice?, linkOf: (BluetoothDevice) -> Int): DeviceReport {
            if (device == null) {
                return DeviceReport(null, null, RawLinkState.UNREADABLE, RawBondState.UNREADABLE)
            }
            return DeviceReport(
                reportedAddress = runCatching { device.address }.getOrNull(),
                reportedName = runCatching { device.name }.getOrNull(),
                link = rawLinkStateOf(runCatching { linkOf(device) }.getOrDefault(RAW_EXTRA_UNREADABLE), null),
                bond = rawBondStateOf(runCatching { device.bondState }.getOrDefault(RAW_EXTRA_UNREADABLE)),
            )
        }

        /**
         * One paired device, reduced to what this phase is authorised to read, with no link claim in it.
         *
         * The same per-field discipline as [deviceReportOf]: each descriptor is a separate cache read and
         * a failure leaves that field unread instead of ending the list. What is *not* read here is any
         * link state, because the bond list reports stored keys and the platform's own wording for a bond
         * is that it "does not necessarily mean the device is currently connected" - so a link field in
         * this record could only be filled by an inference, and prompt section 6 forbids the inference.
         */
        @SuppressLint("MissingPermission")
        fun bondedReportOf(device: BluetoothDevice?): BondedDeviceReport {
            if (device == null) {
                return BondedDeviceReport(null, null, RawBondState.UNREADABLE)
            }
            return BondedDeviceReport(
                reportedAddress = runCatching { device.address }.getOrNull(),
                reportedName = runCatching { device.name }.getOrNull(),
                bond = rawBondStateOf(runCatching { device.bondState }.getOrDefault(RAW_EXTRA_UNREADABLE)),
            )
        }

        fun enumerationOf(devices: List<BluetoothDevice?>, linkOf: (BluetoothDevice) -> Int): ProfileEnumeration =
            ProfileEnumeration.Reported(devices.map { device -> deviceReportOf(device, linkOf) })
    }
}

/** Which axis an announcement moves. */
private enum class AnnouncementKind { LINK, BOND }

/**
 * The action table, built inside a function so the suppression can be answered at one place.
 *
 * `InlinedApi` fires because four of these constants were introduced above this module's floor of 26
 * (`ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` at 31, the hearing-aid action at 29, the CSIS action at 33).
 * They are compile-time `String` values that the build inlines, so nothing links against a class the
 * handset may not have, and an action the OS does not broadcast is simply an action that never arrives -
 * which is the same reasoning Phase 2 recorded for the receiver-flag constant
 * (`SystemBluetoothAdapterHandle`), in the opposite direction: here the value is harmless on old devices
 * and the filter is what makes it meaningful on new ones.
 */
@SuppressLint("InlinedApi")
private fun connectionAnnouncements(): List<Announcement> = listOf(
    Announcement(BluetoothDevice.ACTION_ACL_CONNECTED, null, AnnouncementKind.LINK, BluetoothProfile.STATE_CONNECTED),
    Announcement(
        BluetoothDevice.ACTION_ACL_DISCONNECTED,
        null,
        AnnouncementKind.LINK,
        BluetoothProfile.STATE_DISCONNECTED,
    ),
    Announcement(BluetoothDevice.ACTION_BOND_STATE_CHANGED, null, AnnouncementKind.BOND, null),
    Announcement(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, ObservedProfile.A2DP, AnnouncementKind.LINK, null),
    Announcement(
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        ObservedProfile.HEADSET,
        AnnouncementKind.LINK,
        null,
    ),
    Announcement(
        BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED,
        ObservedProfile.LE_AUDIO,
        AnnouncementKind.LINK,
        null,
    ),
    Announcement(
        BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED,
        ObservedProfile.HEARING_AID,
        AnnouncementKind.LINK,
        null,
    ),
    Announcement(
        BluetoothCsipSetCoordinator.ACTION_CSIS_CONNECTION_STATE_CHANGED,
        ObservedProfile.CSIP_SET_COORDINATOR,
        AnnouncementKind.LINK,
        null,
    ),
)

/** One action, the profile that speaks it, and the link state the action itself already fixes. */
private data class Announcement(
    val action: String,
    val profile: ObservedProfile?,
    val kind: AnnouncementKind,
    val fixedState: Int?,
)

/**
 * The profile constant the platform names this entry with.
 *
 * A function rather than a field on [ObservedProfile], because the numbers are the framework's and
 * `:core` may not carry them: `DependencyDirectionTest` rule 2 fails the build over a single framework
 * type name in a core source, and a framework constant living beside core's own enum would be the same
 * leak through a smaller door. Each value was read from the compileSdk 35 stub source of
 * `android/bluetooth/BluetoothProfile.java` in this session: `HEADSET` 1, `A2DP` 2, `GATT` 7,
 * `HID_DEVICE` 19, `HEARING_AID` 21, `LE_AUDIO` 22, `CSIP_SET_COORDINATOR` 25.
 *
 * `InlinedApi` is the same stated case as the action table: these are compile-time `int`s, and a constant
 * this handset's release never defined cannot be returned by it, so reading the name is harmless. The
 * value is only ever *used* behind the API-level gate [AndroidConnectedDeviceSource] applies through
 * [ObservedProfile.availabilityAt], which is where the phase's version discipline actually lives.
 */
@SuppressLint("InlinedApi")
internal fun ObservedProfile.platformConstant(): Int = when (this) {
    ObservedProfile.HEADSET -> BluetoothProfile.HEADSET
    ObservedProfile.A2DP -> BluetoothProfile.A2DP
    ObservedProfile.GATT -> BluetoothProfile.GATT
    ObservedProfile.HID_DEVICE -> BluetoothProfile.HID_DEVICE
    ObservedProfile.HEARING_AID -> BluetoothProfile.HEARING_AID
    ObservedProfile.LE_AUDIO -> BluetoothProfile.LE_AUDIO
    ObservedProfile.CSIP_SET_COORDINATOR -> BluetoothProfile.CSIP_SET_COORDINATOR
}

/** The reverse of [platformConstant], or null for a constant this phase does not enumerate. */
internal fun ObservedProfile.Companion.fromPlatformConstant(constant: Int): ObservedProfile? =
    ObservedProfile.entries.firstOrNull { profile -> profile.platformConstant() == constant }
