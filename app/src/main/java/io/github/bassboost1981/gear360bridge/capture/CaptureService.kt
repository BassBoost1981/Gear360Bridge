package io.github.bassboost1981.gear360bridge.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import io.github.bassboost1981.gear360bridge.Gear360BridgeApp
import io.github.bassboost1981.gear360bridge.R
import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Haelt die App als Vordergrund-Dienst am Leben, waehrend eine Aufnahme laeuft, damit sie
 * weitergeht, wenn das Display ausgeht oder die App in den Hintergrund geraet.
 *
 * Die Aufnahme selbst laeuft im Geltungsbereich der App, nicht des Dienstes: Ein zweiter Start
 * oder ein spaetes "Abbrechen" kann sie deshalb nicht mitten im Uebertragen abreissen.
 */
class CaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as Gear360BridgeApp).container
        val coordinator = container.coordinator
        val labelJson = intent?.getStringExtra(EXTRA_LABEL)
        if (intent?.action == ACTION_CANCEL || labelJson == null) {
            coordinator.cancelCountdown()
            // Laeuft eine Aufnahme, beendet erst ihr Abschluss den Dienst.
            if (!coordinator.isCapturing) stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        // Doppelter Druck auf den Ausloeser: die laufende Aufnahme haelt und beendet den Dienst.
        if (coordinator.isCapturing) return START_NOT_STICKY

        val label = Json.decodeFromString<PhotoLabel>(labelJson)
        val delaySeconds = intent.getIntExtra(EXTRA_DELAY, 0)
        container.scope.launch {
            val wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gear360bridge:capture")
                .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
            // drop(1): der Zustand vor dieser Aufnahme soll nicht noch einmal ertoenen.
            val sounds = launch { coordinator.state.drop(1).collect { beep(tone, it) } }
            try {
                coordinator.capture(label, delaySeconds)
            } finally {
                withContext(NonCancellable) {
                    // Den Schlusston ausklingen lassen, bevor der Tongeber freigegeben wird.
                    delay(TONE_TAIL_MS)
                    sounds.cancel()
                    tone?.release()
                    if (wakeLock.isHeld) wakeLock.release()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Hoerbare Rueckmeldung fuer jemanden, der vor der Tuer steht und das Handy nicht ansieht. */
    private fun beep(tone: ToneGenerator?, state: CaptureState) {
        val t = tone ?: return
        when (state) {
            is CaptureState.Countdown -> t.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            CaptureState.CameraWorking -> t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
            is CaptureState.Saved -> t.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            is CaptureState.Failed -> t.startTone(ToneGenerator.TONE_PROP_NACK, 600)
            else -> Unit
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_capture), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_capturing))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_CANCEL = "io.github.bassboost1981.gear360bridge.CANCEL"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_DELAY = "delay"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 240_000L
        private const val TONE_TAIL_MS = 700L

        fun start(context: Context, label: PhotoLabel, delaySeconds: Int) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_LABEL, Json.encodeToString(label))
                .putExtra(EXTRA_DELAY, delaySeconds)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
