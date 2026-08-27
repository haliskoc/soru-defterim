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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sorudefteri.MainActivity
import com.sorudefteri.R
import com.sorudefteri.data.local.dao.SoruDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Spaced Repetition (Aralıklı Tekrar) Hatırlatıcı Worker.
 * Her gün saat 19:00'da tetiklenerek tekrar vakti gelmiş soruları sorgular.
 * Tekrar edilmesi gereken sorular varsa kullanıcıya bildirim gönderir.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val soruDao: SoruDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "spaced_repetition_channel"
        const val CHANNEL_NAME = "Aralıklı Tekrar Hatırlatıcıları"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "SpacedRepetitionReminderWork"
    }

    override suspend fun doWork(): Result {
        return try {
            val currentTime = System.currentTimeMillis()
            val dueCount = soruDao.getDueReviewCountDirect(currentTime)

            if (dueCount > 0) {
                sendNotification(dueCount)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Only retry on transient IO/DB busy, otherwise failure to avoid infinite loop
            val isTransient = e is java.io.IOException || e is android.database.sqlite.SQLiteException
            if (isTransient) Result.retry() else Result.failure()
        }
    }

    private fun sendNotification(dueCount: Int) {
        createNotificationChannel()

        // Android 13+ (TIRAMISU) Bildirim İzni Kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "test_coz")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = "Tekrar Vakti! 📚"
        val notificationContent = "Bugün tekrar etmen gereken $dueCount soru var. Şimdi çözmeye başla!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tekrar vakti gelmiş sorular için günlük 19:00 hatırlatıcıları"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
