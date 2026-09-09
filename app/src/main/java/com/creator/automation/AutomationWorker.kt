package com.creator.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters

class AutomationWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {

        val url = inputData.getString("url")

        if (url.isNullOrBlank()) {
            return Result.failure()
        }

        return try {

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            applicationContext.startActivity(intent)

            Result.success()

        } catch (exception: Exception) {

            exception.printStackTrace()

            Result.retry()
        }
    }
}
