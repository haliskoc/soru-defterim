package com.sorudefteri.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.dao.BenzerSoruDao
import com.sorudefteri.data.local.dao.DenemeSinaviDao
import com.sorudefteri.data.local.dao.SoruChatDao
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.data.local.SoruIcerikHash
import com.sorudefteri.data.local.entity.BenzerSoruEntity
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupManifest(
    val appSignature: String = "com.sorudefteri",
    val version: Int = 3,
    val exportDate: Long = System.currentTimeMillis(),
    val questionCount: Int,
    val sorular: List<BackupSoruItem>,
    val testSonuclari: List<BackupTestSonucItem> = emptyList(),
    val soruChatMesajlari: List<BackupSoruChatItem> = emptyList(),
    val benzerSorular: List<BackupBenzerSoruItem> = emptyList(),
    val denemeler: List<BackupDenemeItem> = emptyList(),
    val gamificationData: BackupGamificationData? = null
)

/**
 * İçe aktarma sonucunun kullanıcıya gösterilebilir özeti.
 *
 * Kullanıcı "çözümlerim/ses kayıtlarım aktarılmadı" diye bildirdiğinde,
 * nerede kayıp olduğunu görebilmesi için bu sayaçlar UI'da gösterilir.
 */
data class ImportOzeti(
    val soruSayisi: Int = 0,
    val gorselsizSorular: Int = 0,
    val atlananChatMesaji: Int = 0,
    val atlananBenzerSoru: Int = 0,
    // ===== AKİLLİ İÇE AKTARMA SAYAÇLARI =====
    /** Mevcut kayıtla aynı içerik olduğu tespit edilip merge (güncelleme) uygulanan soru sayısı. */
    val guncellenenSoru: Int = 0,
    /** Mevcut kayıt zaten güncel olduğundan hiç değişiklik yapılmadan atlanan soru sayısı. */
    val guncelSoru: Int = 0,
    /** Parmak izi hesaplanamayan (metinsiz + kaynak yoksunu) ve her zaman yeni eklenen soru sayısı. */
    val tanimsizSoru: Int = 0,
    /** Mükerrer olduğu için atlanan test sonucu sayısı. */
    val mukerrerTest: Int = 0,
    /** Mükerrer olduğu için atlanan deneme sayısı. */
    val mukerrerDeneme: Int = 0,
    /** Mükerrer olduğu için atlanan Sokratik Koç mesajı sayısı. */
    val mukerrerChatMesaji: Int = 0,
    /** Mükerrer olduğu için atlanan benzer soru sayısı. */
    val mukerrerBenzerSoru: Int = 0
) {
    val eksikVar: Boolean
        get() = gorselsizSorular > 0 || atlananChatMesaji > 0 || atlananBenzerSoru > 0

    /** İçe aktarmada dedup/merge mekanizması devreye girdi mi? */
    val dedupKullanildi: Boolean
        get() = guncellenenSoru > 0 || guncelSoru > 0 || mukerrerTest > 0 ||
            mukerrerDeneme > 0 || mukerrerChatMesaji > 0 || mukerrerBenzerSoru > 0
}

@Serializable
data class BackupSoruItem(
    val originalId: Long? = null,
    // Varsayılan değer eklendi: eski manifestlerde imageName alanı olmayabilir ve
    // eksik alan tüm decode'u çökertiyordu. Boş değer "görselsiz metin kaydı" demektir.
    val imageName: String = "",
    val solutionImageName: String? = null,
    val correctAnswer: String? = null,
    val examType: String,
    val subjectId: String,
    val topic: String,
    val mistakeType: String? = null,
    val note: String? = null,
    val isSolved: Boolean = false,
    val solvedAt: Long? = null,
    val aiExplanation: String? = null,
    val isFavorite: Boolean = false,
    val reviewStage: Int = 0,
    val nextReviewDate: Long? = null,
    val lastReviewedAt: Long? = null,
    val kaynakYayin: String? = null,
    val kaynakDeneme: String? = null,
    val kaynakYil: Int? = null,
    val kaynakSoruNo: Int? = null,
    val studentAnswer: String? = null,
    val difficulty: String? = null,
    val ocrMetni: String? = null,
    val isOcrUsed: Boolean = false,
    val questionText: String? = null,
    val audioFileName: String? = null,
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupTestSonucItem(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val netCount: Float,
    val successRate: Int,
    val totalTimeSeconds: Long,
    val examType: String? = null,
    val completedAt: Long
)

@Serializable
data class BackupSoruChatItem(
    val originalSoruId: Long,
    val sender: String,
    val messageText: String,
    val timestamp: Long
)

@Serializable
data class BackupBenzerSoruItem(
    val originalSoruId: Long,
    val questionText: String,
    val optionsJson: String,
    val correctAnswer: String,
    val solution: String,
    val createdAt: Long
)

@Serializable
data class BackupDenemeItem(
    val baslik: String,
    val sinavTuru: String,
    val tarih: Long,
    val toplamNet: Double,
    val dersNetleriJson: String,
    val notlar: String? = null,
    val createdAt: Long
)

@Serializable
data class BackupGamificationData(
    val currentStreak: Int = 0,
    val lastStreakDate: String = "",
    val streakShieldCount: Int = 1,
    val xp: Int = 0,
    val unlockedBadges: List<String> = emptyList()
)

/**
 * Soruların, fotoğrafların, cevapların, test geçmişinin, sohbetlerin, benzer soruların,
 * çizimlerin (scratchpad), sesli notların ve oyunlaştırma durumunun tek bir .sdefteri paketi
 * halinde dışa ve içe aktarılmasını sağlayan dayanıklı, AES-256-GCM şifreli yedekleme yöneticisi.
 */
@Singleton
open class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soruRepository: SoruRepository,
    private val gorselDepo: GorselDepo,
    private val json: Json,
    private val testSonucDao: TestSonucDao? = null,
    private val soruChatDao: SoruChatDao? = null,
    private val benzerSoruDao: BenzerSoruDao? = null,
    private val denemeSinaviDao: DenemeSinaviDao? = null,
    private val soruDedupDao: SoruDao? = null,
    private val ayarlarDataStore: AyarlarDataStore? = null
) {

    /**
     * Son içe aktarmanın ayrıntılı özeti. [importBackup] başarıyla bittiğinde
     * doldurulur; UI bu alanı okuyup kullanıcıya eksik/atlanan kayıtları gösterir.
     * Önceden bu bilgi yalnızca loglanıyordu, kullanıcıya hiç ulaşmıyordu.
     */
    @Volatile
    var sonImportOzeti: ImportOzeti? = null
        private set

    companion object {
        private const val TAG = "BackupManager"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB optimum I/O buffer
        private val MAGIC_HEADER = "SDEFTERI_ENC_V1".toByteArray(Charsets.US_ASCII)
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 120000
        private const val PBKDF2_ITERATIONS_LEGACY = 10000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_ZIP_ENTRIES = 5000
        private const val MAX_SINGLE_FILE_SIZE = 50 * 1024 * 1024L // 50 MB
        private const val MAX_TOTAL_UNCOMPRESSED_SIZE = 500 * 1024 * 1024L // 500 MB
    }

    /**
     * Verilen dosya veya Uri'nin şifreli bir .sdefteri paketi olup olmadığını tespit eder.
     */
    suspend fun isBackupEncrypted(sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val headerBuffer = ByteArray(MAGIC_HEADER.size)
                val read = input.read(headerBuffer)
                return@withContext read == MAGIC_HEADER.size && headerBuffer.contentEquals(MAGIC_HEADER)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tüm veritabanı kayıtlarını ve görselleri URI üzerinden verilen çıkış akışına
     * opsiyonel AES-256-GCM şifrelemeli `.sdefteri` olarak paketler.
     * @param password Opsiyonel parola (verilirse AES-256-GCM ile şifrelenir, verilmezse standart zip)
     * @return Dışa aktarılan soru sayısı
     */
    open suspend fun exportBackup(targetUri: Uri, password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(Exception("Hedef dosya açılamadı veya yazılamadı."))

            outputStream.use { rawOut ->
                exportBackupToStream(rawOut, password)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Yerel otomatik yedekleme (filesDir/backups) veya SAF ile dışa aktarım için geçici dosyaya yazar.
     * Local-only: Bulut / Google Drive senkronizasyonu kaldırıldı.
     * @param password Opsiyonel şifreleme parolası
     */
    open suspend fun exportBackupToTempFile(password: String? = null): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        try {
            val fileName = "soru_defterim_yedek_${System.currentTimeMillis()}.sdefteri"
            val sharedDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
            val tempFile = File(sharedDir, fileName)
            val result = FileOutputStream(tempFile).use { out ->
                exportBackupToStream(out, password)
            }
            result.map { count -> tempFile to count }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * İç akışa yedekleme verilerini yazar. Parola varsa şifreleme katmanı uygulanır.
     * Local-only: Bulut/Drive senkronizasyonu yok, sadece cihaz içi / SAF Uri ile dışa aktarım.
     */
    private suspend fun exportBackupToStream(outputStream: OutputStream, password: String?): Result<Int> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "temp_export_${UUID.randomUUID()}.zip")
        try {
            // TODO(P0): getAllSorular().first() tüm soruları tek seferde belleğe yükler; büyük arşivde streaming/paging (Flow chunk veya paging) ile değiştirilmeli.
            val sorular = soruRepository.getAllSorular().first()
            val backupItems = mutableListOf<BackupSoruItem>()

            // 1. Önce standart ZIP içeriğini geçici dosyaya oluştur
            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zipOut ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    val scratchpadDir = File(context.filesDir, "scratchpads")
                    for ((index, soru) in sorular.withIndex()) {
                        val soruFile = File(soru.imagePath)
                        // Deterministic: soru.id tabanlı, id 0 ise index fallback
                        val imageKey = if (soru.id != 0L) soru.id else (index + 1).toLong()
                        val candidateImageName = "images/soru_${imageKey}.jpg"

                        // DÜZELTME: imageName, ZIP'e GERÇEKTEN yazılan girişe bağlanır.
                        // Önceden dosya yokken bile ad manifest içinde yazılıyordu; içe
                        // aktarma tarafı "görsel bulunamadı" deyip sorunun TAMAMINI
                        // (AI çözümü, ses kaydı, not, FSRS durumu dahil) sessizce
                        // düşürüyordu. Kullanıcı "çözümlerim yedekten gelmiyor" diye
                        // bildiriyordu.
                        val uniqueImageName: String? = if (soruFile.exists() && soruFile.isFile) {
                            zipOut.putNextEntry(ZipEntry(candidateImageName))
                            FileInputStream(soruFile).use { fileIn ->
                                var len: Int
                                while (fileIn.read(buffer).also { len = it } > 0) {
                                    zipOut.write(buffer, 0, len)
                                }
                            }
                            zipOut.closeEntry()
                            candidateImageName
                        } else {
                            Log.w(TAG, "Soru görseli yok, metin olarak yedekleniyor: ${soru.imagePath}")
                            null
                        }

                        var uniqueSolutionName: String? = null
                        if (!soru.solutionImagePath.isNullOrBlank()) {
                            val solutionFile = File(soru.solutionImagePath)
                            if (solutionFile.exists() && solutionFile.isFile) {
                                val solKey = if (soru.id != 0L) soru.id else (index + 1).toLong()
                                uniqueSolutionName = "images/cozum_${solKey}.jpg"
                                zipOut.putNextEntry(ZipEntry(uniqueSolutionName))
                                FileInputStream(solutionFile).use { fileIn ->
                                    var len: Int
                                    while (fileIn.read(buffer).also { len = it } > 0) {
                                        zipOut.write(buffer, 0, len)
                                    }
                                }
                                zipOut.closeEntry()
                            }
                        }

                        var uniqueAudioName: String? = null
                        if (!soru.audioPath.isNullOrBlank()) {
                            val audioFile = File(soru.audioPath)
                            if (audioFile.exists() && audioFile.isFile) {
                                uniqueAudioName = "audio_memos/audio_${imageKey}.m4a"
                                zipOut.putNextEntry(ZipEntry(uniqueAudioName))
                                FileInputStream(audioFile).use { fileIn ->
                                    var len: Int
                                    while (fileIn.read(buffer).also { len = it } > 0) {
                                        zipOut.write(buffer, 0, len)
                                    }
                                }
                                zipOut.closeEntry()
                            }
                        }

                        val padFile = File(scratchpadDir, "soru_${soru.id}.json")
                        if (padFile.exists() && padFile.isFile) {
                            zipOut.putNextEntry(ZipEntry("scratchpads/soru_${imageKey}.json"))
                            FileInputStream(padFile).use { fileIn ->
                                var len: Int
                                while (fileIn.read(buffer).also { len = it } > 0) {
                                    zipOut.write(buffer, 0, len)
                                }
                            }
                            zipOut.closeEntry()
                        }

                        backupItems.add(
                            BackupSoruItem(
                                originalId = imageKey,
                                // Boşsa içe aktarma bu soruyu "görselsiz metin" olarak alır.
                                imageName = uniqueImageName ?: "",
                                solutionImageName = uniqueSolutionName,
                                correctAnswer = soru.correctAnswer,
                                examType = soru.examType,
                                subjectId = soru.subjectId,
                                topic = soru.topic,
                                mistakeType = soru.mistakeType?.name,
                                note = soru.note,
                                isSolved = soru.isSolved,
                                solvedAt = soru.solvedAt,
                                aiExplanation = soru.aiExplanation,
                                isFavorite = soru.isFavorite,
                                reviewStage = soru.reviewStage,
                                nextReviewDate = soru.nextReviewDate,
                                lastReviewedAt = soru.lastReviewedAt,
                                kaynakYayin = soru.kaynakYayin,
                                kaynakDeneme = soru.kaynakDeneme,
                                kaynakYil = soru.kaynakYil,
                                kaynakSoruNo = soru.kaynakSoruNo,
                                studentAnswer = soru.studentAnswer,
                                difficulty = soru.difficulty,
                                ocrMetni = soru.ocrMetni,
                                isOcrUsed = soru.isOcrUsed,
                                questionText = soru.questionText,
                                audioFileName = uniqueAudioName,
                                fsrsStability = soru.fsrsStability,
                                fsrsDifficulty = soru.fsrsDifficulty,
                                fsrsReps = soru.fsrsReps,
                                fsrsLapses = soru.fsrsLapses,
                                createdAt = soru.createdAt
                            )
                        )
                    }

                    // Test Sonuçları
                    val testResults = testSonucDao?.getAllTestResultsDirect() ?: emptyList()
                    val backupTestItems = testResults.map {
                        BackupTestSonucItem(
                            totalQuestions = it.totalQuestions,
                            correctCount = it.correctCount,
                            wrongCount = it.wrongCount,
                            emptyCount = it.emptyCount,
                            netCount = it.netCount,
                            successRate = it.successRate,
                            totalTimeSeconds = it.totalTimeSeconds,
                            examType = it.examType,
                            completedAt = it.completedAt
                        )
                    }

                    // Soru Chat Mesajları
                    val chatMessages = soruChatDao?.getAllMessagesDirect() ?: emptyList()
                    val backupChatItems = chatMessages.map {
                        BackupSoruChatItem(
                            originalSoruId = it.soruId,
                            sender = it.sender,
                            messageText = it.messageText,
                            timestamp = it.timestamp
                        )
                    }

                    // Kalıcı Benzer Sorular
                    val benzerSorular = benzerSoruDao?.getAllBenzerSorularDirect() ?: emptyList()
                    val backupBenzerItems = benzerSorular.map {
                        BackupBenzerSoruItem(
                            originalSoruId = it.soruId,
                            questionText = it.questionText,
                            optionsJson = it.optionsJson,
                            correctAnswer = it.correctAnswer,
                            solution = it.solution,
                            createdAt = it.createdAt
                        )
                    }

                    // Deneme Sınavları
                    val denemeler = denemeSinaviDao?.getAllDenemelerDirect() ?: emptyList()
                    val backupDenemeItems = denemeler.map {
                        BackupDenemeItem(
                            baslik = it.baslik,
                            sinavTuru = it.sinavTuru,
                            tarih = it.tarih,
                            toplamNet = it.toplamNet,
                            dersNetleriJson = it.dersNetleriJson,
                            notlar = it.notlar,
                            createdAt = it.createdAt
                        )
                    }

                    // Oyunlaştırma Durumu
                    val gamificationData = ayarlarDataStore?.let {
                        BackupGamificationData(
                            currentStreak = it.getCurrentStreak(),
                            lastStreakDate = it.getLastStreakDate(),
                            streakShieldCount = it.getStreakShieldCount(),
                            xp = it.getUserXp(),
                            unlockedBadges = it.getUnlockedBadges().toList()
                        )
                    }

                    // Manifest dosyasını ZIP kök dizinine ekle
                    val manifest = BackupManifest(
                        questionCount = backupItems.size,
                        sorular = backupItems,
                        testSonuclari = backupTestItems,
                        soruChatMesajlari = backupChatItems,
                        benzerSorular = backupBenzerItems,
                        denemeler = backupDenemeItems,
                        gamificationData = gamificationData
                    )
                    val manifestJson = json.encodeToString(manifest)
                    zipOut.putNextEntry(ZipEntry("backup_manifest.json"))
                    val writer = java.io.OutputStreamWriter(zipOut, Charsets.UTF_8)
                    writer.write(manifestJson)
                    writer.flush()
                    zipOut.closeEntry()
                    zipOut.flush()
                }
            }

            // 2. Parola belirtilmişse AES-256-GCM ile şifrele, değilse düz zip yaz
            if (!password.isNullOrBlank()) {
                val secureRandom = SecureRandom()
                val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
                val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

                val secretKey = deriveKey(password.toCharArray(), salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                }

                // Format: [MAGIC_HEADER 15B][SALT 16B][IV 12B][CIPHERTEXT + GCM TAG]
                outputStream.write(MAGIC_HEADER)
                outputStream.write(salt)
                outputStream.write(iv)

                CipherOutputStream(outputStream, cipher).use { cipherOut ->
                    FileInputStream(tempZipFile).use { fis ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            cipherOut.write(buffer, 0, len)
                        }
                    }
                    cipherOut.flush()
                }
            } else {
                // Şifresiz standart ZIP
                FileInputStream(tempZipFile).use { fis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        outputStream.write(buffer, 0, len)
                    }
                }
                outputStream.flush()
            }

            Result.success(sorular.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                if (tempZipFile.exists()) tempZipFile.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Seçilen .sdefteri yedek dosyasını açar, doğrular, görselleri kalıcı depoya kopyalar
     * ve soruları veritabanına aktarır.
     * @param sourceUri İçe aktarılacak dosya Uri'si
     * @param password Şifreli dosya ise parola, şifresiz ise null
     * @return İçe aktarılan soru sayısı
     */
    open suspend fun importBackup(sourceUri: Uri, password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val tempExtractDir = File(context.cacheDir, "temp_import_${UUID.randomUUID()}")
        try {
            tempExtractDir.mkdirs()

            val rawInputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Yedek dosyası açılamadı."))

            // Dosya akışını tamponlayarak başlığı kontrol et
            val bufferedIn = BufferedInputStream(rawInputStream)
            // P0-01: mark limiti MAGIC+SALT+IV+1 olmalı; aksi halde reset() başa saramaz ve şifreli dosya şifresiz sanılır
            bufferedIn.mark(MAGIC_HEADER.size + SALT_LENGTH + IV_LENGTH + 1)
            val headerBuffer = ByteArray(MAGIC_HEADER.size)
            val readHeaderSize = bufferedIn.read(headerBuffer)
            val isEncrypted = readHeaderSize == MAGIC_HEADER.size && headerBuffer.contentEquals(MAGIC_HEADER)

            // Şifreli ise salt/iv sakla — legacy fallback için gerekebilir
            var encryptedSalt: ByteArray? = null
            var encryptedIv: ByteArray? = null

            val zipInputStream: ZipInputStream = if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    try { bufferedIn.close() } catch (_: Exception) {}
                    try { rawInputStream.close() } catch (_: Exception) {}
                    return@withContext Result.failure(
                        SecurityException("Bu yedek dosyası şifreli (.sdefteri). Lütfen şifre çözme parolasını girin.")
                    )
                }

                val salt = ByteArray(SALT_LENGTH)
                val iv = ByteArray(IV_LENGTH)
                val readSalt = bufferedIn.read(salt)
                val readIv = bufferedIn.read(iv)

                if (readSalt != SALT_LENGTH || readIv != IV_LENGTH) {
                    try { bufferedIn.close() } catch (_: Exception) {}
                    try { rawInputStream.close() } catch (_: Exception) {}
                    return@withContext Result.failure(Exception("Bozuk şifreli yedek başlığı."))
                }
                encryptedSalt = salt
                encryptedIv = iv

                val secretKey = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                }

                ZipInputStream(BufferedInputStream(CipherInputStream(bufferedIn, cipher)))
            } else {
                // Şifreli değil, başa geri sar
                bufferedIn.reset()
                ZipInputStream(bufferedIn)
            }

            // 1. ZIP içeriğini Zip Slip korumasıyla geçici klasöre aç — legacy fallback destekli
            var extractionSucceeded = false
            var lastExtractionError: Exception? = null
            try {
                zipInputStream.use { zipIn -> extractZipSafely(zipIn, tempExtractDir) }
                extractionSucceeded = true
            } catch (e: Exception) {
                lastExtractionError = e
                val msg = e.localizedMessage?.lowercase().orEmpty()
                val isAuthFailure = msg.contains("tag mismatch") || msg.contains("mac check") || msg.contains("bad tag") || msg.contains("pad block") || e is javax.crypto.AEADBadTagException || e.cause is javax.crypto.AEADBadTagException
                if (isAuthFailure && isEncrypted && encryptedSalt != null && encryptedIv != null) {
                    // Eski iterasyonla tekrar dene
                    // P0-01 FIX: legacy fallback offset bug düzeltildi — önceki kod skip(MAGIC_HEADER) + read(header/salt/iv) ile çifte ilerletme yapıyordu.
                    // Doğrusu: sadece read(header/salt/iv) bırak, skip kaldır.
                    try {
                        try { zipInputStream.close() } catch (_: Exception) {}
                        try { bufferedIn.close() } catch (_: Exception) {}
                        try { rawInputStream.close() } catch (_: Exception) {}
                        // P0: kısmi/bozuk çıkarım kalıntılarını temizle, aksi halde ikinci denemede eski dosyalar kalabilir
                        try { tempExtractDir.deleteRecursively(); tempExtractDir.mkdirs() } catch (_: Exception) {}
                        val newRaw = context.contentResolver.openInputStream(sourceUri)
                            ?: throw Exception("Yedek dosyası yeniden açılamadı")
                        val newBuffered = BufferedInputStream(newRaw)
                        // Sadece header/salt/iv'i oku ve geç (skip kaldırıldı)
                        val skipHeader = ByteArray(MAGIC_HEADER.size)
                        newBuffered.read(skipHeader)
                        val skipSalt = ByteArray(SALT_LENGTH)
                        newBuffered.read(skipSalt)
                        val skipIv = ByteArray(IV_LENGTH)
                        newBuffered.read(skipIv)

                        val legacyKey = deriveKey(password!!.toCharArray(), encryptedSalt!!, PBKDF2_ITERATIONS_LEGACY)
                        val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedIv!!))
                        }
                        val legacyZip = ZipInputStream(BufferedInputStream(CipherInputStream(newBuffered, legacyCipher)))
                        legacyZip.use { zipIn -> extractZipSafely(zipIn, tempExtractDir) }
                        extractionSucceeded = true
                        lastExtractionError = null
                    } catch (e2: Exception) {
                        lastExtractionError = e2
                    }
                }
            }

            if (!extractionSucceeded) {
                return@withContext Result.failure(
                    SecurityException("Yedek şifresi hatalı veya dosya bozulmuş: ${lastExtractionError?.localizedMessage}")
                )
            }

            // 2. Manifest kontrolü
            val manifestFile = File(tempExtractDir, "backup_manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("Geçersiz yedek paketi: backup_manifest.json bulunamadı."))
            }

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val manifest = try {
                json.decodeFromString<BackupManifest>(manifestText)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Yedek manifesti ayrıştırılamadı: ${e.localizedMessage}"))
            }

            if (manifest.appSignature != "com.sorudefteri") {
                return@withContext Result.failure(Exception("Bu dosya geçerli bir Soru Defterim yedeği değil."))
            }

            // 3. Görselleri ve ses dosyalarını kalıcı depoya kopyala ve Entity'leri hazırla
            val newSorular = mutableListOf<Soru>()
            val canonicalExtractDir = tempExtractDir.canonicalFile
            val itemOriginalIds = mutableListOf<Long>()

            val audioDir = File(context.filesDir, "audio_memos").apply { if (!exists()) mkdirs() }
            val scratchpadDir = File(context.filesDir, "scratchpads").apply { if (!exists()) mkdirs() }

            // Görseli olmadan geri yüklenen soru sayısı; içe aktarma sonunda raporlanır.
            var gorselsizImportEdilen = 0
            // Eşlemesi bulunamadığı için atlanan çizim sayısı.
            var cizimAtlanan = 0

            // ===== AKİLLİ İÇE AKTARMA (dedup/merge) HAZIRLIĞI =====
            // Her gelen soru için içerik parmak izi hesaplanır; DB'de aynı izle
            // kayıtlı satır varsa yeni kopya EKLENMEZ, mevcut kayıt merge edilir.
            val incomingItems = manifest.sorular
            val itemHashes = incomingItems.map { item ->
                SoruIcerikHash.hesapla(
                    examType = item.examType,
                    subjectId = item.subjectId,
                    topic = item.topic,
                    questionText = item.questionText,
                    ocrMetni = item.ocrMetni,
                    kaynakYayin = item.kaynakYayin,
                    kaynakDeneme = item.kaynakDeneme,
                    kaynakYil = item.kaynakYil,
                    kaynakSoruNo = item.kaynakSoruNo
                )
            }
            val existingByHash: MutableMap<String, SoruEntity> = HashMap<String, SoruEntity>().apply {
                if (soruDedupDao != null) {
                    putAll(soruDedupDao.getByContentHashes(itemHashes.filter { it.isNotBlank() }).associateBy { it.contentHash })
                }
            }
            var guncellenenSoru = 0
            var guncelSoru = 0
            var tanimsizSoru = 0

            // İçerik eşleşen (merge edilen) soruların eşlemesi: eski manifest id → mevcut DB id.
            // Bunlar yeni satır olarak eklenmediği için idMap'e ekstra birleştirilir;
            // Sokratik Koç / Benzer Soru / Çizim kayıtları mevcut soruya bağlanır.
            val matchedOriginalToNewId = mutableMapOf<Long, Long>()

            /** Merge sırasında boş kalan alanları doldurmak için gelen paketteki dosyayı kalıcı depoya alır. */
            suspend fun kaydetGerekirse(
                zipAdi: String?,
                islem: suspend (File) -> String?
            ): String? {
                if (zipAdi.isNullOrBlank()) return null
                val f = File(tempExtractDir, zipAdi).canonicalFile
                val guvenli = f.path.startsWith(canonicalExtractDir.path + File.separator)
                if (!guvenli || !f.exists() || !f.isFile) return null
                return try { islem(f) } catch (e: Exception) { null }
            }

            for ((hashIndex, item) in incomingItems.withIndex()) {
                val contentHash = itemHashes[hashIndex]
                val existing = if (contentHash.isNotBlank() && soruDedupDao != null) existingByHash[contentHash] else null

                if (contentHash.isBlank() && existing == null) tanimsizSoru++

                if (existing != null && soruDedupDao != null) {
                    // ===== MERGE — mevcut kayıt korunur (id ve bağlı kayıtlar), alanlar yenilenir =====
                    // Gelen ilerleme (tekrar aşaması) daha iyiyse onu benimse; değilse mevcutu muhafaza et.
                    val incomingReviewed = item.lastReviewedAt ?: 0L
                    val existingReviewed = existing.lastReviewedAt ?: 0L
                    val adoptIncoming = item.reviewStage > existing.reviewStage ||
                        (item.reviewStage == existing.reviewStage && incomingReviewed > existingReviewed)

                    // Eksik alanları doldurmak için yalnızca ihtiyaç varsa dosya kopyala (reksiz disk kalıntısı olmasın)
                    val mergedImagePath = if (existing.imagePath.isBlank()) {
                        kaydetGerekirse(item.imageName) { f -> gorselDepo.kaydetDosyadan(f, isSolution = false) }.orEmpty()
                    } else existing.imagePath
                    val mergedSolutionPath = if (existing.solutionImagePath.isNullOrBlank()) {
                        kaydetGerekirse(item.solutionImageName) { f -> gorselDepo.kaydetDosyadan(f, isSolution = true) }
                    } else existing.solutionImagePath
                    val mergedAudioPath = if (existing.audioPath.isNullOrBlank()) {
                        kaydetGerekirse(item.audioFileName) { aFile ->
                            val dest = File(audioDir, "audio_${UUID.randomUUID()}.m4a")
                            aFile.copyTo(dest, overwrite = true)
                            dest.absolutePath
                        }
                    } else existing.audioPath

                    if (mergedImagePath.isBlank() && item.imageName.isNotBlank() && existing.imagePath.isBlank()) gorselsizImportEdilen++
                    if (item.imageName.isNullOrBlank() && existing.imagePath.isBlank()) gorselsizImportEdilen++

                    val progressIsSolved = if (adoptIncoming) item.isSolved else existing.isSolved
                    val progressSolvedAt = if (adoptIncoming) (item.solvedAt ?: existing.solvedAt) else existing.solvedAt
                    val progressStudent = if (adoptIncoming) (item.studentAnswer ?: existing.studentAnswer) else existing.studentAnswer
                    // DİKKAT: updatedAt burada BASILMAZ — gerçek bir değişiklik varsa
                    // yazma aşamasında damgalanır; yoksa kayıt "zaten güncel" sayılır.
                    val merged = existing.copy(
                        imagePath = mergedImagePath,
                        solutionImagePath = mergedSolutionPath,
                        audioPath = mergedAudioPath,
                        correctAnswer = existing.correctAnswer?.takeIf { it.isNotBlank() } ?: item.correctAnswer,
                        mistakeType = existing.mistakeType ?: item.mistakeType,
                        note = existing.note?.takeIf { it.isNotBlank() } ?: item.note,
                        aiExplanation = existing.aiExplanation?.takeIf { it.isNotBlank() } ?: item.aiExplanation,
                        isFavorite = existing.isFavorite || item.isFavorite,
                        isSolved = progressIsSolved,
                        solvedAt = progressSolvedAt,
                        studentAnswer = progressStudent,
                        reviewStage = maxOf(existing.reviewStage, item.reviewStage),
                        nextReviewDate = if (adoptIncoming) (item.nextReviewDate ?: existing.nextReviewDate) else existing.nextReviewDate,
                        lastReviewedAt = maxOf(existingReviewed, incomingReviewed).takeIf { it > 0 },
                        kaynakYayin = existing.kaynakYayin?.takeIf { it.isNotBlank() } ?: item.kaynakYayin,
                        kaynakDeneme = existing.kaynakDeneme?.takeIf { it.isNotBlank() } ?: item.kaynakDeneme,
                        kaynakYil = existing.kaynakYil ?: item.kaynakYil,
                        kaynakSoruNo = existing.kaynakSoruNo ?: item.kaynakSoruNo,
                        difficulty = existing.difficulty?.takeIf { it.isNotBlank() } ?: item.difficulty,
                        ocrMetni = existing.ocrMetni?.takeIf { it.isNotBlank() } ?: item.ocrMetni,
                        isOcrUsed = existing.isOcrUsed || item.isOcrUsed,
                        questionText = existing.questionText?.takeIf { it.isNotBlank() } ?: item.questionText,
                        fsrsStability = if (adoptIncoming) (item.fsrsStability ?: existing.fsrsStability) else existing.fsrsStability,
                        fsrsDifficulty = if (adoptIncoming) (item.fsrsDifficulty ?: existing.fsrsDifficulty) else existing.fsrsDifficulty,
                        fsrsReps = if (adoptIncoming) maxOf(existing.fsrsReps, item.fsrsReps) else existing.fsrsReps,
                        fsrsLapses = if (adoptIncoming) maxOf(existing.fsrsLapses, item.fsrsLapses) else existing.fsrsLapses,
                        contentHash = existing.contentHash
                    )

                    // idMap: bağlı kayıtlar (koç/benzer/çizim) MEVCUT soruya bağlanacak;
                    // bu id ayrı haritada tutulur ve insert haritası kurulduktan sonra birleştirilir.
                    val originalId = item.originalId ?: (newSorular.size + 1).toLong()

                    if (merged == existing) {
                        guncelSoru++
                    } else {
                        try {
                            soruDedupDao.updateEntityFull(merged.copy(updatedAt = System.currentTimeMillis()))
                            guncellenenSoru++
                            // Aynı yedekte aynı sorunun ikinci kopyası gelirse, bir önceki
                            // merge'den sonraki durumu görerek evrilsin (stale snapshot koruması).
                            existingByHash[existing.contentHash] = merged
                        } catch (e: Exception) {
                            Log.w(TAG, "Merge güncellemesi başarısız (${item.originalId}): ${e.message}")
                        }
                    }
                    // idMap'e merge edilenleri de BİLİNÇLİ eklemeyeceğiz: kayıt listesine girmediler.
                    // Ancak koç/benzer/çizim bağı eşleşmesi için eşleşen id ayrı haritada tutulur:
                    matchedOriginalToNewId[originalId] = existing.id
                    continue
                }

                // ===== YENİ KAYIT — klasik ekleme akışı =====
                // DÜZELTME: Görsel eksik olsa bile soru ATILMAZ.
                // Önceden `continue` ile sorunun tamamı (aiExplanation, audioPath,
                // not, çözüm görseli, FSRS durumu) sessizce kayboluyordu. Bu, kullanıcının
                // "çözümlerim ve ses kayıtlarım yedeklemede aktarılmıyor" şikâyetini
                // üreten asıl veri kaybı yoluydu. Artık görsel yoksa soru metin olarak
                // geri yüklenir ve kaç tane bu şekilde geldiği kullanıcıya bildirilir.
                var permanentSoruPath = ""

                val imageName = item.imageName
                if (imageName.isNotBlank()) {
                    val imageFile = File(tempExtractDir, imageName).canonicalFile
                    // Path traversal koruması: görsel tempExtractDir içinde olmalı
                    val guvenliYol = imageFile.path.startsWith(canonicalExtractDir.path + File.separator)
                    if (guvenliYol && imageFile.exists() && imageFile.isFile) {
                        permanentSoruPath = try {
                            gorselDepo.kaydetDosyadan(imageFile, isSolution = false)
                        } catch (e: Exception) {
                            // Bozuk görsel tüm içe aktarmayı düşürmemeli
                            Log.w(TAG, "Soru görseli çözülemedi (${item.originalId}): ${e.message}")
                            ""
                        }
                    } else {
                        Log.w(TAG, "Soru görseli eksik, metin olarak alınıyor: $imageName")
                    }
                }
                if (permanentSoruPath.isBlank()) gorselsizImportEdilen++

                var permanentSolutionPath: String? = null
                if (!item.solutionImageName.isNullOrBlank()) {
                    val solFile = File(tempExtractDir, item.solutionImageName).canonicalFile
                    if (solFile.path.startsWith(canonicalExtractDir.path + File.separator) && solFile.exists() && solFile.isFile) {
                        // DÜZELTME: Bu kopya try/catch içinde DEĞİLDİ. Bozuk tek bir
                        // çözüm görseli (decode edilemeyen dosya) istisna fırlatıp
                        // TÜM içe aktarmayı yarıda bırakıyordu; o ana kadar yazılan
                        // görseller, sorular, ses kayıtları diske yazılmış olarak
                        // kalıyordu. Soru görseliyle aynı davranışa getirildi.
                        permanentSolutionPath = try {
                            gorselDepo.kaydetDosyadan(solFile, isSolution = true)
                        } catch (e: Exception) {
                            Log.w(TAG, "Çözüm görseli çözülemedi (${item.originalId}): ${e.message}")
                            null
                        }
                    }
                }

                var permanentAudioPath: String? = null
                if (!item.audioFileName.isNullOrBlank()) {
                    val aFile = File(tempExtractDir, item.audioFileName).canonicalFile
                    if (aFile.path.startsWith(canonicalExtractDir.path + File.separator) && aFile.exists() && aFile.isFile) {
                        val destAudio = File(audioDir, "audio_${UUID.randomUUID()}.m4a")
                        aFile.copyTo(destAudio, overwrite = true)
                        permanentAudioPath = destAudio.absolutePath
                    }
                }

                newSorular.add(
                    Soru(
                        id = 0L,
                        imagePath = permanentSoruPath,
                        solutionImagePath = permanentSolutionPath,
                        correctAnswer = item.correctAnswer,
                        examType = item.examType,
                        subjectId = item.subjectId,
                        topic = item.topic,
                        mistakeType = item.mistakeType?.let { HataTipi.fromName(it) },
                        note = item.note,
                        isSolved = item.isSolved,
                        solvedAt = item.solvedAt,
                        aiExplanation = item.aiExplanation,
                        isFavorite = item.isFavorite,
                        reviewStage = item.reviewStage,
                        nextReviewDate = item.nextReviewDate,
                        lastReviewedAt = item.lastReviewedAt,
                        kaynakYayin = item.kaynakYayin,
                        kaynakDeneme = item.kaynakDeneme,
                        kaynakYil = item.kaynakYil,
                        kaynakSoruNo = item.kaynakSoruNo,
                        studentAnswer = item.studentAnswer,
                        difficulty = item.difficulty,
                        ocrMetni = item.ocrMetni,
                        isOcrUsed = item.isOcrUsed,
                        questionText = item.questionText,
                        audioPath = permanentAudioPath,
                        fsrsStability = item.fsrsStability,
                        fsrsDifficulty = item.fsrsDifficulty,
                        fsrsReps = item.fsrsReps,
                        fsrsLapses = item.fsrsLapses,
                        createdAt = item.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        contentHash = contentHash
                    )
                )
                // Gerçek id yoksa (eski manifest) YENİ LİSTEDEKİ konumu kullan.
                // Önceden `idx` (manifest sırası) kullanılıyordu; bir kayıt atlandığında
                // sonraki tüm eşlemeler kayıyor ve Sokratik Koç / Benzer Soru /
                // Çizim kayıtları YANLIŞ soruya bağlanıyordu.
                val originalIdYeni = item.originalId ?: (newSorular.size).toLong()
                itemOriginalIds.add(originalIdYeni)
            }

            val idMap = mutableMapOf<Long, Long>()
            if (newSorular.isNotEmpty()) {
                val insertedIds = soruRepository.insertAllSorular(newSorular)
                for (i in newSorular.indices) {
                    if (i < insertedIds.size && i < itemOriginalIds.size) {
                        idMap[itemOriginalIds[i]] = insertedIds[i]
                    }
                }
                // DÜZELTME: Room beklenenden az id döndürürse (ör. bir insert
                // sessizce düşürülürse) eşlemenin geri kalanı boş kalıyor ve
                // Sokratik Koç / Benzer Soru / Çizim kayıtları sessizce kayboluyordu.
                // Artık fark açıkça raporlanıyor.
                if (insertedIds.size < newSorular.size) {
                    Log.w(
                        TAG,
                        "DİKKAT: ${newSorular.size} sorudan ${insertedIds.size} tanesi " +
                            "veritabanına yazıldı. ${newSorular.size - insertedIds.size} sorunun " +
                            "ilişkili kayıtları (Sokratik Koç / Benzer Soru / Çizim) geri yüklenemedi."
                    )
                }
            }

            // Merge edilen (içerik eşleşen) soruların eşlemesini de haritaya ekle:
            // bu id'ler MEVCUT satırlara işaret eder, bağlı kayıtlar onlara bağlanır.
            idMap.putAll(matchedOriginalToNewId)

            // 4. Scratchpad Çizimlerini Geri Yükle
            val extractedScratchpads = File(tempExtractDir, "scratchpads")
            if (extractedScratchpads.exists() && extractedScratchpads.isDirectory) {
                extractedScratchpads.listFiles()?.forEach { f ->
                    val name = f.name
                    val oldId = name.removePrefix("soru_").removeSuffix(".json").toLongOrNull()
                    if (oldId != null) {
                        // DÜZELTME: `?: oldId` fallback'i, eşleme bulunamazsa
                        // çizimi O id'li BAŞKA bir sorunun üzerine yazıyordu
                        // (overwrite = true), yani mevcut bir çizim kalıcı olarak
                        // bozuluyordu. Eşleme yoksa kayıt atlanır; atlananlar
                        // kullanıcıya raporlanır.
                        val newId = idMap[oldId]
                        if (newId != null) {
                            f.copyTo(File(scratchpadDir, "soru_${newId}.json"), overwrite = true)
                        } else {
                            cizimAtlanan++
                        }
                    }
                }
            }

            // 5. Test Sonuçlarını Geri Yükle (akıllı içe aktarma: aynı zaman damgası + aynı sayaçlar = mükerrer, atla)
            var mukerrerTest = 0
            testSonucDao?.let { dao ->
                manifest.testSonuclari.forEach { t ->
                    if (dao.existsDuplicate(t.completedAt, t.totalQuestions, t.correctCount, t.wrongCount)) {
                        mukerrerTest++
                        return@forEach
                    }
                    dao.insertTestResult(
                        TestSonucEntity(
                            id = 0L,
                            totalQuestions = t.totalQuestions,
                            correctCount = t.correctCount,
                            wrongCount = t.wrongCount,
                            emptyCount = t.emptyCount,
                            netCount = t.netCount,
                            successRate = t.successRate,
                            totalTimeSeconds = t.totalTimeSeconds,
                            examType = t.examType,
                            completedAt = t.completedAt
                        )
                    )
                }
            }

            // 6. Soru Chat Mesajlarını Geri Yükle (Sokratik Koç geçmişi)
            var chatAtlanan = 0
            var mukerrerChat = 0
            soruChatDao?.let { dao ->
                manifest.soruChatMesajlari.forEach { c ->
                    // DÜZELTME: `?: c.originalSoruId` fallback'i yanlış soruya bağlıyordu
                    // ve geçersiz id FK ihlali (ON DELETE CASCADE) yüzünden TÜM içe
                    // aktarmayı yarıda bırakıp hata veriyordu. Eşleme yoksa kayıt
                    // atlanır; her kayıt ayrıca try/catch ile izole edilir.
                    val mappedSoruId = idMap[c.originalSoruId]
                    if (mappedSoruId == null) {
                        chatAtlanan++
                        return@forEach
                    }
                    // Akıllı içe aktarma: aynı soru + aynı zaman + aynı gönderen + aynı metin = mükerrer
                    if (dao.existsDuplicate(mappedSoruId, c.timestamp, c.sender, c.messageText)) {
                        mukerrerChat++
                        return@forEach
                    }
                    try {
                        dao.insertMessage(
                            SoruChatMessageEntity(
                                id = 0L,
                                soruId = mappedSoruId,
                                sender = c.sender,
                                messageText = c.messageText,
                                timestamp = c.timestamp
                            )
                        )
                    } catch (e: Exception) {
                        chatAtlanan++
                        Log.w(TAG, "Sokratik Koç mesajı atlandı (${c.originalSoruId}): ${e.message}")
                    }
                }
            }

            // 7. Benzer Soruları Geri Yükle
            var benzerAtlanan = 0
            var mukerrerBenzer = 0
            benzerSoruDao?.let { dao ->
                manifest.benzerSorular.forEach { b ->
                    val mappedSoruId = idMap[b.originalSoruId]
                    if (mappedSoruId == null) {
                        benzerAtlanan++
                        return@forEach
                    }
                    // Akıllı içe aktarma: aynı soru + aynı metin + aynı tarih = mükerrer
                    if (dao.existsDuplicate(mappedSoruId, b.questionText, b.createdAt)) {
                        mukerrerBenzer++
                        return@forEach
                    }
                    try {
                        dao.insertBenzerSoru(
                            BenzerSoruEntity(
                                id = 0L,
                                soruId = mappedSoruId,
                                questionText = b.questionText,
                                optionsJson = b.optionsJson,
                                correctAnswer = b.correctAnswer,
                                solution = b.solution,
                                createdAt = b.createdAt
                            )
                        )
                    } catch (e: Exception) {
                        benzerAtlanan++
                        Log.w(TAG, "Benzer soru atlandı (${b.originalSoruId}): ${e.message}")
                    }
                }
            }

            if (gorselsizImportEdilen > 0 || chatAtlanan > 0 || benzerAtlanan > 0) {
                Log.w(
                    TAG,
                    "İçe aktarma özeti: ${newSorular.size} soru, $gorselsizImportEdilen görselsiz, " +
                        "$chatAtlanan Sokratik Koç mesajı ve $benzerAtlanan benzer soru atlandı."
                )
            }

            // 8. Deneme Sınavlarını Geri Yükle (akıllı içe aktarma: aynı tarih + başlık + net = mükerrer, atla)
            var mukerrerDeneme = 0
            denemeSinaviDao?.let { dao ->
                manifest.denemeler.forEach { d ->
                    if (dao.existsDuplicate(d.tarih, d.baslik, d.toplamNet)) {
                        mukerrerDeneme++
                        return@forEach
                    }
                    dao.insertDeneme(
                        DenemeSinaviEntity(
                            id = 0L,
                            baslik = d.baslik,
                            sinavTuru = d.sinavTuru,
                            tarih = d.tarih,
                            toplamNet = d.toplamNet,
                            dersNetleriJson = d.dersNetleriJson,
                            notlar = d.notlar,
                            createdAt = d.createdAt
                        )
                    )
                }
            }

            // 9. Oyunlaştırma Durumunu Geri Yükle
            manifest.gamificationData?.let { g ->
                ayarlarDataStore?.restoreGamificationState(
                    streak = g.currentStreak,
                    lastStreakDate = g.lastStreakDate,
                    streakShieldCount = g.streakShieldCount,
                    xp = g.xp,
                    badges = g.unlockedBadges.toSet()
                )
            }

            // Kullanıcıya GÖSTER: kaç yeni soru eklendi, kaç mevcut kayıt güncellendi,
            // kaçı zaten günceldi ve ne kadarı eksik/atlendi.
            // Önceden bu bilgiler yalnızca Log.w ile kaydediliyor, UI sadece
            // "N soru eklendi" diyordu; kullanıcı eksik gelen çözüm veya
            // ses kayıtlarının farkında bile olmuyordu.
            // Not: importBackup imzası Result<Int> olarak kalıyor (testler ve
            // çağıranlar bozulmasın); ayrıntılar bu alandan okunuyor.
            val atlananToplam = chatAtlanan + benzerAtlanan
            sonImportOzeti = ImportOzeti(
                soruSayisi = newSorular.size,
                gorselsizSorular = gorselsizImportEdilen,
                atlananChatMesaji = chatAtlanan,
                atlananBenzerSoru = benzerAtlanan,
                guncellenenSoru = guncellenenSoru,
                guncelSoru = guncelSoru,
                tanimsizSoru = tanimsizSoru,
                mukerrerTest = mukerrerTest,
                mukerrerDeneme = mukerrerDeneme,
                mukerrerChatMesaji = mukerrerChat,
                mukerrerBenzerSoru = mukerrerBenzer
            )
            if (gorselsizImportEdilen > 0 || atlananToplam > 0 || cizimAtlanan > 0 || guncellenenSoru > 0 || guncelSoru > 0) {
                val detaylar = buildList {
                    if (guncellenenSoru > 0) add("$guncellenenSoru mevcut kayıt güncellendi (aynı soru zaten var: birleştirildi)")
                    if (guncelSoru > 0) add("$guncelSoru soru zaten güncel (atlandı)")
                    if (gorselsizImportEdilen > 0) add("$gorselsizImportEdilen sorunun görseli bulunamadı")
                    if (chatAtlanan > 0) add("$chatAtlanan Sokratik Koç mesajı atlandı")
                    if (benzerAtlanan > 0) add("$benzerAtlanan benzer soru atlandı")
                    if (cizimAtlanan > 0) add("$cizimAtlanan çizim aktarılamadı")
                    if (mukerrerTest > 0) add("$mukerrerTest mükerrer test sonucu atlandı")
                    if (mukerrerDeneme > 0) add("$mukerrerDeneme mükerrer deneme atlandı")
                }.joinToString(", ")
                Log.w(TAG, "İçe aktarma özeti: $detaylar")
            }
            Result.success(newSorular.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                tempExtractDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    /**
     * PBKDF2WithHmacSHA256 kullanarak paroladan 256-bit AES anahtarı türetir.
     * Yeni yedekler 120k iterasyon; eski 10k yedekler import'ta otomatik denenir.
     */
    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        try { keySpec.clearPassword() } catch (_: Exception) {}
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun extractZipSafely(zipIn: ZipInputStream, destDir: File) {
        var entry: ZipEntry?
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            entry = zipIn.nextEntry
        } catch (e: Exception) {
            throw SecurityException("Yedek şifresi hatalı veya dosya bozulmuş: ${e.localizedMessage}", e)
        }
        val canonicalDestDir = destDir.canonicalFile
        var entryCount = 0
        var totalBytes = 0L

        while (entry != null) {
            entryCount++
            if (entryCount > MAX_ZIP_ENTRIES) {
                throw SecurityException("ZIP arşivinde çok fazla dosya var (Zip Bomb koruması: max $MAX_ZIP_ENTRIES)")
            }

            val destinationFile = File(destDir, entry.name).canonicalFile
            if (!destinationFile.path.startsWith(canonicalDestDir.path + File.separator) && destinationFile != canonicalDestDir) {
                throw SecurityException("Güvensiz zip içeriği tespit edildi (Path Traversal Saldırısı): ${entry.name}")
            }
            if (entry.isDirectory) {
                destinationFile.mkdirs()
            } else {
                destinationFile.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(destinationFile).use { out ->
                    var len: Int
                    while (zipIn.read(buffer).also { len = it } > 0) {
                        entryBytes += len
                        totalBytes += len
                        if (entryBytes > MAX_SINGLE_FILE_SIZE || totalBytes > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                            throw SecurityException("Yedek paketi dosya boyutu sınırını aştı (Zip Bomb koruması)")
                        }
                        out.write(buffer, 0, len)
                    }
                }
            }
            zipIn.closeEntry()
            entry = try { zipIn.nextEntry } catch (e: Exception) {
                throw SecurityException("Yedek şifresi hatalı veya dosya bozulmuş: ${e.localizedMessage}", e)
            }
        }
    }
}

