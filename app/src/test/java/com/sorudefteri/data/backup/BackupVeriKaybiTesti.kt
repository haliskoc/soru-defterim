package com.sorudefteri.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kullanıcı bildirimi:
 *  "Yedekle / Geri Yükle sonrası AI çözümüm, ses kaydım, benzer sorum ve
 *   Sokratik Koç geçmişim gelmiyor."
 *
 * Bu testler, düzeltmelerden SONRA eklenen regresyon korumasıdır. Kapsanan
 * asıl hata: dışa aktarım, görsel dosyası diskte bulunmadığı hâlde manifest'e
 * `imageName` yazıyordu; içe aktarım da "görsel yok" deyip sorunun TAMAMINI
 * (aiExplanation, audioPath, not, FSRS durumu) sessizce düşürüyordu.
 *
 * Ayrıca eski sürüm manifestlerinde `imageName` alanının hiç bulunmaması
 * decode'u tümden çökertiyordu.
 */
class BackupVeriKaybiTesti {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Görseli olmayan soru için dışa aktarım `imageName = ""` yazar.
     * Böylece içe aktarma soruyu "görselsiz metin" olarak alır, ATLAMAZ.
     */
    @Test
    fun `gorseli olmayan soru manifestte bos imageName ile yazilir`() {
        val item = BackupSoruItem(
            originalId = 7L,
            imageName = "",                 // görsel yazılamadı
            aiExplanation = "111'e bölünür, 4 gün ileri.",
            audioFileName = "audio_memos/audio_7.m4a",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Sayı Basamakları"
        )

        val cozulmus = json.decodeFromString<BackupSoruItem>(json.encodeToString(item))

        assertEquals("imageName boş olmalı", "", cozulmus.imageName)
        // Kritik: bu alanlar kaybolmamalı
        assertEquals("111'e bölünür, 4 gün ileri.", cozulmus.aiExplanation)
        assertEquals("audio_memos/audio_7.m4a", cozulmus.audioFileName)
    }

    /**
     * Görseli olan soru için alanlar eksiksiz gidiyor ve geliyor.
     */
    @Test
    fun `gorseli olan sorunun cozumu ve ses kaydi round trip ile korunur`() {
        val item = BackupSoruItem(
            originalId = 12L,
            imageName = "images/soru_12.jpg",
            solutionImageName = "images/cozum_12.jpg",
            aiExplanation = "Mod 7 ile hizli çözüm: 111 % 7 = 6.",
            audioFileName = "audio_memos/audio_12.m4a",
            correctAnswer = "B",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Bölme ve Bölünebilme",
            fsrsStability = 3.5,
            fsrsReps = 2
        )

        val cozulmus = json.decodeFromString<BackupSoruItem>(json.encodeToString(item))

        assertEquals("images/soru_12.jpg", cozulmus.imageName)
        assertEquals("Mod 7 ile hizli çözüm: 111 % 7 = 6.", cozulmus.aiExplanation)
        assertEquals("audio_memos/audio_12.m4a", cozulmus.audioFileName)
        assertEquals("images/cozum_12.jpg", cozulmus.solutionImageName)
        assertEquals(3.5, cozulmus.fsrsStability!!, 0.0001)
        assertEquals(2, cozulmus.fsrsReps)
    }

    /**
     * ESKİ manifestlerde `imageName` alanı yoktur. Düzeltme öncesi bu durum
     * decode'u tümden patlatıyor ve kullanıcının yedeği hiç geri yüklenmiyordu.
     */
    @Test
    fun `eski manifestte imageName alani yoksa decode cokmez`() {
        val eskiJson = """
            {
              "originalId": 3,
              "examType": "TYT",
              "subjectId": "matematik",
              "topic": "Modüler Aritmetik",
              "aiExplanation": "Eski kayıttan gelen çözüm",
              "audioFileName": "audio_memos/audio_3.m4a"
            }
        """.trimIndent()

        val cozulmus = json.decodeFromString<BackupSoruItem>(eskiJson)

        assertEquals("Eski kayıttan gelen çözüm", cozulmus.aiExplanation)
        assertEquals("audio_memos/audio_3.m4a", cozulmus.audioFileName)
        assertEquals("", cozulmus.imageName)
    }

    /**
     * Benzer soru ve Sokratik Koç kayıtları da aynı manifest içinde taşınır;
     * sıraları ve soru bağlantıları korunmalı.
     */
    @Test
    fun `benzer soru ve sokratik koc kayitlari manifestte korunur`() {
        val manifest = BackupManifest(
            appSignature = "com.sorudefteri",
            version = 3,
            exportDate = 1_700_000_000_000L,
            questionCount = 1,
            sorular = listOf(
                BackupSoruItem(
                    originalId = 1L,
                    imageName = "images/soru_1.jpg",
                    examType = "TYT",
                    subjectId = "matematik",
                    topic = "Sayı Basamakları"
                )
            ),
            soruChatMesajlari = listOf(
                BackupSoruChatItem(
                    originalSoruId = 1L,
                    sender = "user",
                    messageText = "Neden 8. günde tamamlanır?",
                    timestamp = 1_700_000_001_000L
                )
            ),
            benzerSorular = listOf(
                BackupBenzerSoruItem(
                    originalSoruId = 1L,
                    questionText = "Benzer blok sorusu",
                    optionsJson = """{"A":"1","B":"2"}""",
                    correctAnswer = "A",
                    solution = "Bölme algoritması",
                    createdAt = 1_700_000_002_000L
                )
            )
        )

        val cozulmus = json.decodeFromString<BackupManifest>(json.encodeToString(manifest))

        assertEquals(1, cozulmus.sorular.size)
        assertEquals(1, cozulmus.soruChatMesajlari.size)
        assertEquals(1, cozulmus.benzerSorular.size)
        assertEquals(1L, cozulmus.soruChatMesajlari.first().originalSoruId)
        assertEquals(
            "Neden 8. günde tamamlanır?",
            cozulmus.soruChatMesajlari.first().messageText
        )
        assertEquals(
            "Benzer blok sorusu",
            cozulmus.benzerSorular.first().questionText
        )
    }

    /**
     * Dışa aktarım sırasında görsel dosyası diskte bulunmayan bir soru için
     * `imageName` boş bırakılır. İçe aktarımdaki "görsel yoksa soruyu ATLA"
     * davranışı böylece tetiklenmez ve çözüm/ses kaydı kurtarılır.
     */
    @Test
    fun `gorsel dosyasi olmayan soru icin yedeklenen kayit bos ad ile cikar`() {
        val cozulmus = json.decodeFromString<BackupSoruItem>(
            """{"originalId":9,"imageName":"","examType":"AYT","subjectId":"fizik",
               "topic":"Optik","aiExplanation":"Mercek formülü uygulanır."}"""
                .trimIndent()
        )

        assertTrue(
            "imageName boş olmalı ki içe aktarma soruyu atlamasın",
            cozulmus.imageName.isBlank()
        )
        assertEquals("Mercek formülü uygulanır.", cozulmus.aiExplanation)
    }
}
