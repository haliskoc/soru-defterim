package com.sorudefteri.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain Soru Modeli
 */
@Immutable
data class Soru(
    val id: Long = 0L,
    val imagePath: String,
    val solutionImagePath: String? = null,
    val correctAnswer: String? = null,
    val examType: String, // "TYT" or "AYT"
    val subjectId: String,
    val topic: String,
    val mistakeType: HataTipi? = null,
    val note: String? = null,
    val isSolved: Boolean = false,
    val solvedAt: Long? = null,
    val aiExplanation: String? = null,
    val isFavorite: Boolean = false,
    val reviewStage: Int = 0, // 0: Yeni, 1: 1 Gün, 2: 3 Gün, 3: 7 Gün, 4: 14 Gün, 5: 30 Gün (Usta)
    val nextReviewDate: Long? = null, // Sonraki tekrar zamanı (timestamp)
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
    /** İçerik parmak izi (akıllı içe aktarma/dedup için) — boş = hesaplanamadı. */
    val contentHash: String = "",
    /** Cihazlar-arası evrensel kimlik (UUID v4, değişmez) — boş = henüz atanmadı. */
    val evrenselId: String = "",
    /** Soft-delete (0/1) — eşleşmede silme yayılımı için. */
    val isDeleted: Int = 0,
    /** Deneme sınavı ile çift yönlü ilişki (denemeler.id). */
    val denemeId: Long? = null,
    /** Deneme kitapçığındaki soru numarası (1..120). */
    val denemeSoruNo: Int? = null
) {
    /**
     * Sorunun tekrar zamanının gelip gelmediğini kontrol eder.
     */
    val isDueForReview: Boolean
        get() {
            val next = nextReviewDate ?: return !isSolved
            return System.currentTimeMillis() >= next
        }
}

/**
 * PERF: Kütüphane başlık/meta sayıları — tam liste çekmeden tekil
 * Room `@Query`leri ile hesaplanır (bkz. `SoruRepository.getKutuphaneMeta`).
 */
data class KutuphaneMeta(
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val favoriteCount: Int = 0,
    val dueReviewCount: Int = 0,
    val availablePublishers: List<String> = emptyList(),
    val availableExamNames: List<String> = emptyList()
)

