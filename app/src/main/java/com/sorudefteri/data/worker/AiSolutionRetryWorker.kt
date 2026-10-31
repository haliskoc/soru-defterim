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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.sorudefteri.MainActivity
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.data.ai.AiRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        const val KEY_FORCE_SOLVE = "forceSolve"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        const val WORK_TAG = "ai_solution_retry"
        const val TAG_MANUAL_SOLVE = "ai_manual_solve"
        const val CHANNEL_ID = "ai_retry_channel"
        const val CHANNEL_NAME = "AI Çözüm Yeniden Deneme"

        fun tagForSoru(soruId: Long): String = "soru_$soruId"

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
                .addTag(tagForSoru(soruId))
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "ai_retry_$soruId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        /**
         * Kullanıcı soru detay ekranındayken veya manuel olarak AI çözümü istediğinde çağrılır.
         * Kullanıcı ekrandan çıksa veya uygulama kapansa dahi arka planda güvenle tamamlanır.
         */
        fun enqueueManualSolve(
            context: Context,
            soruId: Long,
            sendImageToAi: Boolean = true,
            extractedText: String? = null
        ) {
            val safeText = extractedText?.take(9000)
            val data = Data.Builder()
                .putLong(KEY_SORU_ID, soruId)
                .putBoolean(KEY_SEND_IMAGE, sendImageToAi)
                .putString(KEY_EXTRACTED_TEXT, safeText)
                .putBoolean(KEY_FORCE_SOLVE, true)
                .build()

            val request = OneTimeWorkRequestBuilder<AiSolutionRetryWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .addTag(TAG_MANUAL_SOLVE)
                .addTag(tagForSoru(soruId))
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "ai_solve_$soruId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        /**
         * İlgili soruya ait devam eden veya en güncel WorkInfo akışını döner.
         */
        fun observeSoruWork(context: Context, soruId: Long): Flow<WorkInfo?> =
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(tagForSoru(soruId))
                .map { list ->
                    list.firstOrNull { !it.state.isFinished } ?: list.lastOrNull()
                }
                .distinctUntilChanged()

        /**
         * İlgili soru için arka planda aktif (ENQUEUED veya RUNNING) bir iş olup olmadığını döner.
         */
        fun isSoruWorkActive(context: Context, soruId: Long): Flow<Boolean> =
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(tagForSoru(soruId))
                .map { list ->
                    list.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                }
                .distinctUntilChanged()

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }

        const val KEY_BATCH_INDEX = "batchIndex"
        const val KEY_BATCH_TOTAL = "batchTotal"
        const val KEY_BATCH_ID = "batchId"
        const val KEY_IS_AUTO = "isAutoSolve"
        const val KEY_OUT_FAILED = "failed"
        const val KEY_OUT_SKIPPED = "skipped"
        const val TAG_AUTO_SOLVE = "ai_auto_solve"
        const val TAG_BATCH = "ai_batch_solve"
        const val UNIQUE_BATCH = "ai_batch_solve"
        private const val TAG_BATCH_ID_PREFIX = "ai_batch_id_"
        private const val BATCH_PROGRESS_NOTIFICATION_ID = 0x5A1B
        private const val BATCH_SUMMARY_NOTIFICATION_ID = 0x5A1C

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Kaydet anında otomatik çözüm. Aynı soru için zaten kuyrukta iş varsa yenisini eklemez. */
        fun enqueueAutoSolve(
            context: Context,
            soruId: Long,
            sendImageToAi: Boolean = true,
            extractedText: String? = null
        ) {
            val data = Data.Builder()
                .putLong(KEY_SORU_ID, soruId)
                .putBoolean(KEY_SEND_IMAGE, sendImageToAi)
                .putString(KEY_EXTRACTED_TEXT, extractedText?.take(9000))
                .putBoolean(KEY_IS_AUTO, true)
                .build()
            val request = OneTimeWorkRequestBuilder<AiSolutionRetryWorker>()
                .setInputData(data)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .addTag(TAG_AUTO_SOLVE)
                .addTag(tagForSoru(soruId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("ai_auto_$soruId", ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Toplu çözüm: istekleri sıralı zincir halinde kuyruğa ekler. İlk istek hariç her birine
         * rate-limit için [delaySeconds] gecikme verilir. Boş/çözülmüş soruları worker atlar.
         * @return kuyruğa eklenen istek sayısı
         */
        fun enqueueBatch(context: Context, soruIds: List<Long>, delaySeconds: Int): Int {
            val ids = soruIds.filter { it > 0 }.distinct()
            if (ids.isEmpty()) return 0
            val delay = delaySeconds.coerceIn(0, 60).toLong()
            val batchId = System.currentTimeMillis()
            val requests = ids.mapIndexed { index, id ->
                val data = Data.Builder()
                    .putLong(KEY_SORU_ID, id)
                    .putBoolean(KEY_SEND_IMAGE, true)
                    .putInt(KEY_BATCH_INDEX, index)
                    .putInt(KEY_BATCH_TOTAL, ids.size)
                    .putLong(KEY_BATCH_ID, batchId)
                    .build()
                val builder = OneTimeWorkRequestBuilder<AiSolutionRetryWorker>()
                    .setInputData(data)
                    .setConstraints(networkConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .addTag(TAG_BATCH)
                    .addTag("$TAG_BATCH_ID_PREFIX$batchId")
                    .addTag(tagForSoru(id))
                if (index > 0 && delay > 0) builder.setInitialDelay(delay, TimeUnit.SECONDS)
                builder.build()
            }
            var continuation = WorkManager.getInstance(context)
                .beginUniqueWork(UNIQUE_BATCH, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
            for (req in requests.drop(1)) continuation = continuation.then(req)
            continuation.enqueue()
            return requests.size
        }

        fun cancelBatch(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_BATCH)
            wm.cancelAllWorkByTag(TAG_BATCH)
            NotificationManagerCompat.from(context).cancel(BATCH_PROGRESS_NOTIFICATION_ID)
        }

        /** En güncel toplu çözüm partisinin (ve hâlâ süren eski partilerin) ilerlemesi. */
        fun observeBatchProgress(context: Context): Flow<BatchSolveProgress> =
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(TAG_BATCH)
                .map { computeBatchProgress(it) }
                .distinctUntilChanged()

        internal fun computeBatchProgress(infos: List<WorkInfo>): BatchSolveProgress {
            if (infos.isEmpty()) return BatchSolveProgress(0, 0, 0, false)
            fun batchIdOf(info: WorkInfo): Long =
                info.tags.firstOrNull { it.startsWith(TAG_BATCH_ID_PREFIX) }
                    ?.removePrefix(TAG_BATCH_ID_PREFIX)?.toLongOrNull() ?: 0L
            val groups = infos.groupBy { batchIdOf(it) }
            val latest = groups.keys.max()
            val relevant = groups.filter { (id, list) ->
                id == latest || list.any { !it.state.isFinished }
            }.values.flatten()
            var completed = 0
            var failed = 0
            for (info in relevant) {
                when (info.state) {
                    WorkInfo.State.SUCCEEDED ->
                        if (info.outputData.getBoolean(KEY_OUT_FAILED, false)) failed++ else completed++
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> failed++
                    else -> Unit
                }
            }
            return BatchSolveProgress(
                total = relevant.size,
                completed = completed,
                failed = failed,
                running = relevant.any { !it.state.isFinished }
            )
        }
    }

    private val isBatch: Boolean get() = inputData.getInt(KEY_BATCH_TOTAL, 0) > 0
    private val batchIndex: Int get() = inputData.getInt(KEY_BATCH_INDEX, 0)
    private val batchTotal: Int get() = inputData.getInt(KEY_BATCH_TOTAL, 0)

    override suspend fun doWork(): Result {
        if (!isBatch) return doSingleWork()
        val outcome = doSingleWork()
        // Batch modunda zincir kopmasın: kalıcı hata -> success(failed=true)
        val failedOut = Data.Builder().putBoolean(KEY_OUT_FAILED, true).build()
        val finalResult = when (outcome) {
            is Result.Retry -> return outcome
            is Result.Failure -> Result.success(failedOut)
            else -> outcome
        }
        if (lastErrorType == AiErrorType.INVALID_API_KEY) {
            sendBatchSummary(aborted = true, selfFailed = true)
            cancelBatch(applicationContext)
            return finalResult
        }
        val selfFailed = finalResult.outputData.getBoolean(KEY_OUT_FAILED, false)
        updateBatchProgressNotification(selfFailed)
        if (batchIndex >= batchTotal - 1) sendBatchSummary(aborted = false, selfFailed = selfFailed)
        return finalResult
    }

    private var lastErrorType: AiErrorType? = null

    private suspend fun doSingleWork(): Result {
        val soruId = inputData.getLong(KEY_SORU_ID, -1L)
        if (soruId == -1L) return Result.failure()
        val sendImage = inputData.getBoolean(KEY_SEND_IMAGE, true)
        val extractedText = inputData.getString(KEY_EXTRACTED_TEXT)
        val forceSolve = inputData.getBoolean(KEY_FORCE_SOLVE, false)

        return try {
            // Zaten çözülmüşse ve zorunlu/manuel değilse tekrar deneme
            val soru = soruRepository.getSoruById(soruId).first()
            if (soru == null) return Result.failure()
            if (!forceSolve && !soru.aiExplanation.isNullOrBlank()) {
                return Result.success(Data.Builder().putBoolean(KEY_OUT_SKIPPED, true).build())
            }

            val result = aiRepository.getAiSolution(soru, sendImage, extractedText)

            result.fold(
                onSuccess = { explanation ->
                    val latest = soruRepository.getSoruById(soruId).first() ?: soru
                    val updated = latest.copy(
                        aiExplanation = explanation,
                        updatedAt = System.currentTimeMillis()
                    )
                    soruRepository.soruGuncelle(updated)
                    if (!isBatch) sendSuccessNotification(soruId)
                    Result.success()
                },
                onFailure = { throwable ->
                    lastErrorType = (throwable as? AiException)?.errorType
                    val errorMsg = throwable.localizedMessage ?: "AI çözüm alınamadı"
                    val outData = Data.Builder().putString(KEY_ERROR_MESSAGE, errorMsg).build()
                    // 404 MODEL_UNAVAILABLE asla retry edilmez — önce kontrol
                    if (throwable is AiException && throwable.errorType == AiErrorType.MODEL_UNAVAILABLE && throwable.message?.contains("404") == true) {
                        if (!isBatch) sendFailureNotification(errorMsg)
                        return Result.failure(outData)
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
                        if (isRetryable && !isBatch) {
                            // Son deneme de başarısız — kuyrukta kalsın ama kullanıcıya bildir
                            sendFailureNotification(errorMsg)
                        }
                        Result.failure(outData)
                    }
                }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            // Sadece network/timeout retry, diğerleri failure
            val isRetryable = e is InterruptedIOException || e is java.io.IOException || e is android.database.sqlite.SQLiteException
            if (isRetryable && runAttemptCount < 3) return Result.retry()
            val errorMsg = e.localizedMessage ?: "Bilinmeyen bir hata oluştu"
            return Result.failure(Data.Builder().putString(KEY_ERROR_MESSAGE, errorMsg).build())
        }
    }

    private fun openSoruPendingIntent(soruId: Long?): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (soruId != null) putExtra("open_soru_id", soruId)
        }
        return PendingIntent.getActivity(
            applicationContext, soruId?.toInt() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sendSuccessNotification(soruId: Long) {
        createChannel()
        if (!hasNotificationPermission()) return
        val text = "✅ Soru #$soruId AI çözümü hazır! İpuçlarını görmek için dokun."
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.sorudefteri.R.mipmap.ic_launcher)
            .setContentTitle("AI Çözümü Hazır")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openSoruPendingIntent(soruId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(soruIdHash(), notification)
    }

    private suspend fun batchSiblings(): List<WorkInfo> {
        val batchId = inputData.getLong(KEY_BATCH_ID, 0L)
        return try {
            WorkManager.getInstance(applicationContext)
                .getWorkInfosByTagFlow("$TAG_BATCH_ID_PREFIX$batchId").first()
                .filter { it.id != id }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun updateBatchProgressNotification(selfFailed: Boolean) {
        createChannel()
        if (!hasNotificationPermission()) return
        val done = (batchSiblings().count { it.state.isFinished } + 1).coerceAtMost(batchTotal)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.sorudefteri.R.mipmap.ic_launcher)
            .setContentTitle("⚡ Toplu AI çözümü")
            .setContentText("$done/$batchTotal soru işlendi")
            .setProgress(batchTotal, done, false)
            .setOnlyAlertOnce(true)
            .setOngoing(done < batchTotal)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openSoruPendingIntent(null))
            .build()
        NotificationManagerCompat.from(applicationContext).notify(BATCH_PROGRESS_NOTIFICATION_ID, notification)
    }

    private suspend fun sendBatchSummary(aborted: Boolean, selfFailed: Boolean) {
        NotificationManagerCompat.from(applicationContext).cancel(BATCH_PROGRESS_NOTIFICATION_ID)
        createChannel()
        if (!hasNotificationPermission()) return
        val siblings = batchSiblings()
        val solved = siblings.count {
            it.state == WorkInfo.State.SUCCEEDED && !it.outputData.getBoolean(KEY_OUT_FAILED, false)
        } + if (selfFailed) 0 else 1
        val text = if (aborted) {
            "⚠️ Toplu çözüm durduruldu: API anahtarı geçersiz. ($solved/$batchTotal soru çözüldü)"
        } else {
            "⚡ $solved/$batchTotal soru çözüldü"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.sorudefteri.R.mipmap.ic_launcher)
            .setContentTitle("Toplu AI Çözümü Tamamlandı")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openSoruPendingIntent(null))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(BATCH_SUMMARY_NOTIFICATION_ID, notification)
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
