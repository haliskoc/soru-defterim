# Kamera Otomatik Soru Tespiti — En Yüksek Doğruluk Çözümü + Aç/Kapa Planı

**Belge sürümü:** v1.1 (uygulandı 2026-10-18)
**Tarih:** 2026-10-18
**Tür:** Araştırma + kapsam planı
**İlgili kod:** `ui/kamera/*`, `domain/kamera/*`, `data/local/AyarlarDataStore.kt`
**Ön okuma:** `KAMERA_TESPIT_HANDOFF.md` (§2–§6)

## Uygulama durumu (v1.0 + v1.1)

- [x] **v1.0:** `kamera_otomatik_algila` toggle (varsayılan AÇIK), statik-kılavuz fallback, ayar sheet anahtarı.
- [x] **v1.1:** yüksek doğruluk yolu — `kamera_yuksek_dogruluk` (varsayılan KAPALI) + `kamera_perf_otomatik` (varsayılan AÇIK).
  - 1280x720 analiz sabitleme (yalnız yüksek doğrulukta; `ResolutionSelector` + `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`).
  - Kademeli aralık: normal 450ms / yüksek 300ms (`MetinAnalizci`).
  - `TarananSatir(metin,kutu)` + `SoruIsaretleri` (soru başı/şık/çözüm regex) + `SoruAlgilayici.secMetinli` (gevşek eşikler 2.2×/0.15, soru-başı bölme, çözüm eleme, dikey dolgu %8).
  - ROI: kılavuzun %10 genişletilmişi içinde arama (yalnız yüksek doğrulukta).
  - Deklanşör-anında refine (`KameraGoruntuIslemleri.refineOrani`, 2.5sn timeout, IoU<0.30'da önizlemeyi koru).
  - Model-hazır uyarısı (3 üst üste hata → chip + oto-çekim guard).
  - Düşük donanım koruması (RAM ≤3GB veya ≤4 çekirdek veya API<26 → otomatik kapatma, manuel açılabilir).
  - Manifest `com.google.mlkit.vision.DEPENDENCIES=ocr` (kurulumda model indir).
  - `clearAnalyzer()` dispose temizliği (yük yalnız kamera açıkken).
  - Test: 37 kamera testi yeşil (8 yeni `SoruIsaretleriTest`), tam suit yeşil, debug APK derlendi (28 MB).

---

## 1. Sorulan sorunun cevabı: "sıkıntısız" çözüm tam olarak nedir?

Tek başına sihirli bir algoritma yok. Bu projede ulaşılabilir **en yüksek doğruluk**, aşağıdaki 6 parçanın birlikte çalışmasıdır. Her parça, mevcut şikâyetlerden birini kapatır:

```
CameraX ImageAnalysis (1280x720 sabit)
  → MetinAnalizci (kutu + METİN + blok hiyerarşisi)
    → ROI kısıtı (statik kılavuzun ~%10 genişletilmişi içinde ara)
      → İçerik-farkındalıklı sınır (soru no / şık regex)
        → Çok-kare oylama (son 5–8 kare, medyan + güven skoru)
          → AlanDengeleyici (gevşetilmiş kilit)
            → Deklanşör anında TAM çözünürlükte yeniden-tanıma (refine) → kırp
              → Güven düşükse: statik kılavuz + manuel kırpıcıya düşüş
```

### 1.1. Parça parça: ne, hangi hatayı kapatır, maliyeti ne?

| # | Parça | Kapatılan hata | Maliyet / risk |
|---|---|---|---|
| 1 | **Analiz çözünürlüğünü sabitleme** (`setTargetResolution(1280x720)`, kare süresi >300 ms ise 960x720'ye düş) | "Hiç çalışmıyor", ince puntoda kayıp, uzak kadraj | Düşük. Sadece `KameraScreen.kt:295` + performans ölçümü. RAM artışı sınırlı, ImageAnalysis tek kare tutar. |
| 2 | **Metni çöpe atmama** — `MetinAnalizci` geri dönüşü `Satir(metin, kutu)` olur; `block.text` de taşınır | "Sadece üst paragraf", "yandaki soruyla karışma", "başlık/ÇÖZÜM tutma" — bunların hepsi içerik sinyaliyle çözülür | Düşük. Saf `domain` tarafında regex + unit test. Ağ yok, offline. |
| 3 | **Soru-sınır kuralları** — `^\s*\d+\s*[.)-]`, `^\s*soru\s*\d+`, `^[A-Ea-e]\s*[).]`, `(A)`, `ÇÖZÜM/Cevap:` | Küme = bir soru başından SONRAKİ soru başına kadar; şık satırları kendi içinde birleşir | Düşük. `SoruAlgilayici` içine yeni fonksiyon, mevcut 5 test yeşil kalmalı + yeni testler. |
| 4 | **ROI kısıtı (kılavuz-snap)** — arama tüm kadrajda değil, statik çerçevenin %10 genişletilmişinde; kenarlar en yakın satıra yapıştırılır | "Yandaki soru", "iki soruyu bir alma" — kullanıcı nişanı prior olur | Düşük-orta. `KameraScreen`'deki `cerceveKutu` → ViewModel'e taşınır. |
| 5 | **Çok-kare oylama + güven skoru** — son N karenin kutu medyanı; güven < eşikse KILITLI'ye geçme, otomatik çekimi tetikleme | "Titreme", "yanlış kilit", "doğru kesmeme" | Orta. `AlanDengeleyici` yanına `KareOylayici` (saf, test edilebilir). |
| 6 | **Deklanşör-anında refine** — çekilen tam çözünürlük JPEG'e bir kez daha ML Kit, kutu refine edilir, sonra kırpılır | "Ekranda doğru, kayıtta kayık" (preview/analiz/çekim boy uyumsuzluğu) | Orta. Çekim 200–400 ms uzar; uzun pozlamada ilk kareye uygulanır. |

### 1.2. Neden bunlar birlikte şart? (kök neden → çözüm eşleşmesi)

- **Üst-paragraf sorunu** = `boslukKatSayisi 1.6` + metinsizlik. Formül/şekil boşluğu kümeyi koparır. Çözüm: 2 + 3 (içerik sınırı boşluk eşiğini ezer) + dikey dolgu %5 → %8–10.
- **Yan-soru sorunu** = tüm-kadraj arama + geçirgen merkez bandı. Çözüm: 4 (ROI) + 3 (soru-no sınırı).
- **Hiç-çalışmama** = düşük analiz çözünürlüğü + sessiz failure. Çözüm: 1 + Log yerine UI geri bildirimi ("metin bulunamadı, yaklaşın").
- **Kesme kayıklığı** = oran-normalizasyon + üç farklı en-boy (preview/analiz/çekim) + `minOran 0.15` merkez-büyütme + uzun pozlamada tek-kutu-4-kareye. Çözüm: 6 + `minOran`'ı küçült/koşullu yap.
- **Yanlış kilit yapışması** = IoU 0.72 + 1200 ms histerezis + tek-kare karar. Çözüm: 5 + IoU → 0.55, kayıp → 700 ms.

### 1.3. Araştırmada elenen alternatifler (neden MVP değil?)

- **ML Kit Document Scanner API** (`play-services-mlkit-document-scanner`): sayfa kenarı + perspektif + gölge temizlemede mükemmel, hazır UI akışı var. **Ama** sayfanın TAMAMINI tarar, sayfa içindeki TEK soruyu seçmez. Bizim sorun soru-içi sınır. Karar: **ertelendi** — perspektif şikâyeti büyürse v2'de sayfa-düzeltme katmanı olarak eklenebilir, soru seçimi yine §1.1 gerekir.
- **Özel TFLite soru-detektörü**: teorik tavan en yüksek, ama etiketli yüzlerce soru fotoğrafı + eğitim + bakım ister. Mevcut 29 testli saf pipeline çöpe gider. Karar: **son çare**, v1'de yok.
- **Blok-kutusu tek başına**: `block.boundingBox` paragraf adayı verir ama ML Kit blok segmentasyonu soru sınırıyla birebir örtüşmez. Karar: 2'nin içinde yardımcı sinyal, tek başına çözüm değil.

### 1.4. İnternet araştırması notları

- ML Kit docs: girdi kalitesi (çözünürlük/odak) doğruluğu doğrudan etkiler; v2 API blok/satır/kelime + boundingBox + confidence döner — yani metin zaten elimizde, kullanmamak israf.
- Düşük segment cihaz vakası: varsayılan CameraX çözünürlüğüyle OCR %34 → pipeline optimizasyonuyla %79. Çözünürlük sabitlemeden doğruluk konuşulamaz.
- CameraX: `setTargetResolution` ile analiz akışına daha yüksek çözünürlük istenebilir; performans bedeli ölçülerek dengelenir.
- Document Scanner: otomatik kenar + perspektif + hazır UX verir; soru-içi seçim yapmaz.

---

## 2. Aç/Kapa özelliği — davranış tasarımı (MVP v1.0)

### 2.1. İstenen davranış (kullanıcı diliyle)

- Kamera ayarlarında **"Otomatik Soru Algılama"** anahtarı olur.
- **AÇIK (varsayılan):** bugünkü gibi — ARANIYOR → BULUNDU → KILITLI, çerçeve metne yapışır, çekimde o bölgeden kırpılır.
- **KAPALI:** analiz çalışmaz (pil/CPU kazancı), ekranda sabit 3:4 kılavuz çerçeve durur, çekim o çerçeveden kırpılır. "Yeniden tara", durum çipi, otomatik çekim tetiklenmesi kalkar. Çoklu mod etkilenmez (zaten kırpmasız).

### 2.2. Durum matrisi

| Durum | Analiz | Overlay | Çekimde kırpma | Otomatik çekim | Chip metni |
|---|---|---|---|---|---|
| TEKLİ + oto AÇIK + aday var | çalışır | uyarlanan kutu | aday oran | KILITLI'de tetiklenir | "Soru kilitlendi…" |
| TEKLİ + oto AÇIK + aday yok | çalışır | statik kılavuz (beyaz) | statik kılavuz | bekler | "Soruyu çerçeveye getirin" |
| TEKLİ + oto KAPALI | **durur** | statik kılavuz (sabit) | statik kılavuz | **devre dışı** | "Otomatik algılama kapalı — çerçeveye hizalayın" |
| ÇOKLU (oto ne olursa) | durur (bugünkü gibi) | köşe işaretleri | kırpma yok | devre dışı | "Çoklu mod…" |

### 2.3. Teknik tasarım (onay sonrası uygulanacak, kod yok)

1. `AyarlarDataStore.kt`
   - `KEY_KAMERA_OTOMATIK_ALGILA` (`"kamera_otomatik_algila"`), `DEFAULT_KAMERA_OTOMATIK_ALGILA = true`.
   - `kameraOtomatikAlgilaAkisi: Flow<Boolean>` + `setKameraOtomatikAlgila()` + `getKameraOtomatikAlgila()` (mevcut iki ayarın birebir kopyası deseninde).
2. `KameraViewModel.kt`
   - `otomatikAlgilaAkisi: StateFlow<Boolean>` (DataStore'dan).
   - `setOtomatikAlgila(acik)` + kapatılınca `taramayiSifirla()` çağır (eski kutu ekranda kalmasın).
3. `AnalizKontrolcu.kt` — değişiklik yok (tek sahibi Screen'deki `LaunchedEffect` olur).
4. `KameraScreen.kt`
   - `LaunchedEffect(kameraModu, otomatikAlgila) { analizKontrolcu.acik = (kameraModu == TEKLI && otomatikAlgila); viewModel.taramayiSifirla() }`.
   - Ayar sheet'e 3. satır: "Otomatik Soru Algılama" + açıklama ("Kapatınca sabit çerçeveden kırpılır; metin aranmaz").
   - `taramaAlanKutuPx`: oto kapalıysa null'a zorla (statik kılavuz çizilir).
   - `hedefOraniUret`: oto kapalıysa `soruOrani = null` geç (statik kılavuz yolu).
   - Otomatik-çekim `LaunchedEffect`: `otomatikAlgila` guard'ı ekle.
   - "Yeniden tara" butonu + BULUNDU/KILITLI chip'i: oto kapalıysa gizle, yerine bilgi chip'i.
5. Sözleşmeler korunur: `domain/kamera` saf kalır (bu işte saf değişiklik YOK), `captured_image_path` nav sözleşmesi, çevrimdışı, Türkçe UI.

### 2.4. Test planı (MVP)

- Manuel: açık→kilit→çek (kırpma adaydan), kapat→çek (kırpma statikten), kapat→pil/ısı gözlemi, mod değiştir TEKLİ↔ÇOKLU, ön/arka lens, ayar kalıcılığı (uygulamayı kapat-aç).
- Birim test: DataStore desen kopyası olduğu için yeni saf test gerekmez; mevcut 29 kamera testi yeşil kalmalı (`./gradlew testDebugUnitTest`).

---

## 3. Kapsam ve sürümler (deferred listesi)

- **v1.0 MVP (bu onay):** yalnızca §2 aç/kapa. Doğruluk algoritmasına DOKUNULMAZ. Tahmini dokunulan dosya: 3 (`AyarlarDataStore`, `KameraViewModel`, `KameraScreen`).
- **v1.1 (doğruluk-1, düşük risk):** §1.1 parça 1+2+3 (çözünürlük + metin + soru-sınır regex) + yeni unit testler.
- **v1.2 (doğruluk-2, orta risk):** parça 4+5+6 (ROI-snap + oylama + deklanşör refine) + 20–30 fotoğraflık etiketli kabul seti.
- **Ertelenenler:** Document Scanner katmanı, köşe-sürüklemeli manuel düzeltme, TFLite detektör, chip'te güven yüzdesi gösterme, hızlı erişim toggle'ı (ayar dışına yüzen buton).

---

## 4. Onay soruları (koda geçmeden önce)

1. Varsayılan **AÇIK** olsun mu? (önerim: evet — mevcut davranışı bozmaz, isteyen kapatır)
2. Anahtar **yalnız ayar sheet'te** mi olsun, yoksa vizör üstüne hızlı aç/kapa çipi de eklensin mi? (önerim v1.0: yalnız sheet — hızlı chip v1.2'ye)
3. Oto kapalıyken otomatik çekim satırı ayar sheet'te **gizlensin mi, pasif (disabled) mi görünsün**? (önerim: pasif + "otomatik algılama kapalıyken çalışmaz" notu)
4. v1.0 sonrası **v1.1 doğruluk işine girelim mi**, yoksa önce toggle'ı sahada deneyip mi karar verelim?
