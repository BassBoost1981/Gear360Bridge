package io.github.bassboost1981.gear360bridge.capture

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.github.bassboost1981.gear360bridge.osc.CameraInfo
import io.github.bassboost1981.gear360bridge.osc.Osc
import io.github.bassboost1981.gear360bridge.osc.OscClient
import io.github.bassboost1981.gear360bridge.osc.OscException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import kotlin.math.roundToInt

data class LinkState(val connected: Boolean, val batteryPercent: Int?)

/**
 * Haelt die Verbindung zur Kamera. Das Kamera-WLAN hat kein Internet, deshalb wuerde Android
 * Anfragen sonst ueber Mobilfunk schicken; hier werden sie ausdruecklich an das WLAN gebunden.
 */
class CameraLink(context: Context, private val scope: CoroutineScope) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var wifi: Network? = null

    private val _state = MutableStateFlow(LinkState(connected = false, batteryPercent = null))
    val state: StateFlow<LinkState> = _state.asStateFlow()

    val osc: Osc = OscClient(OkHttpClient.Builder().socketFactory(WifiSocketFactory()).build())

    /** Wird einmal aufgerufen, wenn die Kamera neu erreichbar geworden ist. */
    var onConnected: suspend () -> Unit = {}

    private var job: Job? = null

    /** Weckt die Abfrage sofort, wenn das WLAN da ist, statt die naechste Runde abzuwarten. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifi = network
            wake.trySend(Unit)
        }

        override fun onLost(network: Network) {
            if (wifi == network) wifi = null
            wake.trySend(Unit)
        }
    }

    fun start() {
        if (job != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.requestNetwork(request, callback)
        job = scope.launch {
            // Android meldet das WLAN kurz nach der Anfrage. Erst dann hat die erste Abfrage Sinn;
            // sonst erschiene bei jeder Rueckkehr in die App faelschlich "nicht verbunden".
            withTimeoutOrNull(FIRST_NETWORK_WAIT_MS) { wake.receive() }
            while (isActive) {
                poll()
                withTimeoutOrNull(POLL_INTERVAL_MS) { wake.receive() }
            }
        }
    }

    /**
     * Beendet die Ueberwachung. Der zuletzt bekannte Zustand bleibt stehen, bis die naechste
     * Abfrage ihn bestaetigt oder korrigiert.
     */
    fun stop() {
        job?.cancel()
        job = null
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        wifi = null
    }

    private suspend fun poll() {
        val wasConnected = _state.value.connected
        val next = try {
            if (wifi != null && isGear360(osc.info())) LinkState(true, toPercent(osc.batteryLevel())) else LinkState(false, null)
        } catch (e: OscException) {
            LinkState(false, null)
        }
        _state.value = next
        if (next.connected && !wasConnected) {
            // Das Nachholen darf die Verbindungsueberwachung nie mitreissen.
            try {
                onConnected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    /** Oeffnet Verbindungen ueber das WLAN, auch wenn Android Mobilfunk bevorzugen wuerde. */
    private inner class WifiSocketFactory : SocketFactory() {
        private fun delegate(): SocketFactory = wifi?.socketFactory ?: getDefault()
        override fun createSocket(): Socket = delegate().createSocket()
        override fun createSocket(host: String, port: Int): Socket = delegate().createSocket(host, port)
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            delegate().createSocket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress, port: Int): Socket = delegate().createSocket(host, port)
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            delegate().createSocket(address, port, localAddress, localPort)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val FIRST_NETWORK_WAIT_MS = 1_500L

        fun isGear360(info: CameraInfo): Boolean = info.model.trim().equals("GEAR 360", ignoreCase = true)

        fun toPercent(level: Float): Int = (level * 100).roundToInt().coerceIn(0, 100)
    }
}
