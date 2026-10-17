package com.sorudefteri.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.sorudefteri.ui.widget.SoruWidgetUpdater
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sorudefteri.MainActivity
import com.sorudefteri.R
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.dao.SoruDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Spaced Repetition (Aralıklı Tekrar) ve Streak Hatırlatıcı Worker.
 * - Her gün saat 19:00'da tekrar vakti gelmiş sorular için bildirim gönderir.
 * - Saat 20:30 - 22:00 arasında günlük çalışma serisi tamamlanmamışsa seriyi koruma uyarısı gönderir.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val soruDao: SoruDao,
    private val ayarlarDataStore: AyarlarDataStore
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "spaced_repetition_channel"
        const val CHANNEL_NAME = "Aralıklı Tekrar Hatırlatıcıları"
        const val STREAK_CHANNEL_ID = "streak_reminder_channel"
        const val STREAK_CHANNEL_NAME = "Günlük Seri Hatırlatıcıları"
        const val NOTIFICATION_ID = 1001
        const val STREAK_NOTIFICATION_ID = 1002
        const val WORK_NAME = "SpacedRepetitionReminderWork"
        const val STREAK_WORK_NAME = "StreakReminderWork"
    }

    override suspend fun doWork(): Result {
        return try {
            val isStreakWork = tags.contains(STREAK_WORK_NAME)
            if (isStreakWork) {
                // 21:00 Seri Koruma uyarısı
                checkStreakWarning(forceCheck = true)
            } else {
                // 19:00 Aralıklı Tekrar hatırlatması
                val currentTime = System.currentTimeMillis()
                val dueCount = soruDao.getDueReviewCountDirect(currentTime)
                if (dueCount > 0) {
                    sendNotification(dueCount)
                }
                checkStreakWarning(forceCheck = false)
            }

            // Ana ekran widget'ını (streak/tekrar/sınav geri sayımı) günlük olarak yenile
            SoruWidgetUpdater.updateAllNow(context)

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
            action = Intent.ACTION_VIEW
            data = Uri.parse("sorudefteri://test/due")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "test_due")
            putExtra("deepLink", "sorudefteri://test/due")
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

    private suspend fun checkStreakWarning(forceCheck: Boolean = false) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        // 20:30 (1230) ile 22:30 (1350) arası kontrol veya 21:00 streak worker'ı tetiklendiyse
        if (forceCheck || currentMinutes in 1230..1350) {
            val streak = ayarlarDataStore.currentStreak.first()
            val lastStreakDate = ayarlarDataStore.lastStreakDate.first()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val isCompletedToday = lastStreakDate == todayStr

            if (!isCompletedToday && streak > 0) {
                sendStreakNotification(streak)
            }
        }
    }

    private fun sendStreakNotification(streak: Int) {
        createStreakNotificationChannel()

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
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = "🔥 Serin Sönmek Üzere!"
        val notificationContent = "🔥 $streak günlük serin sönmek üzere! Serini korumak için 5 soru çöz veya ekle!"

        val notification = NotificationCompat.Builder(context, STREAK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(STREAK_NOTIFICATION_ID, notification)
    }

    private fun createStreakNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STREAK_CHANNEL_ID,
                STREAK_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Günlük seri koruma uyarı bildirimleri"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
