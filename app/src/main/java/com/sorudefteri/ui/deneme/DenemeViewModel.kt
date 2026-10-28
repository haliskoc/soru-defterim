package com.sorudefteri.ui.deneme

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.pdf.PdfDenemeKitapcikGenerator
import com.sorudefteri.data.pdf.PdfExportConfig
import com.sorudefteri.data.pdf.PdfTemplateMode
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.model.DenemeSinavi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class KonuKayipOzet(
    val subjectId: String,
    val dersAdi: String,
    val topic: String,
    val soruSayisi: Int,
    val cozulmusSayisi: Int,
    val tahminiNetKaybi: Double
)

data class DenemeDetayUiState(
    val isLoading: Boolean = true,
    val denemeEntity: DenemeSinaviEntity? = null,
    val deneme: DenemeSinavi? = null,
    val dersNetleri: List<DenemeDersNeti> = emptyList(),
    val denemeSorulari: List<Soru> = emptyList(),
    val konuKayiplari: List<KonuKayipOzet> = emptyList(),
    val cevapAnahtariMap: Map<Int, String> = emptyMap(),
    val ogrenciCevaplariMap: Map<Int, String> = emptyMap(),
    val isExportingPdf: Boolean = false,
    val nextSuggestedSoruNo: Int = 1
) {
    val cozulmusSoruSayisi: Int
        get() = denemeSorulari.count { it.isSolved }

    val cozulmemisSoruSayisi: Int
        get() = denemeSorulari.count { !it.isSolved }

    val toplamKayipNet: Double
        get() = konuKayiplari.sumOf { it.tahminiNetKaybi }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DenemeViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    private val testSessionManager: TestSessionManager,
    savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _denemeIdFlow = MutableStateFlow<Long?>(
        savedStateHandle?.get<Long>("denemeId")?.takeIf { it > 0L }
            ?: savedStateHandle?.get<String>("denemeId")?.toLongOrNull()?.takeIf { it > 0L }
    )

    private val _isExportingPdf = MutableStateFlow(false)

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    val uiState: StateFlow<DenemeDetayUiState> = _denemeIdFlow
        .flatMapLatest { id ->
            if (id == null || id <= 0L) {
                flowOf(DenemeDetayUiState(isLoading = false))
            } else {
                combine(
                    soruRepository.getDenemeByIdFlow(id),
                    soruRepository.getSorularByDenemeId(id),
                    soruRepository.getAllSorular(),
                    _isExportingPdf
                ) { entity, linkedSorular, allSorular, exporting ->
                    if (entity == null) {
                        DenemeDetayUiState(isLoading = false)
                    } else {
                        val domain = entity.toDomain(json)
                        // Hem doğrudan denemeId ile bağlı olanları hem de aynı deneme başlığıyla kaydedilmiş soruları birleştir
                        val matchedByTitle = if (entity.baslik.isNotBlank()) {
                            allSorular.filter { s ->
                                s.denemeId == null &&
                                    !s.kaynakDeneme.isNullOrBlank() &&
                                    s.kaynakDeneme.trim().equals(entity.baslik.trim(), ignoreCase = true)
                            }
                        } else {
                            emptyList()
                        }

                        val mergedSorular = (linkedSorular + matchedByTitle)
                            .distinctBy { it.id }
                            .sortedWith(
                                compareBy<Soru> { it.denemeSoruNo ?: it.kaynakSoruNo ?: Int.MAX_VALUE }
                                    .thenBy { it.createdAt }
                            )

                        val netPenalty = if (entity.sinavTuru.equals("LGS", ignoreCase = true)) 1.33 else 1.25
                        val konuKayiplari = mergedSorular
                            .groupBy { "${it.subjectId}|${it.topic.ifBlank { "Genel" }}" }
                            .map { (key, list) ->
                                val parts = key.split("|")
                                val subjId = parts.getOrNull(0) ?: "matematik"
                                val topName = parts.getOrNull(1) ?: "Genel"
                                KonuKayipOzet(
                                    subjectId = subjId,
                                    dersAdi = DersTaksonomisi.getDersAdi(entity.sinavTuru, subjId),
                                    topic = topName,
                                    soruSayisi = list.size,
                                    cozulmusSayisi = list.count { it.isSolved },
                                    tahminiNetKaybi = Math.round(list.size * netPenalty * 100.0) / 100.0
                                )
                            }
                            .sortedByDescending { it.tahminiNetKaybi }

                        val maxExistingNo = mergedSorular.mapNotNull { it.denemeSoruNo ?: it.kaynakSoruNo }.maxOrNull() ?: 0
                        val nextNo = (maxExistingNo + 1).coerceAtLeast(1)

                        DenemeDetayUiState(
                            isLoading = false,
                            denemeEntity = entity,
                            deneme = domain,
                            dersNetleri = domain.dersNetleri,
                            denemeSorulari = mergedSorular,
                            konuKayiplari = konuKayiplari,
                            cevapAnahtariMap = parseAnswerMap(entity.cevapAnahtariJson),
                            ogrenciCevaplariMap = parseAnswerMap(entity.ogrenciCevaplariJson),
                            isExportingPdf = exporting,
                            nextSuggestedSoruNo = nextNo
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DenemeDetayUiState(isLoading = true)
        )

    fun loadDeneme(denemeId: Long) {
        if (denemeId > 0L && _denemeIdFlow.value != denemeId) {
            _denemeIdFlow.value = denemeId
        }
    }

    /**
     * Sadece bu denemede yanlış/boş yapılan ve kütüphaneye eklenen sorulardan
     * hedefli tekrar testi başlatır.
     */
    fun startDenemeWrongQuestionsTest(
        onlyUnsolved: Boolean = false,
        onSuccess: () -> Unit
    ) {
        val state = uiState.value
        val sourceList = if (onlyUnsolved) {
            state.denemeSorulari.filter { !it.isSolved }.ifEmpty { state.denemeSorulari }
        } else {
            state.denemeSorulari
        }

        if (sourceList.isEmpty()) {
            _uiEvent.tryEmit("Bu denemeye henüz yanlış veya boş soru eklenmemiş.")
            return
        }

        val testQuestions = sourceList.map { TestQuestion(soru = it) }
        val filtre = TestFiltre(
            examType = state.deneme?.sinavTuru,
            selectedSubjects = sourceList.map { it.subjectId }.toSet(),
            selectedTopics = sourceList.map { it.topic }.filter { it.isNotBlank() }.toSet(),
            onlyUnsolved = onlyUnsolved,
            questionCount = testQuestions.size
        )
        testSessionManager.startSession(testQuestions, filtre)
        onSuccess()
    }

    /**
     * Denemedeki belirli bir konunun sorularından hızlı konu testi başlatır.
     */
    fun startDenemeTopicTest(
        subjectId: String,
        topic: String,
        onSuccess: () -> Unit
    ) {
        val state = uiState.value
        val filtered = state.denemeSorulari.filter {
            it.subjectId == subjectId && it.topic.equals(topic, ignoreCase = true)
        }.ifEmpty { state.denemeSorulari }

        if (filtered.isEmpty()) {
            _uiEvent.tryEmit("Bu konu için soru bulunamadı.")
            return
        }

        val testQuestions = filtered.map { TestQuestion(soru = it) }
        val filtre = TestFiltre(
            examType = state.deneme?.sinavTuru,
            selectedSubjects = setOf(subjectId),
            selectedTopics = setOf(topic),
            onlyUnsolved = false,
            questionCount = testQuestions.size
        )
        testSessionManager.startSession(testQuestions, filtre)
        onSuccess()
    }

    /**
     * Bu denemenin yanlış sorularından veya optik formundan PDF kitapçık/fasikül üretir ve paylaşır.
     */
    fun exportDenemePdf(
        context: Context,
        templateMode: PdfTemplateMode = PdfTemplateMode.OSYM_DENEME
    ) {
        val state = uiState.value
        val deneme = state.deneme ?: return
        if (_isExportingPdf.value) return

        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                val config = PdfExportConfig(
                    title = deneme.baslik.ifBlank { "${deneme.sinavTuru} Deneme Sınavı" },
                    subtitle = buildString {
                        if (deneme.yayinEvi.isNotBlank()) append("${deneme.yayinEvi} • ")
                        append("${deneme.sinavTuru} • Net: ${deneme.toplamNet}")
                        if (deneme.testId.isNotBlank()) append(" • Kod: ${deneme.testId}")
                    },
                    templateMode = templateMode,
                    columnsCount = if (templateMode == PdfTemplateMode.COZUMLU_FASIKUL) 1 else 2,
                    includeCoverPage = true,
                    includeBackCover = true,
                    includeAnswerKey = true,
                    includeAiSolution = true,
                    includeStudentNote = true,
                    includeMistakeType = true,
                    includeSourceInfo = true
                )
                val pdfFile = withContext(Dispatchers.IO) {
                    PdfDenemeKitapcikGenerator.generatePdfWithConfig(
                        context = context,
                        sorular = state.denemeSorulari,
                        config = config
                    )
                }
                PdfDenemeKitapcikGenerator.shareOrPrintPdf(context, pdfFile)
                _uiEvent.tryEmit("PDF hazırlandı: ${deneme.baslik}")
            } catch (e: Exception) {
                _uiEvent.tryEmit("PDF oluşturulamadı: ${e.localizedMessage}")
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    fun toggleSoruSolved(soru: Soru) {
        viewModelScope.launch {
            soruRepository.cozumDurumuDegistir(soru.id, !soru.isSolved)
        }
    }

    fun updateAnswerKey(rawAnswerKey: String) {
        val currentEntity = uiState.value.denemeEntity ?: return
        viewModelScope.launch {
            val parsed = parseFlexibleAnswerKeyInput(rawAnswerKey)
            val encoded = if (parsed.isEmpty()) null else json.encodeToString(parsed)
            soruRepository.updateDeneme(
                currentEntity.copy(
                    cevapAnahtariJson = encoded,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiEvent.tryEmit("Cevap anahtarı güncellendi (${parsed.size} soru).")
        }
    }

    fun deleteCurrentDeneme(onDeleted: () -> Unit) {
        val id = uiState.value.denemeEntity?.id ?: return
        viewModelScope.launch {
            soruRepository.deleteDeneme(id)
            onDeleted()
        }
    }

    companion object {
        /**
         * JSON veya "1:A, 2:C, 3:E" ya da "ACDBEA..." biçimindeki cevap dizgilerini Map<Int, String> olarak çözümler.
         */
        fun parseAnswerMap(rawJson: String?): Map<Int, String> {
            if (rawJson.isNullOrBlank()) return emptyMap()
            return try {
                val map = Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, String>>(rawJson)
                map.mapNotNull { (k, v) ->
                    val qNo = k.trim().toIntOrNull()
                    val ans = v.trim().uppercase()
                    if (qNo != null && ans.isNotBlank()) qNo to ans else null
                }.toMap()
            } catch (_: Exception) {
                parseFlexibleAnswerKeyInput(rawJson).mapNotNull { (k, v) ->
                    k.toIntOrNull()?.let { it to v }
                }.toMap()
            }
        }

        /**
         * Kullanıcının girdiği "1:A, 2:B, 3:D" veya "1A 2B 3D" veya düz "ABCDEA..."
         * cevap anahtarını Map<String, String> ("1" -> "A") formatına çevirir.
         */
        fun parseFlexibleAnswerKeyInput(raw: String): Map<String, String> {
            val clean = raw.trim().uppercase()
            if (clean.isBlank()) return emptyMap()

            val result = linkedMapOf<String, String>()
            // 1) "1:A, 2:C" veya "1-A 2-C" veya "1A 2C" deseni
            val pairRegex = Regex("""(\d{1,3})\s*[:=\-.)]?\s*([A-E])""")
            val matches = pairRegex.findAll(clean).toList()
            if (matches.isNotEmpty()) {
                for (m in matches) {
                    val no = m.groupValues[1].toIntOrNull()
                    val opt = m.groupValues[2]
                    if (no != null && no in 1..200) {
                        result[no.toString()] = opt
                    }
                }
                return result
            }

            // 2) Sadece harflerden oluşan dizi ("ABCDE...")
            val lettersOnly = clean.filter { it in 'A'..'E' }
            if (lettersOnly.isNotEmpty() && lettersOnly.length == clean.replace(Regex("\\s+"), "").length) {
                lettersOnly.forEachIndexed { idx, ch ->
                    result[(idx + 1).toString()] = ch.toString()
                }
            }
            return result
        }
    }
}
