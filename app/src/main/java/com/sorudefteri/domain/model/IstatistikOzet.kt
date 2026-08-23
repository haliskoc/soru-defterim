package com.sorudefteri.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Ders bazlı soru sayıları ve başarı istatistiği.
 */
data class DersSoruSayisi(
    val subjectId: String,
    val subjectName: String,
    val count: Int,
    val solvedCount: Int = 0,
    val color: Color = Color.Unspecified
) {
    val unsolvedCount: Int get() = (count - solvedCount).coerceAtLeast(0)
    val successRate: Float get() = if (count > 0) (solvedCount.toFloat() / count) * 100f else 0f
}

/**
 * Hata tipi dağılım modeli.
 */
data class HataTipiDagilimi(
    val mistakeType: String,
    val displayName: String = mistakeType,
    val count: Int,
    val percentage: Float = 0f,
    val color: Color = Color.Unspecified
) {
    constructor(mistakeType: HataTipi, count: Int) : this(
        mistakeType = mistakeType.name,
        displayName = mistakeType.displayName,
        count = count,
        percentage = 0f,
        color = mistakeType.defaultColor
    )
}

/**
 * Günlük aktivite modeli.
 */
data class GunlukAktivite(
    val date: String,
    val dayName: String = "",
    val count: Int,
    val solvedCount: Int = 0,
    val timestamp: Long = 0L,
    val dateStr: String = date
) {
    constructor(dateStr: String, count: Int, timestamp: Long) : this(
        date = dateStr,
        dayName = "",
        count = count,
        solvedCount = 0,
        timestamp = timestamp,
        dateStr = dateStr
    )
}

/**
 * Genel İstatistik Özeti
 */
data class IstatistikOzet(
    val toplamSoru: Int = 0,
    val cozulenSoru: Int = 0,
    val cozulmeyenSoru: Int = 0,
    val dersDagilimi: List<DersSoruSayisi> = emptyList(),
    val hataTipiDagilimi: List<HataTipiDagilimi> = emptyList(),
    val sonAktiviteler: List<GunlukAktivite> = emptyList()
) {
    val cozulmeOrani: Float
        get() = if (toplamSoru > 0) (cozulenSoru.toFloat() / toplamSoru) * 100f else 0f
}

typealias IstatistikOzetWrapper = IstatistikOzet
