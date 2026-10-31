# Performans Denetimi — Düşük Donanımlı Cihazlar

**Tarih:** 29 Ekim 2026  
**Kapsam:** Tüm uygulama (`app/src/main`, 181 Kotlin dosyası / ~64.000 satır), derleme yapılandırması, dağıtılan APK  
**Yöntem:** 4 paralel denetim ajanı (Compose/UI · Room/IO · Startup/DI/APK · Kamera/AI/Eşzamanlılık) + kritik bulguların kaynak kod ve APK üzerinden elle doğrulanması  
**Hedef cihaz sınıfı:** 2–4 GB RAM, Cortex-A53/A55 sınıfı CPU, eMMC depolama, Android 7–11  
**Ölçüm notu:** Cihaz/emülatör çalıştırılmadı. Süre ve MB değerleri, kodda doğrulanan döngü/allocation/çağrı sayılarından türetilmiş **tahminlerdir**; kesinleştirmek için §8'deki ölçüm planı uygulanmalı.

---

## 0. Yönetici Özeti

### En kritik tek bulgu: dağıtılan APK bir **debug** derlemesi

Depodaki `soru-defterim-guncel.apk` ile `app/build/outputs/apk/debug/app-debug.apk` **birebir aynı** (MD5 `ce25dfdfaa12032a692980197816e717`). CI iş akışı da GitHub Release'e doğrudan `app-debug.apk` yüklüyor (`.github/workflows/build-apk.yml:44,59`).

Bu tek durum, aşağıdaki tüm mikro-optimizasyonlardan daha büyük etkiye sahiptir:

| Etki | Debug APK | Release APK (mevcut, R8'siz) | Fark |
|---|---:|---:|---:|
| Dosya boyutu | 29,14 MB | 17,87 MB | **−11,3 MB** |
| DEX sayısı | 20 | 4 | −16 |
| `debuggable` | Evet | Hayır | ART optimizasyonları açılır |
| R8 küçültme | Yok | Yok | — |

Debug derlemesinde ART, hata ayıklama nedeniyle birçok optimizasyonu devre dışı bırakır; Compose tarafında da ek kontroller çalışır. Düşük uçlu cihazda algılanan yavaşlığın önemli bir kısmı doğrudan bunun sonucudur.

### İlk 10 iş (etki sırasıyla)

| # | İş | Alan | Beklenen kazanç | Efor |
|---|---|---|---|---|
| 1 | Release imzalı APK üret ve yayınla; CI'ı `assembleRelease`'e çevir | Build | APK −11,3 MB, açılış −150…−400 ms, çalışma zamanı belirgin hızlanır | S |
| 2 | R8'i aç, geniş `-keep` kurallarını daralt | Build | APK ~−7…−9 MB daha, RAM −10…−20 MB | M |
| 3 | OMR yakalamayı ana thread'den çıkar + örneklemeli decode + recycle | Kamera | 12 MP'de tepe bellek ~96 MB → ~8 MB; ANR/OOM biter | M |
| 4 | Reels'te WebView sayısını 9 → 1–3'e indir | UI | RAM −100…−250 MB; sayfa geçişi −%30…−%60 kare süresi | S |
| 5 | Paging sorgusunu indekslenebilir hale getir + kısmi indeksler | DB | Sayfa yükleme 25–150 ms → 3–8 ms; kaydırma jank'i biter | M |
| 6 | FTS tetikleyicilerine `UPDATE OF` + hedefli `UPDATE` | DB | Her işaretlemede 3–8 ms + tam satır yazımı → <1 ms | S |
| 7 | Soğuk açılış zincirini sadeleştir (ESP/Keystore lazy, tek DataStore snapshot, splash) | Startup | −150…−400 ms; boş kare kaybolur | M |
| 8 | Yedekleme ve PDF'i akıt/tekilleştir | IO | Yedek 3× disk yazımı biter; PDF 3× üretim → 1× | M |
| 9 | AI timeout/retry zincirini sınırla | Ağ | Teorik en kötü 27–36 dk → ~2 dk; pil/veri | S |
| 10 | Çizim tuvalini rasterize et + kaydı debounce et | UI | Kare 30–80 ms → 8–12 ms; stylus gecikmesi −%60 | M |

**Toplam hedef:** APK ~10–12 MB, soğuk açılış −%40…−60, ağır ekranlarda RAM tepesi −150…−300 MB, OMR taramasında çökme yok.

---

## 1. Doğrulama Durumu ve Düzeltmeler

Ajan raporlarındaki bazı bulgular, kodun son hâlinde **zaten düzeltilmiş** çıktı. Bunları ayıklamak için kritik maddeler elle doğrulandı:

### ✅ Kodla doğrulananlar
- Dağıtılan APK'nın debug olduğu (MD5 eşleşmesi), CI'ın debug yüklediği.
- `derle.sh release` varsayılanının `-PlowRam=true` → R8 kapalı olduğu.
- `material-icons-extended` bağımlılığı ve 116 farklı ikon kullanımı.
- OMR'de `mainExecutor` + `imageProxy.toBitmap()` + galeriden örneklemesiz `decodeStream`.
- `ornekliCoz` içindeki `/(ornekleme * 2) >= tavan` hatası.
- Reels'te `beyondViewportPageCount = 1` ve sayfa başına 3 `SmartMathContent` (WebView).
- Paging sorgusundaki 7 parametre-OR'u + 11 `LIKE '%…%'` + `CASE` sıralama.
- FTS `AFTER UPDATE` tetikleyicisinde kolon filtresi olmaması.
- `BackupManager`'ın tüm tabloyu `.first()` ile alması, manifest'i tek String'e encode etmesi, içe aktarmada `readText`.
- `PdfExportDialog`'un üç butonunda da PDF'i sıfırdan üretmesi.
- AI için 180 s read / 240 s call timeout, 3 sağlayıcı, sağlayıcı başına 3 deneme.
- `BubbleReader`'da piksel başına `getPixel`.
- `MlKit` bağımlılığının unbundled (Play Services'ten model indiren) sürüm olması.
- `MathMarkdownText.RenderFormattedLines`'ın `remember` kullanmaması ve `formatMathSymbols` içinde çağrı başına regex derlenmesi.
- `KonuIsiHaritasiCard`'da konu × soru çifti başına `normalizeString`.
- `MainActivity`'nin `isOnboardingSeen == null` iken boş `Surface` çizmesi.
- `AyarlarDataStore` constructor'ında 3 şifreli anahtarın ana thread'de okunması.
- `DenemeDetayScreen`'in `verticalScroll` + `forEachIndexed` ile tüm satırları bestelemesi.
- `KaTeXMathView`'da `modifier`'ın iki kez uygulanması.

### ⚠️ Ajan bulgusu olup **geçerli olmayan** maddeler
| İddia | Gerçek |
|---|---|
| İstatistik ekranı `verticalScroll` içinde, lazy değil | `IstatistikScreen.kt:224` bir `LazyColumn`; sekme içerikleri `item {}` olarak ekleniyor. |
| Kütüphane aramasında debounce yok | `KutuphaneViewModel.kt:103`'te 300 ms debounce var (temizlemede 0 ms). |
| Çizim tuvali kalıcı `Offscreen` | `DrawingOverlayCanvas.kt:404` koşullu: yalnız silgi stroke'u varsa `Offscreen`. |
| KaTeX WebView `onRelease`'te destroy edilmiyor | `KaTeXMathView.kt:1035-1042`'de `destroy()` var. |
| Coil önbelleği düşük cihazda %25 | `SoruDefteriApplication` düşük RAM'de %12 / 50 MB kullanıyor. |
| `ReminderScheduler` periyodu sıfırlıyor | `ExistingPeriodicWorkPolicy.UPDATE` kullanılıyor; doğru. |

> Not: Bu düzeltmeler, önceki `docs/PERFORMANS_PLANI.md` (18 Ekim) turunun uygulandığını gösteriyor. Bu rapor kalan ve yeni tespit edilen sorunlara odaklanır.

---

## 2. P0 — Çökme, OOM ve yayın engelleyici sorunlar

### P0-1. Dağıtılan artefakt debug; CI release üretmiyor
**Kanıt:** `soru-defterim-guncel.apk` MD5 = `app-debug.apk` MD5; `.github/workflows/build-apk.yml:44` `assembleDebug`, `:59` release asset olarak `app-debug.apk` yüklüyor.  
**Etki:** R8 yok, `debuggable=true`, 20 DEX, 42.136 sınıf; debug tooling APK'da.  
**Düzeltme:**
1. CI'da `./gradlew assembleRelease` (veya `bundleRelease`) çalıştır.
2. Release asset'e yalnızca imzalı release APK koy; debug APK'yı yalnızca artifact olarak tut.
3. Keystore yoksa build'i **fail** et; debug anahtarına sessizce düşme (`app/build.gradle.kts:68-76`).
4. Doğrulama: `aapt dump badging ... | grep debuggable` boş olmalı; `apksigner verify --print-certs` "Android Debug" **olmamalı**.

### P0-2. R8 fiilen kapalı; keep kuralları küçültmeyi zayıflatıyor
**Kanıt:** `derle.sh:55-59` → `-PlowRam=true`; `app/build.gradle.kts:61-62` → `isMinifyEnabled = !lowRam`. `app/proguard-rules.pro:19-44,76-78` geniş `-keep` içeriyor: `androidx.paging.**`, `androidx.work.**`, `coil.**`, `com.google.mlkit.**`.  
**Etki:** Mevcut release bile 17,87 MB; ilk 10 işteki 2. madde olmadan APK hedefi tutmaz.  
**Düzeltme:** R8'i varsayılan yap; `lowRam` yalnızca acil yerel derleme için kalsın. Keep'leri daralt (özellikle Paging/WorkManager/Coil için yalnızca gerçekten reflection kullanan sınıflar). CI'da 2 GB heap kullan.

### P0-3. OMR yakalama: ana thread'de tam çözünürlüklü bitmap
**Kanıt:** `ui/omr/OmrScannerScreen.kt:266-287`
```kotlin
val mainExecutor = ContextCompat.getMainExecutor(context)
imageCapture.takePicture(mainExecutor, ...)
    val rawBitmap = imageProxy.toBitmap()      // 12 MP ARGB ≈ 48 MB, main thread
    val correctedBitmap = Bitmap.createBitmap(rawBitmap, ..., matrix, true) // +48 MB
    imageProxy.close()
```
Galeri yolu `:163-169` `BitmapFactory.decodeStream(stream)` ile örneklemesiz decode ediyor. `ui/omr/OmrViewModel.kt:218-316` girdi bitmap'ini hiç recycle etmiyor; `warped` yalnız başarı yolunda recycle ediliyor; `catch (Exception)` OOM'u yakalamıyor (OOM bir `Error`'dır).  
**Etki:** 12 MP'de ~96 MB tepe; 2 GB/128 MB heap'li cihazda OOM veya 150–400 ms (zayıfta ~1 s) ana thread donması.  
**Düzeltme:**
1. `takePicture`'ı `cameraExecutor` + `OnImageSavedCallback` ile dosyaya yaz.
2. `Dispatchers.Default`'da `inSampleSize` + `RGB_565` + EXIF döndürme ile 1600 px'e indir.
3. `OmrViewModel.analyzeBitmap` içinde `try/finally` ile girdi ve `warped` recycle et.
4. `catch (oom: OutOfMemoryError)` için kullanıcı mesajı ekle.

### P0-4. Reels: aynı anda 9'a kadar KaTeX WebView
**Kanıt:** `ui/reels/SoruReelsScreen.kt:211` `beyondViewportPageCount = 1` → 3 sayfa canlı. `FlashcardReelsPage` içinde 3 ayrı `SmartMathContent` (`:685`, `:707`, `:746`); her biri `KaTeXMathView` → WebView (`MathMarkdownText.kt:91`).  
**Etki:** 3 flashcard sayfası denk geldiğinde 9 WebView; WebView başına ~15–30 MB native bellek → 135–270 MB. 2 GB cihazda OOM/agresif GC.  
**Düzeltme:**
1. `beyondViewportPageCount = 0` yap veya WebView'ı yalnız "yerleşik" sayfada oluştur.
2. `summary` ve `tip` düz metin olarak çizilsin; yalnız `formula` KaTeX'e gitsin (8 hazır kartın tamamında LaTeX var).
3. Sayfa başına **tek** WebView: `CombinedMathMarkdownText`'e geç.
4. Uzun vadede WebView havuzu (2–3) veya saf Compose/KaTeX render.

### P0-5. Paging sorgusu indekslenemiyor + FTS her UPDATE'te çalışıyor
**Kanıt:** `data/local/dao/SoruDao.kt:238-256`
```sql
AND (:query IS NULL OR :query = '' OR topic LIKE '%'||:query||'%' OR … 9 kolon …)
AND (:examType IS NULL OR examType = :examType)
… 6 filtre daha …
ORDER BY CASE WHEN :sortOrder = 'DATE_ASC' THEN createdAt END ASC, …
```
- `:param IS NULL OR col = :param` indekslenebilir kısıt üretmez.
- 11 `LIKE '%…%'` hiçbir B-tree ile karşılanamaz.
- `ORDER BY CASE …` geçici B-tree zorunlu kılar.
- `PagingConfig(pageSize=20, initialLoadSize=40)` → her `OFFSET n` için ilk `n+20` satır üretilir; maliyet O(n²).

**FTS:** `data/local/SoruDatabase.kt:147-150` tetikleyicilerde kolon listesi yok; hangi alan değişirse değişsin 5 metin kolonu yeniden indeksleniyor. Üstüne `SoruRepository.kt:228-240, 286-296` tek alan için `existing.copy(...)` + `@Update` ile **tüm satırı** yazıyor.  
**Etki:** 3.000 soruda sayfa başına 75–150 ms; her yazmada 3–8 ms + 2 sayfa yazımı; WAL şişmesi.  
**Düzeltme:**
1. `@RawQuery` ile yalnız dolu filtreleri WHERE'e koy; `ORDER BY createdAt DESC, id DESC` (statik, `id` tiebreak zorunlu).
2. Migration 14: kısmi indeksler
```sql
CREATE INDEX idx_s_aktif_tarih   ON sorular(createdAt DESC, id DESC) WHERE isDeleted = 0;
CREATE INDEX idx_s_aktif_ders    ON sorular(subjectId, createdAt DESC) WHERE isDeleted = 0;
CREATE INDEX idx_s_aktif_favori  ON sorular(isFavorite, createdAt DESC) WHERE isDeleted = 0;
CREATE INDEX idx_s_aktif_tekrar  ON sorular(nextReviewDate) WHERE isDeleted = 0;
CREATE INDEX idx_s_aktif_cozuldu ON sorular(isSolved, solvedAt) WHERE isDeleted = 0;
```
3. FTS tetikleyicilerini `AFTER UPDATE OF topic, note, ocrMetni, questionText, aiExplanation` olarak yeniden oluştur.
4. `cozumDurumuDegistir`, `advanceReviewStage`, `toggleFavorite` için hedefli `UPDATE ... SET tekKolon` kullan.

### P0-6. Soğuk açılış zinciri: Keystore + 37 DataStore flow + boş kare
**Kanıt:**
- `SoruDefteriApplication.kt` Hilt ile `AyarlarDataStore`'u enjekte ediyor; constructor'ı `AyarlarDataStore.kt:150-180`'de `EncryptedSharedPreferences` + `MasterKey` (Keystore IO) oluşturup 3 anahtarı **ana thread'de** okuyor (`SecurityModule.kt:29-80`).
- `AyarlarDataStore` ~37 ayrı `dataStore.data` flow'u tanımlıyor (ajan sayımı); açılışta ~7 `.first()` + 2 collector.
- `MainActivity.kt:60-61,91-98`: `isOnboardingSeen == null` iken boş `Surface`; ilk anlamlı kare DataStore emisyonundan sonra.
- Her açılışta hatırlatıcı/otomatik yedek için `.first()` + `schedule/cancel` çalışıyor (`SoruDefteriApplication.kt:58-80`).
**Etki:** −150…−400 ms soğuk açılış; ilk kurulumda Keystore daha pahalı; boş kare algısı.  
**Düzeltme:**
1. `dagger.Lazy<SharedPreferences>` ile ESP'yi IO coroutine'inde oluştur.
2. Tek `snapshot: StateFlow<Preferences>` (`stateIn(Eagerly)`) üret; 37 flow'u bundan türet.
3. Onboarding bayrağını normal `SharedPreferences`'ta senkron oku veya `installSplashScreen().setKeepOnScreenCondition { !uiReady }` kullan.
4. Hatırlatıcı/yedek planlamasını yalnızca ayar değişince yap; `cancel()`'ı her açılışta çağırma.

### P0-7. Çizim tuvali her karede tüm stroke'ları yeniden çiziyor
**Kanıt:** `ui/ortak/drawing/DrawingOverlayCanvas.kt:542-586` draw lambda'sında her stroke için `Stroke(...)`, `Color(...)`; `:521` her kalem kalkışında tüm çizim JSON'u yeniden yazılıyor. `Offscreen` artık koşullu (`:404`) ama tam ekran katman yine de her invalidation'da tüm listeyi çiziyor.  
**Etki:** 100 stroke sonrası kare 30–80 ms; stylus gecikmesi 30–60 ms.  
**Düzeltme:** Tamamlanmış stroke'ları bir kez `ImageBitmap`/`Picture`'a rasterize et; yalnız aktif stroke'u çiz. `Stroke`/`Color` nesnelerini cache'le. Kaydı son stroke ekleme veya 1–2 s debounce ile yap.

---

## 3. P1 — Belirgin jank, bellek ve pil

### 3.1 UI / Compose

| ID | Konum | Sorun | Düzeltme | Etki |
|---|---|---|---|---|
| UI-1 | `MathMarkdownText.kt:762-790`, `:1111-1289` | `RenderFormattedLines` her recomposition'da normalize + satır başına `formatMathText`; `formatMathSymbols` içinde çağrı başına ~47 regex derleniyor (dosyada 194 `Regex(`) | `remember(content, textColor) { lines.map { ... } }`; regex'leri dosya düzeyinde `private val` yap | İlk render 80–250 ms → 10–30 ms; sonraki recompose 0 |
| UI-2 | `TestCozScreen.kt:115,155,173-176,334-335` | `timerTick` kök scope'ta okunuyor; anahtar her saniye kökü geçersiz kılıyor; `remainingSeconds` soru gövdesine parametre | Haptik için `snapshotFlow { }.distinctUntilChanged()`; uyarı kartını ayrı composable'a taşı | Saniyede 5–20 ms jank → 0 |
| UI-3 | `OmrScannerScreen.kt:423-551` | Sonsuz `animateFloat` tüm vizörü her karede çiziyor; frame başına `Path`, `Stroke`, `PathEffect`, `Offset` listesi | Nabzı `Modifier.graphicsLayer { alpha = }` ile uygula; geometriyi `drawWithCache` + `remember` | Preview FPS +5–10; CPU −%5…−%10 |
| UI-4 | `DenemeDetayScreen.kt:199,665` | `verticalScroll` + `forEachIndexed` → 40 satır + 40 görsel aynı karede; satır başına `File.exists()` | `LazyColumn` + `items(key = { it.id })`; thumbnail yolu entity'de | Açılış 300–800 ms → 80–150 ms |
| UI-5 | `KaTeXMathView.kt:900,933` | `modifier` hem `BoxWithConstraints`'a hem iç `Box`'a uygulanıyor (çift padding/clickable); `BoxWithConstraints` subcomposition | `modifier`'ı yalnız dışta kullan; iç `Box` `fillMaxWidth()` | Görsel hata + fazladan ölçüm turu |
| UI-6 | `KonuIsiHaritasiCard.kt:109-143`, `:646-677` | Konu × soru çifti başına `normalizeString` (Locale + 9 replace allocation) | Soru konularını bir kez `Map<Long,String>`'e normalize et; katalog konularını bir kez; `remember` | Konu çipinde 100–500 ms → <50 ms |
| UI-7 | `AudioMemoCard.kt:73-77,207,214` | 5 ayrı `collectAsState` (lifecycle'sız); pozisyon 200 ms'de bir → kart 5 Hz recompose | `collectAsStateWithLifecycle`; pozisyonu yalnız Slider/Text alt bileşeninde oku | Oynatmada 5 Hz → 2 küçük bileşen |
| UI-8 | `KameraScreen.kt:157,670,807` | OCR `taramaBilgi` 300–450 ms'de bir güncelleniyor; ekran kökü okuyor → 2–3 Hz recompose | Alanları alt composable'larda oku; `derivedStateOf` | CPU −%3…−%8 |
| UI-9 | `SoruChatBottomSheet.kt:491` | Her AI mesajı ayrı `CombinedMathMarkdownText` → mesaj başına WebView | Uzun sohbette sanal liste + tek WebView; en azından görünmeyeni dispose et | Kaydırmada 50–150 ms |
| UI-10 | `TestCozScreen.kt:310-324` | `AnimatedContent` slide+fade sırasında iki tam soru ağacı (ve iki WebView) | `Crossfade` veya yalnız başlığa animasyon | Geçişte bellek tepesi + 200–400 ms |
| UI-11 | `SoruKarti.kt`, `PhoneTasarim.kt` | Satır başına `SimpleDateFormat` + `synchronized` | `remember(createdAt)` + `DateTimeFormatter` | Küçük, bedava |
| UI-12 | `LazyColumn` `key` eksikleri: `OmrSonucScreen:282`, `OmrScannerScreen:990`, `SoruEklemeScreen:584` | State karışması, gereksiz recompose | `key = { ... }` | Küçük |
| UI-13 | `TestSonucScreen.kt:265-267` | `slowQuestions` her recompose'da O(n) | `remember(result.questions, targetSeconds)` | 100 soruda gereksiz tarama |
| UI-14 | `app/build.gradle.kts` | `composeCompiler {}` bloğu, stability config, baseline profile yok | Önce compiler raporu üret, sonra karar ver | Ölçülmeli |

### 3.2 Veri / Room / IO

| ID | Konum | Sorun | Düzeltme | Etki |
|---|---|---|---|---|
| D-1 | `SoruRepository.kt:548-574` | Meta akışı 4 COUNT + `flatMapLatest` içinde 2 tam tablo `DISTINCT TRIM`; her yazmada 4–8 tarama; `KutuphaneViewModel.kt:176-179` dış `flatMapLatest` ile yeniden abonelik | Tek aggregate COUNT sorgusu; yayınevi/sınav listelerini filtre diyaloğunda `suspend` oku; dış sarmalayıcıyı kaldır; `TRIM`'i yazma anında yap | Yazma başına 80–300 ms arka plan CPU → ~10–30 ms |
| D-2 | `BackupManager.kt:310-313,508,685`; `AutoBackupWorker.kt:36,114` | Tüm tablo `.first()`, manifest tek String, içe aktarma `readText`, arşiv 3 kez diske yazılıyor, `MAX_SAVED_BACKUPS=1` | Sayfalı okuma + `encodeToStream`; tek `FileOutputStream`; `decodeFromStream`; tek transaction | 500 görselde ~10–20 MB heap, ~375 MB yazım → ~2 MB / ~125 MB |
| D-3 | `PdfExportDialog.kt:546,586,627`; `PdfDenemeKitapcikGenerator.kt:421-425,1624-1641` | Üç buton da PDF'i sıfırdan üretiyor; `generatedPdfFile` kullanılmıyor; görsel başına 3 decode | `generatedPdfFile` doluysa onu kullan; ölçümü bir kez yap; sayfa başına 2–3 görsellik `LruCache` | 200 soruluk PDF 5–9 s → 1,5–3 s; 3 dosya → 1 |
| D-4 | `ReelsViewModel.kt:49-58` → `SoruRepository.kt:81-83` → `SoruDao.kt:79-80` | Tüm tablo, tüm TEXT kolonlarıyla RAM'e | `LIMIT 300`, yalnız feed kolonları; due için ayrı sorgu | 2.000 soruda 10–20 MB heap |
| D-5 | `SoruDao.kt:354-355` | `solvedAt` indekssiz; haftalık aktivite 7×2 COUNT | Kısmi indeks + 2 `GROUP BY date(...)` | +50–150 ms → ~5–15 ms |
| D-6 | `SoruDao.kt:159-160` | `LOWER(subjectId) LIKE '%geometri%'` tam tarama | `subjectId IN (...)` eşitlik | Soru eklemede +15–40 ms → ~0 |
| D-7 | `SoruRepository.kt:370-388` | `deleteSorularByIds`: id başına ayrı sorgu (N+1) | Tek `IN` + 500'lük chunk | 200 silmede 200 sorgu → 1 |
| D-8 | `SoruEntity.kt:14-29` | 12 tek kolon indeks; yazma başına 12 B-tree | Kısmi/kompozit indekslere geç; kullanılmayan 6'yı düşür | Yazma +%15–30, DB +%10–20 |
| D-9 | `DatabaseModule.kt:44-52` | Yalnız `foreign_keys=ON`; `synchronous`, `cache_size`, `optimize` yok | `PRAGMA synchronous=NORMAL; PRAGMA cache_size=-8000;` açılışta/kapanışta `PRAGMA optimize` | Commit başına 5–20 ms fsync kalkar |
| D-10 | `IstatistikViewModel.kt:167-171,232-261` | Tam tablo `getAllSorular()` + ~26 COUNT her DB değişiminde | Yalnız kullanılan kolonlar + `distinctUntilChanged`; COUNT'ları `GROUP BY`'a katla | İstatistik açıkken CPU/pil |
| D-11 | `SoruRepository.kt:271`; `SoruDao.kt:342` | `dueReviewCount` bir `val`; `currentTime` varsayılanı açılışta donuyor | `fun dueReviewCount(now: Long): Flow<Int>` veya SQL `strftime('now')` | Doğruluk + gereksiz yeniden hesaplama |
| D-12 | `GorselDepo.kt:367-445` + `AiRepository.kt:118,343,393` | Dosya → `ByteArray` → Base64: ~2,7× kopya | Doğrudan `OutputStream`'a / okio `Source` ile akıt | 5 MB görselde ~14 MB heap → ~5 MB |

### 3.3 Kamera / OCR / AI / eşzamanlılık

| ID | Konum | Sorun | Düzeltme | Etki |
|---|---|---|---|---|
| K-1 | `KameraGoruntuIslemleri.kt:68-72`, `:371` | `ornekliCoz` koşulu ters: `max/(sample*2) >= tavan` → 4000 px / 2560 tavan için sample=1 (tam çözünürlük) | `while (max / sample > tavan) sample *= 2` | 12 MP: 24 MB → 6 MB; tepe 48 → 12 MB |
| K-2 | `OmrScannerScreen.kt:208-229`; `OmrViewModel.kt:206-208` | Analyzer'da fps kapısı yok; her karede tam Y düzlemi kopyası; `FiducialDetector` her karede; `_uiState` 30 kez/s; `analyzeBitmap` sürerken de çalışıyor | 120 ms zaman kapısı + `isProcessing` erken çıkış + yeniden kullanılan buffer, köşeler değişmedikçe emit yok | 30 → 8 fps analiz; CPU %15–30 → %4–8 |
| K-3 | `BubbleReader.kt:149,183` | Baloncuk başına `bitmap.getPixel` → ~380 bin JNI çağrısı | `getPixels` ile bir kez `IntArray`, indeksle eriş | Tarama başına 0,4–2 s → 50–150 ms |
| K-4 | `GeminiProvider.kt:356-376`; `GroqProvider.kt:358-373`; `AppModule.kt:98-101`; `SmartAiRouter.kt:133-205` | Sağlayıcı başına 3 deneme × 3 sağlayıcı; 180 s read / 240 s call; toplam deadline yok | 10/45/60 s timeout, 2 deneme, `withTimeout(90 s)`, yalnız 429/5xx'te fallback; gerçek iptal için `enqueue()` + `call.cancel()` | Teorik 27–36 dk → ~2 dk |
| K-5 | `AiSolutionRetryWorker.kt:138-145,72-82` | WorkManager 3 ek deneme × 9 istek = 36 istek | `runAttemptCount < 1`, 60 s backoff | En kötü 36 → 9 istek |
| K-6 | `BubbleReader.kt:294-304` | Her taramada yeni `TextRecognizer`, `close()` yok | Ortak recognizer enjekte et veya `addOnCompleteListener`'da kapat | Tarama başına birkaç MB native sızıntı |
| K-7 | `gradle/libs.versions.toml:71`; `OcrHelper.kt:55` | Unbundled ML Kit: model ilk kullanımda indirilir; 15 s timeout | Bundled `com.google.mlkit:text-recognition` veya prewarm + ilk seferde uzun timeout | İlk OCR'da 10–30 s indirme |
| K-8 | `GeminiProvider.kt:424`; `GroqProvider.kt:417`; `NvidiaProvider.kt:288`; `GorselDepo.kt:45-52` | Dosyalar WebP, sağlayıcılara `image/jpeg` bildiriliyor | `image/webp` bildir veya JPEG'e çevir | API reddediyorsa her istekte 3–10 s retry/fallback |
| K-9 | `KameraScreen.kt:1279-1292` | Düşük donanım eşiği `totalMem in 1..3 GB`; 4 GB cihazlar kapsam dışı | `<= 4 GB` veya `ActivityManager.isLowRamDevice` / `maxMemory < 192 MB` | 4 GB cihazlarda +1–3 s/çekim önlenir |
| K-10 | `OmrScannerScreen.kt:196-197` | `cameraProviderFuture.get()` kompozisyonda ana thread'i blokluyor | `addListener(..., mainExecutor)` ile async bağla | Ekran açılışında 50–300 ms |
| K-11 | `KameraGoruntuIslemleri.kt:214-226`; `KareBirlestirici.kt:74-88` | 4 ARGB + 4 gri `IntArray` (~40–60 MB); piksel başına 3 allocation | Griyi `ByteArray`; `Arrays.sort(dizi, 0, say)`; buffer yeniden kullanımı | Uzun pozlamada 2–8 s ve OOM riski |

### 3.4 Build / APK / startup (P1)

| ID | Konum | Sorun | Düzeltme | Etki |
|---|---|---|---|---|
| B-1 | `app/build.gradle.kts:131` | `material-icons-extended`: ~11.400 sınıf, bytecode'un ~%44'ü (DEX analizi, tahmini); 116 ikon kullanılıyor | R8 açıkken çoğu zaten gider; yine de `material-icons-core` + yerel `ImageVector`/drawable | R8'siz −5–6 MB; build −%20–30 |
| B-2 | Baseline profile yok (yalnız kütüphane profili 11,4 KB) | İlk açılışta sınıf yükleme/JIT ısınması | `:baselineprofile` modülü + Macrobenchmark `StartupTimingMetric` + `profileinstaller` | −%10–25 / −150–400 ms |
| B-3 | `res/values/themes.xml:3`; `AndroidManifest.xml:63` | Platform `Material.Light`; splash API yok; koyu temada beyaz flaş | `core-splashscreen` + `Theme.SoruDefteri.Starting` + `installSplashScreen()` | Algılanan −100–300 ms |
| B-4 | `res/xml/soru_widget_info.xml:12` | Widget 6 saatte bir uyanıyor, veri değişmese de yeniliyor | `updatePeriodMillis=0`; veri değişiminde `SoruWidgetUpdater` | Pil |
| B-5 | Merged manifest `MlKitInitProvider` (initOrder=99) | Kullanıcı tarama yapmasa da ML Kit runtime açılışta yükleniyor | Provider'ı `tools:node="remove"`; `MlKit.initialize()`'ı kamera ekranında çağır | −20…−60 ms, RAM −5…−15 MB |
| B-6 | `gradle/libs.versions.toml:29` | `security-crypto 1.1.0-alpha06` (~1.450 Tink sınıfı) | 3 API anahtarı için Keystore AES/GCM sarmalayıcı veya stable sürüm | APK −0,4–0,8 MB; açılış −20…−50 ms |
| B-7 | `AndroidManifest` / `res/mipmap-anydpi-v26` | API 24/25'te launcher ve bildirim ikonu eksik/adaptif | PNG mipmap fallback + monokrom `ic_notification` | İşlevsel düzeltme |
| B-8 | `app/build.gradle.kts:68-76` | Keystore yoksa release debug anahtarıyla imzalanıyor | Env yoksa `GradleException` | Güvenlik/dağıtım |

---

## 4. P2 — Fırsat buldukça

- `MathMarkdownText` dosyasındaki 194 regex'in çoğu `formatMathSymbols` içinde her çağrıda derleniyor; dosya düzeyine taşınabilir.
- `KaTeXMathView` her örnekte `BoxWithConstraints` subcomposition açıyor; `onSizeChanged` yeterli.
- `SoruReelsScreen`, `TestCozScreen`, `TestSonucScreen`, `PhoneTasarim`, `FullScreenImageViewer` içinde `ImageRequest.Builder(...).build()` doğrudan composition'da; `remember` ile sabitlenebilir. `FullScreenImageViewer` `.size(2160)` ~33 MB decode istiyor; ekran genişliğinin 2 katı yeterli.
- `DenemeTakipCard`, `AcilEylemMatrisiCard`, `BugunkuAkilliOnerilerCard`, `SuperAppAnalitikCards`, `DenemeDetayliAnalizCard`, `KonuIsiHaritasiCard` → 13 yerde `remember(List)` anahtarı; her recomposition'da O(n) `equals`. Ağır hesap ViewModel'e taşınmalı.
- `cacheDir/pdf`, `cacheDir/teacher_share`, `edited_*.jpg` temizlenmiyor; açılışta "son 24 saat hariç sil" veya boyut sınırı.
- `GorselDepo.getThumbnailPath` compose sırasında 2 `stat`; thumbnail yolu entity'ye yazılmalı.
- `SoruDao.searchSorular`, `getIstatistikler`, `ImageCropperDialogLegacy` ölü kod; yanlış örnek oluşturuyor.
- `MainActivity` iki kez `enableEdgeToEdge()`; kabul edilebilir.
- `resourceConfigurations += listOf("tr","en")` → ~0,1–0,2 MB.
- 4 ABI var; `lib/` yalnızca 0,21 MB, split kazancı ~0,15 MB (düşük öncelik).

---

## 5. Yol Haritası

### Faz 0 — Bugün (yarım gün, en yüksek getiri)
1. Release imzalı APK üret; CI'ı `assembleRelease`'e çevir; debug APK'yı release asset yapmayı bırak.
2. `derle.sh release-full` ile R8'li APK üret ve boyutu/açılışı karşılaştır.
3. `ornekliCoz` koşulunu düzelt (tek satır).
4. Reels `beyondViewportPageCount = 0`.
5. `PRAGMA synchronous=NORMAL` + `cache_size`.
6. AI timeout/retry değerlerini indir.

**Beklenen:** APK 29 → ~12–18 MB, açılışta hissedilir hızlanma, kamera çekiminde 18–36 MB bellek kazancı.

### Faz 1 — 1 hafta (çökme ve donma)
1. OMR capture'ı dosya + `cameraExecutor` + sampled decode + recycle'a çevir (P0-3).
2. FTS `UPDATE OF` + hedefli UPDATE (migration 14) (P0-5).
3. Paging `@RawQuery` + kısmi indeksler (P0-5).
4. Reels'te KaTeX'i tek WebView'a indir (P0-4).
5. Soğuk açılış: ESP lazy + tek DataStore snapshot + splash (P0-6).
6. Çizim rasterizasyonu + kayıt debounce (P0-7).
7. `BubbleReader` `getPixels` (K-3), OMR analyzer fps kapısı (K-2).

**Beklenen:** OMR'de OOM/ANR biter; kütüphane kaydırma akıcı; reels bellek tepesi yarıya iner; açılış −%30…−50.

### Faz 2 — 2–4 hafta (sistemik)
1. Yedekleme streaming + tek yazma yolu (D-2).
2. PDF'i bir kez üret + görsel cache (D-3).
3. Meta akışını tek sorguya indir (D-1).
4. Baseline profile + splash + ML Kit lazy init.
5. `MathMarkdownText` regex/remember optimizasyonu (UI-1).
6. `DenemeDetayScreen` → LazyColumn (UI-4); `KonuIsiHaritasiCard` normalize cache (UI-6).
7. `material-icons` daraltma, keep kuralları, `security-crypto` sadeleştirme.

### Faz 3 — Ölçüm ve doğrulama
- Macrobenchmark: soğuk/sıcak açılış, kütüphane kaydırma (frameOverrunMs P90), reels 20 sayfa, OMR tarama, PDF üretimi.
- `dumpsys meminfo` ile WebView/bitmap tepeleri.
- `EXPLAIN QUERY PLAN` ile Paging sorgusunun `SCAN` + `TEMP B-TREE` yaptığını kanıtla; sonra indeks sonrası planı karşılaştır.
- 1.000 / 3.000 / 10.000 sentetik soru ile `LIMIT/OFFSET` eğrisi.
- Düşük segment fiziksel cihazda 30 dakikalık kullanım sonrası ısı/pil.

---

## 6. Ölçüm Planı (öncesi/sonrası)

| Metrik | Araç | Hedef |
|---|---|---|
| Soğuk açılış | `adb shell am start -W` / Macrobenchmark | −%40…−60 |
| Kare süresi (kütüphane, reels) | `dumpsys gfxinfo framestats`, Perfetto | P90 < 16 ms, frameOverrun < 5% |
| Reels bellek | `dumpsys meminfo` | WebView sayısı ≤ 3, tepe −100 MB |
| OMR tarama | Perfetto + `Debug.getNativeHeapAllocatedSize` | tepe < 40 MB, süre < 1,5 s |
| DB sayfa yükleme | `EXPLAIN QUERY PLAN` + 3.000 satır | < 10 ms |
| PDF 200 soru | Macrobenchmark | < 3 s |
| APK | `unzip -l`, `apkanalyzer` | < 12 MB |
| Pil | Battery Historian / 30 dk kullanım | %20+ düşüş |

---

## 7. Zaten İyi Olanlar (regresyon yaratmamak için korunmalı)

- Paging `items(count, key, contentType)`, `enablePlaceholders = false`, `pageSize = 20` (`KutuphaneScreen.kt:883-900,1061-1085`).
- `CombinedMathMarkdownText`: bölüm başına ayrı WebView yerine **tek** WebView (`MathMarkdownText.kt:153-204`).
- `collectAsStateWithLifecycle` neredeyse her yerde; tek istisna `AudioMemoCard`.
- Kamera ana ekranı: `STRATEGY_KEEP_ONLY_LATEST`, tek thread executor, `ImageProxy` kapatma, 450/300 ms kapı, panel açıkken analiz durdurma.
- `OcrHelper`: ortak recognizer, 1800 px sampling, RGB_565, 15 s timeout, `close()`.
- `GorselDepo`: 1600 px cap, WebP 75, sampled decode, thumbnail cache.
- `ImageCropperDialog`/`BatchCropDialog`: IO dispatcher'da sampled decode + RGB_565 — OMR ekranının kopyalaması gereken doğru desen.
- PDF: `Dispatchers.IO`, sayfa başına bitmap decode + recycle, ölçümde yalnız bounds decode (tekrar üretim dışında).
- Yedekleme: 64 KB buffer'lı streaming zip, zip-bomb limitleri, path traversal koruması.
- Nearby sync: 450 KB inline limit, büyük dosyalar `Payload.fromFile`.
- `android:largeHeap` yok; `extractNativeLibs=false`; `usesCleartextTraffic=false`; WorkManager on-demand init.
- Room migration'ları açık; destructive migration yok; `fallbackToDestructiveMigration` yok.
- Coil bellek önbelleği düşük RAM'de %12 / disk 50 MB; tema statik, `Modifier.blur` yok.
- `ReminderScheduler` `UPDATE` politikası; `KaTeXMathView` `onRelease`'te `destroy()`.

---

## 8. Sonuç

Uygulamanın mimarisi ve son performans turu iyi durumda; ancak **dağıtılan artefaktın debug olması** ölçülen tüm diğer sorunları gölgeliyor. İlk yarım günde release + R8 + birkaç tek satırlık düzeltme, düşük donanımlı cihazlarda algılanan hızı büyük ölçüde artırır.

Ardından OMR bellek yolu, Reels WebView çokluğu ve Paging/FTS veri yolu sırasıyla ele alınmalı. Bu üçü, 2 GB sınıfı cihazlarda çökme ve donmanın ana kaynakları.
