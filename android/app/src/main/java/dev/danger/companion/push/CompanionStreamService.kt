package dev.danger.companion.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.danger.companion.CompanionState
import dev.danger.companion.Emotion
import dev.danger.companion.MainActivity
import dev.danger.companion.Settings
import dev.danger.companion.SettingsStore
import dev.danger.companion.sound.SoundPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CompanionStreamService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    @Volatile
    private var connection: HttpURLConnection? = null

    @Volatile
    private var reconnectRequested = false

    private var streamJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                if (running) {
                    reconnectRequested = true
                    connection?.disconnect()
                } else {
                    reconnectRequested = false
                    running = true
                    streamJob = scope.launch { listenLoop() }
                }
            }
            else -> if (!running) {
                running = true
                streamJob = scope.launch { listenLoop() }
            }
        }

        createChannel()
        startForegroundCompat(buildNotification("Companion attivo", "Avvio in ascolto…"))
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        connection?.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun listenLoop() {
        var backoffMs = INITIAL_BACKOFF_MS
        while (running && scope.isActive) {
            val settings = SettingsStore.read(this)
            updateNotification(settings.topic)
            try {
                streamOnce(settings)
                backoffMs = INITIAL_BACKOFF_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "stream error: ${e.message}")
            }

            if (!running) break
            if (reconnectRequested) {
                reconnectRequested = false
                continue
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun streamOnce(settings: Settings) {
        val base = settings.relayUrl.trim().trimEnd('/')
        val streamUrl = URL("$base/${settings.topic.trim()}/json")
        val conn = (streamUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 0
            setRequestProperty("Accept", "application/json")
            if (settings.token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${settings.token.trim()}")
            }
        }
        connection = conn
        try {
            val code = conn.responseCode
            Log.i(TAG, "GET $streamUrl -> HTTP $code")
            if (code !in 200..299) return

            val reader = conn.inputStream.bufferedReader()
            while (running && !reconnectRequested) {
                val line = reader.readLine() ?: break
                handleLine(line)
            }
        } finally {
            connection = null
            conn.disconnect()
        }
    }

    private suspend fun handleLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        try {
            val event = JSONObject(trimmed)
            if (event.optString("event") != "message") return
            if (event.isNull("message")) return
            handlePayload(event.optString("message"))
        } catch (e: Exception) {
            Log.w(TAG, "ignoring malformed line: ${e.message}")
        }
    }

    private suspend fun handlePayload(payload: String) {
        val root = JSONObject(payload)
        val instance = root.optJSONObject("instance")
        val emotion = Emotion.fromString(if (root.isNull("emotion")) null else root.optString("emotion"))
        val text = if (root.isNull("text")) null else root.optString("text")
        val instanceId = instance?.optString("id")?.takeIf { it.isNotBlank() } ?: "default"
        val label = instance?.optString("label")?.takeIf { it.isNotBlank() } ?: "Companion"
        val color = CompanionState.parseColor(instance?.optString("color"))
        val ttlMs = if (root.has("ttl_ms") && !root.isNull("ttl_ms")) root.optLong("ttl_ms") else null
        val sound = root.optString("sound", "none")

        Log.i(TAG, "applying emotion=$emotion instance=$instanceId text=$text")
        CompanionController.applyEmotion(
            context = this,
            emotion = emotion,
            text = text,
            instanceId = instanceId,
            instanceLabel = label,
            color = color,
            ttlMs = ttlMs,
        )
        withContext(Dispatchers.Main) {
            SoundPlayer.play(this@CompanionStreamService, sound, emotion)
        }
    }

    private fun stopListening() {
        running = false
        connection?.disconnect()
        connection = null
        streamJob?.cancel()
        streamJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Companion stream",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ascolto eventi dal relay ntfy"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(topic: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, buildNotification("Companion attivo", "In ascolto su $topic"))
        } catch (e: Exception) {
            Log.w(TAG, "notification update failed: ${e.message}")
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "companion_stream"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "dev.danger.companion.SERVICE_STOP"
        const val ACTION_RESTART = "dev.danger.companion.SERVICE_RESTART"

        private const val TAG = "CompanionStream"
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 15_000

        fun start(context: Context) {
            val intent = Intent(context, CompanionStreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CompanionStreamService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun restart(context: Context) {
            val intent = Intent(context, CompanionStreamService::class.java).setAction(ACTION_RESTART)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
