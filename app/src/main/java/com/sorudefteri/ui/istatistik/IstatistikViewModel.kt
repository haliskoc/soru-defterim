package com.sorudefteri.ui.istatistik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.manager.BadgeManager
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.LevelInfo
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.UserLevel
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow

data class IstatistikUiState(
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val dueReviewCount: Int = 0,
    val successRate: Float = 0f,
    val subjectStats: List<DersSoruSayisi> = emptyList(),
    val mistakeStats: List<HataTipiDagilimi> = emptyList(),
    val weeklyActivity: List<GunlukAktivite> = emptyList(),
    val recentTestResults: List<TestSonucEntity> = emptyList(),
    val averageNet: Float = 0f,
    val totalTestCount: Int = 0,
    val allQuestions: List<Soru> = emptyList(),
    val userXp: Int = 0,
    val levelInfo: LevelInfo = LevelInfo(UserLevel.CIRAK, 0, 0, 200, 0f, 200),
    val currentStreak: Int = 0,
    val streakShieldCount: Int = 1,
    val unlockedBadges: Set<String> = emptySet(),
    val allDenemeler: List<DenemeSinaviEntity> = emptyList(),
    val diagnosticReport: String? = null,
    val isGeneratingDiagnostic: Boolean = false,
    val diagnosticError: String? = null,
    val isLoading: Boolean = false,
    /** Sade ama detaylı istatistik: son 7 gün ↔ önceki 7 gün karşılaştırması */
    val haftalikKarsilastirma: HaftalikKarsilastirma = HaftalikKarsilastirma()
) {
    val isEmpty: Boolean get() = totalCount == 0 && totalTestCount == 0 && allDenemeler.isEmpty() && !isLoading
}

/**
 * Yeni detay metrikleri: son 7 günü önceki 7 günle karşılaştırır.
 * Eklenen soru, çözülen soru, test sayısı ve ortalama net (test + deneme).
 */
data class HaftalikKarsilastirma(
    val buHaftaEklenenSoru: Int = 0,
    val gecenHaftaEklenenSoru: Int = 0,
    val buHaftaCozulenSoru: Int = 0,
    val gecenHaftaCozulenSoru: Int = 0,
    val buHaftaTestSayisi: Int = 0,
    val gecenHaftaTestSayisi: Int = 0,
    val buHaftaOrtNet: Float = 0f,
    val gecenHaftaOrtNet: Float = 0f,
    val buHaftaDenemeSayisi: Int = 0,
    val gecenHaftaDenemeSayisi: Int = 0
)

private data class GamificationData(
    val xp: Int,
    val levelInfo: LevelInfo,
    val streak: Int,
    val shield: Int,
    val unlockedBadges: Set<String>
)

@HiltViewModel
class IstatistikViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    val konuKatalog: KonuKatalog,
    private val testSessionManager: TestSessionManager,
    private val ayarlarDataStore: AyarlarDataStore? = null,
    val badgeManager: BadgeManager? = null,
    private val aiRepository: AiRepository? = null,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
) : ViewModel() {

    private val _diagnosticReport = MutableStateFlow<String?>(null)
    private val _isGeneratingDiagnostic = MutableStateFlow(false)
    private val _diagnosticError = MutableStateFlow<String?>(null)

    private val countsFlow = combine(
        soruRepository.totalCount,
        soruRepository.solvedCount,
        soruRepository.unsolvedCount,
        soruRepository.dueReviewCount
    ) { total, solved, unsolved, dueReview ->
        Tuple4(total, solved, unsolved, dueReview)
    }

    private val distributionsFlow = combine(
        soruRepository.getSubjectStats(),
        soruRepository.getMistakeStats(),
        soruRepository.getWeeklyActivity()
    ) { subjects, mistakes, weekly ->
        Triple(subjects, mistakes, weekly)
    }

    private val gamificationFlow = if (ayarlarDataStore != null) {
        combine(
            ayarlarDataStore.userXp,
            ayarlarDataStore.currentStreak,
            ayarlarDataStore.streakShieldCount,
            ayarlarDataStore.unlockedBadges
        ) { xp, streak, shield, badges ->
            val lvlInfo = badgeManager?.getLevelInfo(xp) ?: LevelInfo(
                level = UserLevel.fromXp(xp),
                currentXp = xp,
                currentLevelMinXp = 0,
                currentLevelMaxXp = 200,
                progress = (xp.toFloat() / 200f).coerceIn(0f, 1f),
                xpRemainingForNextLevel = (200 - xp).coerceAtLeast(0)
            )
            GamificationData(xp, lvlInfo, streak, shield, badges)
        }
    } else {
        flowOf(
            GamificationData(
                0,
                LevelInfo(UserLevel.CIRAK, 0, 0, 200, 0f, 200),
                0,
                1,
                emptySet()
            )
        )
    }

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    private val diagnosticFlow = combine(_diagnosticReport, _isGeneratingDiagnostic, _diagnosticError) { report, isGen, err ->
        Triple(report, isGen, err)
    }

    // PERF: Haftalık karşılaştırma SQL COUNT'larla ayrı hafif akıştadır;
    // allQuestions yalnızca kartların ihtiyacı için tek Flow'dan gelir.
    private val haftalikFlow = soruRepository.totalCount
        .flatMapLatest { haftalikKarsilastirmaAkisi() }
        .flowOn(defaultDispatcher)

    private val grupA = combine(
        soruRepository.getAllTestResults(),
        soruRepository.getAllSorular(),
        gamificationFlow
    ) { tests, questions, gamification -> Triple(tests, questions, gamification) }

    private val grupB = combine(
        soruRepository.getAllDenemeler(),
        diagnosticFlow,
        haftalikFlow
    ) { denemeler, diagnostic, haftalik -> Triple(denemeler, diagnostic, haftalik) }

    val uiState: StateFlow<IstatistikUiState> = combine(
        combine(countsFlow, distributionsFlow) { counts, dist -> counts to dist },
        combine(grupA, grupB) { a, b -> a to b }
    ) { cd, ab ->
        val (counts, dist) = cd
        val (testResults, allQuestions, gamification) = ab.first
        val (denemeler, diagnostic, haftalik) = ab.second
        val total = counts.first
        val solved = counts.second
        val unsolved = counts.third
        val dueFromDb = counts.fourth
        val subjects = dist.first
        val mistakes = dist.second
        val weekly = dist.third
        val rate = if (total > 0) (solved.toFloat() / total.toFloat()) * 100f else 0f
        val totalTests = testResults.size
        val avgNet = if (testResults.isNotEmpty()) testResults.map { it.netCount }.average().toFloat() else 0f
        val recents = testResults.take(10)
        val calculatedDue = dueFromDb
        IstatistikUiState(
            totalCount = total,
            solvedCount = solved,
            unsolvedCount = unsolved,
            dueReviewCount = calculatedDue,
            successRate = rate,
            subjectStats = subjects,
            mistakeStats = mistakes,
            weeklyActivity = weekly,
            recentTestResults = recents,
            averageNet = avgNet,
            totalTestCount = totalTests,
            allQuestions = allQuestions,
            userXp = gamification.xp,
            levelInfo = gamification.levelInfo,
            currentStreak = gamification.streak,
            streakShieldCount = gamification.shield,
            unlockedBadges = gamification.unlockedBadges,
            allDenemeler = denemeler,
            diagnosticReport = diagnostic.first,
            isGeneratingDiagnostic = diagnostic.second,
            diagnosticError = diagnostic.third,
            isLoading = false,
            haftalikKarsilastirma = haftalik.copy(
                buHaftaTestSayisi = testResults.count { buHaftada(it.completedAt, nowMs()) },
                gecenHaftaTestSayisi = testResults.count { gecenHaftada(it.completedAt, nowMs()) },
                buHaftaOrtNet = testResults.filter { buHaftada(it.completedAt, nowMs()) }.map { it.netCount.toDouble() }.toFloatOrZero(),
                gecenHaftaOrtNet = testResults.filter { gecenHaftada(it.completedAt, nowMs()) }.map { it.netCount.toDouble() }.toFloatOrZero(),
                buHaftaDenemeSayisi = denemeler.count { buHaftada(it.tarih, nowMs()) },
                gecenHaftaDenemeSayisi = denemeler.count { gecenHaftada(it.tarih, nowMs()) }
            )
        )
    }.flowOn(defaultDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IstatistikUiState(isLoading = true)
    )

    /**
     * Yeni detay: son 7 günü önceki 7 günle karşılaştır.
     * Tüm veriler zaten yüklü listelerden türetilir; ek DB sorgusu yapmaz.
     */
    /**
     * PERF: Soru sayımları SQL COUNT ile (tam liste gezmesi yok).
     * Test/deneme sayımları küçük listelerden (zaten bellekte) hesaplanır.
     */
    private fun haftalikKarsilastirmaAkisi(): kotlinx.coroutines.flow.Flow<HaftalikKarsilastirma> = flow {
        val now = System.currentTimeMillis()
        val haftaMillis = 7L * 24L * 60L * 60L * 1000L
        val buHaftaBas = now - haftaMillis
        val gecenHaftaBas = now - 2L * haftaMillis
        val buEklenen = soruRepository.countAddedBetween(buHaftaBas, now)
        val gecenEklenen = soruRepository.countAddedBetween(gecenHaftaBas, buHaftaBas)
        val buCozulen = soruRepository.countSolvedBetween(buHaftaBas, now)
        val gecenCozulen = soruRepository.countSolvedBetween(gecenHaftaBas, buHaftaBas)
        emit(
            HaftalikKarsilastirma(
                buHaftaEklenenSoru = buEklenen,
                gecenHaftaEklenenSoru = gecenEklenen,
                buHaftaCozulenSoru = buCozulen,
                gecenHaftaCozulenSoru = gecenCozulen
            )
        )
    }

    private fun haftalikKarsilastirmaHesapla(
        allQuestions: List<Soru>,
        testResults: List<TestSonucEntity>,
        denemeler: List<DenemeSinaviEntity>
    ): HaftalikKarsilastirma {
        val now = System.currentTimeMillis()
        val haftaMillis = 7L * 24L * 60L * 60L * 1000L
        val buHaftaBas = now - haftaMillis
        val gecenHaftaBas = now - 2L * haftaMillis
        fun buHaftada(t: Long) = t > buHaftaBas && t <= now
        fun gecenHaftada(t: Long) = t > gecenHaftaBas && t <= buHaftaBas

        val buHaftaTestler = testResults.filter { buHaftada(it.completedAt) }
        val gecenHaftaTestler = testResults.filter { gecenHaftada(it.completedAt) }
        val buHaftaDenemeler = denemeler.filter { buHaftada(it.tarih) }
        val gecenHaftaDenemeler = denemeler.filter { gecenHaftada(it.tarih) }

        val buHaftaCozulen = allQuestions.count { q ->
            q.isSolved && q.solvedAt != null && buHaftada(q.solvedAt)
        }
        val gecenHaftaCozulen = allQuestions.count { q ->
            q.isSolved && q.solvedAt != null && gecenHaftada(q.solvedAt)
        }

        return HaftalikKarsilastirma(
            buHaftaEklenenSoru = allQuestions.count { buHaftada(it.createdAt) },
            gecenHaftaEklenenSoru = allQuestions.count { gecenHaftada(it.createdAt) },
            buHaftaCozulenSoru = buHaftaCozulen,
            gecenHaftaCozulenSoru = gecenHaftaCozulen,
            buHaftaTestSayisi = buHaftaTestler.size,
            gecenHaftaTestSayisi = gecenHaftaTestler.size,
            buHaftaOrtNet = if (buHaftaTestler.isNotEmpty()) buHaftaTestler.map { it.netCount }.average().toFloat() else 0f,
            gecenHaftaOrtNet = if (gecenHaftaTestler.isNotEmpty()) gecenHaftaTestler.map { it.netCount }.average().toFloat() else 0f,
            buHaftaDenemeSayisi = buHaftaDenemeler.size,
            gecenHaftaDenemeSayisi = gecenHaftaDenemeler.size
        )
    }

    /**
     * Seçilen konu için doğrudan hedefli test oturumu başlatır.
     * Eşleşen soru yoksa sessiz kalmak yerine bilgi mesajı yayınlar.
     */
    fun startTargetedTest(
        examType: String,
        subjectId: String,
        topic: String,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val questions = try {
                soruRepository.getFilteredSorular(
                    examType = examType,
                    subjectId = subjectId,
                    mistakeType = null,
                    isSolved = null
                ).first()
            } catch (e: Exception) {
                val msg = "Hedefli test yüklenemedi."
                onError?.invoke(msg)
                _uiEvent.tryEmit(msg)
                return@launch
            }

            val cleanTopic = topic.trim().lowercase()
            val topicQuestions = questions.filter { q ->
                val qTopic = q.topic.trim().lowercase()
                qTopic.contains(cleanTopic) || cleanTopic.contains(qTopic)
            }.ifEmpty { questions }

            if (topicQuestions.isNotEmpty()) {
                val testQuestions = topicQuestions.map { TestQuestion(soru = it) }
                val filtre = TestFiltre(
                    examType = examType,
                    selectedSubjects = setOf(subjectId),
                    selectedTopics = if (topic.isNotBlank()) setOf(topic) else emptySet(),
                    onlyUnsolved = false,
                    questionCount = testQuestions.size
                )
                testSessionManager.startSession(testQuestions, filtre)
                onSuccess()
            } else {
                val msg = "Bu konu için arşivde soru bulunamadı."
                onError?.invoke(msg)
                _uiEvent.tryEmit(msg)
            }
        }
    }

    /**
     * Günün Kurtarma Paketi: Tekrar vakti gelmiş sorularla doğrudan test başlatır.
     */
    fun startDueReviewTest(
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val dueQuestions = try {
                soruRepository.getDueReviewSorularList()
            } catch (e: Exception) {
                val msg = "Tekrar listesi yüklenemedi: ${e.localizedMessage}"
                onError?.invoke(msg)
                _uiEvent.tryEmit(msg)
                return@launch
            }

            if (dueQuestions.isNotEmpty()) {
                val testQuestions = dueQuestions.map { TestQuestion(soru = it) }
                val filter = TestFiltre(
                    examType = null,
                    selectedSubjects = emptySet(),
                    selectedTopics = emptySet(),
                    selectedMistakeTypes = emptySet(),
                    onlyUnsolved = false,
                    questionCount = testQuestions.size
                )
                testSessionManager.startSession(testQuestions, filter)
                onSuccess()
            } else {
                val msg = "Şu an tekrar vakti gelmiş soru yok. Arşivine soru ekledikçe veya çözdükçe tekrarların burada listelenecek."
                onError?.invoke(msg)
                _uiEvent.tryEmit(msg)
            }
        }
    }

    fun insertDeneme(entity: DenemeSinaviEntity) {
        viewModelScope.launch {
            soruRepository.insertDeneme(entity)
            _uiEvent.tryEmit("Deneme sınavı başarıyla kaydedildi.")
        }
    }

    fun deleteDeneme(id: Long) {
        viewModelScope.launch {
            soruRepository.deleteDeneme(id)
            _uiEvent.tryEmit("Deneme sınavı silindi.")
        }
    }

    fun generateDiagnosticReport() {
        if (_isGeneratingDiagnostic.value) return
        viewModelScope.launch {
            _isGeneratingDiagnostic.value = true
            _diagnosticError.value = null

            val currentState = uiState.value
            val contextBuilder = StringBuilder()
            contextBuilder.appendLine("ÖĞRENCİ GENEL PERFORMANSI:")
            contextBuilder.appendLine("- Toplam Arşivdeki Soru: ${currentState.totalCount}")
            contextBuilder.appendLine("- Çözülen Soru Sayısı: ${currentState.solvedCount} (Başarı Oranı: %${"%.1f".format(currentState.successRate)})")
            contextBuilder.appendLine("- Tekrar Vakti Gelmiş Soru: ${currentState.dueReviewCount}")
            contextBuilder.appendLine("- Çözülen Test Sayısı: ${currentState.totalTestCount}, Ortalama Test Neti: ${"%.1f".format(currentState.averageNet)}")

            if (currentState.mistakeStats.isNotEmpty()) {
                contextBuilder.appendLine("\nHATA TÜRÜ DAĞILIMI (Öğrencinin Neden Hata Yaptığı):")
                currentState.mistakeStats.forEach {
                    contextBuilder.appendLine("- ${it.displayName}: ${it.count} soru (%${"%.1f".format(it.percentage)})")
                }
            }

            if (currentState.subjectStats.isNotEmpty()) {
                contextBuilder.appendLine("\nDERS BAZLI SORU DAĞILIMI:")
                currentState.subjectStats.forEach {
                    contextBuilder.appendLine("- ${it.subjectName}: ${it.count} soru (Çözülen: ${it.solvedCount})")
                }
            }

            if (currentState.allDenemeler.isNotEmpty()) {
                contextBuilder.appendLine("\nDENEME SINAVI GEÇMİŞİ:")
                currentState.allDenemeler.take(6).forEach {
                    contextBuilder.appendLine("- ${it.baslik} (${it.sinavTuru}): Toplam Net = ${it.toplamNet}")
                }
            }

            val repo = aiRepository
            if (repo == null) {
                _diagnosticError.value = "AI servisi başlatılamadı."
                _isGeneratingDiagnostic.value = false
                return@launch
            }

            val result = repo.generateDiagnosticReport(contextBuilder.toString())
            if (result.isSuccess) {
                _diagnosticReport.value = result.getOrNull()
            } else {
                _diagnosticError.value = result.exceptionOrNull()?.localizedMessage ?: "Teşhis raporu oluşturulamadı."
            }
            _isGeneratingDiagnostic.value = false
        }
    }

    fun clearDiagnosticReport() {
        _diagnosticReport.value = null
        _diagnosticError.value = null
    }
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun nowMs(): Long = System.currentTimeMillis()
private fun buHaftada(t: Long, now: Long): Boolean {
    val hafta = 7L * 24L * 60L * 60L * 1000L
    return t > now - hafta && t <= now
}
private fun gecenHaftada(t: Long, now: Long): Boolean {
    val hafta = 7L * 24L * 60L * 60L * 1000L
    return t > now - 2L * hafta && t <= now - hafta
}
private fun List<Double>.toFloatOrZero(): Float = if (isEmpty()) 0f else average().toFloat()
