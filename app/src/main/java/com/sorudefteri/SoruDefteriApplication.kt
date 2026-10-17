package com.sorudefteri

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sorudefteri.data.backup.AutoBackupWorker
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.worker.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Main Application class for Soru Defterim.
 * Configures Hilt dependency injection, Coil 3 ImageLoader with OkHttpClient,
 * WorkManager Configuration with HiltWorkerFactory, and schedules daily Spaced Repetition reminders.
 */
@HiltAndroidApp
class SoruDefteriApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var ayarlarDataStore: AyarlarDataStore

    @Inject
    lateinit var soruRepository: com.sorudefteri.data.repository.SoruRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Her gün saat 19:00'da tetiklenecek Aralıklı Tekrar bildirimini zamanla
        ReminderScheduler.scheduleDailyReminder(this)
        // Her gün saat 21:00'de tetiklenecek Seri Koruma (Streak) bildirimini zamanla
        ReminderScheduler.scheduleStreakReminder(this)
        // Otomatik yerel yedek - sadece ayar açıksa schedule et, yoksa iptal et
        appScope.launch {
            try {
                val isEnabled = ayarlarDataStore.isAutoBackupEnabled.first()
                val isWifiOnly = ayarlarDataStore.isWifiOnlyBackup.first()
                if (isEnabled) {
                    AutoBackupWorker.schedulePeriodic(this@SoruDefteriApplication, isWifiOnly)
                } else {
                    AutoBackupWorker.cancel(this@SoruDefteriApplication)
                }
            } catch (_: Exception) {
                // varsayılan: kapalıysa planlama yapma
            }
        }
        // Akıllı içe aktarma (dedup/merge) bakımı: contentHash kolonu sonradan
        // eklendiği için eski kayıtların parmak izleri boş kalabilir. Tek seferlik
        // olarak tüm boş kayıtların hash'i hesaplanır ve yazılır.
        appScope.launch {
            try {
                soruRepository.backfillContentHashes()
            } catch (_: Exception) {
                // bakım başarısız olsa bile uygulama çalışmaya devam eder
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
