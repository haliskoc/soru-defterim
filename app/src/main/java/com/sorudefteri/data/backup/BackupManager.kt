package com.sorudefteri.data.backup

import android.content.Context
import android.net.Uri
import com.sorudefteri.data.images.GorselDepo
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
    val version: Int = 2,
    val exportDate: Long = System.currentTimeMillis(),
    val questionCount: Int,
    val sorular: List<BackupSoruItem>
)

@Serializable
data class BackupSoruItem(
    val imageName: String,
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
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Soruların, fotoğrafların, cevapların ve AI çözümlerinin tek bir .sdefteri paketi
 * halinde dışa ve içe aktarılmasını sağlayan dayanıklı, AES-256-GCM şifreli yedekleme yöneticisi.
 */
@Singleton
open class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soruRepository: SoruRepository,
    private val gorselDepo: GorselDepo,
    private val json: Json
) {

    companion object {
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
    suspend fun exportBackupToTempFile(password: String? = null): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
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

                    for ((index, soru) in sorular.withIndex()) {
                        val soruFile = File(soru.imagePath)
                        // Deterministic: soru.id tabanlı, id 0 ise index fallback
                        val imageKey = if (soru.id != 0L) soru.id else (index + 1).toLong()
                        val uniqueImageName = "images/soru_${imageKey}.jpg"

                        if (soruFile.exists() && soruFile.isFile) {
                            zipOut.putNextEntry(ZipEntry(uniqueImageName))
                            FileInputStream(soruFile).use { fileIn ->
                                var len: Int
                                while (fileIn.read(buffer).also { len = it } > 0) {
                                    zipOut.write(buffer, 0, len)
                                }
                            }
                            zipOut.closeEntry()
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

                        backupItems.add(
                            BackupSoruItem(
                                imageName = uniqueImageName,
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
                                createdAt = soru.createdAt
                            )
                        )
                    }

                    // Manifest dosyasını ZIP kök dizinine ekle
                    val manifest = BackupManifest(
                        questionCount = backupItems.size,
                        sorular = backupItems
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

            // 3. Görselleri kalıcı depoya (filesDir/images) kopyala ve Entity'leri hazırla
            val newSorular = mutableListOf<Soru>()
            val canonicalExtractDir = tempExtractDir.canonicalFile

            for (item in manifest.sorular) {
                val imageFile = File(tempExtractDir, item.imageName).canonicalFile
                // Path traversal koruması: görsel tempExtractDir içinde olmalı
                if (!imageFile.path.startsWith(canonicalExtractDir.path + File.separator)) {
                    continue
                }
                if (!imageFile.exists() || !imageFile.isFile) {
                    continue // Eksik soru görseli varsa atla
                }

                val permanentSoruPath = gorselDepo.kaydetDosyadan(imageFile, isSolution = false)

                var permanentSolutionPath: String? = null
                if (!item.solutionImageName.isNullOrBlank()) {
                    val solFile = File(tempExtractDir, item.solutionImageName).canonicalFile
                    if (solFile.path.startsWith(canonicalExtractDir.path + File.separator) && solFile.exists() && solFile.isFile) {
                        permanentSolutionPath = gorselDepo.kaydetDosyadan(solFile, isSolution = true)
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
                        createdAt = item.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            if (newSorular.isNotEmpty()) {
                soruRepository.insertAllSorular(newSorular)
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

