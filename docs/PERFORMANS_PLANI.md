# Performans Tarama Planı — Kasma Kaynakları ve Çözümler

**Belge sürümü:** v1.0 — tarama + plan (uygulama bu belgeye göre)
**Tarih:** 2026-10-18
**Kapsam:** Tüm uygulama (85 UI dosyası tarandı). Öncelik: liste kaydırma, görsel yükleme, arka-plan hesapları, lifecycle sızıntıları.

---

## P0 — Liste kaydırma (en büyük kasma)

### P0-1. Telefon listesi `for + item` ile diziliyor (KRİTİK)
- **Yer:** `KutuphaneScreen.kt:859-874` — `for (index in 0 until pagedSorular.itemCount) { item(key=...) }`.
- **Sorun:** Her öğe ayrı `item {}` slotu; `LazyPagingItems[index]` her recomposition'da çağrılır, prefetch/caching çalışmaz. Liste uzadıkça kaydırma takılır. (Tablet grid'i `items(count, key)` kullanıyor — doğru.)
- **Çözüm:** `items(count = ..., key = itemKey { it.id }, contentType = { "soru" }) { index -> ... }`. `contentType` aynı tipte recomposition'ı atlar.

### P0-2. Kart görselleri tam boy yükleniyor
- **Yer:** `SoruKarti.kt:144` (grid, 16:10 alan) ve `PhoneTasarim.kt:680` (92dp thumb) — `SubcomposeAsyncImage` + `crossfade(true)`, `size()` yok.
- **Sorun:** Coil, 1600px görseli decode edip Crop'la küçültüyor — her kartta CPU+RAM. `crossfade` her kartta alpha animasyonu = ek GPU yükü. `loading` spinner'ı her kartta ayrı `CircularProgressIndicator` (animasyonlu = sürekli recomposition).
- **Çözüm:** `size(360)` (kart genişliğine uygun), `crossfade(false)`, loading'de statik placeholder (renk blok, animasyonsuz). Thumbnail dosyası varsa o istenir (zaten `getThumbnailPath` var — istek boyutu da küçültülür).

### P0-3. Kartta her recomposition'da animasyon
- **Yer:** `SoruKarti.kt:393-402` — iki `animateColorAsState(tween 300)` her kartta.
- **Sorun:** Liste kayarken her kart 300ms renk animasyonu çalıştırır; yüzlerce kart = sürekli animasyon yükü.
- **Çözüm:** Animasyonu kaldır, doğrudan renk ata (durum değişimi zaten nadir).

## P0 — Ana-thread hesapları

### P0-4. Kütüphane filtreleme ana thread'de
- **Yer:** `KutuphaneViewModel.kt:179-284` — `filteredDataFlow` combine bloğunda tüm sorular filtrelenip sıralanıyor; `flowOn` YOK.
- **Sorun:** Arşiv büyüdükçe her harf yazışta/filtrede ana thread bloklanır → yazı yazarken takılma. (Kod yorumunda "Dispatchers.Default" yazıyor ama `flowOn` eklenmemiş.)
- **Çözüm:** `filteredDataFlow` sonuna `.flowOn(Dispatchers.Default)`.

### P0-5. İstatistik tüm soruları ana thread'de topluyor
- **Yer:** `IstatistikViewModel.kt:157-210` — `getAllSorular()` tüm listeyi combine'a sokuyor; `haftalikKarsilastirmaHesapla` + `BugunkuAkilliOnerilerCard (remember(allQuestions))` + `AcilEylemMatrisiCard` + `KonuIsiHaritasiCard` her biri listeyi baştan geziyor.
- **Sorun:** İstatistik sekmesi açılınca büyük arşivde donma. Kartlar `remember` ile korunuyor ama ilk hesap pahalı.
- **Çözüm:** combine'a `.flowOn(Dispatchers.Default)`; ağır kart hesapları zaten `remember`lı — ilk açılışa loading ekle (mevcut `isLoading` var, korunacak).

### P0-6. Reels feed kurulumu ana thread'de
- **Yer:** `ReelsViewModel.kt:97-118` — `buildFeed` (sıralama + karıştırma) `onEach` içinde doğrudan çalışıyor.
- **Sorun:** Açılışta `isLoading=true` var ama hesap ana thread'de → ilk kare gecikir.
- **Çözüm:** `buildFeed` içini `viewModelScope.launch(Dispatchers.Default)` ile sar, bitince state yaz.

## P1 — Bellek ve lifecycle

### P1-1. KaTeX WebView `onRelease` yok (LEAK)
- **Yer:** `KaTeXMathView.kt:860` — `AndroidView(factory, update)`; `onRelease`/`onReset` hiç tanımlı değil.
- **Sorun:** Reels/detay/chat'te her formüllü içerik bir WebView açar; sayfa değişince `destroy()` çağrılmaz → WebView + JS context bellekte kalır. Uzun reels gezmesinde RAM şişer.
- **Çözüm:** `onRelease = { it.destroy() }` ekle. (NonTouchableWebView context'i activity — factory'de `context.applicationContext` kullanılamaz çünkü tema gerekli; destroy yeterli.)

### P1-2. Coil bellek önbelleği %25 + disk 100 MB (yüksek)
- **Yer:** `SoruDefteriApplication.kt:94-104`.
- **Sorun:** Düşük cihazda %25 RAM görsellere gider; liste + reels + detay aynı anda açıkken baskı.
- **Çözüm:** Düşük donanımda (`dusukDonanimMi` benzeri kontrol) memory %12 + disk 50 MB; normalde %18 + 75 MB. Kalite etkilenmez (önbellek yalnız hız).

### P1-3. `SharingStarted.Eagerly` akışlar (5 adet KameraViewModel'de)
- **Yer:** `KameraViewModel.kt:40-68` — DataStore akışları Eagerly.
- **Sorun:** Kamera kapalıyken bile DataStore dinlenir (küçük ama gereksiz; prensip: "çıkınca kapansın").
- **Çözüm:** `WhileSubscribed(5000)` — kamera zaten Hilt ViewModel ile ekrana bağlı, çıkışta durur.

### P1-4. Reels sayfası tam-boy görsel yüklüyor
- **Yer:** `SoruReelsScreen.kt:312` — `AsyncImage(model = imageFile)` + `ContentScale.Fit`, boyut kısıtı yok.
- **Sorun:** Dikey pager'da komşu sayfalar da yüklenir; 1600px × 3 sayfa = RAM baskısı. Kullanıcı "sadece izleyecekse" bile tam boy decode.
- **Çözüm:** `size(1080)` kısıtı (ekran genişliği yeterli, kalite korunur) + `crossfade(false)`. Pager `beyondViewportPageCount` varsayılanı korunur (1+1 sayfa).

## P2 — Ertelenenler (bu işte YOK)

- Paging `pageSize 20/initial 40` — yeterli, dokunulmadı.
- Backup `getAllSorular().first()` (TODO P0) — arka-plan worker'da, UI'ı kasmaz; ayrı iş.
- `SoruDetayScreen`/`TestCozScreen` AsyncImage'ları — detay ekranı tek görsel, sorun değil.
- Coil `thumbnail` ön-yükleme (prefetch) — P1 sonrası ölçümle karar.
- `ImageCropperDialog`/`BatchCropDialog` decode'ları — dialog açıkken tek seferlik, sorun değil.
- Donanım kademesi ayarı (kullanıcının "güçlü cihaz aktarır, zayıf izler" fikri) — ayrı özellik işi; altyapı (dusukDonanim kontrolü) bu planda ekleniyor.

## Doğrulama

- `./gradlew testDebugUnitTest` (tam suit) + `assembleDebug`.
- Manuel: Kütüphane hızlı kaydırma, Reels 20+ sayfa gezinme, İstatistik açılış süresi, düşük cihazda ısınma gözlemi.
