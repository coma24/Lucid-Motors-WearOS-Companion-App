package com.droid.lucidgravitywatch.tools

import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.network.OkHttpLucidGrpcTransport
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec.ProtoFieldMap
import com.droid.lucidgravitywatch.repository.LucidCloudVehicleRepository
import com.droid.lucidgravitywatch.storage.InMemorySessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Manual probe: what a sleeping car reports while it wakes. Sends ONE WakeupVehicle, then polls
 * GetVehicleState (and occasionally GetUserVehicles) and logs the raw body/charging fields, so
 * placeholder values sent during wake-up (e.g. charge port OPEN/AJAR) are visible before the
 * app's merge logic touches them. Sends no other commands. Skipped unless LUCID_PROBE_EMAIL is set:
 *
 *   LUCID_PROBE_EMAIL=... LUCID_PROBE_PASSWORD=... LUCID_PROBE_VIN_SUFFIX=005210 \
 *       ./gradlew testDebugUnitTest --rerun -i --tests '*LucidWakeStateProbe*'
 *
 * Close the watch app first so it doesn't wake the car itself or share the rate-limit quota.
 */
class LucidWakeStateProbe {

    private val email: String? = System.getenv("LUCID_PROBE_EMAIL")
    private val password: String? = System.getenv("LUCID_PROBE_PASSWORD")
    private val vinSuffix: String? = System.getenv("LUCID_PROBE_VIN_SUFFIX")
    private val region: LucidRegion = System.getenv("LUCID_PROBE_REGION")
        ?.let { name -> LucidRegion.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: LucidRegion.US

    /** Read-only: one GetUserVehicles (login) and one GetVehicleState. Never sends WakeupVehicle. */
    @Test
    fun peekStateWithoutWaking() = runBlocking {
        assumeTrue("Set LUCID_PROBE_EMAIL / LUCID_PROBE_PASSWORD / LUCID_PROBE_VIN_SUFFIX", !email.isNullOrBlank() && !vinSuffix.isNullOrBlank())
        val transport = OkHttpLucidGrpcTransport()
        val sessionStore = InMemorySessionStore()
        val repository = LucidCloudVehicleRepository(transport, sessionStore)
        val vehicle = repository.login(email!!, password.orEmpty(), region).firstOrNull { it.vin.endsWith(vinSuffix!!) }
            ?: error("No vehicle with VIN ending $vinSuffix")
        val session = sessionStore.loadSession() ?: error("not signed in")
        val bytes = transport.unaryCall(
            region = region,
            serviceMethod = METHOD_GET_VEHICLE_STATE,
            requestProtobuf = LucidProtobufCodec.encodeGetVehicleStateRequest(vehicle.vehicleId),
            bearerToken = session.idToken
        )
        val state = LucidProtobufCodec.parseRawFields(bytes).getSubMessage(2)
        println("PEEK login: ${vehicle.summary()}")
        println("PEEK GetVehicleState raw{${state?.let(::describeRawState) ?: "no state"}}")
        val reported = state?.varints?.get(5)?.lastOrNull() ?: 0L
        println("PEEK last report ${(System.currentTimeMillis() - reported) / 1000}s ago (server clock)")
    }

    @Test
    fun probeChargePortWhileWaking() = runBlocking {
        assumeTrue("Set LUCID_PROBE_EMAIL / LUCID_PROBE_PASSWORD / LUCID_PROBE_VIN_SUFFIX", !email.isNullOrBlank() && !vinSuffix.isNullOrBlank())
        val out = File(System.getenv("LUCID_PROBE_OUT") ?: "build/wake-state-probe.log")
        out.parentFile?.mkdirs()
        out.writeText("")
        val startMs = System.currentTimeMillis()
        fun log(line: String) {
            val text = "%6.1fs  %s".format((System.currentTimeMillis() - startMs) / 1000.0, line)
            println(text)
            out.appendText(text + "\n")
        }

        val transport = OkHttpLucidGrpcTransport()
        val sessionStore = InMemorySessionStore()
        val repository = LucidCloudVehicleRepository(transport, sessionStore)
        val vehicles = repository.login(email!!, password.orEmpty(), region)
        var vehicle = vehicles.firstOrNull { it.vin.endsWith(vinSuffix!!) }
            ?: error("No vehicle with VIN ending $vinSuffix (have ${vehicles.map { it.vin.takeLast(6) }})")
        log("login: ${vehicle.summary()}")
        if (vehicle.powerState != PowerState.SLEEP) log("WARNING: car is not asleep (${vehicle.powerState}); the waking phase may be missed")

        repository.wakeVehicle(vehicle)
        log("WakeupVehicle sent")

        var n = 0
        val durationMs = System.getenv("LUCID_PROBE_SECONDS")?.toLongOrNull()?.times(1000) ?: DURATION_MS
        while (System.currentTimeMillis() - startMs < durationMs) {
            n++
            try {
                if (n % 8 == 0) {
                    val (raw, full) = userVehicle(transport, sessionStore, vehicle.vehicleId)
                    log("GetUserVehicles  raw{${raw?.let(::describeRawState) ?: "no state"}}  decoded{${full?.summary()}}")
                } else {
                    val session = sessionStore.loadSession() ?: error("not signed in")
                    val bytes = transport.unaryCall(
                        region = region,
                        serviceMethod = METHOD_GET_VEHICLE_STATE,
                        requestProtobuf = LucidProtobufCodec.encodeGetVehicleStateRequest(vehicle.vehicleId),
                        bearerToken = session.idToken
                    )
                    val state = LucidProtobufCodec.parseRawFields(bytes).getSubMessage(2)
                    vehicle = LucidProtobufCodec.decodeGetVehicleStateResponse(bytes, vehicle)
                    log("GetVehicleState  raw{${state?.let(::describeRawState) ?: "no state"}}  merged{${vehicle.summary()}}")
                }
            } catch (e: Exception) {
                log("ERROR ${e.javaClass.simpleName}: ${e.message}")
            }
            delay(POLL_MS)
        }
        log("done; log at ${out.absolutePath}")
    }

    /** One GetUserVehicles call: the car's raw state block and the app's full decode of it. */
    private suspend fun userVehicle(
        transport: OkHttpLucidGrpcTransport,
        sessionStore: InMemorySessionStore,
        vehicleId: String
    ): Pair<ProtoFieldMap?, GravityVehicleSnapshot?> {
        val session = sessionStore.loadSession() ?: return null to null
        val bytes = transport.unaryCall(
            region = region,
            serviceMethod = METHOD_GET_USER_VEHICLES,
            requestProtobuf = LucidProtobufCodec.encodeGetUserVehiclesRequest(),
            bearerToken = session.idToken
        )
        val raw = LucidProtobufCodec.parseRawFields(bytes).getSubMessages(1)
            .firstOrNull { it.getString(1) == vehicleId }
            ?.getSubMessage(4)
        val decoded = LucidProtobufCodec.decodeGetUserVehiclesResponse(bytes).firstOrNull { it.vehicleId == vehicleId }
        return raw to decoded
    }

    /** The raw wire values that drive the Charging ring, before any merge or filtering. */
    private fun describeRawState(state: ProtoFieldMap): String {
        val body = state.getSubMessage(4)
        val charging = state.getSubMessage(8)
        val battery = state.getSubMessage(1)
        val port = body?.varints?.get(8)?.lastOrNull()
        return listOf(
            "power=${state.varints[2]?.lastOrNull()?.let { PowerState.fromProto(it.toInt()).name + "($it)" } ?: "absent"}",
            "body=${if (body == null) "absent" else "present"}",
            "port=${port?.let { DoorState.fromProto(it.toInt()).name + "($it)" } ?: if (body == null) "-" else "unset(0)"}",
            "bodyFields=${body?.varints?.entries?.sortedBy { it.key }?.joinToString(",") { "${it.key}:${it.value.last()}" } ?: "-"}",
            "charging=${if (charging == null) "absent" else "present"}",
            "chargeState=${charging?.varints?.get(1)?.lastOrNull()?.let { ChargeState.fromProto(it.toInt()).name } ?: "-"}",
            "kW=${charging?.getDouble(19) ?: "-"}",
            "batt=${battery?.getDouble(2) ?: "-"}",
            "reported=${state.varints[5]?.lastOrNull() ?: "-"}"
        ).joinToString(" ")
    }

    private fun GravityVehicleSnapshot.summary(): String =
        "power=${powerState.name} port=${chargePortState.name} ringOn=${chargePortState.isOpen} " +
            "kW=$chargeRateKw charge=${chargeState.name} battery=${batteryPercent.toInt()}%"

    private companion object {
        const val DURATION_MS = 150_000L
        const val POLL_MS = 2_500L
        const val METHOD_GET_VEHICLE_STATE = "/mobilegateway.protos.VehicleStateService/GetVehicleState"
        const val METHOD_GET_USER_VEHICLES = "/mobilegateway.protos.LoginSession/GetUserVehicles"
    }
}
