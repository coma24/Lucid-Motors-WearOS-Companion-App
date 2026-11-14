package com.droid.lucidgravitywatch.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SeatClimateSettings
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.network.LucidApiException
import com.droid.lucidgravitywatch.repository.LucidVehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** The watch screens: the home dashboard and the pages it drills down into. */
enum class WatchPage(val title: String) {
    HOME("Home"),
    CLIMATE("Climate"),
    CHARGING("Charging"),
    FLONK("Flonk"),
    SEAT_HEAT("Seat Heat"),
    SEAT_COOLING("Seat Cooling");

    /** Where back/swipe-back goes from this page. */
    val parent: WatchPage
        get() = when (this) {
            SEAT_HEAT, SEAT_COOLING -> CLIMATE
            else -> HOME
        }
}

data class GravityWatchUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isCommandPending: Boolean = false,
    val isInteractive: Boolean = false,
    val isAmbient: Boolean = false,
    val isPolling: Boolean = false,
    val currentPollIntervalMs: Long = GravityWatchViewModel.IDLE_POLL_INTERVAL_MS,
    val selectedRegion: LucidRegion = LucidRegion.US,
    val useFahrenheit: Boolean = true,
    val watchBezelFrameEnabled: Boolean = true,
    val currentPage: WatchPage = WatchPage.HOME,
    val userEmail: String = "",
    val userDisplayName: String = "",
    val vehicles: List<GravityVehicleSnapshot> = emptyList(),
    val selectedVehicleId: String? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    /** True when the most recent Lucid Cloud fetch succeeded, i.e. [vehicles] reflects the car. */
    val hasFreshTelemetry: Boolean = false,
    val isWakingVehicle: Boolean = false,
    /** Label of the command whose effect on the car has not been confirmed yet. */
    val awaitingVehicleStateLabel: String? = null,
    /** True while the user has opened the vehicle selector to swap cars. */
    val isSelectingVehicle: Boolean = false,
    /** True after Lucid Cloud rejected a call as rate-limited, until the next successful fetch. */
    val isRateLimited: Boolean = false,
    /**
     * True while the active car keeps sending new reports (see GravityWatchViewModel's report
     * tracking). A charging car stays online in SLEEP_CHARGE without ever reporting an awake state.
     */
    val hasLiveReports: Boolean = false,
    /**
     * Seat levels tapped in on the seat diagram: held for [GravityWatchViewModel.SEAT_TAP_SETTLE_MS]
     * after the last tap, then sent, and shown until the car confirms them. Null when none.
     */
    val seatDraft: SeatClimateSettings? = null,
    /** Steering heater level tapped in on the seat diagram; handled like [seatDraft]. */
    val steeringDraft: SteeringWheelHeaterLevel? = null
) {
    /**
     * The vehicle being controlled: the user's saved choice, or the only vehicle on the account.
     * Null when the account has several vehicles and none has been chosen yet.
     */
    val activeVehicle: GravityVehicleSnapshot?
        get() = vehicles.firstOrNull { it.vehicleId == selectedVehicleId }
            ?: vehicles.singleOrNull()

    val hasMultipleVehicles: Boolean
        get() = vehicles.size > 1

    /** True when the vehicle selector should be shown instead of the dashboard. */
    val needsVehicleSelection: Boolean
        get() = isAuthenticated && hasMultipleVehicles && (isSelectingVehicle || activeVehicle == null)

    /** The active car reports an awake state, or is evidently online because its reports keep arriving. */
    val isVehicleReachable: Boolean
        get() = activeVehicle?.powerState?.isAwake == true || hasLiveReports

    /** Why vehicle controls are frozen, or null when the car is awake and its status is known. */
    val controlsLockedReason: String?
        get() {
            val vehicle = activeVehicle ?: return "No vehicle"
            return when {
                awaitingVehicleStateLabel != null -> "$awaitingVehicleStateLabel…"
                isCommandPending -> "Sending command…"
                !hasFreshTelemetry -> "Reading car status…"
                !isVehicleReachable -> if (isWakingVehicle) "Waking car…" else "Car asleep"
                else -> null
            }
        }

    val areControlsLocked: Boolean
        get() = controlsLockedReason != null
}

/**
 * Whether climate was on before Max AC or Max Defrost was turned on from the watch. Both modes turn
 * climate on, and turning them off leaves it on (as the Lucid app does too); turning climate off
 * also turns off the seat cooling Max AC switched on.
 */
private class ClimateRestorePoint(val climateWasOn: Boolean) {
    /** The user turned climate on/off or set a temperature while the mode was on. */
    var climateChangedByUser = false

    /** Climate should go back off: it was off before and the user has not touched it since. */
    fun turnsClimateOff(current: GravityVehicleSnapshot): Boolean =
        !climateWasOn && !climateChangedByUser && current.hvacPower.isActive
}

/** A state change a command should produce, described for the log. */
private class ExpectedCondition(
    val description: String,
    val isMet: (GravityVehicleSnapshot) -> Boolean
)

class GravityWatchViewModel(
    private val repository: LucidVehicleRepository,
    /** Monotonic watch time; only ever compared with itself, never with Lucid's report times. */
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {

    companion object {
        // Polling uses GetVehicleState, which showed no rate limit at 1 call/second. GetUserVehicles
        // (~5 calls per server minute) is only used to list the account's vehicles.
        const val IDLE_POLL_INTERVAL_MS = 3_000L
        const val FAST_POLL_INTERVAL_MS = 1_000L
        const val AMBIENT_POLL_INTERVAL_MS = 30_000L
        const val RATE_LIMITED_POLL_INTERVAL_MS = 15_000L
        /** How long polling stays at [FAST_POLL_INTERVAL_MS] after a command is sent. */
        const val FAST_POLL_WINDOW_MS = 10_000L
        /** Longest fast polling while waiting for a started charge to begin (see [pollFastUntil]). */
        const val CHARGE_START_FAST_POLL_MAX_MS = 120_000L
        /** How long polling stays at [RATE_LIMITED_POLL_INTERVAL_MS] after a rate-limit error. */
        const val RATE_LIMIT_BACKOFF_MS = 60_000L
        const val STATE_CONFIRMATION_TIMEOUT_MS = 45_000L
        private const val CONFIRMATION_TIMEOUT_TICK_MS = 1_000L
        const val WAKE_RETRY_INTERVAL_MS = 30_000L
        /**
         * A car counts as online when two consecutive new reports are at most this far apart in
         * Lucid's server clock. Awake and charging cars report every 1-3 s; a sleeping car's report
         * time stayed frozen for 78 s of polling, so one check-in alone never qualifies.
         */
        const val LIVE_REPORT_MAX_SPACING_MS = 10_000L
        /** Live status lapses when no new report has arrived for this long (watch time). */
        const val LIVE_REPORT_STALE_AFTER_MS = 30_000L
        /** Cabin setpoint range accepted by SetCabinTemperature (about 60-85 °F). */
        const val MIN_CABIN_TEMP_C = 15.5
        const val MAX_CABIN_TEMP_C = 29.5
        const val MIN_CHARGE_LIMIT_PERCENT = 50
        const val MAX_CHARGE_LIMIT_PERCENT = 100
        /**
         * Charge rate (AC current limit) range offered on the watch. 80 A is what a Lucid Air
         * reported on an 80 A charger; 10 A is a guess at the low end, to see what the car accepts.
         */
        const val MIN_CHARGE_CURRENT_AMPS = 10
        const val MAX_CHARGE_CURRENT_AMPS = 80

        /** The highest charge rate to offer: what the car says is available, up to [MAX_CHARGE_CURRENT_AMPS]. */
        fun maxChargeCurrentAmps(vehicle: GravityVehicleSnapshot): Int =
            vehicle.acCurrentLimitAmps
                .takeIf { it >= MIN_CHARGE_CURRENT_AMPS }
                ?.coerceAtMost(MAX_CHARGE_CURRENT_AMPS)
                ?: MAX_CHARGE_CURRENT_AMPS
        /** Seat diagram taps are sent once there has been no tap for this long. */
        const val SEAT_TAP_SETTLE_MS = 1_500L
        private const val GRPC_RESOURCE_EXHAUSTED = 8
        private const val TAG = "LucidVM"
    }

    private val _uiState = MutableStateFlow(GravityWatchUiState())
    val uiState: StateFlow<GravityWatchUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var wakeJob: Job? = null
    private var confirmationTimeoutJob: Job? = null
    private var fastPollWindowJob: Job? = null
    private var rateLimitBackoffJob: Job? = null
    private var expectedLockState: LockState? = null
    private var expectedFrunkOpen: Boolean? = null
    private var expectedRearTrunkOpen: Boolean? = null
    /** Other state a sent command should produce (climate, charging), checked against each poll. */
    private var expectedCondition: ExpectedCondition? = null
    private var awaitingPostCommandPoll: Boolean = false
    private var vehicleListJob: Job? = null
    /**
     * Keeps polling at [FAST_POLL_INTERVAL_MS] until a poll satisfies this (or its time cap runs
     * out), after the controls are released: a started charge goes through Preparing first.
     */
    private var fastPollUntil: ((GravityVehicleSnapshot) -> Boolean)? = null
    private var fastPollUntilJob: Job? = null
    /** Sends the seat draft once taps settle. */
    private var seatSendJob: Job? = null
    /** True once the seat draft has been sent; it is then kept until the command settles. */
    private var seatDraftSent: Boolean = false
    /** Set when Max AC / Max Defrost is turned on from the watch; used when turning it off. */
    private var maxAcRestore: ClimateRestorePoint? = null
    private var defrostRestore: ClimateRestorePoint? = null

    // Report tracking for the active car: report times are Lucid's server clock, so they are only
    // compared with each other; staleness is measured with the watch's monotonic clock.
    private var trackedVehicleId: String? = null
    private var latestReportMs: Long? = null
    private val newReportTimesMs = ArrayDeque<Long>()
    private var lastNewReportElapsedMs: Long = 0L

    init {
        restoreExistingSession()
    }

    private fun restoreExistingSession() {
        val session = repository.getActiveSession()
        val useF = repository.isFahrenheitPreferred()
        if (session == null) {
            _uiState.update { it.copy(useFahrenheit = useF) }
            return
        }
        Log.i(TAG, "Restoring saved session for ${session.userEmail} (${session.region})")
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                isLoading = true,
                selectedRegion = session.region,
                useFahrenheit = useF,
                userEmail = session.userEmail,
                userDisplayName = listOf(session.firstName, session.lastName)
                    .filter { part -> part.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { session.userEmail },
                selectedVehicleId = repository.getSelectedVehicleId()
            )
        }
        refreshVehicles()
    }

    private fun isPollingPowerStateActive(): Boolean =
        _uiState.value.isInteractive || _uiState.value.isAmbient

    /**
     * Updates whether the watch app is in the Interactive power state (on screen, display on) or the
     * Ambient power state (still started, display dimmed).
     * - Entering Interactive polls immediately, then every 3 seconds (every second for 10 seconds
     *   after a command, and while waking the car).
     * - Ambient polls every 30 seconds and hides the interactive-only yellow dot. The car is only
     *   auto-woken while Interactive, so an idle watch never keeps it awake.
     * The activity calls [suspendPolling] when it stops, so a backgrounded app never polls.
     */
    fun setInteractivePowerState(interactive: Boolean) {
        val wasInteractive = _uiState.value.isInteractive
        _uiState.update {
            it.copy(
                isInteractive = interactive,
                isAmbient = !interactive,
                isPolling = if (!interactive) false else it.isPolling
            )
        }
        if (interactive != wasInteractive) {
            Log.d(TAG, "Watch power state -> ${if (interactive) "interactive" else "ambient"}")
        }

        if (!_uiState.value.isAuthenticated) return

        if (interactive && !wasInteractive) {
            startPollingLoop(pollImmediately = true)
        } else if (pollingJob?.isActive != true) {
            startPollingLoop(pollImmediately = interactive)
        }
    }

    /**
     * Explicitly suspends polling when the app is completely closed or signed out.
     */
    fun suspendPolling() {
        Log.d(TAG, "Polling suspended")
        _uiState.update {
            it.copy(
                isInteractive = false,
                isAmbient = false,
                isPolling = false
            )
        }
        stopPollingLoop()
    }

    private fun hasPendingExpectedChanges(): Boolean =
        expectedLockState != null || expectedFrunkOpen != null || expectedRearTrunkOpen != null ||
            expectedCondition != null

    private fun clearExpectedChanges() {
        expectedLockState = null
        expectedFrunkOpen = null
        expectedRearTrunkOpen = null
        expectedCondition = null
        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = null
    }

    private fun resolvePollIntervalMs(): Long = when {
        rateLimitBackoffJob?.isActive == true -> RATE_LIMITED_POLL_INTERVAL_MS
        !_uiState.value.isInteractive -> AMBIENT_POLL_INTERVAL_MS
        fastPollWindowJob?.isActive == true || fastPollUntil != null || _uiState.value.isWakingVehicle ->
            FAST_POLL_INTERVAL_MS
        else -> IDLE_POLL_INTERVAL_MS
    }

    /**
     * Polls every second until a poll satisfies [done], for at most [maxMs]; independent of
     * pending commands, so the controls can be free meanwhile.
     */
    private fun pollFastUntil(maxMs: Long, done: (GravityVehicleSnapshot) -> Boolean) {
        fastPollUntil = done
        fastPollUntilJob?.cancel()
        fastPollUntilJob = viewModelScope.launch {
            delay(maxMs)
            stopFastPollUntil("time cap")
        }
        activateFastPolling()
    }

    private fun stopFastPollUntil(reason: String) {
        if (fastPollUntil == null) return
        Log.d(TAG, "Fast polling ended: $reason")
        fastPollUntil = null
        fastPollUntilJob?.cancel()
        fastPollUntilJob = null
        _uiState.update { it.copy(currentPollIntervalMs = resolvePollIntervalMs()) }
    }

    /** Polls every second for [FAST_POLL_WINDOW_MS] so a command's effect shows up promptly. */
    private fun activateFastPolling() {
        fastPollWindowJob?.cancel()
        fastPollWindowJob = viewModelScope.launch { delay(FAST_POLL_WINDOW_MS) }
        _uiState.update { it.copy(currentPollIntervalMs = resolvePollIntervalMs()) }
        if (isPollingPowerStateActive() && _uiState.value.isAuthenticated) {
            startPollingLoop(pollImmediately = false)
        }
    }

    private fun startRateLimitBackoff() {
        if (rateLimitBackoffJob?.isActive == true) return
        Log.w(TAG, "Rate limited by Lucid Cloud; polling every ${RATE_LIMITED_POLL_INTERVAL_MS / 1000}s for ${RATE_LIMIT_BACKOFF_MS / 1000}s")
        rateLimitBackoffJob = viewModelScope.launch { delay(RATE_LIMIT_BACKOFF_MS) }
    }

    private fun startPollingLoop(pollImmediately: Boolean) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var isFirstIteration = true
            while (isActive && isPollingPowerStateActive() && _uiState.value.isAuthenticated) {
                if (isFirstIteration && pollImmediately) {
                    isFirstIteration = false
                    performInteractivePoll()
                } else {
                    isFirstIteration = false
                    val delayMs = resolvePollIntervalMs()
                    _uiState.update { it.copy(currentPollIntervalMs = delayMs) }
                    delay(delayMs)
                    if (!isActive || !isPollingPowerStateActive() || !_uiState.value.isAuthenticated) {
                        break
                    }
                    performInteractivePoll()
                }
            }
        }
    }

    private fun stopPollingLoop() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    private suspend fun performInteractivePoll() {
        if (!isPollingPowerStateActive() || !_uiState.value.isAuthenticated) return
        // At startup no car is known until the vehicle list arrives; polling now would fetch the
        // list a second time (GetUserVehicles allows ~5 calls per minute), so wait for it instead.
        val pendingList = vehicleListJob
        if (_uiState.value.activeVehicle == null && pendingList?.isActive == true) {
            pendingList.join()
            return
        }

        val showYellowDot = _uiState.value.isInteractive
        _uiState.update { it.copy(isPolling = showYellowDot) }
        fetchAndApplyVehicleState("poll")
    }

    /**
     * Fetches the active vehicle's state (GetVehicleState) — or the whole vehicle list
     * (GetUserVehicles) when [refreshVehicleList] is set or no vehicle is chosen yet — and applies it
     * as the authoritative state: reconciles pending command expectations, releases frozen controls
     * once the car's state is confirmed, and wakes a sleeping car while the user is looking at the
     * watch. A failed fetch marks the telemetry stale, which keeps the controls frozen.
     */
    private suspend fun fetchAndApplyVehicleState(source: String, refreshVehicleList: Boolean = false): Boolean {
        try {
            val activeVehicle = _uiState.value.activeVehicle
            val vehicles = if (activeVehicle != null && !refreshVehicleList) {
                val updated = repository.fetchVehicleState(activeVehicle)
                _uiState.value.vehicles.map { if (it.vehicleId == updated.vehicleId) updated else it }
            } else {
                repository.fetchVehicles()
            }
            val selectedId = resolveSelectedVehicleId(vehicles)
            val polledVehicle = vehicles.firstOrNull { it.vehicleId == selectedId }

            Log.i(
                TAG,
                "$source: ${polledVehicle?.describeState() ?: "no vehicle selected (${vehicles.size} on account)"}"
            )
            if (polledVehicle != null) {
                reconcileExpectedChanges(polledVehicle)
                if (fastPollUntil?.invoke(polledVehicle) == true) stopFastPollUntil("reached")
            }
            val liveReports = polledVehicle?.let { observeReport(it) } ?: false
            val reachable = polledVehicle?.powerState?.isAwake == true || liveReports

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPolling = false,
                    vehicles = vehicles,
                    selectedVehicleId = selectedId,
                    hasFreshTelemetry = true,
                    hasLiveReports = liveReports,
                    isWakingVehicle = it.isWakingVehicle && !reachable,
                    isRateLimited = false,
                    statusBanner = "Telemetry synced",
                    errorMessage = null
                )
            }
            releaseControlsIfSettled()
            if (polledVehicle != null) {
                wakeIfAsleep(polledVehicle)
            }
            return true
        } catch (err: CancellationException) {
            _uiState.update { it.copy(isPolling = false) }
            throw err
        } catch (err: Throwable) {
            Log.w(TAG, "$source failed: $err")
            val rateLimited = err.isRateLimit()
            if (rateLimited) {
                startRateLimitBackoff()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPolling = false,
                    hasFreshTelemetry = false,
                    isRateLimited = rateLimited,
                    errorMessage = userFacingError(err)
                )
            }
            return false
        }
    }

    private fun Throwable.isRateLimit(): Boolean =
        this is LucidApiException && grpcStatus == GRPC_RESOURCE_EXHAUSTED

    /** Short error text that fits the watch's status line; the full error is in the log. */
    private fun userFacingError(err: Throwable): String = when (err) {
        is LucidApiException -> when (err.grpcStatus) {
            GRPC_RESOURCE_EXHAUSTED -> "Rate limited, retrying"
            16 -> "Sign-in expired"
            14 -> "Lucid cloud unavailable"
            4 -> "Cloud timed out"
            7 -> "Not permitted"
            5 -> "Vehicle not found"
            else -> "Cloud error ${err.grpcStatus}"
        }
        is UnknownHostException, is ConnectException -> "No internet"
        is SocketTimeoutException -> "Network timed out"
        is IOException -> "Network error"
        else -> "Error: ${err.javaClass.simpleName}"
    }

    /**
     * The saved vehicle choice if it is still on the account, or the only vehicle when there is
     * just one. Null means the user has to pick a vehicle in the selector.
     */
    private fun resolveSelectedVehicleId(vehicles: List<GravityVehicleSnapshot>): String? {
        val saved = _uiState.value.selectedVehicleId ?: repository.getSelectedVehicleId()
        return saved?.takeIf { id -> vehicles.any { it.vehicleId == id } }
            ?: vehicles.singleOrNull()?.vehicleId
    }

    /**
     * Records [vehicle]'s report time and returns whether the car is live: its last two new reports
     * were at most [LIVE_REPORT_MAX_SPACING_MS] apart and the newest arrived within
     * [LIVE_REPORT_STALE_AFTER_MS] of watch time.
     */
    private fun observeReport(vehicle: GravityVehicleSnapshot): Boolean {
        if (vehicle.vehicleId != trackedVehicleId) {
            resetReportTracking()
            trackedVehicleId = vehicle.vehicleId
        }
        val wasLive = _uiState.value.hasLiveReports
        val reportMs = vehicle.lastUpdatedMs
        val previousMs = latestReportMs
        if (previousMs == null || reportMs > previousMs) {
            if (previousMs != null) {
                newReportTimesMs.addLast(reportMs)
                while (newReportTimesMs.size > 2) newReportTimesMs.removeFirst()
                lastNewReportElapsedMs = elapsedRealtimeMs()
            }
            latestReportMs = reportMs
        }
        val live = newReportTimesMs.size == 2 &&
            newReportTimesMs.last() - newReportTimesMs.first() <= LIVE_REPORT_MAX_SPACING_MS &&
            elapsedRealtimeMs() - lastNewReportElapsedMs <= LIVE_REPORT_STALE_AFTER_MS
        if (live != wasLive && !vehicle.powerState.isAwake) {
            Log.i(
                TAG,
                if (live) {
                    "${vehicle.nickname} is ${vehicle.powerState.name}(${vehicle.powerStateCode}) but reporting " +
                        "every ${(newReportTimesMs.last() - newReportTimesMs.first()) / 1000.0}s; treating as reachable"
                } else {
                    "${vehicle.nickname} stopped sending new reports; no longer treated as reachable"
                }
            )
        }
        return live
    }

    private fun resetReportTracking() {
        trackedVehicleId = null
        latestReportMs = null
        newReportTimesMs.clear()
        lastNewReportElapsedMs = 0L
    }

    private fun reconcileExpectedChanges(polledVehicle: GravityVehicleSnapshot) {
        if (expectedLockState != null && polledVehicle.lockState == expectedLockState) {
            Log.i(TAG, "Confirmed by car: doors ${polledVehicle.lockState.displayName}")
            expectedLockState = null
        }
        if (expectedFrunkOpen != null && polledVehicle.frunkState.isOpen == expectedFrunkOpen) {
            Log.i(TAG, "Confirmed by car: frunk ${polledVehicle.frunkState.displayName}")
            expectedFrunkOpen = null
        }
        if (expectedRearTrunkOpen != null && polledVehicle.rearTrunkState.isOpen == expectedRearTrunkOpen) {
            Log.i(TAG, "Confirmed by car: trunk ${polledVehicle.rearTrunkState.displayName}")
            expectedRearTrunkOpen = null
        }
        val condition = expectedCondition
        if (condition != null && condition.isMet(polledVehicle)) {
            Log.i(TAG, "Confirmed by car: ${condition.description}")
            expectedCondition = null
        }
        if (!hasPendingExpectedChanges()) {
            confirmationTimeoutJob?.cancel()
            confirmationTimeoutJob = null
        }
    }

    private fun noteClimateChangedByUser() {
        maxAcRestore?.climateChangedByUser = true
        defrostRestore?.climateChangedByUser = true
    }

    /** Unfreezes the controls once no command is in flight or awaiting confirmation from the car. */
    private fun releaseControlsIfSettled() {
        val state = _uiState.value
        if (state.awaitingVehicleStateLabel == null) return
        if (state.isCommandPending || awaitingPostCommandPoll || hasPendingExpectedChanges()) return
        Log.i(TAG, "\"${state.awaitingVehicleStateLabel}\" settled; controls released")
        _uiState.update { it.copy(awaitingVehicleStateLabel = null) }
        if (seatDraftSent) discardSeatDraft()
    }

    private fun wakeIfAsleep(vehicle: GravityVehicleSnapshot) {
        if (vehicle.powerState.isAwake || _uiState.value.hasLiveReports) return
        if (!_uiState.value.isInteractive) {
            Log.d(TAG, "Car is ${vehicle.powerState.displayName}; not waking while watch is not interactive")
            return
        }
        if (wakeJob?.isActive == true) return
        requestWake(vehicle)
    }

    /**
     * Sends WakeupVehicle and holds [wakeJob] open for [WAKE_RETRY_INTERVAL_MS] so polls don't
     * re-send it on every fetch. Polls then run on the fast interval until the car reports awake.
     */
    private fun requestWake(vehicle: GravityVehicleSnapshot) {
        _uiState.update { it.copy(isWakingVehicle = true) }
        wakeJob = viewModelScope.launch {
            Log.i(TAG, "Car is ${vehicle.powerState.displayName}; sending WakeupVehicle")
            try {
                repository.wakeVehicle(vehicle)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                Log.w(TAG, "WakeupVehicle failed: $err")
                _uiState.update { it.copy(errorMessage = "Wake failed: ${userFacingError(err)}") }
            }
            delay(WAKE_RETRY_INTERVAL_MS)
        }
    }

    fun selectRegion(region: LucidRegion) {
        _uiState.update { it.copy(selectedRegion = region, errorMessage = null) }
    }

    /** Shows [page]; [WatchPage.HOME] returns to the dashboard. */
    fun openPage(page: WatchPage) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun toggleTemperatureUnit() {
        val next = !_uiState.value.useFahrenheit
        repository.setFahrenheitPreferred(next)
        _uiState.update { it.copy(useFahrenheit = next) }
    }

    fun toggleWatchBezelFrame() {
        _uiState.update { it.copy(watchBezelFrameEnabled = !it.watchBezelFrameEnabled) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    fun login(email: String, password: String, region: LucidRegion = _uiState.value.selectedRegion) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Enter your Lucid account email and password.")
            }
            return
        }
        viewModelScope.launch {
            Log.i(TAG, "Signing in to Lucid Cloud ($region)")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedRegion = region,
                    errorMessage = null,
                    statusBanner = null
                )
            }
            runCatching {
                repository.login(email.trim(), password, region)
            }.onSuccess { vehicles ->
                val session = repository.getActiveSession()
                val preferredId = resolveSelectedVehicleId(vehicles)
                val preferredVehicle = vehicles.firstOrNull { v -> v.vehicleId == preferredId }
                Log.i(TAG, "Signed in; ${vehicles.size} vehicle(s): ${preferredVehicle?.describeState()}")
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isLoading = false,
                        userEmail = session?.userEmail ?: email.trim(),
                        userDisplayName = listOfNotNull(session?.firstName, session?.lastName)
                            .filter { p -> p.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { email.trim() },
                        vehicles = vehicles,
                        selectedVehicleId = preferredId,
                        hasFreshTelemetry = true,
                        statusBanner = if (vehicles.isEmpty()) {
                            "Signed in, but no vehicles were found on this Lucid account."
                        } else if (preferredVehicle == null) {
                            "Choose a vehicle"
                        } else {
                            "Connected to ${preferredVehicle?.nickname ?: "Lucid Gravity"}"
                        },
                        errorMessage = null
                    )
                }
                if (preferredVehicle != null) {
                    wakeIfAsleep(preferredVehicle)
                }
                if (isPollingPowerStateActive()) {
                    startPollingLoop(pollImmediately = false)
                }
            }.onFailure { err ->
                Log.w(TAG, "Sign-in failed: $err")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = when {
                            err is LucidApiException && err.grpcStatus == 16 -> "Wrong email or password"
                            else -> userFacingError(err)
                        }
                    )
                }
            }
        }
    }

    fun refreshVehicles() {
        vehicleListJob = viewModelScope.launch {
            val showInteractiveDot = _uiState.value.isInteractive
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isPolling = if (showInteractiveDot) true else it.isPolling,
                    errorMessage = null
                )
            }
            fetchAndApplyVehicleState("refresh", refreshVehicleList = true)
        }
    }

    fun openVehicleSelector() {
        Log.i(TAG, "Vehicle selector opened")
        _uiState.update { it.copy(isSelectingVehicle = true) }
        refreshVehicles()
    }

    fun cancelVehicleSelection() {
        _uiState.update { it.copy(isSelectingVehicle = false) }
    }

    /**
     * Saves [vehicleId] as the vehicle to control. Swapping cars drops any wake-up or command
     * confirmation still pending for the previous car, then wakes the new car if it is asleep.
     */
    fun selectVehicle(vehicleId: String) {
        val vehicle = _uiState.value.vehicles.firstOrNull { it.vehicleId == vehicleId }
        Log.i(TAG, "Selected vehicle ${vehicle?.describeState() ?: vehicleId}")
        repository.setSelectedVehicleId(vehicleId)
        if (vehicleId != _uiState.value.selectedVehicleId) {
            wakeJob?.cancel()
            wakeJob = null
            clearExpectedChanges()
            discardSeatDraft()
            maxAcRestore = null
            defrostRestore = null
            stopFastPollUntil("vehicle changed")
            awaitingPostCommandPoll = false
            resetReportTracking()
            _uiState.update {
                it.copy(isWakingVehicle = false, awaitingVehicleStateLabel = null, hasLiveReports = false)
            }
        }
        _uiState.update {
            it.copy(selectedVehicleId = vehicleId, isSelectingVehicle = false, errorMessage = null)
        }
        if (vehicle != null && _uiState.value.hasFreshTelemetry) {
            wakeIfAsleep(vehicle)
        }
    }

    /**
     * Returns the active vehicle if commands may be sent to it, or null (and logs why) while the
     * controls are frozen because the car is asleep, its status is unknown, or a previous command
     * has not been confirmed yet.
     */
    private fun controllableVehicle(action: String): GravityVehicleSnapshot? {
        val state = _uiState.value
        val reason = state.controlsLockedReason
        if (reason != null) {
            Log.i(TAG, "Ignoring \"$action\": controls frozen ($reason)")
            return null
        }
        return state.activeVehicle
    }

    /**
     * Sends a command and keeps the controls frozen until the car's resulting state is known:
     * - [awaitsStateChange]: until polling sees the expected lock/frunk/trunk state (set by the
     *   caller), or [STATE_CONFIRMATION_TIMEOUT_MS] passes.
     * - [refreshAfter]: until a fetch issued after the command completes.
     * The command's optimistic snapshot is discarded; only state reported by Lucid Cloud is shown.
     */
    private fun executeVehicleCommand(
        vehicle: GravityVehicleSnapshot,
        actionLabel: String,
        awaitsStateChange: Boolean = false,
        refreshAfter: Boolean = true,
        block: suspend (GravityVehicleSnapshot) -> Unit
    ) {
        Log.i(TAG, "Command \"$actionLabel\" -> ${vehicle.describeState()}")
        _uiState.update {
            it.copy(
                isCommandPending = true,
                awaitingVehicleStateLabel = actionLabel,
                statusBanner = "$actionLabel…",
                errorMessage = null
            )
        }
        if (awaitsStateChange) {
            activateFastPolling()
        }
        viewModelScope.launch {
            runCatching {
                block(vehicle)
            }.onSuccess {
                Log.i(TAG, "Command \"$actionLabel\" accepted by Lucid Cloud")
                awaitingPostCommandPoll = refreshAfter
                _uiState.update {
                    it.copy(
                        isCommandPending = false,
                        statusBanner = if (awaitsStateChange) "Waiting for car…" else "$actionLabel complete",
                        errorMessage = null
                    )
                }
                if (awaitsStateChange) {
                    startConfirmationTimeout(actionLabel)
                }
                if (refreshAfter) {
                    fetchAndApplyVehicleState("post-command fetch")
                    awaitingPostCommandPoll = false
                }
                releaseControlsIfSettled()
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Command \"$actionLabel\" failed: $err")
                if (awaitsStateChange) {
                    clearExpectedChanges()
                }
                _uiState.update {
                    it.copy(
                        isCommandPending = false,
                        statusBanner = null,
                        errorMessage = "$actionLabel failed: ${userFacingError(err)}"
                    )
                }
                releaseControlsIfSettled()
            }
        }
    }

    /**
     * Gives up waiting for the car to confirm [actionLabel] after [STATE_CONFIRMATION_TIMEOUT_MS] of
     * polling time. Time while the app is not polling (screen off, app stopped) does not count:
     * watches dim after 7-15 s, and the car usually confirms in the meantime; the poll made when the
     * app returns then shows it, instead of a false "not confirmed".
     */
    private fun startConfirmationTimeout(actionLabel: String) {
        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = viewModelScope.launch {
            var remainingMs = STATE_CONFIRMATION_TIMEOUT_MS
            while (remainingMs > 0) {
                val stepMs = minOf(CONFIRMATION_TIMEOUT_TICK_MS, remainingMs)
                delay(stepMs)
                if (isPollingPowerStateActive()) remainingMs -= stepMs
            }
            if (!hasPendingExpectedChanges()) return@launch
            Log.w(
                TAG,
                "\"$actionLabel\" not confirmed by car within ${STATE_CONFIRMATION_TIMEOUT_MS / 1000}s; " +
                    "last state: ${_uiState.value.activeVehicle?.describeState()}"
            )
            expectedLockState = null
            expectedFrunkOpen = null
            expectedRearTrunkOpen = null
            expectedCondition = null
            confirmationTimeoutJob = null
            _uiState.update { it.copy(errorMessage = "$actionLabel not confirmed by car") }
            releaseControlsIfSettled()
        }
    }

    /** Manually requests a wake-up, even while the watch is in ambient mode. */
    fun wakeVehicle() {
        val current = _uiState.value.activeVehicle ?: return
        if (wakeJob?.isActive == true) {
            Log.i(TAG, "Ignoring wake request: wake already in progress")
            return
        }
        requestWake(current)
        activateFastPolling()
    }

    fun toggleLock() {
        val current = controllableVehicle("toggle lock") ?: return
        val nextLock = if (current.lockState == LockState.LOCKED) {
            LockState.UNLOCKED
        } else {
            LockState.LOCKED
        }
        expectedLockState = nextLock
        val label = if (nextLock == LockState.LOCKED) "Locking doors" else "Unlocking doors"
        executeVehicleCommand(current, label, awaitsStateChange = true) { vehicle ->
            repository.setDoorLocks(vehicle, nextLock)
        }
    }

    fun toggleFrunk() {
        val current = controllableVehicle("toggle frunk") ?: return
        val nextState = if (current.frunkState.isOpen) DoorState.CLOSED else DoorState.OPEN
        expectedFrunkOpen = nextState.isOpen
        val label = if (nextState == DoorState.OPEN) "Opening Frunk" else "Closing Frunk"
        executeVehicleCommand(current, label, awaitsStateChange = true) { vehicle ->
            repository.setFrunkState(vehicle, nextState)
        }
    }

    fun toggleRearTrunk() {
        val current = controllableVehicle("toggle trunk") ?: return
        val nextState = if (current.rearTrunkState.isOpen) DoorState.CLOSED else DoorState.OPEN
        expectedRearTrunkOpen = nextState.isOpen
        val label = if (nextState == DoorState.OPEN) "Opening Rear Trunk" else "Closing Rear Trunk"
        executeVehicleCommand(current, label, awaitsStateChange = true) { vehicle ->
            repository.setRearTrunkState(vehicle, nextState)
        }
    }

    /**
     * Sends a command whose effect the car must confirm: controls stay frozen until a poll shows
     * [isMet], or [STATE_CONFIRMATION_TIMEOUT_MS] of polling passes.
     */
    private fun executeConfirmedCommand(
        vehicle: GravityVehicleSnapshot,
        actionLabel: String,
        expectation: String,
        isMet: (GravityVehicleSnapshot) -> Boolean,
        block: suspend (GravityVehicleSnapshot) -> Unit
    ) {
        expectedCondition = ExpectedCondition(expectation, isMet)
        executeVehicleCommand(vehicle, actionLabel, awaitsStateChange = true, block = block)
    }

    fun toggleChargePort() {
        val current = controllableVehicle("toggle charge port") ?: return
        if (!current.hasPoweredChargePortDoor) {
            // The Gravity's port door is manual: the car would never confirm a command.
            Log.i(TAG, "Ignoring \"toggle charge port\": ${current.model.displayName} has no powered port door")
            return
        }
        if (current.chargeState.isCablePluggedIn) {
            // The car won't move the port door with a cable connected.
            Log.i(TAG, "Ignoring \"toggle charge port\": charge cable connected")
            return
        }
        val open = !current.chargePortState.isOpen
        val nextState = if (open) DoorState.OPEN else DoorState.CLOSED
        executeConfirmedCommand(
            current,
            actionLabel = if (open) "Opening Charge Port" else "Closing Charge Port",
            expectation = "charge port ${nextState.displayName}",
            isMet = { it.chargePortState.isOpen == open }
        ) { vehicle ->
            repository.setChargePortState(vehicle, nextState)
        }
    }

    /** Turns cabin climate on at the car's current target temperature, or off. */
    fun toggleClimatePrecondition() {
        val current = controllableVehicle("toggle climate") ?: return
        val turnOn = !current.hvacPower.isActive
        noteClimateChangedByUser()
        executeConfirmedCommand(
            current,
            actionLabel = if (turnOn) "Starting Climate" else "Stopping Climate",
            expectation = "climate ${if (turnOn) "on" else "off"}",
            isMet = { it.hvacPower.isActive == turnOn }
        ) { vehicle ->
            repository.setCabinTemperature(
                vehicle = vehicle,
                temperatureCelsius = if (turnOn) vehicle.targetTempCelsius else null
            )
        }
    }

    /**
     * Sets the cabin target temperature. Lucid's SetCabinTemperature always carries a power mode,
     * so this also turns cabin climate on.
     */
    fun setCabinTargetTemperature(celsius: Double) {
        val current = controllableVehicle("set cabin temperature") ?: return
        noteClimateChangedByUser()
        val target = celsius.coerceIn(MIN_CABIN_TEMP_C, MAX_CABIN_TEMP_C)
        executeConfirmedCommand(
            current,
            actionLabel = if (current.hvacPower.isActive) "Setting Cabin Temp" else "Starting Climate",
            expectation = "climate on at ${String.format(Locale.US, "%.1f", target)}°C",
            // The car may round the setpoint (e.g. to 0.5 °C), so accept anything close.
            isMet = { it.hvacPower.isActive && abs(it.targetTempCelsius - target) < 0.5 }
        ) { vehicle ->
            repository.setCabinTemperature(vehicle, target)
        }
    }

    /** Vents all windows, or closes them all ([vent] false). */
    fun setAllWindows(vent: Boolean) {
        val current = controllableVehicle(if (vent) "crack windows" else "close windows") ?: return
        executeConfirmedCommand(
            current,
            actionLabel = if (vent) "Cracking Windows" else "Closing Windows",
            expectation = "windows ${if (vent) "open" else "closed"}",
            isMet = { it.windowsOpen == vent }
        ) { vehicle ->
            repository.setAllWindows(vehicle, vent)
        }
    }

    /**
     * Max AC on, or off. Turning it off also turns climate back off when Max AC turned it on (see
     * [ClimateRestorePoint]).
     */
    fun toggleMaxAc() {
        val current = controllableVehicle("toggle max AC") ?: return
        if (!current.maxAcEnabled) {
            maxAcRestore = ClimateRestorePoint(climateWasOn = current.hvacPower.isActive)
            executeConfirmedCommand(
                current,
                actionLabel = "Enabling Max AC",
                expectation = "max AC on",
                isMet = { it.maxAcEnabled }
            ) { vehicle ->
                repository.setMaxAc(vehicle, true)
            }
            return
        }
        val restore = maxAcRestore
        maxAcRestore = null
        disableClimateMode(
            current,
            actionLabel = "Disabling Max AC",
            modeName = "max AC",
            restore = restore,
            isModeOff = { !it.maxAcEnabled }
        ) { vehicle ->
            repository.setMaxAc(vehicle, false)
        }
    }

    /** Max Defrost on, or off; like [toggleMaxAc], turning it off turns climate back off. */
    fun toggleDefrost() {
        val current = controllableVehicle("toggle defrost") ?: return
        if (!current.defrostState.isEnabled) {
            defrostRestore = ClimateRestorePoint(climateWasOn = current.hvacPower.isActive)
            executeConfirmedCommand(
                current,
                actionLabel = "Enabling Max Defrost",
                expectation = DefrostState.ON.displayName,
                isMet = { it.defrostState == DefrostState.ON }
            ) { vehicle ->
                repository.setDefrost(vehicle, DefrostState.ON)
            }
            return
        }
        val restore = defrostRestore
        defrostRestore = null
        disableClimateMode(
            current,
            actionLabel = "Disabling Defrost",
            modeName = "defrost",
            restore = restore,
            isModeOff = { it.defrostState == DefrostState.OFF }
        ) { vehicle ->
            repository.setDefrost(vehicle, DefrostState.OFF)
        }
    }

    /**
     * Turns a climate mode off with [turnOff], and climate off too when [restore] says the mode
     * turned it on; one confirmed command.
     */
    private fun disableClimateMode(
        current: GravityVehicleSnapshot,
        actionLabel: String,
        modeName: String,
        restore: ClimateRestorePoint?,
        isModeOff: (GravityVehicleSnapshot) -> Boolean,
        turnOff: suspend (GravityVehicleSnapshot) -> Unit
    ) {
        val climateOff = restore?.turnsClimateOff(current) == true
        executeConfirmedCommand(
            current,
            actionLabel = actionLabel,
            expectation = if (climateOff) "$modeName off; climate off" else "$modeName off",
            isMet = { polled -> isModeOff(polled) && (!climateOff || !polled.hvacPower.isActive) }
        ) { vehicle ->
            turnOff(vehicle)
            if (climateOff) {
                repository.setCabinTemperature(vehicle, temperatureCelsius = null)
            }
        }
    }

    /**
     * A tap on a seat in the seat diagram: steps its [kind] level Off -> High -> Med -> Low -> Off.
     * Nothing is sent until taps stop for [SEAT_TAP_SETTLE_MS], so several seats and levels can be
     * set with one command.
     */
    fun tapSeat(seat: ClimateSeat, kind: SeatClimateKind) {
        val vehicle = seatDiagramVehicle("tap ${seat.label.lowercase()} seat") ?: return
        val draft = _uiState.value.seatDraft ?: vehicle.seatClimate
        val next = draft.level(seat, kind).nextTap()
        _uiState.update { it.copy(seatDraft = draft.with(seat, kind, next)) }
        scheduleSeatDraftSend()
    }

    /**
     * A tap on the steering wheel in the seat diagram: on/off on cars that report no level (the
     * Air, where "on" is sent as LEVEL_2: Lucid Cloud rejects LEVEL_1 and LEVEL_3 for an Air, and
     * LEVEL_2 is what the BearTalk Lucid client sends), otherwise L3 -> L2 -> L1 -> Off.
     */
    fun tapSteeringWheel() {
        val vehicle = seatDiagramVehicle("tap steering wheel") ?: return
        val current = _uiState.value.steeringDraft ?: vehicle.steeringHeaterSetting
        val next = if (vehicle.hasOnOffSteeringHeater) {
            if (current.bars > 0) SteeringWheelHeaterLevel.OFF else SteeringWheelHeaterLevel.LEVEL_2
        } else {
            current.nextTap()
        }
        _uiState.update { it.copy(steeringDraft = next) }
        scheduleSeatDraftSend()
    }

    private fun seatDiagramVehicle(action: String): GravityVehicleSnapshot? {
        if (seatDraftSent) {
            Log.i(TAG, "Ignoring \"$action\": seat changes already sent")
            return null
        }
        return controllableVehicle(action)
    }

    private fun scheduleSeatDraftSend() {
        seatSendJob?.cancel()
        seatSendJob = viewModelScope.launch {
            delay(SEAT_TAP_SETTLE_MS)
            seatSendJob = null
            sendSeatDraft()
        }
    }

    private fun discardSeatDraft() {
        seatSendJob?.cancel()
        seatSendJob = null
        seatDraftSent = false
        _uiState.update { it.copy(seatDraft = null, steeringDraft = null) }
    }

    /** Sends whatever the seat diagram taps changed, as one confirmed command. */
    private fun sendSeatDraft() {
        val state = _uiState.value
        val vehicle = controllableVehicle("send seat changes")
        if (vehicle == null) {
            discardSeatDraft()
            return
        }
        val seats = state.seatDraft?.takeUnless { it.sameLevelsAs(vehicle.seatClimate) }
        val wheel = state.steeringDraft?.takeUnless { it.bars == vehicle.steeringHeaterSetting.bars }
        if (seats == null && wheel == null) {
            discardSeatDraft()
            return
        }
        seatDraftSent = true
        val label = when {
            seats != null && wheel != null -> "Setting Seats & Wheel"
            seats != null -> "Setting Seats"
            wheel!!.bars > 0 -> "Steering Heat On"
            else -> "Steering Heat Off"
        }
        executeConfirmedCommand(
            vehicle,
            actionLabel = label,
            expectation = listOfNotNull(
                seats?.describe(),
                wheel?.let { "steering heat ${it.displayName}" }
            ).joinToString("; "),
            isMet = { polled ->
                (seats == null || polled.seatClimate.sameLevelsAs(seats)) &&
                    (wheel == null || polled.steeringHeaterSetting.bars == wheel.bars)
            }
        ) { target ->
            if (seats != null) {
                repository.setSeatClimate(
                    vehicle = target,
                    driverHeat = seats.driverHeat,
                    driverVent = seats.driverVent,
                    passengerHeat = seats.passengerHeat,
                    // Rear heat and passenger cooling are only sent when they change, and only to
                    // cars that have them.
                    rearHeat = seats.rearHeat.takeIf {
                        target.showsRearSeatHeat && it.bars != target.rearSeatHeatMode.bars
                    },
                    passengerVent = seats.passengerVent.takeIf {
                        target.showsSeatCooling && it.bars != target.passengerSeatVentMode.bars
                    }
                )
            }
            if (wheel != null) {
                repository.setSteeringWheelHeater(target, wheel)
            }
        }
    }

    fun toggleBatteryPreconditioning() {
        val current = controllableVehicle("toggle battery precon") ?: return
        val nextStatus = if (current.batteryPreconStatus.isEnabled) {
            BatteryPreconStatus.OFF
        } else {
            BatteryPreconStatus.ON
        }
        val label = if (nextStatus == BatteryPreconStatus.ON) {
            "Starting Battery Precon"
        } else {
            "Stopping Battery Precon"
        }
        executeVehicleCommand(current, label) { vehicle ->
            repository.setBatteryPreconditioning(vehicle, nextStatus)
        }
    }

    fun setChargeLimit(limitPercent: Int) {
        val current = controllableVehicle("set charge limit") ?: return
        val clamped = limitPercent.coerceIn(MIN_CHARGE_LIMIT_PERCENT, MAX_CHARGE_LIMIT_PERCENT)
        executeConfirmedCommand(
            current,
            actionLabel = "Charge Limit $clamped%",
            expectation = "charge limit $clamped%",
            isMet = { it.chargeLimitPercent == clamped }
        ) { vehicle ->
            repository.setChargeLimit(vehicle, clamped)
        }
    }

    /** Sets the AC charging current limit (charge rate). */
    fun setChargeCurrentLimit(amps: Int) {
        val current = controllableVehicle("set charge rate") ?: return
        val clamped = amps.coerceIn(MIN_CHARGE_CURRENT_AMPS, maxChargeCurrentAmps(current))
        executeConfirmedCommand(
            current,
            actionLabel = "Charge Rate $clamped A",
            expectation = "charge rate $clamped A",
            isMet = { it.sessionAcCurrentLimitAmps == clamped }
        ) { vehicle ->
            repository.setAcCurrentLimit(vehicle, clamped)
        }
    }

    /** Starts charging when a cable is connected and the car is not charging; otherwise stops it. */
    fun toggleCharging() {
        val current = controllableVehicle("toggle charging") ?: return
        val startCharging = !current.chargeState.isActivelyCharging
        if (startCharging && !current.chargeState.isCablePluggedIn) {
            Log.i(TAG, "Ignoring \"start charging\": no charge cable connected")
            return
        }
        if (!startCharging) {
            stopCharging()
            return
        }
        // The car has taken the command once it reports a starting stage (Connecting ... Preparing),
        // which can last a while before Charging: release the controls then, but keep polling fast
        // until it is actually charging.
        executeConfirmedCommand(
            current,
            actionLabel = "Starting Charge",
            expectation = "charging starting",
            isMet = { it.chargeState.isActivelyCharging || it.chargeState.isStartingCharge }
        ) { vehicle ->
            repository.setChargingActive(vehicle, true)
        }
        pollFastUntil(CHARGE_START_FAST_POLL_MAX_MS) {
            it.chargeState.isActivelyCharging || !it.chargeState.isCablePluggedIn ||
                it.chargeState == ChargeState.CHARGING_END_ERROR
        }
    }

    fun stopCharging() {
        val current = controllableVehicle("stop charging") ?: return
        executeConfirmedCommand(
            current,
            actionLabel = "Stopping Charge",
            expectation = "charging stopped",
            isMet = { !it.chargeState.isActivelyCharging }
        ) { vehicle ->
            repository.setChargingActive(vehicle, false)
        }
    }

    fun flashLights() {
        val current = controllableVehicle("flash lights") ?: return
        executeVehicleCommand(current, "Flashing headlights", refreshAfter = false) { vehicle ->
            repository.flashLights(vehicle)
        }
    }

    fun honkHorn() {
        val current = controllableVehicle("honk horn") ?: return
        executeVehicleCommand(current, "Honking horn", refreshAfter = false) { vehicle ->
            repository.honkHorn(vehicle)
        }
    }

    fun logout() {
        Log.i(TAG, "Signing out")
        stopPollingLoop()
        wakeJob?.cancel()
        wakeJob = null
        fastPollWindowJob?.cancel()
        rateLimitBackoffJob?.cancel()
        clearExpectedChanges()
        discardSeatDraft()
        maxAcRestore = null
        defrostRestore = null
        stopFastPollUntil("signed out")
        awaitingPostCommandPoll = false
        resetReportTracking()
        repository.logout()
        _uiState.value = GravityWatchUiState(
            isInteractive = _uiState.value.isInteractive,
            useFahrenheit = repository.isFahrenheitPreferred()
        )
    }

    private fun GravityVehicleSnapshot.describeState(): String =
        "$nickname [$model] power=${powerState.name}($powerStateCode) lock=${lockState.name} " +
            "frunk=${frunkState.name} trunk=${rearTrunkState.name} port=${chargePortState.name} " +
            "charge=${chargeState.name} kW=${"%.2f".format(Locale.US, chargeRateKw)} battery=${batteryPercent.toInt()}% reported=${formatReportTime(lastUpdatedMs)}"

    /** Lucid's report time (server clock) as wall-clock time in the watch's time zone. */
    private fun formatReportTime(epochMs: Long): String =
        if (epochMs <= 0L) "?" else SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMs))

    class Factory(
        private val repository: LucidVehicleRepository,
        private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GravityWatchViewModel(repository, elapsedRealtimeMs) as T
        }
    }
}
