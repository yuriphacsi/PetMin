package com.yuri.petbite.device

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import com.yuri.petbite.data.PetSettings
import com.yuri.petbite.data.MealSchedule
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FeederApiTest {
    private lateinit var server: MockWebServer
    private lateinit var config: PetSettings
    private var responder: (String, String, String, String?) -> Pair<Int, String> = { _, _, _, _ -> 500 to "{}" }
    @Before fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, json) = responder(request.path.orEmpty(), request.method.orEmpty(), request.body.readUtf8(), request.getHeader("Authorization"))
                return MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
        config = PetSettings(address = server.url("/").toString().trimEnd('/'), token = "test-key")
    }
    @After fun stopServer() { server.shutdown() }
    @Test fun asyncDeliverySendsOnlyOnePost() = runBlocking {
        val posts = AtomicInteger(); val gets = AtomicInteger()
        responder = { path, method, body, auth ->
            assertEquals("Bearer test-key", auth)
            if (method == "POST") {
                assertEquals("/api/dispense", path); assertEquals(1L, JSONObject(body).getLong("sequence")); assertEquals(40, JSONObject(body).getInt("grams")); posts.incrementAndGet()
                202 to """{"requestId":"test-job-123","status":"accepted"}"""
            } else {
                assertEquals("/api/jobs/test-job-123", path)
                if (gets.incrementAndGet() == 1) 200 to """{"requestId":"test-job-123","status":"dispensing"}"""
                else 200 to """{"requestId":"test-job-123","status":"completed","dispensedGrams":39.5}"""
            }
        }
        assertEquals(39.5, FeederApi(1, 5).dispense(config, "test-job-123", 40), .001)
        assertEquals(1, posts.get()); assertEquals(2, gets.get())
    }
    @Test fun timeoutNeverRetriesPost() = runBlocking {
        val posts = AtomicInteger()
        responder = { _, method, _, _ ->
            if (method == "POST") posts.incrementAndGet()
            200 to """{"requestId":"test-job-123","status":"dispensing"}"""
        }
        assertTrue(runCatching { FeederApi(1, 2).dispense(config, "test-job-123", 40) }.isFailure)
        assertEquals(1, posts.get())
    }
    @Test fun mismatchedConfirmationIsNotAccepted() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"requestId":"wrong-job","status":"completed","dispensedGrams":40}""" }
        assertTrue(runCatching { FeederApi(1, 2).dispense(config, "test-job-123", 40) }.isFailure)
    }
    @Test fun explicitRejectionIsDistinguishable() = runBlocking {
        responder = { _, _, _, _ -> 409 to """{"rejected":true,"error":"Sin calibrar"}""" }
        assertTrue(runCatching { FeederApi().dispense(config, "test-job-123", 40) }.exceptionOrNull() is DeviceRejected)
    }
    @Test fun failedDeliveryNeverBecomesSuccess() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"requestId":"test-job-123","status":"failed","detail":"Atasco","dispensedGrams":3}""" }
        assertTrue(runCatching { FeederApi().dispense(config, "test-job-123", 40) }.isFailure)
    }
    @Test fun missingLevelDoesNotBecomeFakeMeasurement() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"apiVersion":3,"nextSequence":1,"retentionDays":30,"online":true,"foodLevelPercent":null,"lastDispensedGrams":null,"ready":false,"busy":false,"clockSynced":true,"deviceId":"ESP123","message":"Sin calibrar","simulation":false}""" }
        val status = FeederApi().status(config)
        assertNull(status.levelPercent); assertFalse(status.ready); assertEquals("Sin calibrar", status.message)
    }
    @Test fun autonomousEventsAndInterruptedOrdersAreImported() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"events":[{"requestId":"schedule-123","timestamp":1791676800000,"grams":40,"dispensedGrams":39.7,"source":"schedule","status":"completed","simulation":false},{"requestId":"manual-123","timestamp":1791676800001,"grams":20,"dispensedGrams":null,"source":"manual","status":"interrupted","detail":"Reinicio"}]}""" }
        val events = FeederApi().events(config)
        assertEquals("Horario ESP32", events[0].source); assertEquals("Completado", events[0].status)
        assertEquals("Sin confirmar", events[1].status); assertNull(events[1].deliveredGrams)
    }
    @Test fun emptyScheduleListReplacesDeviceProgramming() = runBlocking {
        responder = { path, method, body, _ ->
            assertEquals("PUT", method); assertEquals("/api/schedules", path)
            assertEquals("America/Lima", JSONObject(body).getString("timezone"))
            assertEquals(0, JSONObject(body).getJSONArray("schedules").length())
            200 to """{"saved":true,"count":0}"""
        }
        FeederApi().schedules(config, emptyList())
    }
    @Test fun unconfirmedScheduleSaveFails() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"saved":true,"count":0}""" }
        assertTrue(runCatching { FeederApi().schedules(config, listOf(MealSchedule("schedule-1", 8, 0, 40))) }.isFailure)
    }
    @Test fun unconfirmedStopFails() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"stopped":false}""" }
        assertTrue(runCatching { FeederApi().stop(config) }.isFailure)
    }
    @Test fun stopUsesDedicatedCommand() = runBlocking {
        responder = { path, method, _, _ ->
            assertEquals("/api/stop", path); assertEquals("POST", method)
            200 to """{"stopped":true}"""
        }
        FeederApi().stop(config)
    }
    @Test fun historyFollowsPages() = runBlocking {
        val paths = mutableListOf<String>()
        responder = { path, _, _, _ -> paths += path
            if(path == "/api/events") 200 to """{"events":[],"nextOffset":32}"""
            else 200 to """{"events":[],"nextOffset":null}"""
        }
        assertTrue(FeederApi().events(config).isEmpty())
        assertEquals(listOf("/api/events", "/api/events?offset=32"), paths)
    }
    @Test fun repeatedPageIsRejected() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"events":[],"nextOffset":0}""" }
        assertTrue(runCatching { FeederApi().events(config) }.isFailure)
    }
    @Test fun retentionIsConfirmedByDevice() = runBlocking {
        responder = { path, method, body, _ ->
            assertEquals("/api/preferences",path); assertEquals("PUT",method)
            assertEquals(7,JSONObject(body).getInt("retentionDays"))
            200 to """{"saved":true}"""
        }
        FeederApi().retention(config.copy(retentionDays=7))
    }
    @Test fun oldFirmwareIsRejected() = runBlocking {
        responder = { _, _, _, _ -> 200 to """{"apiVersion":2,"online":true}""" }
        assertTrue(runCatching { FeederApi().status(config) }.isFailure)
    }
}
