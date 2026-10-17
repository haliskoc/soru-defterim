package com.sorudefteri.data.ai

/**
 * Harici AI sohbet botlarına (ChatGPT, Gemini, Claude, DeepSeek, Qwen, Kimi)
 * yapıştırılmak üzere optimize edilmiş, uygulamanın UI bileşenleri ve JSON ayrıştırıcısıyla
 * %100 uyumlu çalışan Master Prompt üretici yardımcı nesnesi.
 */
object ExternalAiPromptHelper {

    private const val MAX_LEN_TEXT = 4000
    private const val MAX_LEN_FIELD = 150

    /**
     * Güvenli metin temizleme ve sınırlandırma
     */
    private fun sanitize(input: String?, maxLen: Int = MAX_LEN_FIELD): String {
        if (input.isNullOrBlank()) return ""
        var s = input.trim().take(maxLen)
        s = s.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), " ")
        s = s.replace(Regex("[ \t]{2,}"), " ")
        return s
    }

    // =========================================================================
    // 1. HARİCİ SORU ÇÖZÜMÜ MASTER PROMPTU
    // =========================================================================

    /**
     * Kullanıcının harici AI chatbotuna yapıştıracağı Soru Çözümü promptunu oluşturur.
     * Çıktı formatı doğrudan `MathMarkdownText` ve `AiCozumTab` regex ayrıştırıcısı ile eşleşir.
     */
    fun buildExternalSolutionPrompt(
        ders: String,
        konu: String,
        soruMetni: String? = null,
        dogruCevap: String? = null,
        hataTuru: String? = null,
        sinavTuru: String = "YKS"
    ): String {
        val safeDers = sanitize(ders)
        val safeKonu = sanitize(konu)
        val safeSinav = sanitize(sinavTuru, 30)
        val safeAnswer = sanitize(dogruCevap, 10)
        val safeMistake = sanitize(hataTuru, 100)
        val safeSoruMetni = if (!soruMetni.isNullOrBlank()) sanitize(soruMetni, MAX_LEN_TEXT) else null

        val answerSection = if (safeAnswer.isNotBlank()) {
            "- Bilinen Doğru Cevap Şıkkı: $safeAnswer"
        } else {
            "- Doğru Cevap Şıkkı: Belirtilmemiş (Lütfen soruyu çözerek doğru şıkkı kesin olarak belirle)"
        }

        val mistakeSection = if (safeMistake.isNotBlank()) {
            "- Öğrencinin Yaptığı Hata Türü: $safeMistake (Çözümün 'Hata & Çeldirici Analizi' bölümünde bu hataya ve çeldirici tuzaklarına özellikle dikkat çek!)"
        } else {
            "- Öğrencinin Yaptığı Hata: Genel kavram eksikliği veya dikkatsizlik"
        }

        val questionContentSection = if (!safeSoruMetni.isNullOrBlank()) {
            """
            ---
            [SORU METNİ / OCR VERİSİ]:
            $safeSoruMetni
            """.trimIndent()
        } else {
            """
            ---
            [SORU GÖRSELİ]:
            (Bu prompt ile birlikte sorunun görselini paylaştım. Lütfen görseldeki soruyu okuyarak çöz.)
            """.trimIndent()
        }

        return """
            Sen Türkiye $safeSinav sınav müfredatına (MEB/ÖSYM) tam hakim, pedagojik formasyonu yüksek uzman bir başöğretmensin.
            Sana aşağıda bilgileri (veya görseli) verilen soruyu en ince ayrıntısına kadar, öğrencinin aklında hiçbir soru işareti bırakmayacak pedagojik bir dille çözmeni istiyorum.

            [SORU BİLGİLERİ]:
            - Sınav Türü: $safeSinav
            - Ders: $safeDers
            - Konu: $safeKonu
            $answerSection
            $mistakeSection

            $questionContentSection

            ════════════════════════════════════════════════════════════════════════
            KATI BİÇİMLENDİRME VE ÇIKTI KURALLARI (MUTLAKA UYULMASI GEREKİR):
            ════════════════════════════════════════════════════════════════════════
            1. Yanıtına "Merhaba", "Tabii ki", "İşte çözümünüz" gibi GİRİŞ veya SONUÇ CÜMLELERİ EKLEME.
            2. NOKTALAR/MADDE İŞARETLERİ YIĞINI OLUŞTURMA:
               - Her alt satırın veya formülün başına ASLA tire (-) veya madde işareti koyma!
               - Alt başlıkları madde imi olmadan doğrudan **Kavram Adı:** şeklinde yaz (Örn: **Türevin Limit Tanımı:**). Asla "- **Türevin Limit Tanımı:**" yazma!
               - Çözüm adımlarını 1., 2., 3. gibi numaralandırılmış net adımlar halinde veya akıcı paragraflarla açıkla.
            3. KATI LATEX KURALLARI:
               - Kesirli ifadelerde MUTLAKA \frac{pay}{payda} kullan (örn: ${'$'}\frac{a}{b}${'$'}, ${'$'}\frac{3}{4}${'$'}). Asla ham a/b bırakma.
               - Limit için \lim_{x \to a}, L'Hôpital ve belirsizlik için \frac{0}{0}, eşit değil için \neq, küçük/büyük eşit için \le ve \ge kullan.
               - Tüm matematiksel ve fiziksel formülleri satır içi ${'$'}...${'$'} veya blok ${'$'}${'$'}...${'$'}${'$'} içine al.
            4. Doğrudan ve SADECE aşağıdaki 5 ana başlığı ve emojileri eksiksiz kullanarak yanıt ver:

            🎯 **Doğru Cevap:** [Net doğru şık ve sonuç, örn: C) 24]

            📚 **Temel Kavramlar & Formüller:**
            Soruyu çözmek için gereken temel kuralları, tanımları ve formülleri yaz. İlgili kavram başlıklarını **Kavram:** biçiminde alt başlık yaparak açıkla (başına - koyma). Formülleri standart LaTeX biçiminde ${'$'}...${'$'} içine al.

            📝 **Adım Adım Ayrıntılı Çözüm:**
            1. Soruda verilenler ve istenenlerin analizi.
            2. Matematiksel/mantıksal işlem basamakları (ara adımları atlamadan, adım adım numaralandırarak göster).
            3. Sonuca ulaşma ve doğru şık ile eşleştirme.

            🔍 **Hata & Çeldirici Analizi:**
            Öğrencilerin bu soruda en çok düştüğü kavram yanılgıları, işlem hataları ve çeldirici şıkların hangi mantık tuzaklarıyla hazırlandığı.

            💡 **Püf Noktaları & Sınav Taktikleri:**
            ÖSYM/YKS sınavında bu soru tipinde zaman kazandıran pratik yöntemler, kısa yollar ve sağlama taktikleri.
        """.trimIndent()
    }

    // =========================================================================
    // 2. HARİCİ BENZER SORU TÜRETME MASTER PROMPTU
    // =========================================================================

    /**
     * Kullanıcının harici AI chatbotuna yapıştıracağı Benzer Soru Türetme promptunu oluşturur.
     * Çıktı formatı hem doğrudan okunabilir hem de `AiJsonParser.cleanAndParseJson` ve Regex fallback ile %100 ayrıştırılabilir.
     */
    fun buildExternalSimilarQuestionPrompt(
        ders: String,
        konu: String,
        kaynakSoruMetni: String? = null,
        zorluk: String = "ÖSYM Tarzı",
        sinavTuru: String = "TYT"
    ): String {
        val safeDers = sanitize(ders)
        val safeKonu = sanitize(konu)
        val safeZorluk = sanitize(zorluk, 40)
        val safeSinav = sanitize(sinavTuru, 30)
        val safeKaynak = if (!kaynakSoruMetni.isNullOrBlank()) sanitize(kaynakSoruMetni, MAX_LEN_TEXT) else null

        val sourceSection = if (!safeKaynak.isNullOrBlank()) {
            """
            ---
            [REFERANS / KAYNAK SORU]:
            $safeKaynak
            """.trimIndent()
        } else {
            """
            ---
            [REFERANS / KAYNAK SORU GÖRSELİ]:
            (Bu prompt ile birlikte referans sorunun görselini paylaştım. Lütfen görseldeki soruyu incele.)
            """.trimIndent()
        }

        return """
            Sen ÖSYM ve MEB standartlarına tam hakim kıdemli bir $safeSinav Soru Yazarısın.
            Görevin: Aşağıda verilen kaynak soruyu pedagojik ve kavramsal olarak analiz etmek ve bu soruyla TAM OLARAK AYNI KAZANIMI, soru kalıbını ve zorluk seviyesini ölçen, fakat senaryosu, sayıları, öncülleri ve kurgusu FARKLI yepyeni ve özgün bir test sorusu üretmektir.

            [HEDEF KRİTERLER]:
            - Sınav Türü: $safeSinav
            - Ders: $safeDers
            - Konu: $safeKonu
            - Zorluk Seviyesi: $safeZorluk

            $sourceSection

            ════════════════════════════════════════════════════════════════════════
            KATI KURALLAR VE ÇIKTI FORMATI:
            ════════════════════════════════════════════════════════════════════════
            1. Soru 5 şıklı (A, B, C, D, E) olmalı ve yalnızca TEK BİR KESİN DOĞRU CEVABI bulunmalıdır.
            2. Çeldirici şıklar rastgele değil, öğrencilerin yapabileceği tipik kavram yanılgılarına ve işlem hatalarına göre hesaplanmalıdır.
            3. KATI LATEX KURALLARI:
               - Kesirli ifadeleri hem soru metninde hem de şıklarda MUTLAKA \frac{pay}{payda} formatında LaTeX ile yaz (örn: ${'$'}\frac{3}{4}${'$'}). Asla ham 3/4 bırakma.
               - Limit için \lim_{x \to a}, belirsizlik için \frac{0}{0}, eşitsizlik için \neq, \le, \ge kullan.
               - Formülleri satır içi ${'$'}...${'$'} içine al.
            4. JSON GÜVENLİĞİ:
               - JSON sözdiziminin bozulmaması için formüllerdeki ters eğik çizgileri mutlaka çift ters eğik çizgi ile kaçır (\\frac, \\sqrt, \\lim, \\neq, \\cdot).
               - JSON stringleri içinde ham satır sonları yerine \n kaçışı kullan.
            5. ÇÖZÜM METNİ DÜZENİ:
               - Çözüm metninde her alt satıra gereksiz tire (-) koyma.
               - Adımları 1., 2., 3. şeklinde numaralandır veya **Adım 1:** gibi temiz alt başlıklarla paragraflar halinde yaz.
            6. Yanıtını SADECE aşağıdaki JSON kod bloğu içinde ver. Markdown kod bloğu öncesinde veya sonrasında sohbet metni ekleme:

            ```json
            {
              "questionText": "Özgün soru metni, varsa öncüller (I, II, III) ve soru kökü...",
              "options": {
                "A": "A şıkkı metni",
                "B": "B şıkkı metni",
                "C": "C şıkkı metni",
                "D": "D şıkkı metni",
                "E": "E şıkkı metni"
              },
              "correctAnswer": "A",
              "solution": "Adım adım ayrıntılı çözüm, doğru cevabın ispatı ve çeldiricilerin analizi..."
            }
            ```
        """.trimIndent()
    }
}
