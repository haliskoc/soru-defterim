package com.sorudefteri.domain.repository

import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.domain.model.BenzerSoru
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.IstatistikOzetWrapper
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import kotlinx.coroutines.flow.Flow

/**
 * Soru veri deposu arayüzü (Domain Repository)
 */
interface SoruRepository {

    val totalCount: Flow<Int>
    val solvedCount: Flow<Int>
    val unsolvedCount: Flow<Int>

    fun getAllSorular(): Flow<List<Soru>>

    fun getFilteredSorular(
        examType: String? = null,
        subjectId: String? = null,
        mistakeType: String? = null,
        isSolved: Boolean? = null
    ): Flow<List<Soru>>

    fun searchSorular(query: String): Flow<List<Soru>>

    fun searchSorularFts(query: String): Flow<List<Soru>> = searchSorular(query)

    fun getSoruById(id: Long): Flow<Soru?>

    suspend fun getSoru(id: Long): Soru?

    suspend fun getSoruDirect(id: Long): Soru? = getSoru(id)

    suspend fun soruEkle(soru: Soru): Long

    suspend fun insertSoru(soru: Soru): Long = soruEkle(soru)

    suspend fun soruGuncelle(soru: Soru)

    suspend fun updateSoru(soru: Soru) = soruGuncelle(soru)

    suspend fun soruSil(soru: Soru)

    suspend fun deleteSoru(soru: Soru) = soruSil(soru)

    suspend fun soruSilById(id: Long)

    suspend fun deleteSoruById(id: Long) = soruSilById(id)

    suspend fun cozumDurumuDegistir(id: Long, isSolved: Boolean)

    suspend fun updateSolvedStatus(id: Long, isSolved: Boolean, solvedAt: Long? = null) {
        cozumDurumuDegistir(id, isSolved)
    }

    suspend fun notGuncelle(id: Long, not: String?)

    suspend fun updateNote(id: Long, note: String?) = notGuncelle(id, note)

    suspend fun cozumGorseliGuncelle(id: Long, solutionPath: String?)

    suspend fun updateSolutionImage(id: Long, solutionImagePath: String?) = cozumGorseliGuncelle(id, solutionImagePath)

    suspend fun aiCozumuKaydet(id: Long, aiExplanation: String)

    suspend fun updateAiExplanation(id: Long, aiExplanation: String?) {
        if (aiExplanation != null) {
            aiCozumuKaydet(id, aiExplanation)
        }
    }

    suspend fun tumVerileriTemizle()

    suspend fun deleteAllSorular() = tumVerileriTemizle()

    val favoriteCount: Flow<Int>
    val dueReviewCount: Flow<Int>

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun updateReviewStatus(id: Long, stage: Int, nextReviewDate: Long?, lastReviewedAt: Long)
    suspend fun advanceReviewStage(id: Long)
    suspend fun regressReviewStage(id: Long)
    suspend fun regressReviewStageBatch(ids: List<Long>)
    suspend fun deleteSorularByIds(ids: List<Long>)
    suspend fun updateSolvedStatusBatch(ids: List<Long>, isSolved: Boolean)
    suspend fun updateFavoriteStatusBatch(ids: List<Long>, isFavorite: Boolean)
    suspend fun insertAllSorular(sorular: List<Soru>): List<Long>

    /**
     * Akıllı içe aktarma bakımı: içeriği tanımlanabilir ancak içerik parmak izi
     * (contentHash) henüz yazılmamış eski kayıtları doldurur.
     * Dönen değer güncellenen kayıt sayısıdır.
     */
    suspend fun backfillContentHashes(): Int
    fun getFavoriteSorular(): Flow<List<Soru>>
    fun getDueReviewSorular(): Flow<List<Soru>>
    suspend fun getDueReviewSorularList(currentTime: Long = System.currentTimeMillis()): List<Soru>

    fun getPagedSorular(
        query: String? = null,
        examType: String? = null,
        subjectId: String? = null,
        mistakeType: String? = null,
        isSolved: Boolean? = null,
        isFavorite: Boolean? = null,
        onlyDueReview: Boolean? = null,
        kaynak: String? = null
    ): Flow<androidx.paging.PagingData<Soru>>

    fun getDistinctTopics(subjectId: String): Flow<List<String>>

    fun getIstatistikler(): Flow<IstatistikOzetWrapper>

    fun getSubjectStats(): Flow<List<DersSoruSayisi>>

    fun getMistakeStats(): Flow<List<HataTipiDagilimi>>

    fun getWeeklyActivity(): Flow<List<GunlukAktivite>>
    fun getAllTestResults(): Flow<List<TestSonucEntity>>

    // Soruya bağlı kalıcı benzer sorular (Question Cloner arşivi)
    fun getBenzerSorular(soruId: Long): Flow<List<BenzerSoru>>
    suspend fun saveBenzerSoru(soruId: Long, result: SimilarQuestionResult): Long
    suspend fun deleteBenzerSoru(id: Long)
    suspend fun deleteAllBenzerSorular(soruId: Long)

    // Sokratik AI Öğretmen Sohbeti
    fun getChatMessagesForSoru(soruId: Long): Flow<List<com.sorudefteri.data.local.entity.SoruChatMessageEntity>>
    suspend fun insertChatMessage(message: com.sorudefteri.data.local.entity.SoruChatMessageEntity): Long
    suspend fun clearChatMessagesForSoru(soruId: Long)

    // Deneme Sınavı Takibi
    fun getAllDenemeler(): Flow<List<com.sorudefteri.data.local.entity.DenemeSinaviEntity>>
    suspend fun insertDeneme(entity: com.sorudefteri.data.local.entity.DenemeSinaviEntity): Long
    suspend fun deleteDeneme(id: Long)
}
