package com.sorudefteri.domain.reels

import com.sorudefteri.domain.model.Soru
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Soru Reels akışı için önceliklendirme ve sıralama algoritması.
 *
 * Araştırma temelli kurulum (özet):
 * - Feed algoritmalarında **yakınlık (recency)** etkisi zaman temelli bozulma ile
 *   gömülür (Hacker News / Reddit sıcaklık skorları, üstel zaman bazlı decay
 *   `N(t) = N0 * 2^(-t / t½)`): yeni eklenen sorular yüksek ağırlıkla öne çıkar,
 *   zaman geçtikçe ağırlıkları üstel (yarı-ömür) biçimde soğur.
 * - Sıralama + rastgele karıştırma kombinasyonu bilinen klasik hatadır: `shuffled()`,
 *   öncelik sıralamasını yok eder. Bunun yerine **ağırlıklı karıştırma** kullanılır
 *   (Efraimidis & Spirakis, A-Res ilkesi): her soruya `key = ln(u) / w` atanır,
 *   key'e göre azalan sıralama → yüksek ağırlık çoğunlukla öne, düşük ağırlık
 *   geride ama liste içinde daima belirir. Rastgelelik ağırlık oranını bozmaz.
 * - Eski soruların tamamen kaybolmasını önlemek için **taban ağırlık
 *   (unutma sigortası)** kurulur: eşikten (30 gün) yaşlı her sorunun toplam
 *   ağırlığı en az [Ayarlar.eskiSoruTabanAgirligi] değerine kilitlenir.
 * - Tekrarı gelmiş (due) sorular akışın başında blok oluşturur; gecikme arttıkça
 *   (overdueDays / 14) aciliyet vurgusu birikerek büyür.
 */
object ReelsAkisAlgoritmasi {

    /** Gün başına milisaniye. */
    private const val GUN_MILLIS = 86_400_000L

    /**
     * Algoritma davranış değişkenleri.
     */
    data class Ayarlar(
        /** Yenilik etkisinin yarı-ömrü (gün). 4 gün → 4 gün sonra yeni etki yarıya iner. */
        val yenilikYariOmurGun: Double = 4.0,

        /** Tekrar gecikmesi çarpanı: 14 gün gecikmiş soru tekrar vurgusunun tepesine çıkarır. */
        val tekrarGecikmeAgirligi: Double = 2.2,

        /** Çözülememiş sorulara ek içerik vurgusu. */
        val cozulmemisAgirligi: Double = 0.45,

        /** Hata tipi işaretli sorulara ek vurgu. */
        val hataAgirligi: Double = 0.55,

        /** Favori sorulara ek vurgu. */
        val favoriAgirligi: Double = 0.22,

        /** Taban ağırlık (unutma sigortası) değeri: eski soruların güvence altındaki ağırlığı. */
        val eskiSoruTabanAgirligi: Double = 0.10,

        /** Taban ağırlığın devreye girdiği yaş eşiği (gün). */
        val tabanBaslangicGun: Double = 30.0,

        /** Çok eski sorularda yenilik bileşeninin alt tavanı. */
        val yenilikAltSiniri: Double = 0.02,

        /** Tekrarı ileride planlanmış soruların vurgusu (sakin akış). */
        val gelecekTekrarVurgusu: Double = 0.40,

        /** Tekrar planı olmayan, çözülmüş soruların vurgusu. */
        val plansizVurgu: Double = 0.55
    )

    /** Tek bir sorunun akış ağırlığı + bileşen ayrışımı (test/debug amaçlı). */
    data class Skor(
        val toplam: Double,
        val yenilik: Double,
        val tekrarVurgusu: Double,
        val icerikVurgusu: Double,
        val taban: Double
    )

    /**
     * Bir sorunun, verilen [anZaman]a göre akış ağırlığını hesaplar.
     *
     *   w = taban + tekrarVurgusu * (0.30 + 0.70 * yenilik) + icerikVurgusu
     *
     * id ile en az [Ayarlar.eskiSoruTabanAgirligi] garanti edilir (unutma sigortası).
     */
    fun skorHesapla(soru: Soru, anZaman: Long, ayarlar: Ayarlar = Ayarlar()): Skor {
        val yasGun = max(0.0, (anZaman - soru.createdAt).toDouble() / GUN_MILLIS)

        // 1. YENİLİK: yarı-ömrü üstel azalma = 2^(-yaş / t½)
        val yenilik = max(
            ayarlar.yenilikAltSiniri,
            if (yasGun <= 0.0) 1.0 else 2.0.pow(-yasGun / ayarlar.yenilikYariOmurGun)
        )

        // 2. TEKRAR / ACİLİYET VURGUSU
        val next = soru.nextReviewDate
        val tekrarVurgusu = when {
            // Tekrar planı yok ama çözülmemiş → çözülmeyi bekleyen birikim (backlog).
            next == null && !soru.isSolved -> 1.6
            // Tekrar planı yok ve çözülmüş → normal görünme.
            next == null -> ayarlar.plansizVurgu
            // Tekrarı gelmiş: gecikme uzunsa o kadar öne.
            anZaman >= next -> {
                val gecikmeGun = max(0.0, (anZaman - next).toDouble() / GUN_MILLIS)
                1.6 + ayarlar.tekrarGecikmeAgirligi * min(1.0, gecikmeGun / 14.0)
            }
            // Tekrarı ileride: sakin akış.
            else -> ayarlar.gelecekTekrarVurgusu
        }

        // 3. İÇERİK VURGUSU: hata tipi, favori, çözülmemişlik ödüllendirilir.
        var icerik = 0.25
        if (soru.mistakeType != null) icerik += ayarlar.hataAgirligi
        if (soru.isFavorite) icerik += ayarlar.favoriAgirligi
        if (!soru.isSolved) icerik += ayarlar.cozulmemisAgirligi

        // 4. TABAN (unutma sigortası): yaş eşiği geçen her soru en az taban ağırlıkta.
        val taban = if (yasGun >= ayarlar.tabanBaslangicGun) ayarlar.eskiSoruTabanAgirligi else 0.0

        val toplam = max(
            ayarlar.eskiSoruTabanAgirligi,
            taban + tekrarVurgusu * (0.30 + 0.70 * yenilik) + icerik
        )
        return Skor(toplam, yenilik, tekrarVurgusu, icerik, taban)
    }

    /**
     * Ağırlıklı karıştırma (Efraimidis & Spirakis ilkesi):
     * key = ln(u) / w (u ~ U(0,1])). Key'e göre azalan sıralama → yüksek ağırlık
     * çoğunlukla öne, ağırlık oransal dengesi korunarak doğalcı bir dağılım sağlanır.
     */
    fun agirlikliKaristir(sorular: List<Soru>, skorlar: List<Double>, tohum: Long): List<Soru> {
        if (sorular.isEmpty()) return emptyList()
        val random = Random(tohum)
        val esler = sorular.mapIndexed { index, soru ->
            val w = max(1e-6, skorlar[index])
            val u = random.nextDouble().coerceIn(1e-12, 1.0)
            soru to (ln(u) / w)
        }
        return esler.sortedWith(
            compareByDescending<Pair<Soru, Double>> { it.second }
                .thenBy { it.first.id } // deterministik eşitlik kırılıcısı
        ).map { it.first }
    }

    /**
     * Akışı kurar:
     * 1. ACİL BLOK — tekrai gelen ve çözülmeyi bekleyen sorular akışın başında:
     *    en çok gecikmiş (overdue) en önde, ardından backlog (planlanmamış çözsüz).
     * 2. KALANLAR — yenilik + içerik + taban bileşimli ağırlıkla ağırlıklı karıştırılır.
     * 3. Taban ağırlık eski soruların akışta seyrek ama daima görünmesini sağlar.
     * 4. Hiçbir soru düşürülmez: dönen liste girdi listesiyle aynı eleman sayısına sahiptir.
     *
     * [tohum] gün tohumu ise gün içinde liste kararlı kalır; "karıştır" düğmesi
     * yeni tohum vererek farklı bir doğalcı dağıtımı üretir.
     */
    fun akisKuras(
        sorular: List<Soru>,
        anZaman: Long,
        tohum: Long,
        ayarlar: Ayarlar = Ayarlar()
    ): List<Soru> {
        if (sorular.isEmpty()) return emptyList()
        val skorlar = sorular.map { skorHesapla(it, anZaman, ayarlar).toplam }

        // Acil blok: (a) tekrarı gelmiş; (b) plansiz ama çözülmemiş (backlog).
        val dueIndexler = sorular.indices.filter { i ->
            val s = sorular[i]
            val n = s.nextReviewDate
            (n != null && anZaman >= n) || (n == null && !s.isSolved)
        }

        val dueListe = if (dueIndexler.isEmpty()) {
            emptyList()
        } else {
            dueIndexler.sortedWith(
                compareBy<Int> { i ->
                    aciliyetSirasi(sorular[i], anZaman, ayarlar)
                }.thenBy { sorular[it].id }
            ).map { sorular[it] }
        }

        val dueIndexKumesi = dueIndexler.toHashSet()
        val kalanSkorEsleri = sorular.mapIndexed { index, soru ->
            if (index in dueIndexKumesi) null else soru to skorlar[index]
        }.filterNotNull()

        val kalanKarismis = agirlikliKaristir(
            kalanSkorEsleri.map { it.first },
            kalanSkorEsleri.map { it.second },
            tohum
        )

        return dueListe + kalanKarismis
    }

    /**
     * Aciliyet sıralama anahtarı: küçük değer = daha acil = daha önde.
     * Gecikmiş tekrarlar: −gecikmeGün (en gecikmiş en küçük)
     * Backlog (plansız + çözsüz): 0.0
     */
    private fun aciliyetSirasi(soru: Soru, anZaman: Long, ayarlar: Ayarlar): Double {
        val next = soru.nextReviewDate ?: return 0.0
        if (anZaman < next) return 0.0 // koruma; bu dal filtreyle dışlanmış olmalı
        val gecikmeGun = (anZaman - next).toDouble() / GUN_MILLIS
        return -min(14.0, max(0.0, gecikmeGun))
    }

    /** Gün içinde kararlı bir tohum (epoch day bazlı). */
    fun gunTohumu(anZaman: Long): Long = anZaman / GUN_MILLIS
}
