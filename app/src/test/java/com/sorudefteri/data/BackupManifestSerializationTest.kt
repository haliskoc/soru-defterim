package com.sorudefteri.data

import com.sorudefteri.data.backup.BackupManifest
import com.sorudefteri.data.backup.BackupSoruItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * .sdefteri / .zip arşiv manifestinin JSON serileştirme, geri çözme ve imza doğrulama birim testi.
 */
class BackupManifestSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    @Test
    fun manifestSerilestirmeVeCozme_kayipsizCalisiyor() {
        val originalItem = BackupSoruItem(
            imageName = "images/soru_0_uuid123.jpg",
            solutionImageName = "images/cozum_0_uuid456.jpg",
            correctAnswer = "C",
            examType = "AYT",
            subjectId = "ayt_fizik",
            topic = "Elektrik ve Manyetizma",
            mistakeType = "FORMUL_UNUTMA",
            note = "Sağ el kuralına dikkat et!",
            isSolved = true,
            solvedAt = 1700000000000L,
            aiExplanation = "🎯 **Doğru Cevap:** C şıkkı.\nLorentz kuvveti formülü...",
            isFavorite = true,
            reviewStage = 3,
            nextReviewDate = 1700604800000L,
            lastReviewedAt = 1700000000000L,
            createdAt = 1699000000000L
        )

        val manifest = BackupManifest(
            appSignature = "com.sorudefteri",
            version = 1,
            exportDate = 1700000000000L,
            questionCount = 1,
            sorular = listOf(originalItem)
        )

        // JSON'a dönüştür (Serialize)
        val jsonString = json.encodeToString(manifest)
        assertTrue("JSON imza içermeli", jsonString.contains("com.sorudefteri"))
        assertTrue("JSON soru görseli içermeli", jsonString.contains("soru_0_uuid123.jpg"))
        assertTrue("JSON formülü içermeli", jsonString.contains("Lorentz kuvveti"))

        // JSON'dan geri oku (Deserialize)
        val decodedManifest = json.decodeFromString<BackupManifest>(jsonString)

        assertEquals("com.sorudefteri", decodedManifest.appSignature)
        assertEquals(1, decodedManifest.version)
        assertEquals(1, decodedManifest.questionCount)
        assertEquals(1, decodedManifest.sorular.size)

        val decodedItem = decodedManifest.sorular[0]
        assertEquals("images/soru_0_uuid123.jpg", decodedItem.imageName)
        assertEquals("images/cozum_0_uuid456.jpg", decodedItem.solutionImageName)
        assertEquals("C", decodedItem.correctAnswer)
        assertEquals("AYT", decodedItem.examType)
        assertEquals("ayt_fizik", decodedItem.subjectId)
        assertEquals("Elektrik ve Manyetizma", decodedItem.topic)
        assertEquals("FORMUL_UNUTMA", decodedItem.mistakeType)
        assertEquals("Sağ el kuralına dikkat et!", decodedItem.note)
        assertEquals(true, decodedItem.isSolved)
        assertEquals(true, decodedItem.isFavorite)
        assertEquals(3, decodedItem.reviewStage)
        assertEquals(1700604800000L, decodedItem.nextReviewDate)
        assertEquals(originalItem.aiExplanation, decodedItem.aiExplanation)
    }

    @Test
    fun gecersizImza_tespitEdiliyor() {
        val sahteJson = """
            {
                "appSignature": "com.baska.uygulama",
                "version": 1,
                "exportDate": 1700000000000,
                "questionCount": 0,
                "sorular": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString<BackupManifest>(sahteJson)
        assertEquals("com.baska.uygulama", manifest.appSignature)
        assertTrue("Geçersiz imza reddedilmeli", manifest.appSignature != "com.sorudefteri")
    }
}
