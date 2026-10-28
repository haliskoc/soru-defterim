package com.sorudefteri.ui.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.pdf.OmrMatrixCodeCodec
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.omr.model.OmrAnswerStatus
import com.sorudefteri.domain.omr.model.OmrQuestionEvaluation
import com.sorudefteri.domain.omr.model.OmrScanResult
import com.sorudefteri.domain.omr.model.OmrTemplate
import com.sorudefteri.domain.omr.vision.BubbleReader
import com.sorudefteri.domain.omr.vision.FiducialDetector
import com.sorudefteri.domain.omr.vision.PerspectiveWarper
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class OmrScannerUiState(
    val selectedDenemeId: Long? = null,
    val selectedTestId: String = "",
    val examTitle: String = "Türkiye Geneli Deneme Sınavı",
    val publisherName: String = "ÖSYM / Genel",
    val sinavTuru: String = "TYT",
    val questionCount: Int = 120,
    val customAnswerKey: Map<Int, String> = emptyMap(),
    val liveDetection: FiducialDetector.DetectionResult? = null,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val lastScanResult: OmrScanResult? = null,
    val lastSavedDenemeId: Long? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class OmrViewModel @Inject constructor(
    private val soruRepository: SoruRepository
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allDenemeler: StateFlow<List<DenemeSinaviEntity>> = soruRepository.getAllDenemeler()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(
        OmrScannerUiState(
            customAnswerKey = generateDeterministicAnswerKey("TYT", 120)
        )
    )
    val uiState: StateFlow<OmrScannerUiState> = _uiState.asStateFlow()

    private val _sonucResult = MutableStateFlow<OmrScanResult?>(null)
    val sonucResult: StateFlow<OmrScanResult?> = _sonucResult.asStateFlow()

    private val _eklenenSoruNumaralari = MutableStateFlow<Set<Int>>(emptySet())
    val eklenenSoruNumaralari: StateFlow<Set<Int>> = _eklenenSoruNumaralari.asStateFlow()

    /**
     * Başlangıçta belirli bir deneme veya testId ile tarayıcı açıldıysa yapılandırır.
     */
    fun initScanner(denemeId: Long?, testId: String?) {
        viewModelScope.launch {
            if (denemeId != null && denemeId > 0L) {
                val entity = soruRepository.getDenemeById(denemeId)
                if (entity != null) {
                    selectExistingDeneme(entity)
                    return@launch
                }
            }
            if (!testId.isNullOrBlank()) {
                val byTestId = soruRepository.getDenemeByTestId(testId)
                if (byTestId != null) {
                    selectExistingDeneme(byTestId)
                } else {
                    _uiState.update { it.copy(selectedTestId = testId) }
                }
            }
        }
    }

    /**
     * Mevcut bir deneme sınavını seçer ve varsa onun cevap anahtarını yükler.
     */
    fun selectExistingDeneme(deneme: DenemeSinaviEntity?) {
        if (deneme == null) {
            val defaultCount = OmrTemplate.defaultQuestionCount(_uiState.value.sinavTuru)
            _uiState.update {
                it.copy(
                    selectedDenemeId = null,
                    selectedTestId = "",
                    questionCount = defaultCount,
                    customAnswerKey = generateDeterministicAnswerKey(it.sinavTuru, defaultCount)
                )
            }
            return
        }

        val count = if (deneme.toplamSoru in 1..OmrTemplate.MAX_QUESTIONS) {
            deneme.toplamSoru
        } else {
            OmrTemplate.defaultQuestionCount(deneme.sinavTuru)
        }
        val parsedKey = parseAnswerKeyJson(deneme.cevapAnahtariJson)
            .ifEmpty { generateDeterministicAnswerKey(deneme.sinavTuru, count) }

        _uiState.update {
            it.copy(
                selectedDenemeId = deneme.id,
                selectedTestId = deneme.testId,
                examTitle = deneme.baslik,
                publisherName = deneme.yayinEvi,
                sinavTuru = deneme.sinavTuru,
                questionCount = count,
                customAnswerKey = parsedKey
            )
        }
    }

    fun updateExamType(sinavTuru: String) {
        val count = OmrTemplate.defaultQuestionCount(sinavTuru)
        _uiState.update { state ->
            val updatedKey = generateDeterministicAnswerKey(sinavTuru, count)
            state.copy(
                sinavTuru = sinavTuru,
                questionCount = count,
                customAnswerKey = updatedKey
            )
        }
    }

    fun updateExamMetadata(title: String, publisher: String, questionCount: Int) {
        val clampedCount = questionCount.coerceIn(1, OmrTemplate.MAX_QUESTIONS)
        _uiState.update { state ->
            val currentKey = state.customAnswerKey.toMutableMap()
            for (q in 1..clampedCount) {
                if (!currentKey.containsKey(q)) {
                    currentKey[q] = OmrTemplate.OPTIONS[(q * 3 + 1) % OmrTemplate.OPTIONS.size]
                }
            }
            state.copy(
                examTitle = title.ifBlank { "Optik Deneme Sınavı" },
                publisherName = publisher,
                questionCount = clampedCount,
                customAnswerKey = currentKey
            )
        }
    }

    fun updateSingleAnswerKey(questionNumber: Int, correctOption: String) {
        if (questionNumber !in 1..OmrTemplate.MAX_QUESTIONS) return
        val normalized = correctOption.uppercase().trim()
        if (normalized !in OmrTemplate.OPTIONS) return
        _uiState.update { state ->
            val mutable = state.customAnswerKey.toMutableMap()
            mutable[questionNumber] = normalized
            state.copy(customAnswerKey = mutable)
        }
    }

    /**
     * Hızlı metin dizisinden (Örn: "ACBED...") cevap anahtarını topluca günceller.
     */
    fun updateAnswerKeyFromSequence(sequence: String) {
        val cleanChars = sequence.uppercase().filter { it in listOf('A', 'B', 'C', 'D', 'E') }
        if (cleanChars.isEmpty()) return
        _uiState.update { state ->
            val mutable = state.customAnswerKey.toMutableMap()
            cleanChars.forEachIndexed { index, ch ->
                val qNo = index + 1
                if (qNo <= OmrTemplate.MAX_QUESTIONS) {
                    mutable[qNo] = ch.toString()
                }
            }
            state.copy(customAnswerKey = mutable)
        }
    }

    /**
     * Canlı CameraX vizöründen gelen 4 köşe tespit durumunu günceller.
     */
    fun onLiveFrameDetection(result: FiducialDetector.DetectionResult) {
        _uiState.update { it.copy(liveDetection = result) }
    }

    /**
     * Kamera veya galeriden alınan [Bitmap] üzerinde tam OMR Bilgisayarlı Görü Pipeline'ını çalıştırır:
     * 1. FiducialDetector.detectWithFallback
     * 2. PerspectiveWarper.warp -> 1200x1700
     * 3. BubbleReader.extractTestId -> DB eşleşmesi
     * 4. BubbleReader.readAllQuestions -> Bağıl kontrast & silgi izi analizi
     * 5. Ders bazlı net hesaplama ve Room DB kaydı
     */
    fun analyzeBitmap(bitmap: Bitmap, onScanCompleted: (Long) -> Unit = {}) {
        if (_uiState.value.isProcessing) return
        _uiState.update {
            it.copy(
                isProcessing = true,
                statusMessage = "4 köşe referans kareleri hizalanıyor ve perspektif düzeltiliyor...",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val (scanResult, savedDenemeId) = withContext(Dispatchers.Default) {
                    // 1. 4 Köşe Fiducial Tespiti (kilitlenmemişse kılavuz çerçeve fallback'i ile)
                    val detection = FiducialDetector.detectWithFallback(bitmap)

                    // 2. Skia Matrix.setPolyToPoly ile 1200x1700 perspektif düzeltme
                    val warped = PerspectiveWarper.warp(
                        source = bitmap,
                        corners = detection.corners,
                        outWidth = OmrTemplate.WARP_WIDTH,
                        outHeight = OmrTemplate.WARP_HEIGHT
                    )

                    // 3. Üst başlıktan 2D Matris Kod veya ML Kit ile Test ID okuma
                    val detectedTestId = BubbleReader.extractTestId(warped)
                    val stateSnapshot = _uiState.value

                    // Veritabanında eşleşen deneme var mı kontrol et
                    val matchedDeneme: DenemeSinaviEntity? = when {
                        !detectedTestId.isNullOrBlank() -> soruRepository.getDenemeByTestId(detectedTestId)
                        stateSnapshot.selectedDenemeId != null -> soruRepository.getDenemeById(stateSnapshot.selectedDenemeId)
                        stateSnapshot.selectedTestId.isNotBlank() -> soruRepository.getDenemeByTestId(stateSnapshot.selectedTestId)
                        else -> null
                    }

                    val effectiveSinavTuru = matchedDeneme?.sinavTuru ?: stateSnapshot.sinavTuru
                    val effectiveQuestionCount = when {
                        matchedDeneme != null && matchedDeneme.toplamSoru in 1..OmrTemplate.MAX_QUESTIONS -> matchedDeneme.toplamSoru
                        else -> stateSnapshot.questionCount.coerceIn(1, OmrTemplate.MAX_QUESTIONS)
                    }
                    val effectiveTestId = matchedDeneme?.testId?.takeIf { it.isNotBlank() }
                        ?: detectedTestId
                        ?: stateSnapshot.selectedTestId.takeIf { it.isNotBlank() }
                        ?: "SD-EXAM-${UUID.randomUUID().toString().take(8).uppercase()}"

                    val effectiveTitle = matchedDeneme?.baslik ?: stateSnapshot.examTitle
                    val effectivePublisher = matchedDeneme?.yayinEvi ?: stateSnapshot.publisherName

                    // Cevap anahtarını hazırla
                    val answerKey = parseAnswerKeyJson(matchedDeneme?.cevapAnahtariJson)
                        .ifEmpty { stateSnapshot.customAnswerKey }
                        .ifEmpty { generateDeterministicAnswerKey(effectiveSinavTuru, effectiveQuestionCount) }

                    // 4. Baloncukları (A-E) Bağıl Kontrast Algoritmasıyla Oku
                    val studentAnswers = BubbleReader.readAllQuestions(warped, effectiveQuestionCount)
                    if (warped !== bitmap && !warped.isRecycled) {
                        warped.recycle()
                    }

                    // 5. Değerlendirme ve Ders Bazlı Net Hesabı
                    val evaluation = evaluateScan(
                        testId = effectiveTestId,
                        denemeId = matchedDeneme?.id,
                        baslik = effectiveTitle,
                        yayinEvi = effectivePublisher,
                        sinavTuru = effectiveSinavTuru,
                        questionCount = effectiveQuestionCount,
                        studentAnswers = studentAnswers,
                        correctAnswers = answerKey
                    )

                    // 6. Veritabanına Kaydet / Güncelle
                    val persistedId = persistScanResultToDb(evaluation, matchedDeneme)
                    val finalResult = evaluation.copy(denemeId = persistedId)
                    finalResult to persistedId
                }

                _sonucResult.value = scanResult
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        lastScanResult = scanResult,
                        lastSavedDenemeId = savedDenemeId
                    )
                }
                onScanCompleted(savedDenemeId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = "Optik form okunurken hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                    )
                }
            }
        }
    }

    /**
     * Emülatörde veya fiziksel optik form yokken bile tek tıkla örnek işaretlenmiş
     * optik form Bitmap'i üretip gerçek CV pipeline'ından (Fiducial -> Warp -> BubbleReader) geçirir!
     */
    fun simulateScan(onScanCompleted: (Long) -> Unit = {}) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            val state = _uiState.value
            val testId = state.selectedTestId.ifBlank {
                "SD-EXAM-${UUID.randomUUID().toString().take(8).uppercase()}"
            }
            val answerKey = state.customAnswerKey.ifEmpty {
                generateDeterministicAnswerKey(state.sinavTuru, state.questionCount)
            }

            // Gerçekçi öğrenci işaretlemeleri (~%78 doğru, ~%14 yanlış, ~%8 boş)
            val simulatedStudentMarks = LinkedHashMap<Int, String?>(state.questionCount)
            for (q in 1..state.questionCount) {
                val correct = answerKey[q] ?: "A"
                val mod = q % 13
                simulatedStudentMarks[q] = when {
                    mod == 0 -> null // Boş bırakılmış soru
                    mod == 5 || mod == 11 -> {
                        // Yanlış işaretlenmiş soru (Kütüphaneye ekleme akışını test etmek için)
                        OmrTemplate.OPTIONS.first { it != correct }
                    }
                    else -> correct // Doğru işaretlenmiş soru
                }
            }

            val sampleBitmap = withContext(Dispatchers.Default) {
                createSampleOpticalBitmap(
                    testId = testId,
                    title = state.examTitle,
                    sinavTuru = state.sinavTuru,
                    questionCount = state.questionCount,
                    studentMarks = simulatedStudentMarks
                )
            }

            analyzeBitmap(sampleBitmap) { savedId ->
                if (!sampleBitmap.isRecycled) {
                    sampleBitmap.recycle()
                }
                onScanCompleted(savedId)
            }
        }
    }

    /**
     * OmrSonucScreen açıldığında ilgili `denemeId` kaydını veritabanından yükler
     * ve soru bazlı optik karne matrisini oluşturur.
     */
    fun loadDenemeResult(denemeId: Long) {
        viewModelScope.launch {
            val entity = soruRepository.getDenemeById(denemeId) ?: return@launch
            val existingSorular = soruRepository.getSorularByDenemeIdList(denemeId)
            _eklenenSoruNumaralari.value = existingSorular.mapNotNull { it.denemeSoruNo }.toSet()

            // Eğer bellekte aynı deneme için yeni taranmış sonuç varsa onu koru
            val current = _sonucResult.value
            if (current != null && current.denemeId == denemeId && current.questionResults.isNotEmpty()) {
                return@launch
            }

            val qCount = if (entity.toplamSoru in 1..OmrTemplate.MAX_QUESTIONS) {
                entity.toplamSoru
            } else {
                OmrTemplate.defaultQuestionCount(entity.sinavTuru)
            }

            val correctMap = parseAnswerKeyJson(entity.cevapAnahtariJson)
                .ifEmpty { generateDeterministicAnswerKey(entity.sinavTuru, qCount) }

            val studentMap = parseStudentAnswersJson(entity.ogrenciCevaplariJson, qCount, correctMap, entity)

            val reconstructed = evaluateScan(
                testId = entity.testId.ifBlank { "SD-EXAM-${entity.id}" },
                denemeId = entity.id,
                baslik = entity.baslik,
                yayinEvi = entity.yayinEvi,
                sinavTuru = entity.sinavTuru,
                questionCount = qCount,
                studentAnswers = studentMap,
                correctAnswers = correctMap
            )
            _sonucResult.value = reconstructed
        }
    }

    /**
     * Sonuç ekranında kullanıcının bir sorunun okunan cevabını veya doğru cevabını
     * dokunarak anında düzeltmesini sağlar; netleri yeniden hesaplar ve DB'yi günceller.
     */
    fun updateQuestionAnswerInteractive(
        questionNumber: Int,
        newStudentAnswer: String?,
        newCorrectAnswer: String? = null
    ) {
        val current = _sonucResult.value ?: return
        viewModelScope.launch {
            val updatedStudentMap = current.studentAnswers.toMutableMap()
            updatedStudentMap[questionNumber] = newStudentAnswer

            val updatedCorrectMap = current.correctAnswers.toMutableMap()
            if (!newCorrectAnswer.isNullOrBlank() && newCorrectAnswer in OmrTemplate.OPTIONS) {
                updatedCorrectMap[questionNumber] = newCorrectAnswer
            }

            val updatedEval = evaluateScan(
                testId = current.testId,
                denemeId = current.denemeId,
                baslik = current.baslik,
                yayinEvi = current.yayinEvi,
                sinavTuru = current.sinavTuru,
                questionCount = current.totalQuestions,
                studentAnswers = updatedStudentMap,
                correctAnswers = updatedCorrectMap
            )

            _sonucResult.value = updatedEval

            val denemeId = current.denemeId
            if (denemeId != null && denemeId > 0L) {
                val existingEntity = soruRepository.getDenemeById(denemeId)
                if (existingEntity != null) {
                    persistScanResultToDb(updatedEval, existingEntity)
                }
            }
        }
    }

    /**
     * Yanlış veya boş soruları değerlendirip ders bazlı ve toplam netleri hesaplar.
     */
    fun evaluateScan(
        testId: String,
        denemeId: Long?,
        baslik: String,
        yayinEvi: String,
        sinavTuru: String,
        questionCount: Int,
        studentAnswers: Map<Int, String?>,
        correctAnswers: Map<Int, String>
    ): OmrScanResult {
        val evaluations = ArrayList<OmrQuestionEvaluation>(questionCount)
        var totalDogru = 0
        var totalYanlis = 0
        var totalBos = 0

        // Ceza katsayısı: LGS'de 3 yanlış 1 doğruyu götürür (0.33), YKS/KPSS'de 4 yanlış 1 doğruyu götürür (0.25)
        val penaltyFactor = if (sinavTuru.equals("LGS", ignoreCase = true)) (1.0 / 3.0) else 0.25

        for (q in 1..questionCount) {
            val studentOpt = studentAnswers[q]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            val correctOpt = (correctAnswers[q] ?: "A").trim().uppercase()
            val (dersAdi, subjectId) = OmrTemplate.resolveSubjectForQuestion(sinavTuru, q)

            val status = when {
                studentOpt == null -> {
                    totalBos++
                    OmrAnswerStatus.EMPTY
                }
                studentOpt == BubbleReader.MULTI_MARK -> {
                    totalYanlis++
                    OmrAnswerStatus.MULTI_INVALID
                }
                studentOpt == correctOpt -> {
                    totalDogru++
                    OmrAnswerStatus.CORRECT
                }
                else -> {
                    totalYanlis++
                    OmrAnswerStatus.WRONG
                }
            }

            evaluations.add(
                OmrQuestionEvaluation(
                    questionNumber = q,
                    studentAnswer = studentOpt,
                    correctAnswer = correctOpt,
                    status = status,
                    dersAdi = dersAdi,
                    subjectId = subjectId
                )
            )
        }

        val dersSonuclari = evaluations
            .groupBy { it.dersAdi }
            .map { (dersAdi, list) ->
                val d = list.count { it.status == OmrAnswerStatus.CORRECT }
                val y = list.count { it.status == OmrAnswerStatus.WRONG || it.status == OmrAnswerStatus.MULTI_INVALID }
                val b = list.count { it.status == OmrAnswerStatus.EMPTY }
                val rawNet = d - (y * penaltyFactor)
                val roundedNet = (rawNet * 100.0).roundToInt() / 100.0
                DenemeDersNeti(
                    dersAdi = dersAdi,
                    dogruSayisi = d,
                    yanlisSayisi = y,
                    bosSayisi = b,
                    net = roundedNet
                )
            }

        val totalRawNet = totalDogru - (totalYanlis * penaltyFactor)
        val totalRoundedNet = (totalRawNet * 100.0).roundToInt() / 100.0

        return OmrScanResult(
            testId = testId,
            denemeId = denemeId,
            baslik = baslik,
            yayinEvi = yayinEvi,
            sinavTuru = sinavTuru,
            totalQuestions = questionCount,
            studentAnswers = studentAnswers,
            correctAnswers = correctAnswers,
            questionResults = evaluations,
            toplamDogru = totalDogru,
            toplamYanlis = totalYanlis,
            toplamBos = totalBos,
            toplamNet = totalRoundedNet,
            dersSonuclari = dersSonuclari
        )
    }

    private suspend fun persistScanResultToDb(
        result: OmrScanResult,
        existingEntity: DenemeSinaviEntity?
    ): Long {
        val dersNetleriJson = json.encodeToString(result.dersSonuclari)
        val answerKeyStringMap = result.correctAnswers.mapKeys { it.key.toString() }
        val cevapAnahtariJson = json.encodeToString(answerKeyStringMap)

        val studentStringMap = result.studentAnswers.mapKeys { it.key.toString() }
            .mapValues { it.value ?: "" }
        val ogrenciCevaplariJson = json.encodeToString(studentStringMap)

        val now = System.currentTimeMillis()
        return if (existingEntity != null) {
            val updated = existingEntity.copy(
                testId = result.testId.ifBlank { existingEntity.testId },
                baslik = result.baslik.ifBlank { existingEntity.baslik },
                yayinEvi = result.yayinEvi.ifBlank { existingEntity.yayinEvi },
                sinavTuru = result.sinavTuru,
                toplamSoru = result.totalQuestions,
                toplamDogru = result.toplamDogru,
                toplamYanlis = result.toplamYanlis,
                toplamBos = result.toplamBos,
                toplamNet = result.toplamNet,
                dersNetleriJson = dersNetleriJson,
                cevapAnahtariJson = cevapAnahtariJson,
                ogrenciCevaplariJson = ogrenciCevaplariJson,
                updatedAt = now
            )
            soruRepository.updateDeneme(updated)
            existingEntity.id
        } else {
            val newEntity = DenemeSinaviEntity(
                testId = result.testId,
                baslik = result.baslik.ifBlank {
                    "${result.sinavTuru} Optik Deneme (${result.testId.takeLast(4)})"
                },
                yayinEvi = result.yayinEvi.ifBlank { "Optik Tarama" },
                sinavTuru = result.sinavTuru,
                tarih = now,
                sureDakika = if (result.sinavTuru == "TYT") 165 else 180,
                toplamSoru = result.totalQuestions,
                toplamDogru = result.toplamDogru,
                toplamYanlis = result.toplamYanlis,
                toplamBos = result.toplamBos,
                toplamNet = result.toplamNet,
                dersNetleriJson = dersNetleriJson,
                cevapAnahtariJson = cevapAnahtariJson,
                ogrenciCevaplariJson = ogrenciCevaplariJson,
                notlar = "Kamera tabanlı OMR optik okuyucu ile tarandı.",
                createdAt = now,
                updatedAt = now
            )
            soruRepository.insertDeneme(newEntity)
        }
    }

    /**
     * Gerçek CV motorunu (FiducialDetector -> PerspectiveWarper -> BubbleReader) uçtan uca
     * test etmek ve göstermek için işaretlenmiş örnek bir A4 Optik Form Bitmap'i üretir.
     */
    fun createSampleOpticalBitmap(
        testId: String = "SD-EXAM-2026X8F9",
        title: String = "3D Türkiye Geneli TYT Deneme-1",
        sinavTuru: String = "TYT",
        questionCount: Int = 120,
        studentMarks: Map<Int, String?> = emptyMap()
    ): Bitmap {
        // 1. Önce 1200 x 1700 iç optik şablon düzlemini çiz
        val inner = Bitmap.createBitmap(
            OmrTemplate.WARP_WIDTH,
            OmrTemplate.WARP_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val innerCanvas = Canvas(inner)
        innerCanvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 44)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 30f
        }
        innerCanvas.drawText("SORU DEFTERİM AKILLI OPTİK FORM", 65f, 95f, headerPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 72, 88)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 22f
        }
        innerCanvas.drawText("Sınav: $title ($sinavTuru)", 65f, 138f, subPaint)
        innerCanvas.drawText("Test Kodu: $testId", 65f, 176f, subPaint)

        // Sağ üst köşeye 2D Vektörel Matris Kodu çiz (OmrMatrixCodeCodec ile birebir uyumlu)
        OmrMatrixCodeCodec.drawMatrixCode(
            canvas = innerCanvas,
            rect = OmrMatrixCodeCodec.OPTICAL_FORM_MATRIX_ROI_WARP,
            payload = testId
        )

        // Ayırıcı üst çizgi
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 190, 205)
            strokeWidth = 3f
        }
        innerCanvas.drawLine(50f, 280f, 1150f, 280f, linePaint)

        // Sütun ve satır baloncuklarını çiz
        val strokeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            color = Color.rgb(90, 102, 120)
        }
        val fillBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(22, 26, 32) // Kurşun kalem koyuluğu
        }
        val timingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 50, 65)
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val clampedCount = questionCount.coerceIn(1, OmrTemplate.MAX_QUESTIONS)
        for (q in 1..clampedCount) {
            val (trackX, trackY) = OmrTemplate.getTimingTrackCenter(q)
            innerCanvas.drawRect(trackX - 8f, trackY - 4f, trackX + 8f, trackY + 4f, timingPaint)
            innerCanvas.drawText("$q.", trackX + 14f, trackY + 5f, numberPaint)

            val centers = OmrTemplate.getBubbleCenters(q)
            val marked = studentMarks[q]

            centers.forEach { (opt, center) ->
                val (cx, cy) = center
                innerCanvas.drawCircle(cx, cy, OmrTemplate.BUBBLE_RADIUS, strokeCirclePaint)
                if (marked == opt) {
                    innerCanvas.drawCircle(cx, cy, OmrTemplate.BUBBLE_RADIUS * 0.88f, fillBubblePaint)
                }
            }
        }

        // 2. Dış kamera çerçevesi ve 4 köşe siyah Fiducial referans kareleri
        val margin = 90
        val outW = OmrTemplate.WARP_WIDTH + margin * 2
        val outH = OmrTemplate.WARP_HEIGHT + margin * 2
        val outer = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val outerCanvas = Canvas(outer)
        outerCanvas.drawColor(Color.rgb(244, 246, 250))

        // Kağıt gölgesi ve beyaz A4 zemin
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        outerCanvas.drawRect(24f, 24f, (outW - 24).toFloat(), (outH - 24).toFloat(), paperPaint)

        // İç şablonu (margin, margin) konumuna yerleştir -> Köşeler tam (margin, margin) noktalarında!
        outerCanvas.drawBitmap(inner, margin.toFloat(), margin.toFloat(), null)
        inner.recycle()

        // 4 Köşe Referans Karesi (Fiducial Markers) - Merkezleri tam (margin, margin) köşelerinde
        val fiducialHalf = 26f
        val fiducialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val fiducialCenters = listOf(
            PointF(margin.toFloat(), margin.toFloat()),
            PointF((margin + OmrTemplate.WARP_WIDTH).toFloat(), margin.toFloat()),
            PointF((margin + OmrTemplate.WARP_WIDTH).toFloat(), (margin + OmrTemplate.WARP_HEIGHT).toFloat()),
            PointF(margin.toFloat(), (margin + OmrTemplate.WARP_HEIGHT).toFloat())
        )
        for (pt in fiducialCenters) {
            outerCanvas.drawRect(
                RectF(
                    pt.x - fiducialHalf,
                    pt.y - fiducialHalf,
                    pt.x + fiducialHalf,
                    pt.y + fiducialHalf
                ),
                fiducialPaint
            )
        }

        return outer
    }

    private fun parseAnswerKeyJson(rawJson: String?): Map<Int, String> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return try {
            val mapStr = json.decodeFromString<Map<String, String>>(rawJson)
            mapStr.mapNotNull { (k, v) ->
                val qNo = k.trim().toIntOrNull()
                val opt = v.trim().uppercase(Locale.US)
                if (qNo != null && opt in OmrTemplate.OPTIONS) qNo to opt else null
            }.toMap()
        } catch (_: Exception) {
            // Esnek fallback: "1:A, 2:C" veya regex tabanlı çözümleme
            val regex = Regex("""["']?(\d{1,3})["']?\s*[:=]\s*["']?([ABCDE])["']?""", RegexOption.IGNORE_CASE)
            regex.findAll(rawJson).associate { match ->
                match.groupValues[1].toInt() to match.groupValues[2].uppercase(Locale.US)
            }
        }
    }

    private fun parseStudentAnswersJson(
        rawJson: String?,
        questionCount: Int,
        correctMap: Map<Int, String>,
        entity: DenemeSinaviEntity
    ): Map<Int, String?> {
        if (!rawJson.isNullOrBlank()) {
            try {
                val mapStr = json.decodeFromString<Map<String, String>>(rawJson)
                val parsed = LinkedHashMap<Int, String?>(questionCount)
                for (q in 1..questionCount) {
                    val v = mapStr[q.toString()]?.trim()?.uppercase(Locale.US)
                    parsed[q] = if (v.isNullOrBlank()) null else v
                }
                if (parsed.isNotEmpty()) return parsed
            } catch (_: Exception) {}
        }

        // Eğer öğrenci cevapları JSON olarak kaydedilmemişse (manuel girilmiş deneme ise)
        // doğru/yanlış/boş sayılarına sadık bir dağılım oluştur
        val fallback = LinkedHashMap<Int, String?>(questionCount)
        var remCorrect = entity.toplamDogru.coerceAtLeast(0)
        var remWrong = entity.toplamYanlis.coerceAtLeast(0)
        for (q in 1..questionCount) {
            val correctOpt = correctMap[q] ?: "A"
            when {
                remCorrect > 0 -> {
                    fallback[q] = correctOpt
                    remCorrect--
                }
                remWrong > 0 -> {
                    fallback[q] = OmrTemplate.OPTIONS.first { it != correctOpt }
                    remWrong--
                }
                else -> fallback[q] = null
            }
        }
        return fallback
    }

    private fun generateDeterministicAnswerKey(sinavTuru: String, count: Int): Map<Int, String> {
        val seedOffset = sinavTuru.hashCode().let { if (it < 0) -it else it } % 5
        val map = LinkedHashMap<Int, String>(count)
        for (q in 1..count) {
            val idx = (q * 7 + seedOffset + (q / 5)) % OmrTemplate.OPTIONS.size
            map[q] = OmrTemplate.OPTIONS[idx]
        }
        return map
    }
}
