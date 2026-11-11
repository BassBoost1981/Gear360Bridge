package io.github.bassboost1981.gear360bridge.osc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OscClient(
    http: OkHttpClient,
    private val baseUrl: HttpUrl = "http://192.168.107.1/".toHttpUrl(),
) : Osc {
    private val http = http.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun info(): CameraInfo {
        val o = parse(call(Request.Builder().url(url("osc/info")).get().build()))
        return read {
            CameraInfo(o.string("model"), o.string("serialNumber"), o.string("firmwareVersion"))
        }
    }

    override suspend fun batteryLevel(): Float {
        val o = parse(call(post("osc/state", "{}")))
        return read { o.getValue("state").jsonObject.getValue("batteryLevel").jsonPrimitive.float }
    }

    override suspend fun startSession(): String {
        val o = execute("camera.startSession", buildJsonObject { })
        return read { o.results().string("sessionId") }
    }

    override suspend fun closeSession(sessionId: String) {
        execute("camera.closeSession", buildJsonObject { put("sessionId", sessionId) })
    }

    override suspend fun takePicture(sessionId: String): String {
        val o = execute("camera.takePicture", buildJsonObject { put("sessionId", sessionId) })
        return read { o.string("id") }
    }

    override suspend fun commandStatus(commandId: String): CommandStatus {
        val body = buildJsonObject { put("id", commandId) }.toString()
        val o = parse(call(post("osc/commands/status", body)))
        return read {
            when (o.string("state")) {
                "inProgress" -> CommandStatus.InProgress
                "done" -> CommandStatus.Done(o.results().string("fileUri"))
                else -> CommandStatus.Failed(o.errorCode())
            }
        }
    }

    override suspend fun listImages(entryCount: Int): List<CameraImage> {
        val o = execute("camera.listImages", buildJsonObject {
            put("entryCount", entryCount)
            put("maxSize", 160)
            put("includeThumb", false)
        })
        return read {
            o.results().getValue("entries").jsonArray.map {
                val e = it.jsonObject
                CameraImage(e.string("name"), e.string("uri"), e.getValue("size").jsonPrimitive.long, e.string("dateTimeZone"))
            }
        }
    }

    override suspend fun downloadImage(fileUri: String, target: File): Long = withContext(Dispatchers.IO) {
        val body = commandBody("camera.getImage", buildJsonObject { put("fileUri", fileUri) })
        val response = open(post("osc/commands/execute", body))
        response.use {
            if (it.header("Content-Type").orEmpty().contains("json")) {
                val o = parseText(it.body?.string().orEmpty())
                throw OscException.CameraFailure(o.errorCode())
            }
            try {
                target.outputStream().use { out -> it.body!!.byteStream().copyTo(out) }
            } catch (e: IOException) {
                target.delete()
                throw OscException.Unreachable(e)
            }
            target.length()
        }
    }

    private suspend fun execute(name: String, parameters: JsonObject): JsonObject {
        val o = parse(call(post("osc/commands/execute", commandBody(name, parameters))))
        if (o["state"]?.jsonPrimitive?.content == "error") throw OscException.CameraFailure(o.errorCode())
        return o
    }

    private fun commandBody(name: String, parameters: JsonObject): String =
        buildJsonObject {
            put("name", name)
            put("parameters", parameters)
        }.toString()

    private fun url(path: String): HttpUrl = baseUrl.resolve(path)!!

    private fun post(path: String, body: String): Request =
        Request.Builder().url(url(path)).post(body.toRequestBody(jsonType)).build()

    /** Fuehrt die Anfrage aus und gibt den Antworttext zurueck. */
    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        open(request).use {
            try {
                it.body?.string().orEmpty()
            } catch (e: IOException) {
                throw OscException.Unreachable(e)
            }
        }
    }

    private fun open(request: Request): Response {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw OscException.Unreachable(e)
        }
        if (!response.isSuccessful) {
            response.close()
            throw OscException.BadResponse("HTTP ${response.code}")
        }
        return response
    }

    private fun parse(text: String): JsonObject = parseText(text)

    private fun parseText(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw OscException.BadResponse("no json")
    }

    /** Fehlende oder falsch getypte Felder werden zu BadResponse. */
    private inline fun <T> read(block: () -> T): T = try {
        block()
    } catch (e: OscException) {
        throw e
    } catch (e: Exception) {
        throw OscException.BadResponse(e.message ?: "unexpected response")
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.results(): JsonObject = getValue("results").jsonObject
    private fun JsonObject.errorCode(): String =
        this["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content ?: "unknown"
}
