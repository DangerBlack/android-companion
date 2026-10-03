package dev.danger.companion.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import dev.danger.companion.CompanionStore
import dev.danger.companion.widget.FaceWidget
import java.util.concurrent.TimeUnit

class CompanionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val state = CompanionStore.current(applicationContext)
            CompanionStore.save(applicationContext, state)
            FaceWidget().updateAll(applicationContext)
            Log.i(TAG, "re-applied persisted state: ${state.emotion} color=#${"%06X".format(0xFFFFFF and state.colorArgb)}")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CompanionWorker"
        const val EXPIRY_WORK_NAME = "companion-expiry"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CompanionWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        fun scheduleExpiry(context: Context, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<CompanionWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(EXPIRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelExpiry(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(EXPIRY_WORK_NAME)
        }
    }
}
