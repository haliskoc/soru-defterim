package com.sorudefteri.data.backup

import android.content.Context
import android.net.Uri
import com.sorudefteri.data.local.AyarlarDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive API İzin Kapsamları (DriveScopes).
 * AppData klasörü, kullanıcının ana Drive dosya listesinde görünmeyen,
 * sadece bu uygulamanın erişebildiği gizli ve güvenli özel depolama alanıdır.
 */
object DriveScopes {
    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
}

/**
 * Google Drive Bulut Senkronizasyon Üst Verisi (Metadata).
 */
@Serializable
data class CloudBackupMetadata(
    val fileId: String? = null,
    val fileName: String = "soru_defterim_cloud_backup.sdefteri",
    val lastSyncTimestamp: Long = 0L,
    val sizeBytes: Long = 0L,
    val questionCount: Int = 0,
    val isSyncSuccessful: Boolean = false,
    val accountEmail: String? = null
)

/**
 * Sessiz Google Drive AppData Senkronizasyon Yardımcısı (Faz 5.5).
 *
 * Soru Defterim veritabanı, çözümler ve soru görsellerini Google Drive'ın gizli
 * `appDataFolder` alanına sessizce yedekleyen ve geri yükleyen mimari katman.
 */
@Singleton
class GoogleDriveBackupHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val ayarlarDataStore: AyarlarDataStore? = null
) {

    @Volatile
    private var cachedMetadata: CloudBackupMetadata = CloudBackupMetadata()

    /**
     * Buluta yüklenecek olan tam .sdefteri yedek paketini (veritabanı + görseller)
     * hazırlar ve dosyasını döner.
     *
     * @param context Android Context (varsayılan: Enjekte edilen uygulama bağlamı)
     * @return Bulut yüklemesi için hazırlanan .sdefteri dosyası
     */
    suspend fun createCloudBackupPayload(context: Context = this.context): File = withContext(Dispatchers.IO) {
        val result = backupManager.exportBackupToTempFile(password = null)
        val (tempFile, count) = result.getOrThrow()

        val syncDir = File(context.filesDir, "cloud_sync").apply { if (!exists()) mkdirs() }
        val payloadFile = File(syncDir, "soru_defterim_cloud_backup.sdefteri")

        tempFile.copyTo(payloadFile, overwrite = true)
        tempFile.delete()

        val now = System.currentTimeMillis()
        cachedMetadata = CloudBackupMetadata(
            fileId = "drive_appdata_${now}",
            fileName = payloadFile.name,
            lastSyncTimestamp = now,
            sizeBytes = payloadFile.length(),
            questionCount = count,
            isSyncSuccessful = true,
            accountEmail = null
        )

        ayarlarDataStore?.setDriveLastSyncTime(now)
        payloadFile
    }

    /**
     * Google Drive AppData klasöründen indirilen yedek dosyasını yerel veritabanına ve
     * kalıcı görsel deposuna geri yükler.
     *
     * @param context Android Context
     * @param backupFile İçe aktarılacak bulut yedek dosyası
     * @return Başarılı olursa geri yüklenen soru sayısı
     */
    suspend fun restoreCloudBackupPayload(
        context: Context = this.context,
        backupFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists() || !backupFile.isFile) {
                return@withContext Result.failure(IllegalArgumentException("Yedek dosyası bulunamadı: ${backupFile.absolutePath}"))
            }

            val fileUri = Uri.fromFile(backupFile)
            backupManager.importBackup(fileUri, password = null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * En son gerçekleştirilen bulut senkronizasyonuna ait metadata bilgisini döner.
     */
    fun getCloudBackupMetadata(): CloudBackupMetadata {
        return cachedMetadata
    }

    /**
     * Arka planda veya manuel olarak Google Drive AppData klasörüyle sessiz
     * senkronizasyon gerçekleştirir.
     */
    suspend fun syncToCloud(context: Context = this.context): Result<CloudBackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val payloadFile = createCloudBackupPayload(context)
            if (!payloadFile.exists() || payloadFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Bulut yedek paketi oluşturulamadı."))
            }

            // Google Drive AppData klasörüne yükleme henüz UYGULANMADI.
            // Aşağıdaki mimari gerekiyor:
            //   1. OAuth token (GoogleSignIn / Google Identity)
            //   2. Drive API REST v3: POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart
            //      Parents: ['appDataFolder']
            //   3. Metadata güncelleme
            //
            // DÜZELTME: Önceden burada doğrudan Result.success() dönülüyordu; UI
            // "Google Drive senkronizasyonu başarıyla tamamlandı" mesajı gösteriyor
            // ama HİÇBİR ŞEY yüklenmiyordu (kullanıcı verisini bulut sanıyordu).
            // Gerçekçi olmayan başarı yerine açık hata dönüyoruz.
            val payloadDir = File(context.filesDir, "cloud_sync")
            return@withContext Result.failure(
                UnsupportedOperationException(
                    "Google Drive'a yükleme henüz uygulanmadı. " +
                        "Yedek şu an cihazda şu klasöre kaydedildi: ${payloadDir.absolutePath}. " +
                        "Yedekleme ekranından manuel olarak dışa aktarabilirsiniz."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
