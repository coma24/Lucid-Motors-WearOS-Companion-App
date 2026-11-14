package com.droid.lucidgravitywatch.ui

import android.app.Activity
import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
import com.droid.lucidgravitywatch.R
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.theme.AuroraGreen
import com.droid.lucidgravitywatch.theme.CrimsonAlert
import com.droid.lucidgravitywatch.theme.CryoBlue
import com.droid.lucidgravitywatch.theme.EurekaGold
import com.droid.lucidgravitywatch.theme.LucidPlatinum
import com.droid.lucidgravitywatch.theme.LucidSilver
import com.droid.lucidgravitywatch.theme.ObsidianBlack
import com.droid.lucidgravitywatch.theme.StealthBorder
import com.droid.lucidgravitywatch.theme.StealthCard
import com.droid.lucidgravitywatch.theme.StealthSurface
import com.droid.lucidgravitywatch.theme.ThermalAmber
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import com.droid.lucidgravitywatch.viewmodel.GravityWatchViewModel
import com.droid.lucidgravitywatch.viewmodel.WatchPage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GravityWatchApp(viewModel: GravityWatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    GravityWatchScreen(
        uiState = uiState,
        onSelectRegion = viewModel::selectRegion,
        onLogin = viewModel::login,
        onOpenPage = viewModel::openPage,
        onToggleTemperatureUnit = viewModel::toggleTemperatureUnit,
        onToggleWatchBezelFrame = viewModel::toggleWatchBezelFrame,
        onRefresh = viewModel::refreshVehicles,
        onWakeVehicle = viewModel::wakeVehicle,
        onToggleLock = viewModel::toggleLock,
        onToggleFrunk = viewModel::toggleFrunk,
        onToggleRearTrunk = viewModel::toggleRearTrunk,
        onToggleChargePort = viewModel::toggleChargePort,
        onToggleClimatePrecondition = viewModel::toggleClimatePrecondition,
        onSetTargetTemperature = viewModel::setCabinTargetTemperature,
        onToggleDefrost = viewModel::toggleDefrost,
        onToggleMaxAc = viewModel::toggleMaxAc,
        onSetAllWindows = viewModel::setAllWindows,
        onTapSeat = viewModel::tapSeat,
        onTapSteeringWheel = viewModel::tapSteeringWheel,
        onToggleBatteryPrecon = viewModel::toggleBatteryPreconditioning,
        onSetChargeLimit = viewModel::setChargeLimit,
        onSetChargeCurrentLimit = viewModel::setChargeCurrentLimit,
        onToggleCharging = viewModel::toggleCharging,
        onStopCharging = viewModel::stopCharging,
        onFlashLights = viewModel::flashLights,
        onHonkHorn = viewModel::honkHorn,
        onSelectVehicle = viewModel::selectVehicle,
        onOpenVehicleSelector = viewModel::openVehicleSelector,
        onCancelVehicleSelection = viewModel::cancelVehicleSelection,
        onLogout = viewModel::logout
    )
}

@Composable
fun GravityWatchScreen(
    uiState: GravityWatchUiState,
    onSelectRegion: (LucidRegion) -> Unit,
    onLogin: (String, String, LucidRegion) -> Unit,
    onOpenPage: (WatchPage) -> Unit = {},
    onToggleTemperatureUnit: () -> Unit = {},
    onToggleWatchBezelFrame: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onWakeVehicle: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onToggleFrunk: () -> Unit = {},
    onToggleRearTrunk: () -> Unit = {},
    onToggleChargePort: () -> Unit = {},
    onToggleClimatePrecondition: () -> Unit = {},
    onSetTargetTemperature: (Double) -> Unit = {},
    onToggleDefrost: () -> Unit = {},
    onToggleMaxAc: () -> Unit = {},
    onSetAllWindows: (Boolean) -> Unit = {},
    onTapSeat: (ClimateSeat, SeatClimateKind) -> Unit = { _, _ -> },
    onTapSteeringWheel: () -> Unit = {},
    onToggleBatteryPrecon: () -> Unit = {},
    onSetChargeLimit: (Int) -> Unit = {},
    onSetChargeCurrentLimit: (Int) -> Unit = {},
    onToggleCharging: () -> Unit = {},
    onStopCharging: () -> Unit = {},
    onFlashLights: () -> Unit = {},
    onHonkHorn: () -> Unit = {},
    onSelectVehicle: (String) -> Unit = {},
    onOpenVehicleSelector: () -> Unit = {},
    onCancelVehicleSelection: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        val isLargeDisplay = maxWidth > 320.dp && maxHeight > 320.dp
        var selectedWatchSizeDp by rememberSaveable { mutableStateOf(227) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isLargeDisplay) 16.dp else 0.dp,
                    vertical = if (isLargeDisplay) 6.dp else 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLargeDisplay) {
                HostCompanionTopBar(
                    uiState = uiState,
                    selectedWatchSizeDp = selectedWatchSizeDp,
                    onSelectWatchSizeDp = { selectedWatchSizeDp = it },
                    onToggleWatchBezelFrame = onToggleWatchBezelFrame,
                    onLogout = onLogout
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val watchModifier = if (isLargeDisplay && uiState.watchBezelFrameEnabled) {
                Modifier
                    .size(selectedWatchSizeDp.dp)
                    .clip(CircleShape)
                    .background(StealthSurface)
                    .border(3.dp, StealthBorder, CircleShape)
            } else {
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxSize()
                    .background(ObsidianBlack)
            }

            Box(
                modifier = watchModifier,
                contentAlignment = Alignment.Center
            ) {
                val activeVehicle = uiState.activeVehicle

                if (!uiState.isAuthenticated) {
                    LucidWatchLoginView(
                        uiState = uiState,
                        onSelectRegion = onSelectRegion,
                        onLogin = onLogin
                    )
                } else if (uiState.needsVehicleSelection) {
                    VehicleSelectorView(
                        uiState = uiState,
                        onSelectVehicle = onSelectVehicle,
                        onCancel = onCancelVehicleSelection
                    )
                } else {
                    val dashboard: @Composable () -> Unit = {
                        SimplifiedWatchDashboard(
                            uiState = uiState,
                            vehicle = activeVehicle,
                            onRefresh = onRefresh,
                            onOpenVehicleSelector = onOpenVehicleSelector,
                            onToggleFrunk = onToggleFrunk,
                            onToggleLock = onToggleLock,
                            onToggleRearTrunk = onToggleRearTrunk,
                            onStopCharging = onStopCharging,
                            onOpenPage = onOpenPage,
                            onLogout = onLogout
                        )
                    }
                    // Drill-down pages need a vehicle; without one the dashboard shows its
                    // "Contacting Cloud..." state instead.
                    val page = if (activeVehicle == null) WatchPage.HOME else uiState.currentPage
                    val climatePage: @Composable () -> Unit = {
                        ClimatePage(
                            uiState = uiState,
                            vehicle = activeVehicle!!,
                            onToggleClimate = onToggleClimatePrecondition,
                            onSetTargetTemperature = onSetTargetTemperature,
                            onToggleDefrost = onToggleDefrost,
                            onToggleMaxAc = onToggleMaxAc,
                            onSetAllWindows = onSetAllWindows,
                            onOpenPage = onOpenPage,
                            onToggleTemperatureUnit = onToggleTemperatureUnit
                        )
                    }
                    if (page == WatchPage.HOME) {
                        dashboard()
                    } else {
                        SwipeBackBox(
                            pageKey = page,
                            onBack = { onOpenPage(page.parent) },
                            background = if (page.parent == WatchPage.CLIMATE) climatePage else dashboard
                        ) {
                            when (page) {
                                WatchPage.CLIMATE -> climatePage()
                                WatchPage.SEAT_HEAT, WatchPage.SEAT_COOLING -> SeatClimatePage(
                                    uiState = uiState,
                                    vehicle = activeVehicle!!,
                                    kind = if (page == WatchPage.SEAT_HEAT) SeatClimateKind.HEAT else SeatClimateKind.COOL,
                                    onTapSeat = onTapSeat,
                                    onTapSteeringWheel = onTapSteeringWheel
                                )
                                WatchPage.CHARGING -> ChargingPage(
                                    uiState = uiState,
                                    vehicle = activeVehicle!!,
                                    onSetChargeLimit = onSetChargeLimit,
                                    onSetChargeCurrentLimit = onSetChargeCurrentLimit,
                                    onToggleCharging = onToggleCharging,
                                    onToggleChargePort = onToggleChargePort
                                )
                                WatchPage.FLONK -> FlonkPage(
                                    uiState = uiState,
                                    onHonkHorn = onHonkHorn,
                                    onFlashLights = onFlashLights
                                )
                                WatchPage.HOME -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCompanionTopBar(
    uiState: GravityWatchUiState,
    selectedWatchSizeDp: Int,
    onSelectWatchSizeDp: (Int) -> Unit,
    onToggleWatchBezelFrame: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StealthCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = EurekaGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LUCID ACCESS",
                color = LucidPlatinum,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (uiState.watchBezelFrameEnabled) {
                SmallChipButton(
                    label = "Small",
                    active = selectedWatchSizeDp == 192,
                    onClick = { onSelectWatchSizeDp(192) },
                    testTag = "watch_size_small_button"
                )
                SmallChipButton(
                    label = "Medium",
                    active = selectedWatchSizeDp == 213,
                    onClick = { onSelectWatchSizeDp(213) },
                    testTag = "watch_size_medium_button"
                )
                SmallChipButton(
                    label = "Large",
                    active = selectedWatchSizeDp == 227,
                    onClick = { onSelectWatchSizeDp(227) },
                    testTag = "watch_size_large_button"
                )
            }
            SmallChipButton(
                label = if (uiState.watchBezelFrameEnabled) "Round Watch" else "Expanded",
                active = uiState.watchBezelFrameEnabled,
                onClick = onToggleWatchBezelFrame,
                testTag = "bezel_toggle_button"
            )
            if (uiState.isAuthenticated) {
                SmallChipButton(
                    label = "Sign Out",
                    active = false,
                    onClick = onLogout,
                    testTag = "vehicle_logout_button"
                )
            }
        }
    }
}

@Composable
private fun LucidWatchLoginView(
    uiState: GravityWatchUiState,
    onSelectRegion: (LucidRegion) -> Unit,
    onLogin: (String, String, LucidRegion) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordStep by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val backToEmail = {
        localError = null
        isPasswordStep = false
    }
    if (isPasswordStep) {
        // Swipe right (or back) on the password step returns to email entry.
        SwipeBackBox(
            pageKey = "login_password",
            onBack = backToEmail,
            background = {
                LoginForm(uiState, false, email, password, localError, onSelectRegion, {}, {}, {})
            }
        ) {
            LoginForm(
                uiState = uiState,
                showPassword = true,
                email = email,
                password = password,
                localError = localError,
                onSelectRegion = onSelectRegion,
                onEmailEntered = {},
                onPasswordEntered = { entered ->
                    password = entered.replace("\n", "")
                    if (!uiState.isLoading) {
                        if (password.isEmpty()) {
                            localError = "Enter your Lucid account password"
                        } else {
                            localError = null
                            onLogin(email.trim(), password, uiState.selectedRegion)
                        }
                    }
                },
                onEditEmail = backToEmail
            )
        }
    } else {
        LoginForm(
            uiState = uiState,
            showPassword = false,
            email = email,
            password = password,
            localError = localError,
            onSelectRegion = onSelectRegion,
            onEmailEntered = { entered ->
                email = entered.trim()
                if (email.isBlank()) {
                    localError = "Enter your Lucid account email"
                } else {
                    localError = null
                    isPasswordStep = true
                }
            },
            onPasswordEntered = {},
            onEditEmail = {}
        )
    }
}

/** One step of sign-in: email entry, or password entry for the email shown above it. */
@Composable
private fun LoginForm(
    uiState: GravityWatchUiState,
    showPassword: Boolean,
    email: String,
    password: String,
    localError: String?,
    onSelectRegion: (LucidRegion) -> Unit,
    onEmailEntered: (String) -> Unit,
    onPasswordEntered: (String) -> Unit,
    onEditEmail: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LUCID ACCESS",
            color = EurekaGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.6.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LucidRegion.entries.forEach { region ->
                SmallChipButton(
                    label = region.code.uppercase(),
                    active = uiState.selectedRegion == region,
                    onClick = { onSelectRegion(region) },
                    testTag = "region_${region.code}"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!showPassword) {
            // Wear keyboards run full-screen; a phone-style TextField loses sync with them,
            // so text entry goes through the system RemoteInput activity instead.
            WatchTextEntryField(
                label = "Lucid account email",
                value = email,
                inputKey = LOGIN_EMAIL_INPUT_KEY,
                masked = false,
                testTag = "login_email_input",
                onValueEntered = onEmailEntered
            )

            val displayedError = localError ?: uiState.errorMessage
            if (!displayedError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayedError,
                    color = CrimsonAlert,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("login_error_text")
                )
            }
        } else {
            Text(
                text = email,
                color = LucidSilver,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onEditEmail)
                    .padding(bottom = 4.dp)
                    .testTag("login_email_summary")
            )
            WatchTextEntryField(
                label = "Lucid account password",
                value = password,
                inputKey = LOGIN_PASSWORD_INPUT_KEY,
                masked = true,
                testTag = "login_password_input",
                onValueEntered = onPasswordEntered
            )

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = EurekaGold
                )
            }

            val displayedError = localError ?: uiState.errorMessage
            if (!displayedError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayedError,
                    color = CrimsonAlert,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("login_error_text")
                )
            }
        }
    }
}

internal const val LOGIN_EMAIL_INPUT_KEY = "lucid_login_email"
internal const val LOGIN_PASSWORD_INPUT_KEY = "lucid_login_password"

@Composable
private fun WatchTextEntryField(
    label: String,
    value: String,
    inputKey: String,
    masked: Boolean,
    testTag: String,
    onValueEntered: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val entered = result.data
            ?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(inputKey)
            ?.toString()
        if (result.resultCode == Activity.RESULT_OK && entered != null) {
            onValueEntered(entered)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StealthBorder, RoundedCornerShape(12.dp))
            .clickable {
                val remoteInput = RemoteInput.Builder(inputKey)
                    .setLabel(label)
                    .wearableExtender {
                        setEmojisAllowed(false)
                        setInputActionType(EditorInfo.IME_ACTION_DONE)
                    }
                    .build()
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                launcher.launch(intent)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Text(text = label, color = LucidSilver, fontSize = 10.sp)
        Text(
            text = when {
                value.isEmpty() -> "Tap to enter"
                masked -> "•".repeat(value.length)
                else -> value
            },
            color = if (value.isEmpty()) LucidSilver else LucidPlatinum,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class PendingConfirmationAction {
    FRUNK,
    UNLOCK,
    TRUNK,
    STOP_CHARGING
}

/**
 * Simplified Wear OS dashboard:
 * - Current charge level (%) and vehicle nickname at the top
 * - First row of three icon buttons:
 *   1. Frunk
 *   2. Door Lock/Unlock (displaying current Locked / Unlocked state)
 *   3. Trunk
 * - Bottom row of three smaller buttons that open the drill-down pages:
 *   Climate, Charging and Flonk (flash lights / honk horn)
 */
@Composable
private fun SimplifiedWatchDashboard(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot?,
    onRefresh: () -> Unit,
    onOpenVehicleSelector: () -> Unit,
    onToggleFrunk: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleRearTrunk: () -> Unit,
    onStopCharging: () -> Unit,
    onOpenPage: (WatchPage) -> Unit,
    onLogout: () -> Unit
) {
    if (vehicle == null) {
        var showEmptyActions by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(5_000L)
            showEmptyActions = true
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Contacting Cloud...",
                color = LucidSilver,
                fontSize = 13.2.sp,
                textAlign = TextAlign.Center
            )
            if (showEmptyActions) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChipButton(
                        label = "Refresh",
                        active = true,
                        onClick = onRefresh,
                        testTag = "empty_refresh_button",
                        activeColor = Color.White
                    )
                    SmallChipButton("Sign Out", false, onLogout, "empty_logout_button")
                }
            }
        }
        return
    }

    var pendingConfirmation by rememberSaveable { mutableStateOf<PendingConfirmationAction?>(null) }

    val isLocked = vehicle.lockState == LockState.LOCKED
    val chargePercent = vehicle.batteryPercent.roundToInt().coerceIn(0, 100)
    val isCharging = vehicle.chargeState.isActivelyCharging
    // Controls stay frozen until the car is awake and its state is confirmed by Lucid Cloud
    val controlsLockedReason = uiState.controlsLockedReason
    val controlsEnabled = controlsLockedReason == null
    if (!controlsEnabled && pendingConfirmation != null) {
        pendingConfirmation = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        val twentyPixelsInDp = with(LocalDensity.current) { 20f.toDp() }
        val topHeaderPadding = maxHeight * 0.05f

        val batteryIconTint = when {
            chargePercent <= 20 -> Color(0xFFFFEA00)
            chargePercent >= 80 -> AuroraGreen
            else -> Color.White
        }
        val powerColor = when {
            vehicle.chargeRateKw < -0.1 -> CryoBlue
            vehicle.chargeRateKw > 0.1 -> AuroraGreen
            else -> LucidSilver
        }
        val boltTint = if (vehicle.chargeState == ChargeState.EVSE_NOT_READY) {
            CrimsonAlert
        } else {
            powerColor
        }

        // Top Section: Current Charge Level & Vehicle Name moved down by 5% of screen vertical height
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topHeaderPadding)
                .testTag("charge_level_header")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    contentDescription = "Battery Charge",
                    tint = batteryIconTint,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("battery_status_icon")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$chargePercent%",
                    color = Color.White,
                    fontSize = 28.8.sp,
                    lineHeight = 28.8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("charge_level_text")
                )
            }
            val showChargingPower = vehicle.chargeState.isCablePluggedIn ||
                vehicle.chargeRateKw > 0.1 ||
                vehicle.chargeRateKw < -0.1
            val statusLine = uiState.errorMessage ?: controlsLockedReason
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.hasMultipleVehicles) {
                    SwapVehicleButton(onClick = onOpenVehicleSelector)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (statusLine != null) {
                    Text(
                        text = statusLine,
                        color = if (uiState.errorMessage != null && !uiState.isRateLimited) {
                            CrimsonAlert
                        } else {
                            Color(0xFFFFEA00)
                        },
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(y = 2.dp)
                            .widthIn(max = 150.dp)
                            .testTag("vehicle_status_text")
                    )
                } else if (showChargingPower) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.offset(y = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Charging Power",
                            tint = boltTint,
                            modifier = Modifier
                                .size(16.8.dp)
                                .testTag("charging_bolt_icon")
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = vehicle.formatChargingPowerKw(),
                            color = powerColor,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(y = 1.dp)
                                .clickable(enabled = controlsEnabled) {
                                    pendingConfirmation = PendingConfirmationAction.STOP_CHARGING
                                }
                                .testTag("vehicle_nickname_text")
                        )
                    }
                } else {
                    Text(
                        text = vehicle.nickname.uppercase(),
                        color = LucidSilver,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(y = 2.dp)
                            .testTag("vehicle_nickname_text")
                    )
                }
            }
        }

        // Middle Section: Three Icon Buttons (Frunk, Door Lock/Unlock, Trunk) - 10% smaller, centered vertically on screen
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Frunk Icon Button (matches lock/unlock icon color/line weight and yellow ring when open; original when closed)
            ControlIconButton(
                drawableRes = frunkIconRes(vehicle.model, vehicle.frunkState.isOpen),
                contentDesc = "Frunk Open Button",
                isActive = vehicle.frunkState.isOpen,
                isPrimaryCenter = false,
                activeColor = Color(0xFFFFEA00),
                onClick = { pendingConfirmation = PendingConfirmationAction.FRUNK },
                enabled = controlsEnabled,
                testTag = "overview_frunk_button"
            )

            // 2. Door Lock / Unlock Icon Button (yellow ring when unlocked, blank ring when locked)
            ControlIconButton(
                drawableRes = if (isLocked) R.drawable.lock_24px else R.drawable.lock_open_24px,
                contentDesc = if (isLocked) "Doors Locked - Tap to Unlock" else "Doors Unlocked - Tap to Lock",
                isActive = !isLocked,
                isPrimaryCenter = true,
                activeColor = Color(0xFFFFEA00),
                inactiveColor = Color.Transparent,
                onClick = {
                    // Locking is a single tap; unlocking asks first to avoid accidental unlocks.
                    if (isLocked) pendingConfirmation = PendingConfirmationAction.UNLOCK else onToggleLock()
                },
                enabled = controlsEnabled,
                testTag = "primary_lock_toggle_button"
            )

            // 3. Trunk Icon Button (matches lock/unlock icon color/line weight and yellow ring when open; original when closed)
            ControlIconButton(
                drawableRes = trunkIconRes(vehicle.model, vehicle.rearTrunkState.isOpen),
                contentDesc = "Trunk Open Button",
                isActive = vehicle.rearTrunkState.isOpen,
                isPrimaryCenter = false,
                activeColor = Color(0xFFFFEA00),
                onClick = { pendingConfirmation = PendingConfirmationAction.TRUNK },
                enabled = controlsEnabled,
                testTag = "overview_trunk_button"
            )
        }

        // Bottom row: drill-down pages. The outer buttons sit a little higher so the row follows
        // the curve of a round screen. Navigation stays available while controls are frozen.
        val interiorTempC = vehicle.interiorTempCelsius
        val climateTempText = if (interiorTempC.isNaN()) {
            "--°"
        } else if (uiState.useFahrenheit) {
            "${(interiorTempC * 9.0 / 5.0 + 32.0).roundToInt()}°"
        } else {
            "${interiorTempC.roundToInt()}°"
        }
        val climateTempColor = temperatureDisplayColor(interiorTempC, uiState.useFahrenheit)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 4.dp + twentyPixelsInDp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom
        ) {
            val isChargePortOpen = vehicle.chargePortState.isOpen
            DrilldownNavButton(
                icon = R.drawable.nav_ev_station_24px,
                contentDesc = "Charging",
                isActive = isChargePortOpen,
                activeColor = powerColor,
                onClick = { onOpenPage(WatchPage.CHARGING) },
                testTag = "nav_charging_button",
                modifier = Modifier.padding(bottom = 10.dp)
            )
            ClimateTempNavButton(
                temperatureText = climateTempText,
                temperatureColor = climateTempColor,
                isActive = vehicle.hvacPower.isActive,
                activeColor = AuroraGreen,
                onClick = { onOpenPage(WatchPage.CLIMATE) },
                testTag = "nav_climate_button"
            )
            DrilldownNavButton(
                icon = R.drawable.nav_campaign_24px,
                contentDesc = "Flash and Honk",
                isActive = false,
                activeColor = EurekaGold,
                onClick = { onOpenPage(WatchPage.FLONK) },
                testTag = "nav_flonk_button",
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Small yellow dot at the very bottom center of the watch screen while polling in Interactive power state
        if (uiState.isInteractive && uiState.isPolling) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEA00))
                    .semantics { contentDescription = "Polling Lucid API" }
                    .testTag("polling_indicator_dot")
            )
        }

        // Confirmation pop-up for Frunk, Unlock, Trunk, and Stop Charging actions
        val activeConfirmation = pendingConfirmation
        if (activeConfirmation != null) {
            ConfirmationOverlay(
                prompt = when (activeConfirmation) {
                    PendingConfirmationAction.FRUNK -> if (vehicle.frunkState.isOpen) "Close Frunk?" else "Open Frunk?"
                    PendingConfirmationAction.UNLOCK -> "Unlock Doors?"
                    PendingConfirmationAction.TRUNK -> if (vehicle.rearTrunkState.isOpen) "Close Trunk?" else "Open Trunk?"
                    PendingConfirmationAction.STOP_CHARGING -> "Stop Charging?"
                },
                onCancel = { pendingConfirmation = null },
                onConfirm = {
                    pendingConfirmation = null
                    when (activeConfirmation) {
                        PendingConfirmationAction.FRUNK -> onToggleFrunk()
                        PendingConfirmationAction.UNLOCK -> onToggleLock()
                        PendingConfirmationAction.TRUNK -> onToggleRearTrunk()
                        PendingConfirmationAction.STOP_CHARGING -> onStopCharging()
                    }
                }
            )
        }
    }
}

/** Small round button on the home screen that opens a drill-down page. */
@Composable
private fun DrilldownNavButton(
    @DrawableRes icon: Int,
    contentDesc: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF181818))
            .border(if (isActive) 2.dp else 1.dp, if (isActive) activeColor else StealthBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = Color(0xFFB2B2B2),
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Temperature display color based on the active unit mode:
 * - CryoBlue: < 60°F or < 15°C
 * - White: >= 60°F / 15°C and < 85°F / 30°C
 * - ThermalAmber (orange): >= 85°F / 30°C and < 104°F / 40°C
 * - CrimsonAlert (red): >= 104°F / 40°C
 */
internal fun temperatureDisplayColor(celsius: Double, useFahrenheit: Boolean): Color {
    if (celsius.isNaN()) return Color.White
    return if (useFahrenheit) {
        val tempF = (celsius * 9.0 / 5.0 + 32.0).roundToInt()
        when {
            tempF < 60 -> CryoBlue
            tempF < 85 -> Color.White
            tempF < 104 -> ThermalAmber
            else -> CrimsonAlert
        }
    } else {
        val tempC = celsius.roundToInt()
        when {
            tempC < 15 -> CryoBlue
            tempC < 30 -> Color.White
            tempC < 40 -> ThermalAmber
            else -> CrimsonAlert
        }
    }
}

/** Small round Climate button on the home screen showing the car's interior temperature. */
@Composable
private fun ClimateTempNavButton(
    temperatureText: String,
    temperatureColor: Color,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF181818))
            .border(if (isActive) 2.dp else 1.dp, if (isActive) activeColor else StealthBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Climate" }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = temperatureText,
            color = temperatureColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** Small circular button that opens the vehicle selector on accounts with several vehicles. */
@Composable
private fun SwapVehicleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(1.dp, StealthBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Swap vehicle" }
            .testTag("swap_vehicle_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = LucidSilver,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Lists the account's vehicles so the user can pick which one to control. Shown once when an
 * account has several vehicles and none is chosen, and again from the dashboard's swap button.
 */
@Composable
private fun VehicleSelectorView(
    uiState: GravityWatchUiState,
    onSelectVehicle: (String) -> Unit,
    onCancel: () -> Unit
) {
    val hasCurrentChoice = uiState.vehicles.any { it.vehicleId == uiState.selectedVehicleId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp)
            .testTag("vehicle_selector"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CHOOSE VEHICLE",
            color = EurekaGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        uiState.vehicles.forEach { vehicle ->
            val isCurrent = vehicle.vehicleId == uiState.selectedVehicleId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StealthCard)
                    .border(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) EurekaGold else StealthBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectVehicle(vehicle.vehicleId) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("vehicle_option_${vehicle.vehicleId}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = vehicle.nickname,
                    color = LucidPlatinum,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${vehicle.model.displayName} · VIN …${vehicle.vin.takeLast(6)}",
                    color = LucidSilver,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = vehicleLastSeenText(vehicle),
                    color = LucidSilver,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        if (hasCurrentChoice) {
            Spacer(modifier = Modifier.height(6.dp))
            SmallChipButton("Cancel", false, onCancel, "vehicle_selector_cancel")
        }
    }
}

/** Battery level plus how long ago the car last reported, e.g. "66% · 2m ago". */
private fun vehicleLastSeenText(
    vehicle: GravityVehicleSnapshot,
    nowMs: Long = System.currentTimeMillis()
): String {
    val battery = "${vehicle.batteryPercent.roundToInt()}%"
    if (vehicle.lastUpdatedMs <= 0L) return battery
    val ageMinutes = ((nowMs - vehicle.lastUpdatedMs) / 60_000L).coerceAtLeast(0L)
    val age = when {
        ageMinutes < 2 -> "just now"
        ageMinutes < 60 -> "${ageMinutes}m ago"
        ageMinutes < 48 * 60 -> "${ageMinutes / 60}h ago"
        else -> "${ageMinutes / (24 * 60)}d ago"
    }
    return "$battery · $age"
}

@Composable
internal fun ControlIconButton(
    @DrawableRes drawableRes: Int,
    contentDesc: String,
    isActive: Boolean,
    isPrimaryCenter: Boolean,
    activeColor: Color,
    inactiveColor: Color = StealthBorder,
    iconTint: Color = Color(0xFFDEC79B),
    size: Dp? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String
) {
    val buttonSize = size ?: if (isPrimaryCenter) 62.4.dp else 55.2.dp
    val borderColor = if (isActive) activeColor else inactiveColor

    // Keyed on the ring and frozen state: on the watch the ring sometimes kept its previous
    // colour after the closure state changed (yellow on a closed trunk). Rebuilding the button
    // whenever either changes guarantees a fresh draw.
    key(isActive, enabled) {
        Surface(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(
                            width = if (isPrimaryCenter || isActive) 2.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .alpha(if (enabled) 1f else 0.35f)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = contentDesc }
                .testTag(testTag),
            color = ObsidianBlack,
            shape = CircleShape
        ) {
            if (isPrimaryCenter) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(buttonSize * (100f / 116f))
                            .clip(CircleShape)
                            .background(Color(0xFF1B1B1B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = drawableRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun SmallChipButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    activeColor: Color = EurekaGold
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor else StealthCard)
            .border(
                width = 1.dp,
                color = if (active) activeColor else StealthBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) ObsidianBlack else LucidPlatinum,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Frunk / trunk artwork is model-specific. The Air variants are placeholders (generated from
 * Icon Images/air_frunk_trunk.jpg) until final Air icons are supplied. UNKNOWN falls back to Gravity.
 */
@DrawableRes
private fun frunkIconRes(model: VehicleModel, isOpen: Boolean): Int = when (model) {
    VehicleModel.AIR -> if (isOpen) R.drawable.btn_frunk_open_air else R.drawable.btn_open_frunk_air
    else -> if (isOpen) R.drawable.btn_frunk_open else R.drawable.btn_open_frunk
}

@DrawableRes
private fun trunkIconRes(model: VehicleModel, isOpen: Boolean): Int = when (model) {
    VehicleModel.AIR -> if (isOpen) R.drawable.btn_trunk_open_air else R.drawable.btn_open_trunk_air
    else -> if (isOpen) R.drawable.btn_trunk_open else R.drawable.btn_open_trunk
}
