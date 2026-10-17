package com.sorudefteri.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.domain.manager.BadgeManager
import com.sorudefteri.domain.model.Badge
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.domain.model.UserLevel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * FAZ 4 Birim Testleri:
 * 1. Günlük Seri (Streak) ve Seri Kalkanı (Shield) Davranışları
 * 2. XP Kazanımı, Seviye (LevelInfo/UserLevel) İlerlemesi
 * 3. Başarım Rozetleri (Badges) Kriter Değerlendirmeleri ve Çift Ödül Koruması
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreakAndGamificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var testDataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var ayarlarDataStore: AyarlarDataStore
    private lateinit var mockSoruDao: SoruDao
    private lateinit var badgeManager: BadgeManager
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockEncryptedPrefs = mockk<SharedPreferences>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testDataStoreFile = File.createTempFile("test_ayarlar", ".preferences_pb").apply {
            deleteOnExit()
        }
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + Job()),
            produceFile = { testDataStoreFile }
        )
        AyarlarDataStore.testDataStore = dataStore
        ayarlarDataStore = AyarlarDataStore(mockContext, mockEncryptedPrefs)

        mockSoruDao = mockk(relaxed = true)
        badgeManager = BadgeManager(ayarlarDataStore, mockSoruDao)
    }

    @After
    fun tearDown() {
        AyarlarDataStore.testDataStore = null
        Dispatchers.resetMain()
        testDataStoreFile.delete()
    }

    // =========================================================================
    // 1. Günlük Seri ve Aktivite Eşiği Testleri
    // =========================================================================

    @Test
    fun streak_aktiviteEsigi_5SoruVeya3SoruEklemeIster() = runTest {
        // 4 soru çözüldü, 2 soru eklendi -> hedef tutmadı (< 5 ve < 3)
        val achieved1 = ayarlarDataStore.recordDailyActivity(
            questionsSolvedDelta = 4,
            questionsAddedDelta = 2,
            customDate = "2026-09-01"
        )
        assertFalse(achieved1)
        assertEquals(0, ayarlarDataStore.getCurrentStreak())

        // 1 soru daha çözüldü -> toplam çözülen = 5 >= 5 -> hedef tamam!
        val achieved2 = ayarlarDataStore.recordDailyActivity(
            questionsSolvedDelta = 1,
            questionsAddedDelta = 0,
            customDate = "2026-09-01"
        )
        assertTrue(achieved2)
        assertEquals(1, ayarlarDataStore.getCurrentStreak())
        assertEquals("2026-09-01", ayarlarDataStore.getLastStreakDate())
        assertEquals(1, ayarlarDataStore.getStreakShieldCount())
    }

    @Test
    fun streak_ayniGunIkinciAktivite_seriArtmaz() = runTest {
        // 1. Aktivite
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-01")
        assertEquals(1, ayarlarDataStore.getCurrentStreak())

        // Aynı gün 2. Aktivite
        val achievedAgain = ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 10, customDate = "2026-09-01")
        assertTrue(achievedAgain)
        assertEquals(1, ayarlarDataStore.getCurrentStreak())
    }

    @Test
    fun streak_ardisikGunAktivite_seriArtar() = runTest {
        // 1. Gün (2026-09-01)
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-01")
        assertEquals(1, ayarlarDataStore.getCurrentStreak())

        // 2. Gün (2026-09-02)
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-02")
        assertEquals(2, ayarlarDataStore.getCurrentStreak())
        assertEquals("2026-09-02", ayarlarDataStore.getLastStreakDate())

        // 3. Gün (2026-09-03)
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-03")
        assertEquals(3, ayarlarDataStore.getCurrentStreak())
        assertEquals("2026-09-03", ayarlarDataStore.getLastStreakDate())
    }

    @Test
    fun streak_birGunKacirma_kalkanHarcanirVeSeriKorunur() = runTest {
        // 1. Gün: aktivite yapıldı -> streak: 1, shield: 1
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-01")
        assertEquals(1, ayarlarDataStore.getCurrentStreak())
        assertEquals(1, ayarlarDataStore.getStreakShieldCount())

        // 2. Gün: BOŞ GEÇİLDİ (2026-09-02)

        // 3. Gün: aktivite yapıldı (2026-09-03)
        // 1 gün kaçırıldığı için kalkan harcanır (1 -> 0), seri korunur ve devam eder (1 -> 2)
        val achieved = ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-03")
        assertTrue(achieved)
        assertEquals(2, ayarlarDataStore.getCurrentStreak())
        assertEquals(0, ayarlarDataStore.getStreakShieldCount())
        assertEquals("2026-09-03", ayarlarDataStore.getLastStreakDate())
    }

    @Test
    fun streak_kalkanYokkenGunKacirma_seriSifirlanir() = runTest {
        // 1. Gün: aktivite -> streak 1
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-01")
        // Kalkanı manuel 0 yap
        ayarlarDataStore.setStreakShieldCount(0)

        // 2. Gün boş geçildi

        // 3. Gün: aktivite yapıldı (kalkan yok) -> seri sıfırlanıp 1 olur
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-03")
        assertEquals(1, ayarlarDataStore.getCurrentStreak())
    }

    @Test
    fun streak_ikiGunUstUsteKacirma_kalkanVarkenDahiSeriSifirlanir() = runTest {
        // Seri 3'e çıksın
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-01")
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-02")
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-03")
        assertEquals(3, ayarlarDataStore.getCurrentStreak())
        assertEquals(1, ayarlarDataStore.getStreakShieldCount())

        // 4 ve 5 Eylül BOŞ GEÇİLDİ (2 gün kaçırıldı)

        // 6 Eylül aktivite yapıldı
        ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = "2026-09-06")
        assertEquals(1, ayarlarDataStore.getCurrentStreak())
    }

    // =========================================================================
    // 2. XP Kazanımı ve Kullanıcı Seviye (UserLevel) Testleri
    // =========================================================================

    @Test
    fun gamification_userLevelAraliklari_dogruHesaplanir() {
        assertEquals(UserLevel.CIRAK, UserLevel.fromXp(0))
        assertEquals(UserLevel.CIRAK, UserLevel.fromXp(150))
        assertEquals(UserLevel.CIRAK, UserLevel.fromXp(200))

        assertEquals(UserLevel.HATA_AVCISI, UserLevel.fromXp(201))
        assertEquals(UserLevel.HATA_AVCISI, UserLevel.fromXp(400))
        assertEquals(UserLevel.HATA_AVCISI, UserLevel.fromXp(600))

        assertEquals(UserLevel.ANALIZ_UZMANI, UserLevel.fromXp(601))
        assertEquals(UserLevel.ANALIZ_UZMANI, UserLevel.fromXp(1000))
        assertEquals(UserLevel.ANALIZ_UZMANI, UserLevel.fromXp(1500))

        assertEquals(UserLevel.DERECE_ADAYI, UserLevel.fromXp(1501))
        assertEquals(UserLevel.DERECE_ADAYI, UserLevel.fromXp(2500))
        assertEquals(UserLevel.DERECE_ADAYI, UserLevel.fromXp(3500))

        assertEquals(UserLevel.SINAV_FATIHI, UserLevel.fromXp(3501))
        assertEquals(UserLevel.SINAV_FATIHI, UserLevel.fromXp(10000))
    }

    @Test
    fun gamification_levelInfo_progressVeKalanXpHesabiDogru() {
        // Çırak (0-200 XP): 100 XP -> %50 ilerleme, 100 XP kaldı
        val info1 = badgeManager.getLevelInfo(100)
        assertEquals(UserLevel.CIRAK, info1.level)
        assertEquals(0.5f, info1.progress, 0.01f)
        assertEquals(100, info1.xpRemainingForNextLevel)

        // Hata Avcısı (201-600 XP, aralık 400 XP): 400 XP -> (400-200)/400 = 0.5f, 200 XP kaldı
        val info2 = badgeManager.getLevelInfo(400)
        assertEquals(UserLevel.HATA_AVCISI, info2.level)
        assertEquals(0.5f, info2.progress, 0.02f)
        assertEquals(200, info2.xpRemainingForNextLevel)

        // Sınav Fatihi (3501+ XP): Maksimum seviye, progress 1.0f, kalan 0
        val info3 = badgeManager.getLevelInfo(5000)
        assertEquals(UserLevel.SINAV_FATIHI, info3.level)
        assertEquals(1.0f, info3.progress, 0.01f)
        assertEquals(0, info3.xpRemainingForNextLevel)
    }

    @Test
    fun gamification_xpKazanmaAksiyonlari_dogruDegerlerVerir() = runTest {
        assertEquals(0, ayarlarDataStore.getUserXp())

        // 1. Soru Ekleme: +10 XP
        val xpAfterAdd = badgeManager.onQuestionAdded()
        assertEquals(10, xpAfterAdd)
        assertEquals(10, ayarlarDataStore.getUserXp())

        // 2. Test Bitirme: +25 XP
        val dummyResult = createDummyResult(isFullSuccess = false)
        val xpAfterTest = badgeManager.onTestCompleted(dummyResult, isTimePressure = false, hourOfDay = 14)
        assertEquals(35, ayarlarDataStore.getUserXp())

        // 3. Aşama 5 Usta Yapma: +50 XP
        val xpAfterMaster = badgeManager.onMasterStageReached()
        assertEquals(85, xpAfterMaster)
        assertEquals(85, ayarlarDataStore.getUserXp())
    }

    // =========================================================================
    // 3. Başarım Rozetleri (Badges) Testleri
    // =========================================================================

    @Test
    fun badges_zamanBaskisiModunda_hizCanavariRozetiAcilir() = runTest {
        val result = createDummyResult(isFullSuccess = false)
        val xpGained = badgeManager.onTestCompleted(result, isTimePressure = true, hourOfDay = 14)

        // Test tamamlama 25 XP + HIZ_CANAVARI 50 XP = 75 XP
        assertEquals(75, xpGained)
        val unlocked = ayarlarDataStore.getUnlockedBadges()
        assertTrue(unlocked.contains(Badge.HIZ_CANAVARI.id))
    }

    @Test
    fun badges_yuzdeYuzBasari_hataGecirmezRozetiAcilir() = runTest {
        val result = createDummyResult(isFullSuccess = true)
        val xpGained = badgeManager.onTestCompleted(result, isTimePressure = false, hourOfDay = 14)

        // Test tamamlama 25 XP + HATA_GECIRMEZ 50 XP = 75 XP
        assertEquals(75, xpGained)
        val unlocked = ayarlarDataStore.getUnlockedBadges()
        assertTrue(unlocked.contains(Badge.HATA_GECIRMEZ.id))
    }

    @Test
    fun badges_geceSaatlerindeTest_geceKusuRozetiAcilir() = runTest {
        val result = createDummyResult(isFullSuccess = false)
        // Saat 23:00'te test tamamlandı
        val xpGained = badgeManager.onTestCompleted(result, isTimePressure = false, hourOfDay = 23)

        // Test tamamlama 25 XP + GECE_KUSU 30 XP = 55 XP
        assertEquals(55, xpGained)
        val unlocked = ayarlarDataStore.getUnlockedBadges()
        assertTrue(unlocked.contains(Badge.GECE_KUSU.id))
    }

    @Test
    fun badges_islemHatasiVeGeometri_soruCozumundeAcilir() = runTest {
        coEvery { mockSoruDao.getSolvedCountByMistakeType("ISLEM_HATASI") } returns 5
        coEvery { mockSoruDao.getSolvedGeometriCount() } returns 10

        val newBadges = badgeManager.evaluateBadges()
        assertTrue(newBadges.contains(Badge.ISLEM_HATASI_AVCISI.id))
        assertTrue(newBadges.contains(Badge.GEOMETRI_BUKUCU.id))

        // Her biri 50 XP, toplam 100 XP
        assertEquals(100, ayarlarDataStore.getUserXp())
    }

    @Test
    fun badges_yediliSeri_yediGunlukSerideAcilir() = runTest {
        // 7 gün boyunca ardışık aktivite yap
        for (i in 1..7) {
            val dateStr = "2026-09-%02d".format(i)
            ayarlarDataStore.recordDailyActivity(questionsSolvedDelta = 5, customDate = dateStr)
        }
        assertEquals(7, ayarlarDataStore.getCurrentStreak())

        val newlyUnlocked = badgeManager.evaluateBadges()
        assertTrue(newlyUnlocked.contains(Badge.YEDILI_SERI.id))
        // YEDILI_SERI 100 XP
        assertEquals(100, ayarlarDataStore.getUserXp())
    }

    @Test
    fun badges_oncedenAcilmisRozet_ciftOdulVermez() = runTest {
        // İlk açılış
        val isFirstUnlock = ayarlarDataStore.unlockBadge(Badge.HIZ_CANAVARI.id)
        assertTrue(isFirstUnlock)
        ayarlarDataStore.addXp(Badge.HIZ_CANAVARI.xpReward)
        assertEquals(50, ayarlarDataStore.getUserXp())

        // İkinci açılış denemesi
        val isSecondUnlock = ayarlarDataStore.unlockBadge(Badge.HIZ_CANAVARI.id)
        assertFalse(isSecondUnlock)

        // Tekrar test bitse bile HIZ_CANAVARI tekrar XP vermez
        val result = createDummyResult(isFullSuccess = false)
        val xpGained = badgeManager.onTestCompleted(result, isTimePressure = true, hourOfDay = 14)
        // Sadece test tamamlama XP'si (25 XP) verilir, rozet XP'si verilmez
        assertEquals(25, xpGained)
        assertEquals(75, ayarlarDataStore.getUserXp())
    }

    // =========================================================================
    // Yardımcı Test Fabrikası
    // =========================================================================

    private fun createDummyResult(isFullSuccess: Boolean): TestResult {
        val q1 = TestQuestion(
            soru = Soru(id = 1L, imagePath = "/img/1.jpg", examType = "TYT", subjectId = "matematik", topic = "Sayılar", isSolved = true),
            userAnswer = "A",
            isCorrect = true,
            timeSpentSeconds = 45L
        )
        val q2 = TestQuestion(
            soru = Soru(id = 2L, imagePath = "/img/2.jpg", examType = "TYT", subjectId = "matematik", topic = "Fonksiyon", isSolved = isFullSuccess),
            userAnswer = if (isFullSuccess) "B" else "C",
            isCorrect = isFullSuccess,
            timeSpentSeconds = 60L
        )

        val questions = listOf(q1, q2)
        val correct = if (isFullSuccess) 2 else 1
        val wrong = if (isFullSuccess) 0 else 1

        return TestResult(
            totalQuestions = 2,
            correctCount = correct,
            wrongCount = wrong,
            emptyCount = 0,
            totalTimeSeconds = 105L,
            questions = questions
        )
    }
}
