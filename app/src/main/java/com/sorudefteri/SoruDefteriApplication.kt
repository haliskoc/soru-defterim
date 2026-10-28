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

    @Inject
    lateinit var gorselDepo: com.sorudefteri.data.images.GorselDepo

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                // PERF: Hatırlatıcılar ayarlı — kapalıysa planlama yapılmaz, açıksa güncellenir.
                val dailyOn = ayarlarDataStore.isDailyReminderEnabled.first()
                val streakOn = ayarlarDataStore.isStreakReminderEnabled.first()
                if (dailyOn) ReminderScheduler.scheduleDailyReminder(this@SoruDefteriApplication)
                else ReminderScheduler.cancelDailyReminder(this@SoruDefteriApplication)
                if (streakOn) ReminderScheduler.scheduleStreakReminder(this@SoruDefteriApplication)
                else ReminderScheduler.cancelStreakReminder(this@SoruDefteriApplication)
            } catch (_: Exception) {
            }
        }
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
        // eklendiği için eski kayıtların parmak izleri boş kalabilir.
        // PERF: Bayrakla TEK SEFER çalışır; her açılışta tekrarlanmaz.
        appScope.launch {
            try {
                if (!ayarlarDataStore.isContentHashBackfilled.first()) {
                    soruRepository.backfillContentHashes()
                    ayarlarDataStore.setContentHashBackfilled(true)
                }
            } catch (_: Exception) {
                // bakım başarısız olsa bile uygulama çalışmaya devam eder
            }
        }
        // PERF: Eski/senkronla gelmiş görsellerin eksik thumb'ları tek seferlik
        // üretilir (grid tam çözünürlük çözmesin). Hafif yol listesi + chunked IO.
        appScope.launch {
            try {
                if (!ayarlarDataStore.isThumbsRepairedV1.first()) {
                    val yollar = soruRepository.getAllImagePaths()
                    if (yollar.isNotEmpty()) {
                        gorselDepo.repairMissingThumbnails(yollar)
                    }
                    ayarlarDataStore.setThumbsRepairedV1(true)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Düşük donanımda önbelleği kıs (liste + reels aynı anda açıkken RAM baskısı olmasın).
        // Kalite etkilenmez — önbellek yalnız kaydırma hızını etkiler.
        val dusuk = dusukCihazMi()
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
                    .maxSizePercent(context, if (dusuk) 0.12 else 0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (dusuk) 50L * 1024 * 1024 else 75L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    /** RAM ≤ 3 GB veya çekirdek ≤ 4 ise düşük cihaz (görsel önbelleği kısılır). */
    private fun dusukCihazMi(): Boolean {
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
            val ramDusuk = if (am != null) {
                val bilgi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(bilgi)
                bilgi.totalMem in 1..3L * 1024 * 1024 * 1024
            } else false
            ramDusuk || Runtime.getRuntime().availableProcessors() <= 4
        } catch (_: Exception) {
            false
        }
    }
}
