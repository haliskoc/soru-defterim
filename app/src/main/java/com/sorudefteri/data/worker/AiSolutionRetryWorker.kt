package com.sorudefteri.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sorudefteri.MainActivity
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.data.ai.AiRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * AI çözümünü çevrimdışıyken kuyruğa alır, internet geldiğinde otomatik dener.
 * Başarıda soruya aiExplanation yazar ve bildirim gösterir.
 * Hata rotation veya process-kill'de kaybolmaz — WorkManager persist eder.
 */
@HiltWorker
class AiSolutionRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_SORU_ID = "soruId"
        const val KEY_SEND_IMAGE = "sendImageToAi"
        const val KEY_EXTRACTED_TEXT = "extractedOcrText"
        const val WORK_TAG = "ai_solution_retry"
        const val CHANNEL_ID = "ai_retry_channel"
        const val CHANNEL_NAME = "AI Çözüm Yeniden Deneme"

        fun enqueue(
            context: Context,
            soruId: Long,
            sendImageToAi: Boolean,
            extractedText: String?
        ) {
            // WorkManager Data 10KB limit - truncate
            val safeText = extractedText?.take(9000)
            val data = Data.Builder()
                .putLong(KEY_SORU_ID, soruId)
                .putBoolean(KEY_SEND_IMAGE, sendImageToAi)
                .putString(KEY_EXTRACTED_TEXT, safeText)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AiSolutionRetryWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "ai_retry_$soruId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }

    override suspend fun doWork(): Result {
        val soruId = inputData.getLong(KEY_SORU_ID, -1L)
        if (soruId == -1L) return Result.failure()
        val sendImage = inputData.getBoolean(KEY_SEND_IMAGE, true)
        val extractedText = inputData.getString(KEY_EXTRACTED_TEXT)

        return try {
            // Zaten çözülmüşse tekrar deneme
            val soru = soruRepository.getSoruById(soruId).first()
            if (soru == null) return Result.failure()
            if (!soru.aiExplanation.isNullOrBlank()) {
                return Result.success()
            }

            val result = aiRepository.getAiSolution(soru, sendImage, extractedText)

            result.fold(
                onSuccess = { explanation ->
                    val updated = soru.copy(
                        aiExplanation = explanation,
                        updatedAt = System.currentTimeMillis()
                    )
                    soruRepository.soruGuncelle(updated)
                    sendSuccessNotification(soru.topic)
                    Result.success()
                },
                onFailure = { throwable ->
                    // 404 MODEL_UNAVAILABLE asla retry edilmez — önce kontrol
                    if (throwable is AiException && throwable.errorType == AiErrorType.MODEL_UNAVAILABLE && throwable.message?.contains("404") == true) {
                        sendFailureNotification(throwable.localizedMessage ?: "Model bulunamadı")
                        return Result.failure()
                    }
                    val isRetryable = when {
                        throwable is AiException -> when (throwable.errorType) {
                            AiErrorType.NETWORK_ERROR,
                            AiErrorType.NETWORK_TIMEOUT,
                            AiErrorType.MODEL_UNAVAILABLE -> true
                            AiErrorType.INVALID_API_KEY,
                            AiErrorType.PARSING_FAILED,
                            AiErrorType.QUOTA_EXCEEDED,
                            AiErrorType.UNKNOWN -> false
                        }
                        throwable is InterruptedIOException -> true
                        else -> false
                    }

                    if (isRetryable && runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        if (isRetryable) {
                            // Son deneme de başarısız — kuyrukta kalsın ama kullanıcıya bildir
                            sendFailureNotification(throwable.localizedMessage ?: "AI çözüm alınamadı")
                        }
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            // Sadece network/timeout retry, diğerleri failure
            val isRetryable = e is InterruptedIOException || e is java.io.IOException || e is android.database.sqlite.SQLiteException
            if (isRetryable && runAttemptCount < 3) return Result.retry()
            return Result.failure()
        }
    }

    private fun sendSuccessNotification(topic: String) {
        createChannel()
        if (!hasNotificationPermission()) return
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, soruIdHash(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.sorudefteri.R.mipmap.ic_launcher)
            .setContentTitle("AI Çözümü Hazır ✅")
            .setContentText("\"$topic\" için yapay zeka çözümü hazır. Dokunarak görüntüle.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$topic\" için yapay zeka çözümü hazır."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(soruIdHash(), notification)
    }

    private fun sendFailureNotification(reason: String) {
        createChannel()
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.sorudefteri.R.mipmap.ic_launcher)
            .setContentTitle("AI Çözümü Başarısız")
            .setContentText(reason.take(100))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(soruIdHash() + 1, notification)
    }

    private fun soruIdHash(): Int = inputData.getLong(KEY_SORU_ID, 0).hashCode()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Çevrimdışı kuyruğa alınan AI çözümleri hazır olduğunda bildirim"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
