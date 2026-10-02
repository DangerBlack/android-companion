package dev.danger.companion.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.danger.companion.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ServiceControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_START -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        SettingsStore.setEnabled(appContext, true)
                        CompanionStreamService.start(appContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "start failed: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_STOP -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        SettingsStore.setEnabled(appContext, false)
                        CompanionStreamService.stop(appContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "stop failed: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "dev.danger.companion.START"
        const val ACTION_STOP = "dev.danger.companion.STOP"
        private const val TAG = "ServiceControl"
    }
}
