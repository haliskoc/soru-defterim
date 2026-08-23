package com.sorudefteri.data

import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Room SoruEntity ve Domain Soru modeli arasındaki çift yönlü veri dönüşüm birim testi.
 */
class SoruEntityMappingTest {

    @Test
    fun toDomainVeToEntity_tumAlanlariKayipsizDonusturuyor() {
        val domainSoru = Soru(
            id = 42L,
            imagePath = "/data/user/0/com.sorudefteri/files/images/soru_42.jpg",
            solutionImagePath = "/data/user/0/com.sorudefteri/files/images/cozum_42.jpg",
            correctAnswer = "B",
            examType = "AYT",
            subjectId = "ayt_biyoloji",
            topic = "Genden Proteine",
            mistakeType = HataTipi.BILGI_EKSIKLIGI,
            note = "mRNA kodon tablosunu tekrar et",
            isSolved = true,
            solvedAt = 1700000000000L,
            aiExplanation = "Detaylı açıklama...",
            isFavorite = true,
            reviewStage = 4,
            nextReviewDate = 1701200000000L,
            lastReviewedAt = 1700000000000L,
            createdAt = 1699000000000L,
            updatedAt = 1700000000000L
        )

        // Domain -> Entity
        val entity = domainSoru.toEntity()
        assertEquals(42L, entity.id)
        assertEquals(domainSoru.imagePath, entity.imagePath)
        assertEquals(domainSoru.solutionImagePath, entity.solutionImagePath)
        assertEquals("B", entity.correctAnswer)
        assertEquals("AYT", entity.examType)
        assertEquals("ayt_biyoloji", entity.subjectId)
        assertEquals("Genden Proteine", entity.topic)
        assertEquals("BILGI_EKSIKLIGI", entity.mistakeType)
        assertEquals(true, entity.isSolved)
        assertEquals(true, entity.isFavorite)
        assertEquals(4, entity.reviewStage)
        assertEquals(1701200000000L, entity.nextReviewDate)

        // Entity -> Domain
        val mappedDomain = entity.toDomain()
        assertEquals(domainSoru.id, mappedDomain.id)
        assertEquals(domainSoru.imagePath, mappedDomain.imagePath)
        assertEquals(domainSoru.solutionImagePath, mappedDomain.solutionImagePath)
        assertEquals(domainSoru.correctAnswer, mappedDomain.correctAnswer)
        assertEquals(domainSoru.examType, mappedDomain.examType)
        assertEquals(domainSoru.subjectId, mappedDomain.subjectId)
        assertEquals(domainSoru.topic, mappedDomain.topic)
        assertEquals(domainSoru.mistakeType, mappedDomain.mistakeType)
        assertEquals(domainSoru.isSolved, mappedDomain.isSolved)
        assertEquals(domainSoru.isFavorite, mappedDomain.isFavorite)
        assertEquals(domainSoru.reviewStage, mappedDomain.reviewStage)
        assertEquals(domainSoru.nextReviewDate, mappedDomain.nextReviewDate)
        assertEquals(domainSoru.aiExplanation, mappedDomain.aiExplanation)
    }

    @Test
    fun nullAlanlar_guvenliSekildeYoneliyor() {
        val entity = SoruEntity(
            id = 1L,
            imagePath = "/path/test.jpg",
            solutionImagePath = null,
            correctAnswer = null,
            examType = "TYT",
            subjectId = "tyt_turkce",
            topic = "Paragrafta Anlam",
            mistakeType = null,
            note = null,
            isSolved = false,
            solvedAt = null,
            aiExplanation = null,
            isFavorite = false,
            reviewStage = 0,
            nextReviewDate = null,
            lastReviewedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val domain = entity.toDomain()
        assertNull(domain.solutionImagePath)
        assertNull(domain.correctAnswer)
        assertNull(domain.mistakeType)
        assertNull(domain.note)
        assertNull(domain.aiExplanation)
        assertNull(domain.nextReviewDate)
        assertEquals(false, domain.isFavorite)
        assertEquals(0, domain.reviewStage)
    }
}
