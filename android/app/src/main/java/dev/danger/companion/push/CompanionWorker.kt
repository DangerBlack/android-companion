package dev.danger.companion.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import dev.danger.companion.CompanionStore
import dev.danger.companion.widget.FaceWidget

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

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CompanionWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
