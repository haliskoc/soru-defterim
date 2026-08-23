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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupManifest(
    val appSignature: String = "com.sorudefteri",
    val version: Int = 1,
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
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Soruların, fotoğrafların, cevapların ve AI çözümlerinin tek bir .sdefteri (.zip) paketi
 * halinde dışa ve içe aktarılmasını sağlayan dayanıklı ve fail-safe yedekleme yöneticisi.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soruRepository: SoruRepository,
    private val gorselDepo: GorselDepo,
    private val json: Json
) {

    private val BUFFER_SIZE = 8192

    /**
     * Tüm veritabanı kayıtlarını ve görselleri URI üzerinden verilen çıkış akışına ZIP (.sdefteri) olarak paketler.
     * @return Dışa aktarılan soru sayısı
     */
    suspend fun exportBackup(targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(Exception("Hedef dosya yazılamadı."))

            outputStream.use { rawOut ->
                exportBackupToStream(rawOut)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun exportBackupToStream(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sorular = soruRepository.getAllSorular().first()
            val backupItems = mutableListOf<BackupSoruItem>()

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                val buffer = ByteArray(BUFFER_SIZE)

                for ((index, soru) in sorular.withIndex()) {
                    val soruFile = File(soru.imagePath)
                    val uniqueImageName = "images/soru_${index}_${UUID.randomUUID()}.jpg"

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
                            uniqueSolutionName = "images/cozum_${index}_${UUID.randomUUID()}.jpg"
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
                            createdAt = soru.createdAt
                        )
                    )
                }

                // Manifest dosyasını kök dizine ekle
                val manifest = BackupManifest(
                    questionCount = backupItems.size,
                    sorular = backupItems
                )
                val manifestJson = json.encodeToString(manifest)
                zipOut.putNextEntry(ZipEntry("backup_manifest.json"))
                zipOut.write(manifestJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
                zipOut.flush()
            }

            Result.success(sorular.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Seçilen .sdefteri (.zip) yedek dosyasını açar, doğrular, görselleri kalıcı depoya kopyalar
     * ve soruları veritabanına ekler.
     * @return İçe aktarılan soru sayısı
     */
    suspend fun importBackup(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val tempExtractDir = File(context.cacheDir, "temp_import_${UUID.randomUUID()}")
        try {
            tempExtractDir.mkdirs()

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Yedek dosyası açılamadı."))

            // 1. ZIP içeriğini geçici klasöre aç
            inputStream.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    val buffer = ByteArray(BUFFER_SIZE)

                    while (entry != null) {
                        val newFile = File(tempExtractDir, entry.name)

                        // Path traversal güvenlik kontrolü
                        if (!newFile.canonicalPath.startsWith(tempExtractDir.canonicalPath)) {
                            throw SecurityException("Güvensiz zip içeriği tespit edildi.")
                        }

                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { out ->
                                var len: Int
                                while (zipIn.read(buffer).also { len = it } > 0) {
                                    out.write(buffer, 0, len)
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            // 2. Manifest kontrolü
            val manifestFile = File(tempExtractDir, "backup_manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("Geçersiz yedek dosyası: backup_manifest.json bulunamadı."))
            }

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val manifest = try {
                json.decodeFromString<BackupManifest>(manifestText)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Yedek dosyası ayrıştırılamadı: ${e.localizedMessage}"))
            }

            if (manifest.appSignature != "com.sorudefteri") {
                return@withContext Result.failure(Exception("Bu dosya geçerli bir Soru Defterim yedeği değil."))
            }

            // 3. Görselleri kalıcı depoya (filesDir/images) kopyala ve Entity'leri hazırla
            val newSorular = mutableListOf<Soru>()

            for (item in manifest.sorular) {
                val imageFile = File(tempExtractDir, item.imageName)
                if (!imageFile.exists() || !imageFile.isFile) {
                    continue // Eksik görsel varsa bu soruyu atla
                }

                val permanentSoruPath = gorselDepo.kaydetDosyadan(imageFile, isSolution = false)

                var permanentSolutionPath: String? = null
                if (!item.solutionImageName.isNullOrBlank()) {
                    val solFile = File(tempExtractDir, item.solutionImageName)
                    if (solFile.exists() && solFile.isFile) {
                        permanentSolutionPath = gorselDepo.kaydetDosyadan(solFile, isSolution = true)
                    }
                }

                newSorular.add(
                    Soru(
                        id = 0L, // Yeni ID otomatik atanacak
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
            // Geçici klasörü temizle
            try {
                tempExtractDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
