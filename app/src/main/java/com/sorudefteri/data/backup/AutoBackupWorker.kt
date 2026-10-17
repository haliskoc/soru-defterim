package com.sorudefteri.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sorudefteri.data.local.AyarlarDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Arka planda periyodik ve Wi-Fi duyarlı otomatik yedekleme gerçekleştiren Hilt Worker.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val ayarlarDataStore: AyarlarDataStore
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "soru_defterim_auto_backup"
        private const val TAG = "AutoBackupWorker"
        // Dahili depolama şişmesini önlemek için yalnızca en son 1 tam otomatik yedek saklanır
        private const val MAX_SAVED_BACKUPS = 1

        /**
         * 24 saatlik periyodik otomatik yerel yedekleme görevini kuyruğa ekler veya günceller.
         * Yerel depolama (filesDir/backups/) kullanıldığı için ağ bağlantısı gereksizdir ve internetsiz çalışır.
         */
        fun schedulePeriodic(context: Context, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "Yerel otomatik yedekleme görevi planlandı (ağ kısıtı kaldırıldı).")
        }

        /**
         * Otomatik yedekleme görevini iptal eder.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Otomatik yedekleme görevi iptal edildi.")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Kullanıcı ayarlarını kontrol et
            val isAutoBackupEnabled = ayarlarDataStore.getAutoBackupEnabled()
            if (!isAutoBackupEnabled) {
                Log.d(TAG, "Otomatik yedekleme ayarlarda kapalı, işlem atlandı.")
                return@withContext Result.success()
            }

            Log.d(TAG, "Otomatik yedekleme başlatılıyor...")

            // 2. Yedek paketini geçici dosyaya oluştur
            val backupResult = backupManager.exportBackupToTempFile(password = null)
            if (backupResult.isFailure) {
                Log.e(TAG, "Yedek dosyası oluşturulamadı", backupResult.exceptionOrNull())
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val (tempFile, questionCount) = backupResult.getOrThrow()
            Log.d(TAG, "Yedek başarıyla oluşturuldu: $questionCount soru. Dosya: ${tempFile.name}")

            // 3. Kalıcı yedekler klasörüne (filesDir/backups/) taşı ve rotasyon uygula
            val backupsDir = File(appContext.filesDir, "backups").apply {
                if (!exists()) mkdirs()
            }

            val destFile = File(backupsDir, tempFile.name)
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            // Rotasyon: En fazla son MAX_SAVED_BACKUPS adet yedeği sakla, eskileri sil
            rotateBackups(backupsDir)

            Log.i(TAG, "Otomatik yedekleme tamamlandı: ${destFile.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Otomatik yedekleme sırasında hata", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun rotateBackups(backupsDir: File) {
        val backupFiles = backupsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".sdefteri")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backupFiles.size > MAX_SAVED_BACKUPS) {
            backupFiles.drop(MAX_SAVED_BACKUPS).forEach { oldBackup ->
                Log.d(TAG, "Eski otomatik yedek siliniyor: ${oldBackup.name}")
                oldBackup.delete()
            }
        }
    }
}
