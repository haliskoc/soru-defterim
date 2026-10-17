package com.sorudefteri.domain.model

/**
 * Başarım Rozetleri (Badges)
 *
 * @property id Benzersiz rozet kimliği
 * @property title Rozet başlığı
 * @property description Başarım açıklaması
 * @property iconName İkon adı
 * @property xpReward Kilidi açıldığında kazanılan XP ödülü
 */
enum class Badge(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val xpReward: Int
) {
    ISLEM_HATASI_AVCISI(
        id = "ISLEM_HATASI_AVCISI",
        title = "İşlem Hatası Avcısı",
        description = "5 işlem hatası sorusunu çöz",
        iconName = "calculate",
        xpReward = 50
    ),
    GEOMETRI_BUKUCU(
        id = "GEOMETRI_BUKUCU",
        title = "Geometri Bükücü",
        description = "10 geometri sorusunu çözüldü yap",
        iconName = "square_foot",
        xpReward = 50
    ),
    GECE_KUSU(
        id = "GECE_KUSU",
        title = "Gece Kuşu",
        description = "23:00 - 05:00 arasında soru çöz",
        iconName = "nights_stay",
        xpReward = 30
    ),
    HATA_GECIRMEZ(
        id = "HATA_GECIRMEZ",
        title = "Hata Geçirmez",
        description = "Bir testte %100 başarı sağla",
        iconName = "shield",
        xpReward = 50
    ),
    HIZ_CANAVARI(
        id = "HIZ_CANAVARI",
        title = "Hız Canavarı",
        description = "Zaman baskısı modunda testi bitir",
        iconName = "bolt",
        xpReward = 50
    ),
    YEDILI_SERI(
        id = "YEDILI_SERI",
        title = "Ateşli Hafta",
        description = "7 günlük seri yakala",
        iconName = "local_fire_department",
        xpReward = 100
    );

    companion object {
        fun fromId(id: String): Badge? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Kullanıcı Seviyesi (XP bazlı)
 *
 * - Level 1: Çırak (0-200 XP)
 * - Level 2: Hata Avcısı (201-600 XP)
 * - Level 3: Analiz Uzmanı (601-1500 XP)
 * - Level 4: Derece Adayı (1501-3500 XP)
 * - Level 5: Sınav Fatihi (3500+ XP)
 */
enum class UserLevel(
    val levelNumber: Int,
    val title: String,
    val minXp: Int,
    val maxXp: Int
) {
    CIRAK(1, "Çırak", 0, 200),
    HATA_AVCISI(2, "Hata Avcısı", 201, 600),
    ANALIZ_UZMANI(3, "Analiz Uzmanı", 601, 1500),
    DERECE_ADAYI(4, "Derece Adayı", 1501, 3500),
    SINAV_FATIHI(5, "Sınav Fatihi", 3501, Int.MAX_VALUE);

    companion object {
        fun fromXp(xp: Int): UserLevel = when {
            xp <= 200 -> CIRAK
            xp <= 600 -> HATA_AVCISI
            xp <= 1500 -> ANALIZ_UZMANI
            xp <= 3500 -> DERECE_ADAYI
            else -> SINAV_FATIHI
        }
    }
}

/**
 * Kullanıcının seviye ilerleme durumu ve XP dağılımı
 */
data class LevelInfo(
    val level: UserLevel,
    val currentXp: Int,
    val currentLevelMinXp: Int,
    val currentLevelMaxXp: Int,
    val progress: Float,
    val xpRemainingForNextLevel: Int
)
