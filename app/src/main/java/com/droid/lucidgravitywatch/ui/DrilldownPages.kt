package com.droid.lucidgravitywatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.droid.lucidgravitywatch.R
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.theme.AuroraGreen
import com.droid.lucidgravitywatch.theme.CrimsonAlert
import com.droid.lucidgravitywatch.theme.EurekaGold
import com.droid.lucidgravitywatch.theme.LucidPlatinum
import com.droid.lucidgravitywatch.theme.LucidSilver
import com.droid.lucidgravitywatch.theme.ObsidianBlack
import com.droid.lucidgravitywatch.theme.StealthBorder
import com.droid.lucidgravitywatch.theme.StealthCard
import com.droid.lucidgravitywatch.theme.StealthSurface
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import com.droid.lucidgravitywatch.viewmodel.GravityWatchViewModel
import com.droid.lucidgravitywatch.viewmodel.WatchPage
import kotlin.math.abs
import kotlin.math.roundToInt

private val StatusYellow = Color(0xFFFFEA00)

/**
 * Rotary input: a Galaxy Watch bezel reports each click as one ±136 px event, which a fixed
 * pixels-per-step turned into several steps. Events at least [ROTARY_DETENT_PIXELS] are taken as
 * one click = one step; smaller ones (smooth crowns) add up to a step every [ROTARY_SMOOTH_PIXELS_PER_STEP].
 */
private const val ROTARY_DETENT_PIXELS = 20f
private const val ROTARY_SMOOTH_PIXELS_PER_STEP = 60f

/**
 * A Galaxy Watch bezel has 24 clicks per turn. While it's spun (clicks closer together than
 * [ROTARY_SPIN_CLICK_MS]) each click moves the value as far as the bezel turned, so the knob keeps
 * up with it on long ranges (charge limit, rate); separate clicks still move one step.
 */
private const val BEZEL_DEGREES_PER_CLICK = 15f
private const val ROTARY_SPIN_CLICK_MS = 200L

// ---------------------------------------------------------------------------------------------
// Climate
// ---------------------------------------------------------------------------------------------

/**
 * Climate drill-down on one screen: inside temperature, a pill combining climate power and the
 * target temperature, then round buttons for seat heat, seat cooling and max defrost.
 */
@Composable
internal fun ClimatePage(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot,
    onToggleClimate: () -> Unit,
    onSetTargetTemperature: (Double) -> Unit,
    onToggleDefrost: () -> Unit,
    onToggleMaxAc: () -> Unit,
    onSetAllWindows: (Boolean) -> Unit,
    onOpenPage: (WatchPage) -> Unit,
    onToggleTemperatureUnit: () -> Unit
) {
    var editingTarget by rememberSaveable { mutableStateOf(false) }
    var confirmVent by rememberSaveable { mutableStateOf(false) }
    val controlsEnabled = uiState.controlsLockedReason == null
    val useF = uiState.useFahrenheit
    val climateOn = vehicle.hvacPower.isActive

    if (editingTarget) {
        val scale = TemperatureScale.of(useF)
        ValueEditor(
            title = if (climateOn) "TARGET TEMP" else "START CLIMATE AT",
            initialValue = scale.fromCelsius(vehicle.targetTempCelsius),
            minValue = scale.min,
            maxValue = scale.max,
            step = scale.step,
            format = scale::format,
            confirmEnabled = controlsEnabled,
            statusText = uiState.controlsLockedReason,
            testTagPrefix = "target_temp",
            onCancel = { editingTarget = false },
            onConfirm = { value ->
                editingTarget = false
                onSetTargetTemperature(scale.toCelsius(value))
            }
        )
        return
    }

    if (!controlsEnabled && confirmVent) confirmVent = false

    val seats = vehicle.seatClimate
    Box(modifier = Modifier.fillMaxSize()) {
        // One screen, centred vertically; it scrolls only if a status message makes it too tall.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("climate_page")) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
            ) {
                PageStatusLine(uiState)

                // Hero inside temperature, outside below it; tapping either switches °F/°C.
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleTemperatureUnit)
                        .padding(horizontal = 10.dp)
                        .semantics { contentDescription = "Temperatures, tap to switch units" }
                        .testTag("climate_unit_toggle"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = vehicle.formatTemperature(vehicle.interiorTempCelsius, useF),
                        color = temperatureDisplayColor(vehicle.interiorTempCelsius, useF),
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${vehicle.formatTemperature(vehicle.exteriorTempCelsius, useF)} out",
                        color = temperatureDisplayColor(vehicle.exteriorTempCelsius, useF),
                        fontSize = 11.sp,
                        lineHeight = 12.sp
                    )
                }

                // Climate: power + target, and the two Max modes in one capsule.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClimateTargetPill(
                        target = vehicle.formatTemperature(vehicle.targetTempCelsius, useF, halfDegrees = true),
                        climateOn = climateOn,
                        enabled = controlsEnabled,
                        onTogglePower = onToggleClimate,
                        onEditTarget = { editingTarget = true },
                        modifier = Modifier.weight(1f)
                    )
                    SplitCapsule(
                        enabled = controlsEnabled,
                        halves = listOf(
                            CapsuleHalf(
                                iconRes = R.drawable.climate_defrost_24px,
                                contentDesc = "Max Defrost",
                                isActive = vehicle.defrostState.isEnabled,
                                accent = AuroraGreen,
                                onClick = onToggleDefrost,
                                testTag = "climate_defrost_toggle"
                            ),
                            CapsuleHalf(
                                iconRes = R.drawable.climate_max_ac_24px,
                                contentDesc = "Max AC",
                                isActive = vehicle.maxAcEnabled,
                                accent = SeatCoolBlue,
                                onClick = onToggleMaxAc,
                                testTag = "climate_max_ac_toggle"
                            )
                        )
                    )
                }

                // Seats: heat and cooling open the seat diagrams, so they stay usable while frozen.
                SplitCapsule(
                    enabled = true,
                    halves = listOfNotNull(
                        CapsuleHalf(
                            iconRes = R.drawable.seat_heat_24px,
                            contentDesc = "Seat Heat",
                            isActive = ClimateSeat.entries.any { seats.level(it, SeatClimateKind.HEAT).isOn } ||
                                vehicle.isSteeringHeaterOn,
                            accent = SeatClimateKind.HEAT.accent(),
                            onClick = { onOpenPage(WatchPage.SEAT_HEAT) },
                            testTag = "climate_seat_heat_button"
                        ),
                        CapsuleHalf(
                            iconRes = R.drawable.seat_cool_24px,
                            contentDesc = "Seat Cooling",
                            isActive = ClimateSeat.entries.any { seats.level(it, SeatClimateKind.COOL).isOn },
                            accent = SeatClimateKind.COOL.accent(),
                            onClick = { onOpenPage(WatchPage.SEAT_COOLING) },
                            testTag = "climate_seat_cooling_button"
                        ).takeIf { vehicle.showsSeatCooling }
                    )
                )

                // Windows stand apart: not climate, and opening them is a security risk. Like the
                // lock, closing is one tap and venting asks first.
                WindowsPill(
                    open = vehicle.windowsOpen,
                    enabled = controlsEnabled,
                    onClick = { if (vehicle.windowsOpen) onSetAllWindows(false) else confirmVent = true }
                )
            }
        }
        if (confirmVent) {
            ConfirmationOverlay(
                prompt = "Crack Windows?",
                onCancel = { confirmVent = false },
                onConfirm = {
                    confirmVent = false
                    onSetAllWindows(true)
                }
            )
        }
    }
}

/** Display units for the target temperature editor: whole °F, or half °C. */
private enum class TemperatureScale(val min: Double, val max: Double, val step: Double) {
    FAHRENHEIT(60.0, 85.0, 1.0),
    CELSIUS(GravityWatchViewModel.MIN_CABIN_TEMP_C, GravityWatchViewModel.MAX_CABIN_TEMP_C, 0.5);

    fun fromCelsius(celsius: Double): Double = when (this) {
        FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt().toDouble()
        CELSIUS -> (celsius * 2.0).roundToInt() / 2.0
    }.coerceIn(min, max)

    fun toCelsius(value: Double): Double = when (this) {
        FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
        CELSIUS -> value
    }

    fun format(value: Double): String = when (this) {
        FAHRENHEIT -> "${value.roundToInt()}°F"
        CELSIUS -> if (value % 1.0 == 0.0) "${value.toInt()}°C" else "$value°C"
    }

    companion object {
        fun of(useFahrenheit: Boolean) = if (useFahrenheit) FAHRENHEIT else CELSIUS
    }
}

// ---------------------------------------------------------------------------------------------
// Charging
// ---------------------------------------------------------------------------------------------

/**
 * Charging drill-down on one screen: battery as the hero (with a ring around the screen edge and
 * the charge limit marked on it), the charge settings pill, and one action that depends on the
 * cable: the charge port when unplugged, Start/Stop Charging when plugged in.
 */
@Composable
internal fun ChargingPage(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot,
    onSetChargeLimit: (Int) -> Unit,
    onSetChargeCurrentLimit: (Int) -> Unit,
    onToggleCharging: () -> Unit,
    onToggleChargePort: () -> Unit
) {
    var editingLimit by rememberSaveable { mutableStateOf(false) }
    var editingRate by rememberSaveable { mutableStateOf(false) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val controlsEnabled = uiState.controlsLockedReason == null
    if (!controlsEnabled && confirmStop) confirmStop = false

    if (editingLimit) {
        ValueEditor(
            title = "CHARGE LIMIT",
            initialValue = vehicle.chargeLimitPercent
                .coerceIn(
                    GravityWatchViewModel.MIN_CHARGE_LIMIT_PERCENT,
                    GravityWatchViewModel.MAX_CHARGE_LIMIT_PERCENT
                ).toDouble(),
            minValue = GravityWatchViewModel.MIN_CHARGE_LIMIT_PERCENT.toDouble(),
            maxValue = GravityWatchViewModel.MAX_CHARGE_LIMIT_PERCENT.toDouble(),
            arcMinValue = 0.0,
            step = 1.0,
            format = { "${it.roundToInt()}%" },
            confirmEnabled = controlsEnabled,
            statusText = uiState.controlsLockedReason,
            testTagPrefix = "charge_limit",
            onCancel = { editingLimit = false },
            onConfirm = { value ->
                editingLimit = false
                onSetChargeLimit(value.roundToInt())
            }
        )
        return
    }

    // The charge rate as set (the session limit); the other field is the most available.
    val currentLimitAmps = vehicle.sessionAcCurrentLimitAmps
    val maxRateAmps = GravityWatchViewModel.maxChargeCurrentAmps(vehicle)
    if (editingRate) {
        ValueEditor(
            title = "CHARGE RATE",
            initialValue = currentLimitAmps
                .takeIf { it > 0 }
                ?.coerceIn(GravityWatchViewModel.MIN_CHARGE_CURRENT_AMPS, maxRateAmps)
                ?.toDouble()
                ?: maxRateAmps.toDouble(),
            minValue = GravityWatchViewModel.MIN_CHARGE_CURRENT_AMPS.toDouble(),
            maxValue = maxRateAmps.toDouble(),
            step = 5.0,
            fineStep = 1.0,
            format = { "${it.roundToInt()} A" },
            confirmEnabled = controlsEnabled,
            statusText = uiState.controlsLockedReason,
            testTagPrefix = "charge_rate",
            onCancel = { editingRate = false },
            onConfirm = { value ->
                editingRate = false
                onSetChargeCurrentLimit(value.roundToInt())
            }
        )
        return
    }

    val chargeState = vehicle.chargeState
    val isCharging = chargeState.isActivelyCharging
    val pluggedIn = chargeState.isCablePluggedIn

    val batteryPercent = vehicle.batteryPercent.roundToInt().coerceIn(0, 100)

    Box(modifier = Modifier.fillMaxSize()) {
        BatteryRing(
            percent = batteryPercent,
            limitPercent = vehicle.chargeLimitPercent
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("charging_page")) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                PageStatusLine(uiState)
                Text(
                    text = "$batteryPercent%",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("charging_battery")
                )
                Text(
                    text = chargingSummary(vehicle),
                    color = if (isCharging) AuroraGreen else LucidSilver,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("charging_summary")
                )
                Spacer(modifier = Modifier.height(2.dp))
                ChargeSettingsPill(
                    limitPercent = vehicle.chargeLimitPercent,
                    currentLimitAmps = currentLimitAmps,
                    charging = isCharging,
                    enabled = controlsEnabled,
                    onEditLimit = { editingLimit = true },
                    onEditRate = { editingRate = true }
                )
                // The port can't close on a connected cable, and its state says nothing new while
                // plugged in, so the cable decides which action is offered here.
                if (pluggedIn) {
                    ChargingSessionPill(
                        charging = isCharging,
                        starting = chargeState.isStartingCharge,
                        enabled = controlsEnabled,
                        onClick = { if (isCharging) confirmStop = true else onToggleCharging() }
                    )
                } else {
                    // Gravity's port door is manual: show its state without offering a command.
                    ChargePortPill(
                        open = vehicle.chargePortState.isOpen,
                        enabled = controlsEnabled,
                        onClick = onToggleChargePort.takeIf { vehicle.hasPoweredChargePortDoor }
                    )
                }
            }
        }
        if (confirmStop) {
            ConfirmationOverlay(
                prompt = "Stop Charging?",
                onCancel = { confirmStop = false },
                onConfirm = {
                    confirmStop = false
                    onToggleCharging()
                }
            )
        }
    }
}

/**
 * Battery level as a 270° ring around the edge of the round screen (matching [ValueArc]), green
 * up to [percent] and [StealthBorder] after that. A gold tick marks [limitPercent].
 */
@Composable
private fun BatteryRing(percent: Int, limitPercent: Int) {
    Canvas(modifier = Modifier.fillMaxSize().testTag("charging_battery_ring")) {
        val stroke = 6.dp.toPx()
        val inset = VALUE_ARC_INSET.toPx()
        val diameter = size.minDimension - inset * 2
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = StealthBorder,
            startAngle = ARC_START_DEGREES,
            sweepAngle = ARC_SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val chargeFraction = (percent.coerceIn(0, 100) / 100f)
        drawArc(
            color = AuroraGreen,
            startAngle = ARC_START_DEGREES,
            sweepAngle = ARC_SWEEP_DEGREES * chargeFraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Charge limit tick, across the ring at the corresponding percentage
        val limitFraction = (limitPercent.coerceIn(0, 100) / 100.0)
        val angle = Math.toRadians(ARC_START_DEGREES + ARC_SWEEP_DEGREES * limitFraction)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        val half = stroke * 1.4f
        drawLine(
            color = EurekaGold,
            start = Offset(center.x + dx * (radius - half), center.y + dy * (radius - half)),
            end = Offset(center.x + dx * (radius + half), center.y + dy * (radius + half)),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Charge settings in one pill: the charge limit and the charge rate (AC current limit), each
 * opening its editor; outlined green while charging.
 */
@Composable
private fun ChargeSettingsPill(
    limitPercent: Int,
    currentLimitAmps: Int,
    charging: Boolean,
    enabled: Boolean,
    onEditLimit: () -> Unit,
    onEditRate: () -> Unit
) = key(enabled, charging) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .width(176.dp)
            .height(46.dp)
            .clip(shape)
            .background(StealthCard.dimmedUnless(enabled))
            .border(
                if (charging) 1.5.dp else 1.dp,
                (if (charging) AuroraGreen else StealthBorder).dimmedUnless(enabled),
                shape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillValue(
            label = "LIMIT",
            value = "$limitPercent%",
            enabled = enabled,
            onClick = onEditLimit,
            testTag = "charging_limit_row",
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(StealthBorder.dimmedUnless(enabled))
        )
        PillValue(
            label = "RATE",
            value = if (currentLimitAmps > 0) "$currentLimitAmps A" else "--",
            enabled = enabled,
            onClick = onEditRate,
            testTag = "charging_rate_row",
            modifier = Modifier.weight(1f)
        )
    }
}

/** Small label over a value, tappable; one section of a settings pill. */
@Composable
private fun PillValue(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = LucidSilver.dimmedUnless(enabled),
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = Color.White.dimmedUnless(enabled),
            fontSize = 17.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Start or stop charging, spelled out; only shown with a cable connected. Stopping asks first.
 * While the car works towards charging ([starting]: Connecting ... Preparing) it reads "Starting…"
 * and can't be tapped, so a start isn't sent twice.
 */
@Composable
private fun ChargingSessionPill(charging: Boolean, starting: Boolean, enabled: Boolean, onClick: () -> Unit) =
    key(enabled, charging, starting) {
        val shape = RoundedCornerShape(18.dp)
        val accent = if (charging) CrimsonAlert else AuroraGreen
        val label = when {
            charging -> "Stop Charging"
            starting -> "Starting…"
            else -> "Start Charging"
        }
        val tappable = enabled && !starting
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(shape)
                .border(1.dp, accent.dimmedUnless(enabled), shape)
                .clickable(enabled = tappable, onClick = onClick)
                .padding(horizontal = 14.dp)
                .semantics { contentDescription = label }
                .testTag("charging_start_stop"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (charging) {
                // Stop: a small filled square
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.dimmedUnless(enabled))
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.charging_bolt_24px),
                    contentDescription = null,
                    tint = accent.dimmedUnless(enabled),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                color = accent.dimmedUnless(enabled),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }

/**
 * Charge port (only offered unplugged), styled apart like the windows pill: amber "Port Open" while
 * open. With no [onClick] (a car with a manual port door) it is a read-only status: "Port Closed"
 * or "Port Open", never dimmed by locked controls, since there is nothing to press.
 */
@Composable
private fun ChargePortPill(open: Boolean, enabled: Boolean, onClick: (() -> Unit)?) = key(enabled, open) {
    val readOnly = onClick == null
    val shown = enabled || readOnly
    val shape = RoundedCornerShape(18.dp)
    val color = (if (open) StatusYellow else LucidPlatinum).dimmedUnless(shown)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, (if (open) StatusYellow else StealthBorder).dimmedUnless(shown), shape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = if (open) "Charge port open" else "Charge port closed" }
            .testTag(if (readOnly) "charging_port_status" else "charging_port_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.charging_port_24px),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = when {
                open -> "Port Open"
                readOnly -> "Port Closed"
                else -> "Port"
            },
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun chargingSummary(vehicle: GravityVehicleSnapshot): String {
    val state = vehicle.chargeState
    if (state == ChargeState.UNKNOWN || state == ChargeState.NOT_CONNECTED) return "Unplugged"
    val parts = mutableListOf(state.displayName)
    if (abs(vehicle.chargeRateKw) > 0.1) parts += vehicle.formatChargingPowerKw()
    if (state.isActivelyCharging && vehicle.sessionMinutesRemaining > 0) {
        val h = vehicle.sessionMinutesRemaining / 60
        val m = vehicle.sessionMinutesRemaining % 60
        parts += if (h > 0) "${h}h ${m}m left" else "${m}m left"
    }
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------------------------------------
// Flonk (flash + honk)
// ---------------------------------------------------------------------------------------------

/** Flash the lights or honk the horn; both fire immediately, as they did on the home screen. */
@Composable
internal fun FlonkPage(
    uiState: GravityWatchUiState,
    onHonkHorn: () -> Unit,
    onFlashLights: () -> Unit
) {
    val controlsEnabled = uiState.controlsLockedReason == null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("flonk_page"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PageStatusLine(uiState)
        if (uiState.errorMessage != null || uiState.controlsLockedReason != null) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlIconButton(
                    drawableRes = R.drawable.btn_lights,
                    contentDesc = "Flash Lights Button",
                    isActive = false,
                    isPrimaryCenter = false,
                    activeColor = EurekaGold,
                    size = 66.24.dp,
                    onClick = onFlashLights,
                    enabled = controlsEnabled,
                    testTag = "flonk_lights_button"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Flash", color = LucidSilver, fontSize = 13.2.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlIconButton(
                    drawableRes = R.drawable.btn_horn,
                    contentDesc = "Honk Horn Button",
                    isActive = false,
                    isPrimaryCenter = false,
                    activeColor = EurekaGold,
                    size = 66.24.dp,
                    onClick = onHonkHorn,
                    enabled = controlsEnabled,
                    testTag = "flonk_horn_button"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Honk", color = LucidSilver, fontSize = 13.2.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------------------------

/**
 * Shows [content] with Wear OS swipe-to-dismiss: swiping right reveals [background] (the screen
 * underneath) and calls [onBack]. The hardware/system back action does the same. Only wrap
 * screens that have somewhere to go back to: on the home screen, the system's own swipe should
 * still close the app.
 */
@Composable
internal fun SwipeBackBox(
    pageKey: Any,
    onBack: () -> Unit,
    background: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    key(pageKey) {
        val swipeState = rememberSwipeToDismissBoxState()
        BasicSwipeToDismissBox(
            onDismissed = onBack,
            state = swipeState,
            backgroundKey = "background",
            contentKey = pageKey
        ) { isBackground ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
            ) {
                if (isBackground) background() else content()
            }
        }
    }
}

/** A crown-scrollable, round-screen list with the page title and status line at the top. */
@Composable
private fun DrilldownList(
    title: String?,
    uiState: GravityWatchUiState,
    testTag: String,
    topPadding: Dp = 20.dp,
    content: ScalingLazyListScope.() -> Unit
) {
    // Start with the title at the top edge instead of centered, so the first screen shows as
    // much as possible (no auto-centering padding above the first item).
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topPadding, bottom = 20.dp),
        autoCentering = null,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (title != null) PageTitle(title)
                PageStatusLine(uiState)
            }
        }
        content()
    }
}

@Composable
internal fun PageTitle(title: String) {
    Text(
        text = title,
        color = EurekaGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

/** Why controls are frozen, or the last error; nothing when the car is ready. */
@Composable
internal fun PageStatusLine(uiState: GravityWatchUiState) {
    val text = uiState.errorMessage ?: uiState.controlsLockedReason ?: return
    Text(
        text = text,
        color = if (uiState.errorMessage != null && !uiState.isRateLimited) CrimsonAlert else StatusYellow,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("page_status_text")
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LucidSilver,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Full-width row showing a label and its current value; tapping it changes the value. */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    highlighted: Boolean = false
) = key(enabled, highlighted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StealthCard.dimmedUnless(enabled))
            .border(1.dp, (if (highlighted) EurekaGold else StealthBorder).dimmedUnless(enabled), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = LucidPlatinum.dimmedUnless(enabled), fontSize = 12.sp, maxLines = 1)
        Text(
            text = value,
            color = (if (highlighted) EurekaGold else Color.White).dimmedUnless(enabled),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Disabled controls are dimmed through their colours rather than Modifier.alpha: toggling an alpha
 * layer inside the ScalingLazyColumn left rows drawn from a stale frame on a Galaxy Watch (cards
 * vanished, or a highlight border appeared around the row above).
 */
internal fun Color.dimmedUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * 0.4f)

/** One tappable half of a [SplitCapsule]. */
private class CapsuleHalf(
    val iconRes: Int,
    val contentDesc: String,
    val isActive: Boolean,
    val accent: Color,
    val onClick: () -> Unit,
    val testTag: String
)

/**
 * Related icon buttons joined in one capsule with thin dividers; an active half fills with its
 * accent. Groups controls that belong together (the Max modes, the seat modes).
 */
@Composable
private fun SplitCapsule(halves: List<CapsuleHalf>, enabled: Boolean) =
    key(enabled, halves.map { it.isActive }) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(shape)
                .background(StealthCard.dimmedUnless(enabled))
                .border(1.dp, StealthBorder.dimmedUnless(enabled), shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            halves.forEachIndexed { index, half ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .background(StealthBorder.dimmedUnless(enabled))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .background(
                            if (half.isActive) half.accent.copy(alpha = 0.22f).dimmedUnless(enabled) else Color.Transparent
                        )
                        .clickable(enabled = enabled, onClick = half.onClick)
                        .semantics {
                            contentDescription = "${half.contentDesc} ${if (half.isActive) "on" else "off"}"
                        }
                        .testTag(half.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = half.iconRes),
                        contentDescription = null,
                        tint = (if (half.isActive) half.accent else LucidPlatinum).dimmedUnless(enabled),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

/**
 * All-windows control, styled apart from the climate buttons: a flat outlined pill with a label,
 * amber with "Open" while any window is open.
 */
@Composable
private fun WindowsPill(open: Boolean, enabled: Boolean, onClick: () -> Unit) = key(enabled, open) {
    val shape = RoundedCornerShape(18.dp)
    val color = (if (open) StatusYellow else LucidPlatinum).dimmedUnless(enabled)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, (if (open) StatusYellow else StealthBorder).dimmedUnless(enabled), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = if (open) "Windows open" else "Windows closed" }
            .testTag("climate_windows_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.climate_windows_24px),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = if (open) "Open" else "Windows",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Climate power and target temperature in one pill: the round button on the left turns climate
 * on/off; the rest shows the target and opens the temperature editor. Outlined green while on.
 */
@Composable
private fun ClimateTargetPill(
    target: String,
    climateOn: Boolean,
    enabled: Boolean,
    onTogglePower: () -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier
) = key(enabled, climateOn) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background((if (climateOn) AuroraGreen.copy(alpha = 0.12f) else StealthCard).dimmedUnless(enabled))
            .border(
                if (climateOn) 1.5.dp else 1.dp,
                (if (climateOn) AuroraGreen else StealthBorder).dimmedUnless(enabled),
                shape
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background((if (climateOn) AuroraGreen.copy(alpha = 0.25f) else StealthSurface).dimmedUnless(enabled))
                .clickable(enabled = enabled, onClick = onTogglePower)
                .semantics { contentDescription = "Climate ${if (climateOn) "on" else "off"}" }
                .testTag("climate_power_toggle"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.climate_power_24px),
                contentDescription = null,
                tint = (if (climateOn) AuroraGreen else LucidPlatinum).dimmedUnless(enabled),
                modifier = Modifier.size(22.dp)
            )
        }
        // Small TGT label stacked over the value, so the pill can stay narrow.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .clickable(enabled = enabled, onClick = onEditTarget)
                .testTag("climate_target_row"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TGT",
                color = LucidSilver.dimmedUnless(enabled),
                fontSize = 8.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Text(
                text = target,
                color = Color.White.dimmedUnless(enabled),
                fontSize = 17.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/** Full-width on/off row: filled when on. */
@Composable
private fun ToggleRow(
    label: String,
    isOn: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    detail: String? = null,
    onColor: Color = AuroraGreen,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 14.dp
) = key(enabled, isOn) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background((if (isOn) onColor.copy(alpha = 0.22f) else StealthCard).dimmedUnless(enabled))
            .border(
                if (isOn) 1.5.dp else 1.dp,
                (if (isOn) onColor else StealthBorder).dimmedUnless(enabled),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 9.dp)
            .semantics { contentDescription = "$label ${if (isOn) "on" else "off"}" }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = (if (isOn) Color.White else LucidPlatinum).dimmedUnless(enabled),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (detail != null) {
            Text(text = detail, color = LucidSilver.dimmedUnless(enabled), fontSize = 9.sp, maxLines = 1)
        }
    }
}

/**
 * Full-screen value picker: dragging around the arc at the screen edge or the crown/bezel change
 * the value in [fineStep]s, the -/+ buttons in [step]s; ✓ sends it and ✕ (or back / swipe)
 * discards it. Nothing is sent
 * to the car until ✓, so moving the value doesn't fire a command per step.
 */
@Composable
private fun ValueEditor(
    title: String,
    initialValue: Double,
    minValue: Double,
    maxValue: Double,
    step: Double,
    format: (Double) -> String,
    confirmEnabled: Boolean,
    /** Step for the arc and the crown/bezel; [step] is for the -/+ buttons. */
    fineStep: Double = step,
    /** Value corresponding to 0% of the visual 270° arc (defaults to [minValue]). */
    arcMinValue: Double = minValue,
    statusText: String?,
    testTagPrefix: String,
    onCancel: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue.coerceIn(minValue, maxValue)) }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    var lastClickMillis by remember { mutableStateOf(0L) }
    // Steps for one bezel click's worth of arc (at least 1): temperature 1, limit 3, rate 4
    val spinStepsPerClick = remember(minValue, maxValue, fineStep) {
        ((maxValue - minValue) / fineStep * BEZEL_DEGREES_PER_CLICK / ARC_SWEEP_DEGREES)
            .roundToInt().coerceAtLeast(1)
    }
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    fun setValue(next: Double) {
        val clamped = next.coerceIn(minValue, maxValue)
        if (clamped != value) {
            value = clamped
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** One crown/bezel click. */
    fun nudge(steps: Int) = setValue(value + steps * fineStep)

    /** One -/+ press: to the next multiple of [step] in that direction (23 -> 25 or 20 for 5). */
    fun press(up: Boolean) {
        val k = (value - minValue) / step
        val target = if (up) {
            kotlin.math.floor(k + 1e-9) + 1
        } else {
            kotlin.math.ceil(k - 1e-9) - 1
        }
        setValue(minValue + target * step)
    }

    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val pixels = event.verticalScrollPixels
                if (abs(pixels) >= ROTARY_DETENT_PIXELS) {
                    rotaryAccumulator = 0f
                    val spinning = event.uptimeMillis - lastClickMillis < ROTARY_SPIN_CLICK_MS
                    lastClickMillis = event.uptimeMillis
                    val steps = if (spinning) spinStepsPerClick else 1
                    nudge(if (pixels > 0) steps else -steps)
                } else {
                    rotaryAccumulator += pixels
                    val steps = (rotaryAccumulator / ROTARY_SMOOTH_PIXELS_PER_STEP).toInt()
                    if (steps != 0) {
                        rotaryAccumulator -= steps * ROTARY_SMOOTH_PIXELS_PER_STEP
                        nudge(steps)
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .testTag("${testTagPrefix}_editor")
    ) {
        // Behind the buttons, so taps on them still reach them.
        ValueArc(
            fraction = ((value - arcMinValue) / (maxValue - arcMinValue)).toFloat(),
            onFraction = { fraction ->
                val steps = ((fraction * (maxValue - arcMinValue)) / fineStep).roundToInt()
                setValue(arcMinValue + steps * fineStep)
            },
            testTag = "${testTagPrefix}_arc"
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        PageTitle(title)
        if (statusText != null) {
            Text(text = statusText, color = StatusYellow, fontSize = 10.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = Icons.Default.Remove,
                contentDesc = "Decrease",
                enabled = value > minValue,
                onClick = { press(up = false) },
                testTag = "${testTagPrefix}_decrease"
            )
            Text(
                text = format(value),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(104.dp)
                    .testTag("${testTagPrefix}_value")
            )
            RoundIconButton(
                icon = Icons.Default.Add,
                contentDesc = "Increase",
                enabled = value < maxValue,
                onClick = { press(up = true) },
                testTag = "${testTagPrefix}_increase"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundIconButton(
                icon = Icons.Default.Close,
                contentDesc = "Cancel",
                enabled = true,
                onClick = onCancel,
                testTag = "${testTagPrefix}_cancel"
            )
            RoundIconButton(
                icon = Icons.Default.Check,
                contentDesc = "Confirm",
                enabled = confirmEnabled,
                filled = true,
                onClick = { onConfirm(value) },
                testTag = "${testTagPrefix}_confirm"
            )
        }
        }
    }
}

/** The value arc covers 270°, from bottom-left clockwise over the top to bottom-right. */
private const val ARC_START_DEGREES = 135f
private const val ARC_SWEEP_DEGREES = 270f

/** Distance from the screen edge to the middle of the arc's track. */
internal val VALUE_ARC_INSET = 9.dp

/**
 * A slider around the edge of the round screen: [fraction] of the arc is filled, with a knob at
 * the end. Touching or dragging near the arc reports the fraction under the finger; touches
 * further in are left for the controls above it.
 */
@Composable
private fun ValueArc(fraction: Float, onFraction: (Float) -> Unit, testTag: String) {
    val current by rememberUpdatedState(onFraction)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag)
            .pointerInput(Unit) {
                val inset = VALUE_ARC_INSET.toPx()
                val reach = 30.dp.toPx()
                fun fractionAt(position: Offset): Float? {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f - inset
                    val d = position - center
                    if (d.getDistance() < radius - reach) return null
                    var angle = Math.toDegrees(kotlin.math.atan2(d.y, d.x).toDouble()).toFloat()
                    if (angle < 0f) angle += 360f
                    var relative = (angle - ARC_START_DEGREES + 360f) % 360f
                    // In the gap at the bottom: snap to the nearer end
                    if (relative > ARC_SWEEP_DEGREES) {
                        relative = if (relative > ARC_SWEEP_DEGREES + (360f - ARC_SWEEP_DEGREES) / 2f) 0f else ARC_SWEEP_DEGREES
                    }
                    return relative / ARC_SWEEP_DEGREES
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val start = fractionAt(down.position) ?: return@awaitEachGesture
                    down.consume()
                    current(start)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // Once dragging, follow the finger even if it drifts inwards
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val d = change.position - center
                        if (d.getDistance() > 1f) {
                            val radius = minOf(size.width, size.height) / 2f - inset
                            val onRing = center + d * (radius / d.getDistance())
                            fractionAt(onRing)?.let(current)
                        }
                        change.consume()
                    }
                }
            }
    ) {
        val stroke = 6.dp.toPx()
        val inset = VALUE_ARC_INSET.toPx()
        val diameter = size.minDimension - inset * 2
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = StealthBorder,
            startAngle = ARC_START_DEGREES,
            sweepAngle = ARC_SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val f = fraction.coerceIn(0f, 1f)
        drawArc(
            color = EurekaGold,
            startAngle = ARC_START_DEGREES,
            sweepAngle = ARC_SWEEP_DEGREES * f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val angle = Math.toRadians((ARC_START_DEGREES + ARC_SWEEP_DEGREES * f).toDouble())
        val radius = diameter / 2f
        val knob = Offset(
            size.width / 2f + radius * kotlin.math.cos(angle).toFloat(),
            size.height / 2f + radius * kotlin.math.sin(angle).toFloat()
        )
        drawCircle(color = ObsidianBlack, radius = 10.dp.toPx(), center = knob)
        drawCircle(color = EurekaGold, radius = 8.dp.toPx(), center = knob)
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    filled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) EurekaGold else StealthSurface)
            .border(1.dp, if (filled) EurekaGold else StealthBorder, CircleShape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) ObsidianBlack else LucidPlatinum,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Shared ✕ / ✓ confirmation pop-up used before consequential commands. */
@Composable
internal fun ConfirmationOverlay(
    prompt: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.88f))
            .clickable(enabled = true, onClick = {})
            .testTag("confirmation_popup_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(StealthCard)
                .border(1.5.dp, EurekaGold, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = prompt,
                color = LucidPlatinum,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("confirmation_prompt_text")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StealthSurface)
                        .border(1.dp, StealthBorder, CircleShape)
                        .clickable(onClick = onCancel)
                        .testTag("confirmation_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = LucidPlatinum,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EurekaGold)
                        .border(1.dp, EurekaGold, CircleShape)
                        .clickable(onClick = onConfirm)
                        .testTag("confirmation_confirm_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
