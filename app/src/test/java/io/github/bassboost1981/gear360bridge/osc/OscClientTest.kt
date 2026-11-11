package io.github.bassboost1981.gear360bridge.osc

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Antworten wortgleich von der echten Kamera (Firmware C200GLU0API1), siehe Entwurf. */
class OscClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var client: OscClient

    private val info = """{ "manufacturer": "Samsung Electronics", "model": "GEAR 360", "serialNumber": "TESTSERIAL01", "firmwareVersion": "C200GLU0API1", "supportUrl": "www.samsung.com", "endpoints": { "httpPort": 80, "httpUpdatePort": 80 }, "gps": false, "gyro": false, "uptime": 50, "api": [ "\/osc\/info", "\/osc\/state", "\/osc\/checkForUpdates", "\/osc\/commands\/execute", "\/osc\/commands\/status" ] }"""
    private val state = """{ "fingerprint": "ABCD1234", "state": { "sessionId": "SID_0000", "batteryLevel": 0.700000 } }"""
    private val startSession = """{ "name": "camera.startSession", "state": "done", "results": { "sessionId": "SID_0216", "timeout": 300 } }"""
    private val takePicture = """{ "name": "camera.takePicture", "state": "inProgress", "id": "1", "progress": { "completion": 0.000000 } }"""
    private val statusDone = """{ "name": "camera.takePicture", "state": "done", "results": { "fileUri": "\/mnt\/mmc\/DCIM\/100PHOTO\/SAM_0005.JPG" } }"""
    private val listImages = """{ "name": "camera.listImages", "state": "done", "results": { "entries": [ { "name": "SAM_0005.JPG", "uri": "\/mnt\/mmc\/DCIM\/100PHOTO\/SAM_0005.JPG", "size": 8298241, "dateTimeZone": "2016:01:01 00:14:50+0100", "width": 7200, "heigh": 3600 } ], "totalEntries": 1 } }"""
    private val error = """{"name":"camera.takePicture","state":"error","error":{"code":"invalidSessionId","message":"x"}}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OscClient(OkHttpClient(), server.url("/"))
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun info_parsesModelAndFirmware() = runBlocking {
        server.enqueue(json(info))
        val result = client.info()
        assertEquals(CameraInfo("GEAR 360", "TESTSERIAL01", "C200GLU0API1"), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/osc/info", request.path)
    }

    @Test fun batteryLevel_readsState() = runBlocking {
        server.enqueue(json(state))
        assertEquals(0.7f, client.batteryLevel(), 0.0001f)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/osc/state", request.path)
    }

    @Test fun startSession_returnsId() = runBlocking {
        server.enqueue(json(startSession))
        assertEquals("SID_0216", client.startSession())
        assertTrue(server.takeRequest().body.readUtf8().contains("camera.startSession"))
    }

    @Test fun closeSession_sendsSessionId() = runBlocking {
        server.enqueue(json("""{ "name": "camera.closeSession", "state": "done" }"""))
        client.closeSession("SID_0216")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("camera.closeSession"))
        assertTrue(body.contains("SID_0216"))
    }

    @Test fun takePicture_sendsSessionAndReturnsId() = runBlocking {
        server.enqueue(json(takePicture))
        assertEquals("1", client.takePicture("SID_0216"))
        val request = server.takeRequest()
        assertEquals("/osc/commands/execute", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("camera.takePicture"))
        assertTrue(body.contains("SID_0216"))
    }

    @Test fun commandStatus_inProgress_and_done() = runBlocking {
        server.enqueue(json(takePicture))
        server.enqueue(json(statusDone))
        assertEquals(CommandStatus.InProgress, client.commandStatus("1"))
        assertEquals(CommandStatus.Done("/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG"), client.commandStatus("1"))
        val request = server.takeRequest()
        assertEquals("/osc/commands/status", request.path)
        assertTrue(request.body.readUtf8().replace(" ", "").contains("\"id\":\"1\""))
    }

    @Test fun commandStatus_error_isFailed() = runBlocking {
        server.enqueue(json(error))
        assertEquals(CommandStatus.Failed("invalidSessionId"), client.commandStatus("1"))
    }

    @Test fun listImages_toleratesHeighTypo() = runBlocking {
        server.enqueue(json(listImages))
        val images = client.listImages()
        assertEquals(
            listOf(CameraImage("SAM_0005.JPG", "/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG", 8298241L, "2016:01:01 00:14:50+0100")),
            images,
        )
        assertTrue(server.takeRequest().body.readUtf8().contains("camera.listImages"))
    }

    @Test fun downloadImage_writesFile() = runBlocking {
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))
        val target = File(tmp.root, "a.jpg")
        assertEquals(5000L, client.downloadImage("/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG", target))
        assertTrue(bytes.contentEquals(target.readBytes()))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("camera.getImage"))
        assertTrue(body.contains("SAM_0005.JPG"))
    }

    @Test fun downloadImage_truncated_throwsAndDeletesFile() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "image/jpeg")
                .setBody(Buffer().write(ByteArray(5000)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val target = File(tmp.root, "b.jpg")
        try {
            client.downloadImage("/x.jpg", target)
            fail("expected OscException.Unreachable")
        } catch (e: OscException.Unreachable) {
            assertFalse(target.exists())
        }
    }

    @Test fun downloadImage_jsonErrorInsteadOfImage_throwsCameraFailure() = runBlocking {
        server.enqueue(json("""{"name":"camera.getImage","state":"error","error":{"code":"invalidParameterValue","message":"x"}}"""))
        val target = File(tmp.root, "c.jpg")
        try {
            client.downloadImage("/x.jpg", target)
            fail("expected OscException.CameraFailure")
        } catch (e: OscException.CameraFailure) {
            assertEquals("invalidParameterValue", e.code)
            assertFalse(target.exists())
        }
    }

    @Test fun errorState_throwsCameraFailure() = runBlocking {
        server.enqueue(json(error))
        try {
            client.takePicture("SID_0000")
            fail("expected OscException.CameraFailure")
        } catch (e: OscException.CameraFailure) {
            assertEquals("invalidSessionId", e.code)
        }
    }

    @Test fun http500_throwsBadResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        try {
            client.info()
            fail("expected OscException.BadResponse")
        } catch (e: OscException.BadResponse) {
        }
    }

    @Test fun garbage_throwsBadResponse() = runBlocking {
        server.enqueue(json("<html>not json</html>"))
        try {
            client.startSession()
            fail("expected OscException.BadResponse")
        } catch (e: OscException.BadResponse) {
        }
    }

    @Test fun serverDown_throwsUnreachable() = runBlocking {
        server.shutdown()
        try {
            client.info()
            fail("expected OscException.Unreachable")
        } catch (e: OscException.Unreachable) {
        }
    }
}
