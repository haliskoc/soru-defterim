package com.sorudefteri.domain.manager

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.domain.model.Badge
import com.sorudefteri.domain.model.LevelInfo
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.domain.model.UserLevel
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XP, Seviyeler ve Başarım Rozetleri (Badges) Yönetim Servisi.
 *
 * XP Ödülleri:
 * - Soru ekleme: +10 XP
 * - Test bitirme: +25 XP
 * - 5. aşama Usta yapma: +50 XP
 * - 7 Günlük Seri: +100 XP
 */
@Singleton
class BadgeManager @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val soruDao: SoruDao
) {

    fun calculateLevel(xp: Int): UserLevel = UserLevel.fromXp(xp)

    fun getLevelInfo(xp: Int): LevelInfo {
        val level = calculateLevel(xp)
        val min = level.minXp
        val max = level.maxXp
        val progress = if (level == UserLevel.SINAV_FATIHI) {
            1.0f
        } else {
            val range = (max - min).toFloat()
            if (range > 0) ((xp - min).toFloat() / range).coerceIn(0f, 1f) else 0f
        }
        val remaining = if (level == UserLevel.SINAV_FATIHI) 0 else (max - xp).coerceAtLeast(0)
        return LevelInfo(
            level = level,
            currentXp = xp,
            currentLevelMinXp = min,
            currentLevelMaxXp = max,
            progress = progress,
            xpRemainingForNextLevel = remaining
        )
    }

    /**
     * Soru ekleme ödülü (+10 XP) ve günlük aktivite kaydı.
     */
    suspend fun onQuestionAdded(): Int {
        // PERF: addXp + recordDailyActivity iki ayrı DataStore edit()'iydi
        // (dosya yeniden yazımı + fsync ×2). Tek edit'te birleştirildi.
        val newXp = ayarlarDataStore.addXpAndRecordActivity(xpDelta = 10, questionsAddedDelta = 1)
        evaluateBadges()
        return newXp
    }

    /**
     * Test tamamlama ödülü (+25 XP), rozet değerlendirmesi ve günlük aktivite kaydı.
     */
    suspend fun onTestCompleted(
        result: TestResult,
        isTimePressure: Boolean = false,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Int {
        var totalXpGained = 25
        // PERF: Temel +25 XP ve rozet XP'leri tek edit'te toplanır;
        // günlük aktivite aynı edit'e gömülür (önceden 3-7 ardışık edit).
        val bonusBadges = mutableListOf<String>()
        var bonusXp = 0

        // HIZ_CANAVARI: Zaman baskısı modunda testi bitir
        if (isTimePressure && result.totalQuestions > 0 && !ayarlarDataStore.hasBadgeCached(Badge.HIZ_CANAVARI.id)) {
            bonusBadges.add(Badge.HIZ_CANAVARI.id)
            bonusXp += Badge.HIZ_CANAVARI.xpReward
            totalXpGained += Badge.HIZ_CANAVARI.xpReward
        }

        // HATA_GECIRMEZ: Bir testte %100 başarı sağla
        if (result.totalQuestions > 0 && result.correctCount == result.totalQuestions &&
            !ayarlarDataStore.hasBadgeCached(Badge.HATA_GECIRMEZ.id)
        ) {
            bonusBadges.add(Badge.HATA_GECIRMEZ.id)
            bonusXp += Badge.HATA_GECIRMEZ.xpReward
            totalXpGained += Badge.HATA_GECIRMEZ.xpReward
        }

        // GECE_KUSU: 23:00 - 05:00 arasında soru çöz
        if ((hourOfDay >= 23 || hourOfDay < 5) && result.totalQuestions > 0 &&
            !ayarlarDataStore.hasBadgeCached(Badge.GECE_KUSU.id)
        ) {
            bonusBadges.add(Badge.GECE_KUSU.id)
            bonusXp += Badge.GECE_KUSU.xpReward
            totalXpGained += Badge.GECE_KUSU.xpReward
        }

        ayarlarDataStore.addXpAndRecordActivity(
            xpDelta = 25 + bonusXp,
            questionsSolvedDelta = result.questions.size,
            bonusBadges = bonusBadges.toSet()
        )

        evaluateBadges()
        return totalXpGained
    }

    /**
     * Soruyu 5. aşama (Usta) yapma ödülü (+50 XP).
     */
    suspend fun onMasterStageReached(): Int {
        val newXp = ayarlarDataStore.addXp(50)
        evaluateBadges()
        return newXp
    }

    /**
     * Rozet şartlarını veritabanı ve ayarlara göre değerlendirip yeni rozetlerin kilidini açar.
     *
     * PERF: Kilitli olmayan rozetler için COUNT sorguları yalnızca ilgili
     * rozet kilitsizken çalışır (önbellekli rozet seti ilk okumada alınır,
     * her rozet için ayrı getUnlockedBadges() okuması yok).
     */
    suspend fun evaluateBadges(): Set<String> {
        val currentBadges = ayarlarDataStore.getUnlockedBadges().toMutableSet()

        // YEDILI_SERI: 7 günlük seri yakala (+100 XP)
        val currentStreak = ayarlarDataStore.getCurrentStreak()
        if (currentStreak >= 7 && !currentBadges.contains(Badge.YEDILI_SERI.id)) {
            if (ayarlarDataStore.unlockBadge(Badge.YEDILI_SERI.id)) {
                ayarlarDataStore.addXp(Badge.YEDILI_SERI.xpReward)
                currentBadges.add(Badge.YEDILI_SERI.id)
            }
        }

        // ISLEM_HATASI_AVCISI: 5 işlem hatası sorusunu çöz
        if (!currentBadges.contains(Badge.ISLEM_HATASI_AVCISI.id)) {
            val count = soruDao.getSolvedCountByMistakeType("ISLEM_HATASI")
            if (count >= 5) {
                if (ayarlarDataStore.unlockBadge(Badge.ISLEM_HATASI_AVCISI.id)) {
                    ayarlarDataStore.addXp(Badge.ISLEM_HATASI_AVCISI.xpReward)
                    currentBadges.add(Badge.ISLEM_HATASI_AVCISI.id)
                }
            }
        }

        // GEOMETRI_BUKUCU: 10 geometri sorusunu çözüldü yap
        if (!currentBadges.contains(Badge.GEOMETRI_BUKUCU.id)) {
            val count = soruDao.getSolvedGeometriCount()
            if (count >= 10) {
                if (ayarlarDataStore.unlockBadge(Badge.GEOMETRI_BUKUCU.id)) {
                    ayarlarDataStore.addXp(Badge.GEOMETRI_BUKUCU.xpReward)
                    currentBadges.add(Badge.GEOMETRI_BUKUCU.id)
                }
            }
        }

        return currentBadges
    }
}
