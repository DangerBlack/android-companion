package dev.danger.companion.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.danger.companion.SettingsStore
import dev.danger.companion.push.CompanionStreamService
import dev.danger.companion.push.CompanionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "BOOT_COMPLETED received")
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CompanionWorker.enqueue(appContext)
                val settings = SettingsStore.read(appContext)
                if (settings.enabled) {
                    try {
                        CompanionStreamService.start(appContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "dataSync foreground service start forbidden at boot: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "boot handling failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
