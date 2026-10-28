package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru

/**
 * Soruların Room veritabanı tablosundaki karşılığı
 */
@Entity(
    tableName = "sorular",
    indices = [
        Index(value = ["examType"]),
        Index(value = ["subjectId"]),
        Index(value = ["topic"]),
        Index(value = ["mistakeType"]),
        Index(value = ["isSolved"]),
        Index(value = ["isFavorite"]),
        Index(value = ["createdAt"]),
        Index(value = ["nextReviewDate"]),
        Index(value = ["reviewStage"]),
        // Akıllı içe aktarma (dedup/merge): aynı içerikli sorunun hızlı tespiti için
        Index(value = ["contentHash"]),
        // Cihazlar-arası eşleşme: evrensel kimlik tekildir, hiç değişmez
        Index(value = ["evrenselId"], unique = true),
        // Deneme sınavına bağlı yanlış soruların hızlı sorgulanması için
        Index(value = ["denemeId"])
    ]
)
data class SoruEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val solutionImagePath: String? = null,
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
    val audioPath: String? = null,
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Akıllı içe aktarma için içerik parmak izi (SHA-256, onaltılık).
     * Soru metni/OCR metni + ders/konu + kaynak bilgileriyle hesaplanır; görsel
     * yolu, timestamp'ler ve kullanıcı durumları (favori, tekrar aşaması) dahil
     * değildir — böylece aynı soru farklı cihaz/yedek sürümlerinde bile tanınır.
     * Boş string = hesaplanamadı (örn. metinsiz, kaynak bilgisi yoksun soru).
     */
    val contentHash: String = "",
    /**
     * Cihazlar-arası evrensel kimlik (UUID v4). Soru oluştuğu cihazda bir kez
     * üretilir, eşleşmeyle taşınır, HİÇ değişmez. Boş = v12 öncesi kayıt
     * (migration geriye dönük doldurur).
     */
    val evrenselId: String = "",
    /**
     * Soft-delete bayrağı (0/1). Eşleşmede silme yayılımı (tombstone) için;
     * kalıcı silme v2'de.
     */
    val isDeleted: Int = 0,
    /** Deneme sınavı ile çift yönlü ilişki (denemeler.id). */
    val denemeId: Long? = null,
    /** Deneme kitapçığındaki soru numarası (1..120). */
    val denemeSoruNo: Int? = null
) {
    /**
     * Entity'yi Domain modeline dönüştürür.
     */
    fun toDomain(): Soru {
        return Soru(
            id = id,
            imagePath = imagePath,
            solutionImagePath = solutionImagePath,
            correctAnswer = correctAnswer,
            examType = examType,
            subjectId = subjectId,
            topic = topic,
            mistakeType = mistakeType?.let { HataTipi.fromName(it) },
            note = note,
            isSolved = isSolved,
            solvedAt = solvedAt,
            aiExplanation = aiExplanation,
            isFavorite = isFavorite,
            reviewStage = reviewStage,
            nextReviewDate = nextReviewDate,
            lastReviewedAt = lastReviewedAt,
            kaynakYayin = kaynakYayin,
            kaynakDeneme = kaynakDeneme,
            kaynakYil = kaynakYil,
            kaynakSoruNo = kaynakSoruNo,
            studentAnswer = studentAnswer,
            difficulty = difficulty,
            ocrMetni = ocrMetni,
            isOcrUsed = isOcrUsed,
            questionText = questionText,
            audioPath = audioPath,
            fsrsStability = fsrsStability,
            fsrsDifficulty = fsrsDifficulty,
            fsrsReps = fsrsReps,
            fsrsLapses = fsrsLapses,
            createdAt = createdAt,
            updatedAt = updatedAt,
            contentHash = contentHash,
            evrenselId = evrenselId,
            isDeleted = isDeleted,
            denemeId = denemeId,
            denemeSoruNo = denemeSoruNo
        )
    }
}

/**
 * Soru domain modelini Room Entity'ye dönüştürür.
 */
fun Soru.toEntity(): SoruEntity {
    return SoruEntity(
        id = id,
        imagePath = imagePath,
        solutionImagePath = solutionImagePath,
        correctAnswer = correctAnswer,
        examType = examType,
        subjectId = subjectId,
        topic = topic,
        mistakeType = mistakeType?.name,
        note = note,
        isSolved = isSolved,
        solvedAt = solvedAt,
        aiExplanation = aiExplanation,
        isFavorite = isFavorite,
        reviewStage = reviewStage,
        nextReviewDate = nextReviewDate,
        lastReviewedAt = lastReviewedAt,
        kaynakYayin = kaynakYayin,
        kaynakDeneme = kaynakDeneme,
        kaynakYil = kaynakYil,
        kaynakSoruNo = kaynakSoruNo,
        studentAnswer = studentAnswer,
        difficulty = difficulty,
        ocrMetni = ocrMetni,
        isOcrUsed = isOcrUsed,
        questionText = questionText,
        audioPath = audioPath,
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsReps = fsrsReps,
        fsrsLapses = fsrsLapses,
        createdAt = createdAt,
        updatedAt = updatedAt,
        contentHash = contentHash,
        evrenselId = evrenselId,
        isDeleted = isDeleted,
        denemeId = denemeId,
        denemeSoruNo = denemeSoruNo
    )
}

