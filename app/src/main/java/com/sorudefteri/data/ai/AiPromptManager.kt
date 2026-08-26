package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI istemleri için desteklenen senaryo türleri.
 */
enum class AiPromptType(val title: String, val description: String) {
    SORU_URETIMI(
        title = "Soru Üretimi",
        description = "Konu, zorluk, alan ve sınav tipine göre Türkçe, şıklı ve LaTeX destekli soru üretimi."
    ),
    COZUM_URETIMI(
        title = "Çözüm Üretimi",
        description = "Adım adım pedagojik çözüm, temel kavramlar, hata analizi ve püf noktaları."
    ),
    OCR_TEMIZLEME(
        title = "OCR Temizleme & LaTeX",
        description = "OCR ile okunan bozuk formülleri, indeksleri ve Türkçe karakterleri düzelten prompt."
    ),
    BENZER_SORU(
        title = "Benzer Soru Türetme",
        description = "Sorunun kazanımını koruyarak yeni sayılar/senaryo ile benzer soru türetme."
    )
}

/**
 * 4 ana senaryo için detaylı, modüler, versiyonlanabilir ve kullanıcı tarafından düzenlenebilir
 * System Prompt yöneticisi.
 */
@Singleton
class AiPromptManager @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore
) {

    companion object {
        val PROMPT_SORU_URETIMI_DEFAULT = """
            Sen Türkiye YKS (TYT / AYT / YDT) sınav sistemine ve MEB müfredatına tam hakim, kıdemli bir soru yazarısın.
            Verilen sınav türü, alan, ders ve konu başlığına uygun, pedagojik olarak kusursuz, özgün ve kaliteli bir test sorusu üret.

            Kurallar:
            1. Soru metni açık, anlaşılır ve dil bilgisi kurallarına uygun Türkçe olmalıdır.
            2. Matematiksel, fiziksel veya kimyasal formülleri standart LaTeX biçiminde ($...$ satır içi, $$...$$ blok) sun.
            3. A, B, C, D, E olmak üzere 5 seçenek sun. Seçenekler homojen ve mantıklı olmalıdır.
            4. Yalnızca tek bir kesin doğru cevap bulunmalı, çeldiriciler öğrencilerin sık yaptığı kavram yanılgılarına dayanmalıdır.
            5. Soru için adım adım ayrıntılı çözümü de ekle.

            Yanıtını SADECE aşağıdaki JSON formatında ver, markdown kod bloğu dışında ek metin ekleme:
            {
              "questionText": "Soru metni ve öncüller...",
              "options": {
                "A": "Seçenek A",
                "B": "Seçenek B",
                "C": "Seçenek C",
                "D": "Seçenek D",
                "E": "Seçenek E"
              },
              "correctAnswer": "A",
              "solution": "Adım adım ayrıntılı çözüm..."
            }
        """.trimIndent()

        val PROMPT_COZUM_URETIMI_DEFAULT = """
            Sen Türkiye YKS (TYT / AYT) sınavlarına hazırlanan öğrenciler için alanında uzman, pedagojik formasyona sahip kıdemli bir öğretmensin.
            Verilen soruyu öğrencinin eksiksiz anlayacağı şekilde, adım adım ve pedagojik yöntemlerle açıkla.
            Matematiksel formülleri ve ifadeleri standart LaTeX biçiminde (örn: ${'$'}x^2 + 5x + 6 = 0${'$'}, ${'$'}\int_a^b f(x)dx${'$'}, ${'$'}\frac{a}{b}${'$'}, ${'$'}\sum_{i=1}^n${'$'}) sun.

            Lütfen çözümü aşağıdaki yapılandırılmış format ve başlıklarla sun:

            🎯 **Doğru Cevap:** [Net doğru cevap şıkkı ve sonuç]

            📚 **Temel Kavramlar & Formüller:**
            - Soruyu çözmek için gereken temel kurallar, formüller veya kavramlar.

            📝 **Adım Adım Ayrıntılı Çözüm:**
            1. Soruda verilenler ve istenenlerin analizi.
            2. Adım adım matematiksel/mantıksal işlem süreci (tüm adımları sade ve net göster).
            3. Sonuca ulaşma ve doğru şıkla eşleştirme.

            🔍 **Hata & Çeldirici Analizi:**
            - Bu soruda öğrencilerin en sık düştüğü kavram yanılgıları ve çeldirici şıkların tuzakları.

            💡 **Püf Noktaları & Sınav Taktikleri (Önemli İpuçları):**
            - YKS'de bu soru tipinde zaman kazandıran pratik ipuçları veya pratik sağlama yöntemi.
        """.trimIndent()

        val PROMPT_OCR_TEMIZLEME_DEFAULT = """
            Sen OCR (Optik Karakter Tanıma) metin düzeltme ve matematiksel formül LaTeX formatlama uzmanısın.
            Aşağıda bir sınav sorusunun görselinden OCR ile taranmış ham ve bozuk metin verilmiştir.
            
            Görevlerin:
            1. Türkçe karakter hatalarını (ı/i, ş/s, ğ/g, ç/c, ö/o, ü/u) düzelt.
            2. Matematiksel ifadeleri, kırık formülleri, üs ve indisleri standart LaTeX biçimine (${'$'}...${'$'}) dönüştür. (Örn: x2 -> ${'$'}x^2${'$'}, 1/2 -> ${'$'}\frac{1}{2}${'$'}, f(x)= -> ${'$'}f(x)=${'$'}).
            3. Soru öncüllerini (I, II, III vb.), soru kökünü ve seçenekleri (A, B, C, D, E) net bir şekilde yapılandır.
            4. Sayfa numarası, yayınevi filigranı veya tarama gürültüsü gibi gereksiz kısımları temizle.
            5. Soru içeriğini, anlamını veya sayısal değerlerini ASLA değiştirme; yalnızca yazım, format ve LaTeX düzenlemesi yap.

            Temizlenmiş, düzgün ve LaTeX destekli soru metnini doğrudan döndür.
        """.trimIndent()

        val PROMPT_BENZER_SORU_DEFAULT = """
            Sen uzman bir YKS (TYT / AYT) soru yazarısın.
            Verilen soruyu pedagojik ve kavramsal olarak analiz et. Bu soruyla TAM OLARAK AYNI KAZANIMI, soru kalıbını ve zorluk seviyesini ölçen, fakat sayıları, öncülleri, metni veya senaryosu FARKLI yepyeni ve özgün bir soru üret.

            Kurallar:
            1. Matematiksel formülleri standart LaTeX formatında ($...$) yaz.
            2. A, B, C, D, E olmak üzere 5 şık ver.
            3. Doğru cevabı ve adım adım ayrıntılı çözümünü hazırla.

            Yanıtını SADECE aşağıdaki geçerli JSON formatında ver, fazladan açıklama ekleme:
            {
              "questionText": "Soru metni ve öncüller...",
              "options": {
                "A": "Şık A",
                "B": "Şık B",
                "C": "Şık C",
                "D": "Şık D",
                "E": "Şık E"
              },
              "correctAnswer": "A",
              "solution": "Adım adım ayrıntılı çözüm..."
            }
        """.trimIndent()
    }

    /**
     * Belirtilen senaryo türü için varsayılan sistem istemini döndürür.
     */
    fun getDefaultPrompt(type: AiPromptType): String {
        return when (type) {
            AiPromptType.SORU_URETIMI -> PROMPT_SORU_URETIMI_DEFAULT
            AiPromptType.COZUM_URETIMI -> PROMPT_COZUM_URETIMI_DEFAULT
            AiPromptType.OCR_TEMIZLEME -> PROMPT_OCR_TEMIZLEME_DEFAULT
            AiPromptType.BENZER_SORU -> PROMPT_BENZER_SORU_DEFAULT
        }
    }

    /**
     * Kullanıcının özel bir istemi varsa onu, yoksa varsayılan istemi döndüren Flow.
     */
    fun getEffectivePromptFlow(type: AiPromptType): Flow<String> {
        return ayarlarDataStore.getCustomPrompt(type).map { custom ->
            custom.ifBlank { getDefaultPrompt(type) }
        }
    }

    /**
     * Kullanıcının özel bir istemi varsa onu, yoksa varsayılan istemi anlık döndürür.
     */
    suspend fun getEffectivePrompt(type: AiPromptType): String {
        val custom = ayarlarDataStore.getCustomPromptValue(type)
        return custom.ifBlank { getDefaultPrompt(type) }
    }

    /**
     * Soru üretimi için tam prompt metnini oluşturur.
     */
    suspend fun buildSoruUretimiPrompt(
        dersAdi: String,
        konu: String,
        zorluk: String = "ÖSYM Tarzı",
        alan: String = "Sayısal",
        sinavTipi: String = "TYT",
        customTemplate: String? = null
    ): String {
        val basePrompt = customTemplate?.ifBlank { null } ?: getEffectivePrompt(AiPromptType.SORU_URETIMI)
        return """
            $basePrompt

            ---
            Hedef Kriterler:
            - Sınav Türü: $sinavTipi
            - Alan: $alan
            - Ders: $dersAdi
            - Konu: $konu
            - Zorluk Seviyesi: $zorluk
        """.trimIndent()
    }

    /**
     * Çözüm üretimi için tam prompt metnini oluşturur.
     */
    suspend fun buildCozumUretimiPrompt(
        dersAdi: String,
        konu: String,
        soruMetni: String? = null,
        correctAnswer: String? = null,
        mistakeType: String? = null,
        customTemplate: String? = null
    ): String {
        val basePrompt = customTemplate?.ifBlank { null } ?: getEffectivePrompt(AiPromptType.COZUM_URETIMI)

        val answerInfo = if (!correctAnswer.isNullOrBlank()) {
            "Doğru cevap şıkkı: $correctAnswer"
        } else {
            "Doğru cevap şıkkı belirtilmemiş. Lütfen soruyu çözerek doğru şıkkı ve sonucu net olarak belirle."
        }

        val mistakeInfo = if (!mistakeType.isNullOrBlank()) {
            "Öğrencinin yaptığı hata türü: $mistakeType (Çözümde bu hata türüne özel uyarı ve analiz ekle)."
        } else {
            ""
        }

        val textInfo = if (!soruMetni.isNullOrBlank()) {
            "Soru Metni:\n$soruMetni"
        } else {
            "Görseldeki soruyu analiz et."
        }

        return """
            $basePrompt

            ---
            Ders: $dersAdi
            Konu: $konu
            $answerInfo
            $mistakeInfo
            $textInfo
        """.trimIndent()
    }

    /**
     * OCR temizleme için tam prompt metnini oluşturur.
     */
    suspend fun buildOcrTemizlemePrompt(
        rawOcrText: String,
        customTemplate: String? = null
    ): String {
        val basePrompt = customTemplate?.ifBlank { null } ?: getEffectivePrompt(AiPromptType.OCR_TEMIZLEME)
        return """
            $basePrompt

            ---
            [HAM OCR METNİ]:
            $rawOcrText
        """.trimIndent()
    }

    /**
     * Benzer soru türetme için tam prompt metnini oluşturur.
     */
    suspend fun buildBenzerSoruPrompt(
        dersAdi: String,
        konu: String,
        kaynakSoruMetni: String? = null,
        customTemplate: String? = null
    ): String {
        val basePrompt = customTemplate?.ifBlank { null } ?: getEffectivePrompt(AiPromptType.BENZER_SORU)
        val sourceInfo = if (!kaynakSoruMetni.isNullOrBlank()) {
            "Kaynak Soru Metni:\n$kaynakSoruMetni"
        } else {
            "Görseldeki kaynak soruyu analiz et."
        }

        return """
            $basePrompt

            ---
            Ders: $dersAdi
            Konu: $konu
            $sourceInfo
        """.trimIndent()
    }
}
