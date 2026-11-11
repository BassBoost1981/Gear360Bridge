package io.github.bassboost1981.gear360bridge.osc

import java.io.File

data class CameraInfo(val model: String, val serialNumber: String, val firmwareVersion: String)

data class CameraImage(val name: String, val uri: String, val size: Long, val dateTimeZone: String)

sealed interface CommandStatus {
    data object InProgress : CommandStatus
    data class Done(val fileUri: String) : CommandStatus
    data class Failed(val code: String) : CommandStatus
}

sealed class OscException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Die Kamera ist nicht erreichbar oder die Verbindung ist abgerissen. */
    class Unreachable(cause: Throwable) : OscException("unreachable", cause)

    /** Die Kamera hat geantwortet und das Kommando abgelehnt. */
    class CameraFailure(val code: String) : OscException(code)

    /** Die Antwort war keine gueltige OSC-Antwort. */
    class BadResponse(detail: String) : OscException(detail)
}

/** Open Spherical Camera API v1, so weit die Gear 360 (SM-C200) sie umsetzt. */
interface Osc {
    suspend fun info(): CameraInfo
    suspend fun batteryLevel(): Float
    suspend fun startSession(): String
    suspend fun closeSession(sessionId: String)
    suspend fun takePicture(sessionId: String): String
    suspend fun commandStatus(commandId: String): CommandStatus
    suspend fun listImages(entryCount: Int = 50): List<CameraImage>
    suspend fun downloadImage(fileUri: String, target: File): Long
}
