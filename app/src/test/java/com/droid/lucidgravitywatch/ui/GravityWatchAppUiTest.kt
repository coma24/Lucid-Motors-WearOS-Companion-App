package com.droid.lucidgravitywatch.ui

import android.app.Activity
import android.app.Application
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import com.droid.lucidgravitywatch.theme.CrimsonAlert
import com.droid.lucidgravitywatch.theme.CryoBlue
import com.droid.lucidgravitywatch.theme.LucidGravityWatchTheme
import com.droid.lucidgravitywatch.theme.ThermalAmber
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import com.droid.lucidgravitywatch.viewmodel.WatchPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class GravityWatchAppUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Simulates the system Wear keyboard returning text to the RemoteInput launcher.
    private fun enterRemoteInput(fieldTag: String, inputKey: String, text: String) {
        composeTestRule.onNodeWithTag(fieldTag).performClick()
        val shadowActivity = shadowOf(composeTestRule.activity)
        val started = shadowActivity.nextStartedActivityForResult
        val results = Intent()
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(inputKey).build()),
            results,
            Bundle().apply { putCharSequence(inputKey, text) }
        )
        shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, results)
        composeTestRule.waitForIdle()
    }

    private val sampleGravity = GravityVehicleSnapshot(
        vehicleId = "veh-gravity-gt",
        vin = "50EA1GBA7RA000888",
        nickname = "Gravity GT",
        model = VehicleModel.GRAVITY,
        variantName = "Grand Touring",
        paintColorName = "Aurora Green",
        powerState = PowerState.CLOUD_1,
        lockState = LockState.LOCKED,
        walkawayState = WalkawayState.ACTIVE,
        frunkState = DoorState.CLOSED,
        rearTrunkState = DoorState.CLOSED,
        chargePortState = DoorState.CLOSED,
        frontLeftDoor = DoorState.CLOSED,
        frontRightDoor = DoorState.CLOSED,
        rearLeftDoor = DoorState.CLOSED,
        rearRightDoor = DoorState.CLOSED,
        batteryPercent = 84.0,
        remainingRangeKm = 580.0,
        batteryKwh = 100.8,
        batteryCapacityKwh = 120.0,
        batteryPreconStatus = BatteryPreconStatus.OFF,
        chargeState = ChargeState.CABLE_CONNECTED,
        chargeLimitPercent = 85,
        chargeRateKw = 19.2,
        chargeRateMph = 58.0,
        sessionMinutesRemaining = 18,
        interiorTempCelsius = 21.0,
        exteriorTempCelsius = 16.0,
        targetTempCelsius = 21.0,
        hvacPower = HvacPower.PRECONDITION,
        defrostState = DefrostState.OFF,
        maxAcEnabled = false,
        driverSeatHeatMode = SeatClimateMode.LOW,
        driverSeatVentMode = SeatClimateMode.OFF,
        passengerSeatHeatMode = SeatClimateMode.OFF,
        steeringHeaterLevel = SteeringWheelHeaterLevel.LEVEL_1,
        odometerKm = 3200.0,
        softwareVersion = "2.5.12",
        lastUpdatedMs = 1792113000000L
    )

    @Test
    fun loginView_submitsEmailPasswordAndRegion() {
        var submittedEmail = ""
        var submittedPassword = ""
        var submittedRegion = LucidRegion.US

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = false,
                        selectedRegion = LucidRegion.EU
                    ),
                    onSelectRegion = {},
                    onLogin = { email, pass, region ->
                        submittedEmail = email
                        submittedPassword = pass
                        submittedRegion = region
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("LUCID ACCESS").assertIsDisplayed()
        // Step 1: "Lucid account email" entry box is displayed without password box underneath
        composeTestRule.onNodeWithTag("login_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").assertDoesNotExist()

        enterRemoteInput("login_email_input", LOGIN_EMAIL_INPUT_KEY, "pilot@lucidmotors.com")

        // Step 2: "Lucid account email" entry box is replaced by "Lucid account password" entry box
        composeTestRule.onNodeWithTag("login_email_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("login_password_input").assertIsDisplayed()

        composeTestRule.onNodeWithText("pilot@lucidmotors.com").assertIsDisplayed()
        enterRemoteInput("login_password_input", LOGIN_PASSWORD_INPUT_KEY, "Gravity2026!")
        composeTestRule.onNodeWithText("Gravity2026!").assertDoesNotExist()

        assertEquals("pilot@lucidmotors.com", submittedEmail)
        assertEquals("Gravity2026!", submittedPassword)
        assertEquals(LucidRegion.EU, submittedRegion)
    }

    @Test
    fun simplifiedDashboard_showsChargeLevelClosuresAndDrilldownButtons() {
        var frunkClicked = false
        var lockClicked = false
        var trunkClicked = false
        var stopChargingClicked = false
        val openedPages = mutableListOf<WatchPage>()
        val currentVehicle = mutableStateOf(sampleGravity)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = true,
                        hasFreshTelemetry = true,
                        vehicles = listOf(currentVehicle.value),
                        selectedVehicleId = currentVehicle.value.vehicleId
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleFrunk = { frunkClicked = true },
                    onToggleLock = {
                        lockClicked = true
                        currentVehicle.value = currentVehicle.value.copy(lockState = LockState.UNLOCKED)
                    },
                    onToggleRearTrunk = { trunkClicked = true },
                    onStopCharging = { stopChargingClicked = true },
                    onOpenPage = { openedPages += it }
                )
            }
        }

        // Verify charge level at top; sampleGravity has chargeRateKw = 19.2 (> 0.1kW) -> nearest kW "19 kW"
        composeTestRule.onNodeWithTag("charge_level_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("84%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("19 kW").assertIsDisplayed()

        // Tapping charging power text opens "Stop Charging?" confirmation dialog
        composeTestRule.onNodeWithText("19 kW").performClick()
        org.junit.Assert.assertFalse(stopChargingClicked)
        composeTestRule.onNodeWithText("Stop Charging?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()
        org.junit.Assert.assertFalse(stopChargingClicked)
        composeTestRule.onNodeWithText("19 kW").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(stopChargingClicked)

        // When plugged in with charging power between -0.1kW and 0.1kW -> shows lightning bolt + "0 kW"
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.CABLE_CONNECTED,
            chargeRateKw = 0.05
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 kW").assertIsDisplayed()

        // When API returns EVSE_NOT_READY (proto 25) -> treated same as CABLE_CONNECTED with red lightning bolt + "0 kW"
        assertEquals(ChargeState.EVSE_NOT_READY, ChargeState.fromProto(25))
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.fromProto(25),
            chargeRateKw = 0.0
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 kW").assertIsDisplayed()

        // When RangeXChange discharging (< -0.1kW) -> shows lightning bolt + "-9 kW"
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.DISCHARGING,
            chargeRateKw = -9.4
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("-9 kW").assertIsDisplayed()

        // When unplugged and charging power within [-0.1, 0.1] -> shows vehicle nickname
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.NOT_CONNECTED,
            chargeRateKw = 0.0
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertDoesNotExist()
        composeTestRule.onNodeWithText("GRAVITY GT").assertIsDisplayed()

        composeTestRule.onNodeWithTag("swap_vehicle_button").assertDoesNotExist()

        // Verify Locked state initially shown on Door Lock/Unlock button
        composeTestRule.onNodeWithContentDescription("Doors Locked - Tap to Unlock").assertIsDisplayed()

        // 1. Frunk button requires confirmation ("Open Frunk?" when closed, "Close Frunk?" when open)
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        org.junit.Assert.assertFalse(frunkClicked)
        composeTestRule.onNodeWithText("Open Frunk?").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeTestRule.onNodeWithText("Confirm").assertDoesNotExist()
        // Cancel (X icon button) does not send command
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()
        org.junit.Assert.assertFalse(frunkClicked)
        // Confirm (Checkmark icon button) sends command
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(frunkClicked)

        // When Frunk is open, pressing Frunk shows "Close Frunk?"
        currentVehicle.value = currentVehicle.value.copy(frunkState = DoorState.OPEN)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithText("Close Frunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()

        // 2. Unlocking asks first ("Unlock Doors?"); locking is a single tap
        composeTestRule.onNodeWithTag("primary_lock_toggle_button").performClick()
        org.junit.Assert.assertFalse(lockClicked)
        composeTestRule.onNodeWithText("Unlock Doors?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(lockClicked)
        composeTestRule.onNodeWithContentDescription("Doors Unlocked - Tap to Lock").assertIsDisplayed()

        lockClicked = false
        composeTestRule.onNodeWithTag("primary_lock_toggle_button").performClick()
        assertTrue(lockClicked)
        composeTestRule.onNodeWithTag("confirmation_popup_overlay").assertDoesNotExist()

        // 3. Trunk button requires confirmation ("Open Trunk?" when closed, "Close Trunk?" when open)
        composeTestRule.onNodeWithTag("overview_trunk_button").performClick()
        org.junit.Assert.assertFalse(trunkClicked)
        composeTestRule.onNodeWithText("Open Trunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(trunkClicked)

        currentVehicle.value = currentVehicle.value.copy(rearTrunkState = DoorState.OPEN)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_trunk_button").performClick()
        composeTestRule.onNodeWithText("Close Trunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()

        // 4. Horn and lights moved off the home screen; the bottom row opens the drill-down pages
        composeTestRule.onNodeWithTag("overview_horn_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("overview_lights_button").assertDoesNotExist()
        // Climate button displays interior temperature (21.0°C -> 70° in Fahrenheit mode)
        composeTestRule.onNodeWithText("70°").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_charging_button").performClick()
        composeTestRule.onNodeWithTag("nav_climate_button").performClick()
        composeTestRule.onNodeWithTag("nav_flonk_button").performClick()
        assertEquals(listOf(WatchPage.CHARGING, WatchPage.CLIMATE, WatchPage.FLONK), openedPages)
    }

    @Test
    fun simplifiedDashboard_freezesControlsAndShowsReasonUntilCarAwakeAndStatusKnown() {
        var frunkClicked = false
        var hornClicked = false
        val uiState = mutableStateOf(
            GravityWatchUiState(
                isAuthenticated = true,
                hasFreshTelemetry = true,
                isWakingVehicle = true,
                vehicles = listOf(sampleGravity.copy(powerState = PowerState.SLEEP)),
                selectedVehicleId = sampleGravity.vehicleId
            )
        )
        val showFlonk = mutableStateOf(false)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = uiState.value.copy(
                        currentPage = if (showFlonk.value) WatchPage.FLONK else WatchPage.HOME
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleFrunk = { frunkClicked = true },
                    onHonkHorn = { hornClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Waking car…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_popup_overlay").assertDoesNotExist()
        // Drill-down pages freeze their controls the same way
        showFlonk.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("page_status_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flonk_horn_button").performClick()
        org.junit.Assert.assertFalse(hornClicked)
        showFlonk.value = false

        // Car awake and status known -> controls work and the status line is replaced
        uiState.value = uiState.value.copy(
            isWakingVehicle = false,
            vehicles = listOf(sampleGravity)
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_status_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(frunkClicked)
    }

    @Test
    fun vehicleSelector_shownWhenNoVehicleChosen_andSwapButtonOpensIt() {
        val secondCar = sampleGravity.copy(vehicleId = "veh-air-pe", nickname = "PE GT", vin = "50EA1GBA5NA005210")
        val uiState = mutableStateOf(
            GravityWatchUiState(
                isAuthenticated = true,
                hasFreshTelemetry = true,
                vehicles = listOf(sampleGravity, secondCar)
            )
        )
        var selectedId: String? = null
        var swapClicked = false

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = uiState.value,
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onSelectVehicle = { id ->
                        selectedId = id
                        uiState.value = uiState.value.copy(selectedVehicleId = id, isSelectingVehicle = false)
                    },
                    onOpenVehicleSelector = {
                        swapClicked = true
                        uiState.value = uiState.value.copy(isSelectingVehicle = true)
                    }
                )
            }
        }

        // No vehicle chosen yet -> selector lists both cars, no Cancel
        composeTestRule.onNodeWithTag("vehicle_selector").assertIsDisplayed()
        composeTestRule.onNodeWithText("VIN …005210", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("vehicle_selector_cancel").assertDoesNotExist()

        composeTestRule.onNodeWithTag("vehicle_option_veh-air-pe").performClick()
        assertEquals("veh-air-pe", selectedId)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_selector").assertDoesNotExist()

        // Dashboard shows the swap button, which reopens the selector with a Cancel option
        composeTestRule.onNodeWithTag("swap_vehicle_button").performClick()
        assertTrue(swapClicked)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vehicle_selector_cancel").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun simplifiedDashboard_showsYellowPollingDotOnlyWhilePollingInInteractiveState() {
        val isPollingState = mutableStateOf(true)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = true,
                        isInteractive = true,
                        isPolling = isPollingState.value,
                        vehicles = listOf(sampleGravity),
                        selectedVehicleId = sampleGravity.vehicleId
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("polling_indicator_dot").assertIsDisplayed()

        isPollingState.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("polling_indicator_dot").assertDoesNotExist()
    }

    private fun dashboardState(page: WatchPage, vehicle: GravityVehicleSnapshot = sampleGravity) =
        GravityWatchUiState(
            isAuthenticated = true,
            hasFreshTelemetry = true,
            vehicles = listOf(vehicle),
            selectedVehicleId = vehicle.vehicleId,
            currentPage = page
        )

    @Test
    fun climatePage_showsTemperatures_togglesClimate_andSetsTargetOnlyOnConfirm() {
        var climateToggled = 0
        val targets = mutableListOf<Double>()
        val pages = mutableListOf<WatchPage>()

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.CLIMATE),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleClimatePrecondition = { climateToggled++ },
                    onSetTargetTemperature = { targets += it },
                    onOpenPage = { pages += it }
                )
            }
        }

        // 21 °C inside and target, shown in °F; climate is preconditioning
        composeTestRule.onNodeWithTag("climate_unit_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("climate_power_toggle").performClick()
        assertEquals(1, climateToggled)

        // Target editor: +/- only change the displayed value until confirm
        composeTestRule.onNodeWithTag("climate_target_row").performClick()
        composeTestRule.onNodeWithTag("target_temp_editor").assertIsDisplayed()
        composeTestRule.onNodeWithText("70°F").assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_temp_increase").performClick()
        composeTestRule.onNodeWithTag("target_temp_increase").performClick()
        composeTestRule.onNodeWithText("72°F").assertIsDisplayed()
        assertTrue(targets.isEmpty())
        composeTestRule.onNodeWithTag("target_temp_confirm").performClick()
        assertEquals(1, targets.size)
        assertEquals(22.22, targets.single(), 0.01)
        composeTestRule.onNodeWithTag("target_temp_editor").assertDoesNotExist()

        // Cancel discards the edit
        composeTestRule.onNodeWithTag("climate_target_row").performClick()
        composeTestRule.onNodeWithTag("target_temp_decrease").performClick()
        composeTestRule.onNodeWithTag("target_temp_cancel").performClick()
        assertEquals(1, targets.size)

        // Seat heat/cooling buttons sit further down the list and open the seat diagrams
        composeTestRule.onNodeWithTag("climate_page").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("climate_seat_heat_button").performScrollTo().performClick()
        assertEquals(listOf(WatchPage.SEAT_HEAT), pages)
        // sampleGravity has no ventilated seats
        composeTestRule.onNodeWithTag("climate_seat_cooling_button").assertDoesNotExist()
    }

    @Test
    fun climatePage_ventingWindowsAsksFirst_closingIsOneTap() {
        val vehicle = mutableStateOf(sampleGravity)
        val choices = mutableListOf<Boolean>()
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.CLIMATE, vehicle.value),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onSetAllWindows = { choices += it }
                )
            }
        }

        // Closed: venting asks first; cancel sends nothing
        composeTestRule.onNodeWithTag("climate_windows_button").performClick()
        composeTestRule.onNodeWithText("Crack Windows?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()
        assertTrue(choices.isEmpty())
        composeTestRule.onNodeWithTag("climate_windows_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertEquals(listOf(true), choices)

        // Open: one tap closes, no pop-up
        vehicle.value = sampleGravity.copy(windowsOpen = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("climate_windows_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_popup_overlay").assertDoesNotExist()
        assertEquals(listOf(true, false), choices)
    }

    @Test
    fun seatHeatPage_tapsSeatsAndWheel_andBackGoesToClimate() {
        val taps = mutableListOf<Pair<ClimateSeat, SeatClimateKind>>()
        var wheelTaps = 0
        val pages = mutableListOf<WatchPage>()
        val vehicle = sampleGravity.copy(hasHeatedRearSeats = true, hasVentilatedFrontSeats = true)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.SEAT_HEAT, vehicle),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onOpenPage = { pages += it },
                    onTapSeat = { seat, kind -> taps += seat to kind },
                    onTapSteeringWheel = { wheelTaps++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("seat_heat_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seat_zone_driver").performClick()
        composeTestRule.onNodeWithTag("seat_zone_passenger").performClick()
        composeTestRule.onNodeWithTag("seat_zone_rear").performClick()
        composeTestRule.onNodeWithTag("seat_zone_steering_wheel").performClick()
        assertEquals(
            listOf(
                ClimateSeat.DRIVER to SeatClimateKind.HEAT,
                ClimateSeat.PASSENGER to SeatClimateKind.HEAT,
                ClimateSeat.REAR to SeatClimateKind.HEAT
            ),
            taps
        )
        assertEquals(1, wheelTaps)

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf(WatchPage.CLIMATE), pages)
    }

    @Test
    fun seatCoolingPage_showsFrontSeatsOnly() {
        val taps = mutableListOf<Pair<ClimateSeat, SeatClimateKind>>()
        val vehicle = sampleGravity.copy(hasHeatedRearSeats = true, hasVentilatedFrontSeats = true)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.SEAT_COOLING, vehicle),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onTapSeat = { seat, kind -> taps += seat to kind }
                )
            }
        }

        composeTestRule.onNodeWithTag("seat_cooling_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seat_zone_rear").assertDoesNotExist()
        composeTestRule.onNodeWithTag("seat_zone_steering_wheel").assertDoesNotExist()
        composeTestRule.onNodeWithTag("seat_zone_driver").performClick()
        assertEquals(listOf(ClimateSeat.DRIVER to SeatClimateKind.COOL), taps)
    }

    @Test
    fun chargingPage_startStopNeedsCable_stopAsksFirst_andLimitSetsOnConfirm() {
        // An Air: its charge port door is powered, so the port pill is a control
        val vehicle = mutableStateOf(
            sampleGravity.copy(model = VehicleModel.AIR, chargeState = ChargeState.NOT_CONNECTED, chargeRateKw = 0.0)
        )
        var chargingToggled = 0
        var portToggled = 0
        val limits = mutableListOf<Int>()
        val rates = mutableListOf<Int>()

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.CHARGING, vehicle.value),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleCharging = { chargingToggled++ },
                    onToggleChargePort = { portToggled++ },
                    onSetChargeLimit = { limits += it },
                    onSetChargeCurrentLimit = { rates += it }
                )
            }
        }

        // Unplugged: the charge port is offered, start/stop is not
        composeTestRule.onNodeWithTag("charging_start_stop").assertDoesNotExist()
        composeTestRule.onNodeWithTag("charging_port_toggle").performClick()
        assertEquals(1, portToggled)

        // Plugged in: the port is no longer offered; Start Charging fires immediately
        vehicle.value = vehicle.value.copy(chargeState = ChargeState.CABLE_CONNECTED)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_port_toggle").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Charging").performClick()
        assertEquals(1, chargingToggled)

        // Charging: Stop Charging asks for confirmation first
        vehicle.value = vehicle.value.copy(chargeState = ChargeState.CHARGING, chargeRateKw = 11.0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Stop Charging").performClick()
        composeTestRule.onNodeWithText("Stop Charging?").assertIsDisplayed()
        assertEquals(1, chargingToggled)
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertEquals(2, chargingToggled)

        // Charge limit 85% -> 86% on confirm
        composeTestRule.onNodeWithTag("charging_limit_row").performClick()
        composeTestRule.onNodeWithTag("charge_limit_increase").performClick()
        composeTestRule.onNodeWithTag("charge_limit_confirm").performClick()
        assertEquals(listOf(86), limits)

        // Charge rate: unknown (--) starts the editor at 80 A; minus once -> 75 A (5 A steps)
        composeTestRule.onNodeWithTag("charging_rate_row").performClick()
        composeTestRule.onNodeWithTag("charge_rate_decrease").performClick()
        composeTestRule.onNodeWithTag("charge_rate_confirm").performClick()
        assertEquals(listOf(75), rates)

        // Touching the top of the arc picks the middle of the range: 10..80 A -> 45 A
        composeTestRule.onNodeWithTag("charging_rate_row").performClick()
        composeTestRule.onNodeWithTag("charge_rate_arc").performTouchInput {
            val radius = minOf(width, height) / 2f - VALUE_ARC_INSET.toPx()
            down(Offset(centerX, centerY - radius))
            up()
        }
        composeTestRule.onNodeWithTag("charge_rate_confirm").performClick()
        assertEquals(listOf(75, 45), rates)

        // -/+ snap to multiples of 5 A from an in-between rate: 23 A, minus -> 20 A
        vehicle.value = vehicle.value.copy(sessionAcCurrentLimitAmps = 23)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_rate_row").performClick()
        composeTestRule.onNodeWithTag("charge_rate_decrease").performClick()
        composeTestRule.onNodeWithTag("charge_rate_confirm").performClick()
        assertEquals(listOf(75, 45, 20), rates)
    }

    @Test
    fun chargingPage_gravityShowsPortStateWithoutOfferingACommand() {
        // The Gravity's charge port door is manual: the pill only reports its state
        val vehicle = mutableStateOf(sampleGravity.copy(chargeState = ChargeState.NOT_CONNECTED, chargeRateKw = 0.0))
        var portToggled = 0

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.CHARGING, vehicle.value),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleChargePort = { portToggled++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("charging_port_toggle").assertDoesNotExist()
        composeTestRule.onNodeWithTag("charging_port_status").assertHasNoClickAction()
        composeTestRule.onNodeWithText("Port Closed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("charging_port_status").performClick()
        assertEquals(0, portToggled)

        vehicle.value = vehicle.value.copy(chargePortState = DoorState.OPEN)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Port Open").assertIsDisplayed()
    }

    @Test
    fun flonkPage_flashesAndHonksWithoutConfirmation() {
        var honked = false
        var flashed = false
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(WatchPage.FLONK),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onHonkHorn = { honked = true },
                    onFlashLights = { flashed = true }
                )
            }
        }
        composeTestRule.onNodeWithText("FLONK").assertDoesNotExist()
        composeTestRule.onNodeWithText("Flonk").assertDoesNotExist()
        composeTestRule.onNodeWithTag("flonk_lights_button").performClick()
        composeTestRule.onNodeWithTag("flonk_horn_button").performClick()
        assertTrue(flashed)
        assertTrue(honked)
        composeTestRule.onNodeWithTag("confirmation_popup_overlay").assertDoesNotExist()
    }

    @Test
    fun drilldownPage_backReturnsHome() {
        val page = mutableStateOf(WatchPage.CHARGING)
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(page.value),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onOpenPage = { page.value = it }
                )
            }
        }
        composeTestRule.onNodeWithTag("charging_page").assertIsDisplayed()
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals(WatchPage.HOME, page.value)
        composeTestRule.onNodeWithTag("nav_charging_button").assertIsDisplayed()
    }

    @Test
    fun drilldownPage_swipeRightReturnsHome() {
        val page = mutableStateOf(WatchPage.CLIMATE)
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = dashboardState(page.value),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onOpenPage = { page.value = it }
                )
            }
        }
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        assertEquals(WatchPage.HOME, page.value)
    }

    @Test
    fun loginPasswordStep_swipeRightReturnsToEmailEntry() {
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> }
                )
            }
        }
        enterRemoteInput("login_email_input", LOGIN_EMAIL_INPUT_KEY, "pilot@lucidmotors.com")
        composeTestRule.onNodeWithTag("login_password_input").assertIsDisplayed()

        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_password_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("login_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("pilot@lucidmotors.com").assertIsDisplayed()
    }

    @Test
    fun temperatureDisplayColor_matchesThresholdsInFahrenheitAndCentigrade() {
        // Centigrade thresholds: <15C CryoBlue, 15..29C White, 30..39C ThermalAmber, >=40C CrimsonAlert
        assertEquals(CryoBlue, temperatureDisplayColor(14.4, useFahrenheit = false))
        assertEquals(androidx.compose.ui.graphics.Color.White, temperatureDisplayColor(15.0, useFahrenheit = false))
        assertEquals(androidx.compose.ui.graphics.Color.White, temperatureDisplayColor(29.4, useFahrenheit = false))
        assertEquals(ThermalAmber, temperatureDisplayColor(30.0, useFahrenheit = false))
        assertEquals(ThermalAmber, temperatureDisplayColor(39.4, useFahrenheit = false))
        assertEquals(CrimsonAlert, temperatureDisplayColor(40.0, useFahrenheit = false))

        // Fahrenheit thresholds: <60F CryoBlue, 60..84F White, 85..103F ThermalAmber, >=104F CrimsonAlert
        assertEquals(CryoBlue, temperatureDisplayColor((59.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
        assertEquals(androidx.compose.ui.graphics.Color.White, temperatureDisplayColor((60.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
        assertEquals(androidx.compose.ui.graphics.Color.White, temperatureDisplayColor((84.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
        assertEquals(ThermalAmber, temperatureDisplayColor((85.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
        assertEquals(ThermalAmber, temperatureDisplayColor((103.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
        assertEquals(CrimsonAlert, temperatureDisplayColor((104.0 - 32.0) * 5.0 / 9.0, useFahrenheit = true))
    }
}

