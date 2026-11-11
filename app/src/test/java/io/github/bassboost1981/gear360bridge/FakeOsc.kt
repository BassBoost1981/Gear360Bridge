package io.github.bassboost1981.gear360bridge

import io.github.bassboost1981.gear360bridge.osc.CameraImage
import io.github.bassboost1981.gear360bridge.osc.CameraInfo
import io.github.bassboost1981.gear360bridge.osc.CommandStatus
import io.github.bassboost1981.gear360bridge.osc.Osc
import io.github.bassboost1981.gear360bridge.osc.OscException
import java.io.File
import java.io.IOException

/** Kamera-Attrappe mit einstellbaren Antworten und Aufrufzaehlern. */
class FakeOsc : Osc {
    var startSessionCalls = 0
    var takePictureCalls = 0
    var downloadCalls = 0
    val downloadedUris = mutableListOf<String>()
    val closedSessions = mutableListOf<String>()

    /** Wenn gesetzt, wartet listImages, bis das Tor geoeffnet wird. */
    var listGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** Wenn gesetzt, scheitert jeder Aufruf wie bei abgerissener Verbindung. */
    var unreachable = false

    /** Fehler, die takePicture der Reihe nach wirft, bevor es gelingt. */
    val takePictureErrors = ArrayDeque<OscException>()

    /** Antworten auf commandStatus der Reihe nach, danach immer [defaultStatus]. */
    val statuses = ArrayDeque<CommandStatus>()
    var defaultStatus: CommandStatus = CommandStatus.Done(OWN_URI)

    val images = mutableListOf(CameraImage("SAM_0005.JPG", OWN_URI, 5000, OWN_DATE))
    var downloadError: OscException? = null

    /** Tatsaechlich gelieferte Bytezahl, falls sie von der gemeldeten Groesse abweichen soll. */
    var deliveredBytes: Long? = null

    private fun check() {
        if (unreachable) throw OscException.Unreachable(IOException("down"))
    }

    override suspend fun info(): CameraInfo {
        check()
        return CameraInfo("GEAR 360", "TESTSERIAL01", "C200GLU0API1")
    }

    override suspend fun batteryLevel(): Float {
        check()
        return 0.7f
    }

    override suspend fun startSession(): String {
        check()
        startSessionCalls++
        return "SID_$startSessionCalls"
    }

    override suspend fun takePicture(sessionId: String): String {
        check()
        takePictureCalls++
        takePictureErrors.removeFirstOrNull()?.let { throw it }
        return "1"
    }

    override suspend fun commandStatus(commandId: String): CommandStatus {
        check()
        return statuses.removeFirstOrNull() ?: defaultStatus
    }

    override suspend fun closeSession(sessionId: String) {
        check()
        closedSessions += sessionId
    }

    override suspend fun listImages(entryCount: Int): List<CameraImage> {
        check()
        listGate?.await()
        return images.toList()
    }

    override suspend fun downloadImage(fileUri: String, target: File): Long {
        check()
        downloadCalls++
        downloadedUris += fileUri
        downloadError?.let { throw it }
        val size = deliveredBytes ?: images.first { it.uri == fileUri }.size
        target.writeBytes(ByteArray(size.toInt()))
        return size
    }

    companion object {
        const val OWN_URI = "/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG"
        const val OWN_DATE = "2016:01:01 00:14:50+0100"
    }
}
