# Soru Defterim — Kamera "Soru Alanı Tespiti" Devir Belgesi (Handoff)

> Bu belge, Android uygulaması **Soru Defterim**'deki kamera ekranındaki **soru alanı
> otomatik tespiti**nin mevcut durumunu, neden yetersiz kaldığını ve iyileştirme
> seçeneklerini ayrıntılı aktarır. Amaç: başka bir yapay zekâ ajanı ile bu problemi
> tartışmak ve yeni bir tespit algoritması tasarlamak. Belge kendi kendine yeterlidir
> (repo erişimi varsayılmaz).

---

## 1. Bağlam ve hedef davranış

- **Uygulama**: Soru Defterim — YKS/LGS/KPSS soru kayıt uygulaması (Kotlin, Jetpack Compose,
  Hilt, CameraX 1.4.1, ML Kit Text Recognition 19.0.1 Latin/unbundled).
- **Akış (TEKLİ SORU modu)**: Kullanıcı tek bir soruyu kameraya hizalar → uygulama
  **soru bölgesini otomatik tespit edip** ekranda 4 köşeli çerçeve ile gösterir →
  durum çipi sırasıyla ARANIYOR (sarı) → BULUNDU (sarı) → KILITLI (yeşil) olur →
  kullanıcı deklanşöre basar → **çekilen fotoğraf, tespit edilen bölgeden gerçekten kırpılır**
  (eski davranış: çerçeve sığdırılıyor ama kayıtta koca sayfa kalıyordu — bu düzeltildi).
- **ÇOKLU SORU modu**: kırpma YOK, tüm sayfa tek karede kaydedilir (tespit devre dışı).
- **Şikâyet (aktif görev)**: "Soru alanı tespiti çok kötü çalışıyor; sorunun alanını doğru
  bulamıyor." Somut hatalar §5'te.

## 2. Mevcut boru hattı (üretim kodu, adım adım)

```
CameraX ImageAnalysis (KEEP_ONLY_LATEST, varsayılan çözünürlük ~640×480, rotationDegrees)
   │  ~2.2 fps (450 ms hız sınırı, atomik tek-işlem kilidi)
   ▼
MetinAnalizci (ML Kit TextRecognition, Latin)
   │  Çıktı: SATIR kutuları (TextBlock→Line→boundingBox), dik (upright) koordinatlar
   │  ⚠ METİN İÇERİĞİ ŞU AN ÇÖPE GİDİYOR — yalnızca kutular tüketiliyor
   ▼
KameraViewModel.onAnalizSonuc(satirlar, analizGen, analizYuk)
   ▼
SoruAlgilayici.sec(...)  ← SAF kümeleme + tek soru seçimi (§3)
   │  Kutu → KutuOrani (normalize, 0..1)
   ▼
AlanDengeleyici (EMA 0.35; IoU 0.72; 900 ms stabil→KILITLI; 1200 ms kayıp toleransı)
   ▼
StateFlow<TaramaBilgi> → Compose overlay (dış karartma + köşe braketleri + durum çipi)
   ▼
Çekim: kilitli alan (yoksa statik kılavuz) → EXIF + inSampleSize çözümleme → GERÇEK KIRPMA
```

### Bileşen özeti

| Bileşen | Dosya | Rol | Önemli parametreler |
|---|---|---|---|
| MetinAnalizci | `ui/kamera/MetinAnalizci.kt` | ML Kit → satır kutuları | 450 ms aralık; ImageProxy işlem bitince kapanır; 90/270° boyut takası |
| SoruAlgilayici | `domain/kamera/SoruAlgilayici.kt` | Satırları kümeler, tek soru seçer | §3'te tam |
| AlanDengeleyici | `domain/kamera/AlanDengeleyici.kt` | Yumuşatma + kilit durum makinesi | EMA 0.35, IoU 0.72, kilit 900 ms, kayıp 1200 ms |
| KirpmaHesaplayici | `domain/kamera/KirpmaHesaplayici.kt` | FILL_CENTER + ayna koordinat dönüşümleri | `gorselOraniGorunumPxne` / `gorunumPxiGorselOranina` |
| KameraGoruntuIslemleri | `ui/kamera/KameraGoruntuIslemleri.kt` | EXIF, örnekleme, kırpma, uzun pozlama | hedef alan minimum %15 (dolgu) |
| Statik kılavuz | `KameraScreen.kt` + `statikCerceve()` | TESPİT YOKKEN kırpma yedeği | dikey 3:4, %75 dolgu, 24dp yanal / 96dp dikey pay |

Ek davranışlar: **uzun pozlama** (deklanşör → 4 kare → NCC piramit hizalama → medyan
birleştirme), **otomatik çekim** (KILITLI + 600 ms; varsayılan KAPALI), **pinch-zoom +
dokun-odak**, "Yeniden tara" sıfırlama.

## 3. SoruAlgilayici — birebir mevcut algoritma

Girdi: satır kutuları (analiz px) + analiz boyutu. Çıktı: tek soru kutusu (analiz px) veya null.

```
1. FİLTRE: 0.008·H ≤ satırYüksekliği ≤ 0.20·H  (H = analiz yüksekliği)
2. Satırları üst-koordinata göre sırala
3. GREEDY tek geçişli kümeleme — satırı son eklenen satırla karşılaştır:
     boşluk = satır.ust − sonSatır.alt
     boşluk ≤ 1.6 × max(ikiSatırınYüksekliği)          ← dikey yakınlık
     VE yatayÖrtüşme / min(genişlikler) ≥ 0.25         ← hizalı sütun varsayımı
     ikisi de sağlanırsa KÜMEYE EKLE, yoksa YENİ KÜME AÇ
4. PUANLAMA (her küme union'ı üzerinden):
     ELE: kümeMerkezX ∈ [0.12, 0.88] bandının dışındaysa
     ELE: kümeAlanı < 0.02·kareAlanıysa
     skor = kümeAlanı × (1 + 0.1 × min(satırSayısı, 8))
     beraberlikte: merkeze yakınlık
5. En yüksek skorlu kümenin union'ı + DOLGU (yatay %3, dikey %5) + sınırda kırpma
```

## 4. Sorun tanımı

Tespit, aşağıdaki senaryolarda **yanlış bölge** üretiyor (kullanıcı geri bildirimi ve kod
incelemesinden çıkarım):

1. **Yanlış komşu**: tespit, kadrajdaki İKİ soruyu birden seçiyor ya da komşu soruya kayıyor.
2. **Bölünmüş soru**: soru içindeki formül/şekil/görsel boşluğu yüzünden soru iki kümeye
   ayrılıyor; üst veya alt yarım seçiliyor → kırpma eksik.
3. **Yanlış kilit**: KILITLI olan bölge yanlış; kullanıcı "Yeniden tara"ya basana kadar
   yanlış bölge korunuyor (IoU 0.72 kilidi doğru bölgeye geçişi zorlaştırıyor).
4. **Başlık/çözüm/numara tutma**: sayfa başlığı, "ÇÖZÜM", sayfa numarası vb. adayları öne çıkıyor.
5. **Titreme & gecikme**: hareket ederken çerçeve takip edemiyor / zıplıyor; kilit ritüeli yavaş.
6. **Küçük metinde kayıp**: yakın olmayan ya da ince puntolu satırlar analiz çözünürlüğünde
   kayboluyor, küme eksik kalıyor.
7. **Eğimli sayfa**: eksen-hizalı dikdörtgen, eğik sayfada arka plan taşıyor.

## 5. Kök neden analizi (neden bu algoritma yetersiz)

1. **İçerik görmezden geliniyor (en büyük eksik)**: ML Kit satır metnini okuyor ama biz
   yalnız kutuları alıyoruz. Oysa soru sınırını belirleyen asıl sinyal **metindeki
   numaralandırma**dır: "12.", "Soru 5", "A) B) C) D)", "ÇÖZÜM", madde işaretleri.
   Saf geometri, sayfa tipi üstü sabit kurallarla sınır çizmeye çalışıyor.
2. **Tek kare üstünde karar**: her analiz karesi bağımsız; kararsızlık tek başına
   yumuşatma (EMA) ile örtbas ediliyor; EMA hem titremeyi azaltır hem **geciktirir** ve
   yanlış bölgeye "yapışmayı" kolaylaştırır.
3. **Kümeleme kör noktaları**: `1.6×satırYüksekliği` boşluk eşiği metin-olmayan boşlukları
   (formül/şekil) soru içinden KOPARIR; `0.25` yatay örtüşme eşiği ise iki sütunlu şık
   düzenlerini KOPARABİLİR. Eşikler dinamik değil.
4. **Analiz çözünürlüğü düşük ve sabitsiz**: ImageAnalysis hedef çözünürlük sabitlenmedi
   (CameraX varsayılan ~640×480); ince metin ve uzak kadrajda satırlar kayboluyor.
5. **Merkez bandı varsayımı**: [0.12, 0.88] filtresi merkez dışındaki soruyu eleyebiliyor;
   "kullanıcı soruyu çerçeveye alsın" diye yapısallaştırılmamış bir beklenti var.
6. **Kilit tuzakları**: stabilite penceresi (900 ms) + IoU benzerliği (0.72) yanlış bölgeye
   kilitlenince **yeniden değerlendirme** yavaş; kayıp toleransı (1200 ms) yanlış bölgeyi
   daha da koruyor.
7. **Perspektif yok**: eksen-hizalı kutu, eğik sayfada içeriği düzeltmiyor (dörtgen/uydurma yok).
8. **Metin-olmayan sorular** (grafik/şekil tabanlı) tespit açısından neredeyse görünmez.

## 6. İyileştirme seçenekleri (tartışma için öncelikli)

**A. İçerik-farkındalıklı sınır çizme (yüksek kazanç, düşük maliyet)**
MetinAnalizci zaten metni okuyor; geri dönüşe satır metnini de ekleyin. Kurallar:
- Soru başı desenleri: `^\s*\d+\s*[-.)]`, `^\s*soru\s*\d+`, `^\s*\d+\s*[.)]\s*\S` (YKS/LGS)
- Şık desenleri: `^[A-Ea-e]\s*[).]`, `\(\s*[A-Ea-e]\s*\)`
- Bölüm sonu: "ÇÖZÜM", "Cevap:", sayfa sonu
- Küme = (soru başı) → (bir sonraki soru başı öncesi); şık satırları kendi içinde birleştir
- Bu, "iki soru birleşti" ve "başlık tutuldu" hatalarını doğrudan keser.

**B. ML Kit blok (paragraf) yapısını kullan**: şu an `TextBlock→Line` düzleştiriliyor;
`block.boundingBox` + `block.text` üst seviye aday olarak kullanılabilir.

**C. Çok-kareli oylama (temporal voting)**: son N (5–8) karenin kutularının medyan/oy
kümesi; tek kare hataları eler; kilidi "oylama stabilitesi"ne bağlamak EMA'dan daha
sağlam.

**D. Statik kılavuz + kenar yapıştırma (snap)**: kullanıcı zaten soruyu çerçeveye hizalıyor;
tespit yalnız kılavuzun içinde/etrafında çalışır ve kenarları en yakın metin satırı
kenarına yapıştırır. Hata modunu dramatik düşürür.

**E. Hibrit manuel düzeltme**: kilitlemeden sonra köşeleri sürükleme (Compose drag handles)
— "kesin çözüm" garantisi, düşük maliyet, güven algısı.

**F. Kolon tespiti**: x-ekseni histogramı ile çok-sütun sayfalarda örtüşme kuralından
önce sütun ayırma.

**G. Adaptif eşikler + çözünürlük**: sabit `0.008·H` yerine metin ölçeği kestirimi;
`setTargetResolution` ile analiz ~720×540 (RAM/latency dengesiyle).

**H. Perspektif düzeltme**: dörtgen/sayfa tespiti (veya ML Kit **Document Scanner** API)
→ üstüne A+C uygulama.

**I. Özel TFLite detektör**: en pahalı seçenek; etiketli veri gerekir; son çare.

**Kombinasyon önerisi (başlangıç hipotezi)**: **A + C + D** (içerik + oylama + kılavuz-snap)
→ %80 kalite artışı, düşük risk. E (manuel düzeltme) kullanıcıya güven veren sigorta.

## 7. BOZMAMANIZ GEREKEN SÖZLEŞMELER

1. **KutuOrani**: EXIF+upright normalize (0..1) görüntü koordinatları; analiz boyutları ≠
   çekim boyutları; tüm dönüşümler `KirpmaHesaplayici` üzerinden (FILL_CENTER + ön kamera aynası).
2. **Çekim hattı**: `captured_image_path` + `is_solution_image` tek-yol nav sözleşmesi.
3. **Saf/domain ayrımı**: `domain/kamera` saf kalacak (unit testler); Android bağımlılıkları
   `ui/kamera`da. Yeni algoritma saf ve test edilebilir olmalı (mevcut 29 kamera testi yeşil).
4. **Çevrimdışı zorunlu**: ağ yok; ML Kit Latin unbundled cihaz üstü.
5. **RAM**: 8 GB geliştirme makinesi; analiz ≤ ~450 ms aralık; ImageProxy kapatma disiplini.
6. **UI Türkçe**: TEKLİ/ÇOKLU mod seçici, "Yeniden tara", durum çipleri; çoklu modda kırpma YOK.
7. **Varsayılanlar**: uzun pozlama AÇIK, otomatik çekim KAPALI; kilit ritüeli otomatik
   çekimin tetikleyicisi (kilit güvenilirliği otomatik çekim kalitesini doğrudan etkiler).

## 8. Kabul kriterleri (öneri)

- **Doğruluk**: 20 gerçekçi sayfa fotoğrafında tam kapsama ≥ %90 (soru + şıklar içeride,
  komşu soru hariç); formül/şekilli soruda bölünme YOK.
- **Gecikme**: kadraj hareketi → çizim ≤ 300 ms; kilit ≤ 1.2 s; yanlış kilit < %5.
- **Kararlılık**: çerçeve zıplaması (kareler arası IoU) ≥ 0.85 medyan.
- **Performans**: analiz ≤ 300 ms/kare (orta seviye cihaz), ekstra RAM ≤ ~150 MB.

## 9. Dosya haritası (repo yolları)

- `app/src/main/java/com/sorudefteri/domain/kamera/` — Saf motorlar:
  `SoruAlgilayici.kt`, `AlanDengeleyici.kt`, `KirpmaHesaplayici.kt`, `KareHizalayici.kt`,
  `KareBirlestirici.kt`, `KameraModeller.kt`
- `app/src/main/java/com/sorudefteri/ui/kamera/` — Android yapıştırma:
  `MetinAnalizci.kt`, `KameraViewModel.kt`, `KameraGoruntuIslemleri.kt`, `AnalizKontrolcu.kt`,
  `KameraScreen.kt` (overlay + çekim)
- `app/src/test/java/com/sorudefteri/domain/kamera/` — 5 test sınıfı, 29 test
  (kümeleme, EMA/kilit, FILL_CENTER dönüşümleri, hizalama, birleştirme)
- Bağımlılıklar: `gradle/libs.versions.toml` (CameraX 1.4.1, mlkitTextRecognition 19.0.1)

## 10. Önerilen tartışma sırası (açık sorular)

1. İçerik-farkındalıklı sınır (A) ile başlayıp mı örneklensin, yoksa A+C+D kombinasyonu
   mu hedeflensin?
2. Doğruluk metriği için etiketli sayfa seti nasıl kurulur? (Sınırlayıcı: gerçek kullanıcı
   fotoğrafı, düşük ışık + eğim senaryoları zorunlu.)
3. Kilit mekanizması yeniden tasarlanırken otomatik çekim (KILITLI + 600 ms) ile olan
   sözleşmeyi nasıl koruyalım?
4. Manuel düzeltme (E) normal akışa mı entegre edilir, yalnız hata durumunda mı?
5. ML Kit Document Scanner'a geçiş, mevcut kırpma/perspektif hattını nasıl etkiler —
   tek soru tespitinde kazanç değer mi?
6. Yüksek çözünürlüklü analiz (720p) hangi cihaz sınıfında ölçülmeli; kare başına ms bütçesi?
