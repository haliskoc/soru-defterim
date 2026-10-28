# Soru Defteri — Responsive / Edge-to-Edge UI Denetim Raporu

**Tarih:** 28 Ekim 2026  
**Kapsam:** `SoruEklemeScreen` ve tüm alt bileşenleri, `KameraScreen` ve kamera alt bileşenleri, `PdfExportDialog`, `ImageCropperDialog`, `BatchCropDialog`, `DrawingCanvasDialog`, `SimilarQuestionDialog`, `ZoomableImageDialog`, `FullScreenImageViewer`, `DenemeEkleDialog`, `TeacherShareDialog`, ortak matematik bileşenleri ve `AppNavHost` inset/klavye davranışı.  
**Sürüm:** Compose BOM `2025.02.00`, Material3 `1.3.1`, `enableEdgeToEdge()` açık, `windowSoftInputMode="adjustResize"`, orientation kilidi yok.  
**Yöntem:** Satır satır statik okuma + dp cinsinden hesaplanan yerleşim ölçümleri. Emülatörde piksel doğrulaması yapılmadı; metin genişlikleri ortalama karakter genişliğiyle tahmin edildi (14sp ≈ 6.8dp, 16sp ≈ 8dp, 12sp ≈ 6dp, 22sp ≈ 10dp). Sonuçlar ±%10 toleransla okunmalıdır.

## 0. Yönetici özeti

Toplam **52 bulgu**: **6 Kritik**, **23 Yüksek**, **21 Orta**, **2 Düşük**.

| Şiddet | Adet | Öne çıkan konu |
|---|---:|---|
| Kritik | 6 | Üst inset tüketimi, çizim kaybı, 320dp'de 5 şıkkın üst üste binmesi, kamera çerçevesinin panel altında kalması |
| Yüksek | 23 | Başlık/buton satırlarının taşması, dialog ve kamera sheet'lerinin scroll'suz kalması, 48dp altı hedefler |
| Orta | 21 | Sabit yükseklikler, klavye açıkken fazla boşluk, tablette aşırı geniş içerik |
| Düşük | 2 | Tek satırlık başlık taşmaları |

**En kritik 5 sorun**

1. **E49** — `AppNavHost` compact dalında üst inset tüketilip uygulanmadığı için tüm ekranların üst çubukları status bar'ın altında kalıyor.
2. **A9** — `DrawingCanvasDialog`'un kaydetme yolu yok; `onSolutionDrawingSaved` hiç çağrılmıyor, çizim kapatınca kayboluyor.
3. **B17** — Kamera çerçevesi ile alt panel aynı anda sığmıyor; 360×640'ta ≈72dp, 480×320'de ≈100dp örtüşme var.
4. **A6** — `GorselSeciciSection` üç butonlu satırda her butona 69dp kalıyor; metin alanı 1.3dp'ye düşüp butonlar okunamaz hale geliyor.
5. **A12** — `CevapSeciciSection` 5×52dp = 260dp, 320dp'de 224dp alana sığmıyor; şıklar 9dp üst üste binip yanlış şık seçimine yol açıyor.

> 6. sırada **C26** (PdfExportDialog üç aksiyon butonunun 76.5/91.8/76.5dp'ye sıkışması) geliyor.

## 1. Ölçüm varsayımları

- Aksi belirtilmedikçe `fontScale = 1.0`, yoğunluk `1dp = 1px` kabul edildi; 1.15×, 1.3× ve 2.0× font ölçekleri ayrıca belirtildi.
- **320dp telefon:** ekran içeriği `320 − 2×16 = 288dp`; `PhoneAdimKarti` iç paddingi sonrası `256dp`; kart içindeki section paddingi sonrası `224dp`.
- **360dp telefon:** `328 → 296 → 264dp`. **411dp telefon:** `379 → 347 → 315dp`.
- **AlertDialog:** Material3 genişliği 280–560dp aralığında; telefonlarda 280dp, içerik paddingi sonrası **232dp**. Bu yüzden Deneme/Teacher dialogları 360 ve 411dp'de de 232dp içerikle çalışır.
- **PdfExportDialog:** `fillMaxWidth(0.94f)`; 320dp'de 300.8dp (içerik 260.8dp), 1280dp'de 1203.2dp (içerik 1163.2dp).
- **SimilarQuestionDialog:** `fillMaxSize` + 16dp dış padding + 20dp iç padding; 320dp'de 248dp, 1280dp'de 1208dp içerik.
- **System bar:** status bar 24dp (gesture) / 48dp (3 tuşlu veya çentik); nav bar 24/48dp; IME 280–320dp.
- **Dialog penceresi:** `DialogProperties.decorFitsSystemWindows` varsayılan `true`; tam ekran dialoglar sistem çubuklarının içinde açılır. `AlertDialog` ayrı pencere kullandığı için activity'nin `adjustResize` davranışı doğrudan uygulanmaz.

## 2. Doğrulama notları (bulgu sayılmayanlar)

1. **Kamera önizleme/kırpma eşlemesi tutarlı.** `PreviewView.ScaleType.FILL_CENTER` ile `KirpmaHesaplayici.dolguOlcegi` aynı `max(görselGen/gorunumGen, görselYuk/gorunumYuk)` varsayımını kullanıyor; overlay ve JPEG kırpma aynı normalize koordinatları görüyor. "Preview aspect ratio uyuşmazlığı" ayrı bulgu yazılmadı. Yalnız `Preview`/`ImageCapture` için ayrı `ResolutionSelector` olmaması risk olarak B18'in yanında not edildi.
2. **`ModalBottomSheet` tablette sonsuz genişlemiyor.** Material3 1.3.1'de varsayılan `sheetMaxWidth = 640.dp`; "tablette 1280dp sheet" iddiası geçersiz.
3. **`SoruMetadataSection` genel olarak sağlam.** Ders çipleri `horizontalScroll`, hata tipi `FlowRow`; zorluk çipleri 3×≈52dp + 16dp = ≈172dp < 224dp. Bu yüzden zorluk satırı bulgu yazılmadı.
4. **Standart Material3 butonları/IconButton'ları** varsayılan olarak 48dp dokunma hedefi genişletmesi kullanır. Yalnızca açıkça `size(…dp)` ile 48dp'nin altına sabitlenen hedefler işaretlendi.
5. **Orientation kilidi yok.** Manifest'te `screenOrientation` bulunmadığı için portre ve yatay ölçümler ayrı ayrı yapıldı; `configChanges` de yok, yani rotasyonda Activity yeniden oluşuyor (B16).
6. **`AlertDialog` genişliği 280dp'de sabit.** Deneme/Teacher bulgularındaki taşmalar ekran genişliğinden değil, dialogun 232dp içerik genişliğinden kaynaklanıyor.

## 3. A) SoruEklemeScreen ve alt bileşenleri (1–14)

### A1 — TopAppBar başlığı 320dp'de aksiyon butonuyla çakışıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/SoruEklemeTopBar.kt:30-68`
- **Kod:** `TopAppBar(title = { Text(text = if (isEditMode) "Soruyu Düzenle" else "Yeni Soru Ekle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(...) }, actions = { FilledTonalButton(...) { ... Text(if (isEditMode) " Güncelle" else " Kaydet") } })`
- **Ölçüm:** 320dp'de kullanılabilir genişlik 320 − 2×4 = 312dp. Geri ikonu 48dp + başlık `titleLarge` 22sp'de ≈150dp + "Kaydet" butonu (metin ≈48dp + ikon 18dp + 8dp boşluk + 2×24dp content padding) ≈122dp = **328dp**. Başlığa ≈142dp kalıyor; "Yeni Soru Ekle" 150dp olduğu için son harfler ellipsis'e gidiyor. 360dp'de 328dp < 352dp olduğundan sorun görünmüyor.
- **Cihaz:** 320dp telefon (ör. 720p/5"), fontScale ≥1.15'te 360dp de etkilenir.
- **Şiddet:** **Orta**
- **Öneri:** `BoxWithConstraints` ile <360dp'de `titleMedium` kullanın; kaydet butonunu yalnız ikon + `contentDescription` yapın veya `Text` yerine `Icon` gösterin. Başlığa `maxLines = 1, overflow = TextOverflow.Ellipsis` ekleyin.

### A2 — Çoklu çekim bandında "Sıradakine Geç" butonu sıkışıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/SoruEklemeScreen.kt:175-204`
- **Kod:** `Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("📦 Çoklu Çekim: Soru ${uiState.currentBatchIndex} / ${uiState.totalBatchCount}") ; Button(...) { Text("Sıradakine Geç", fontSize = 12.sp) } }`
- **Ölçüm:** 320dp'de bant iç genişliği 288 − 28 = **260dp**. Metin `bodyMedium` 14sp'de ≈195dp; butona 65dp kalıyor. Butonun metni ("Sıradakine Geç" ≈84dp) + 20dp yatay padding = 104dp istediği için 65dp'ye sığmıyor; metin iki satıra kırılıyor, buton yüksekliği ≈44dp'ye çıkıyor. 360dp'de 268dp alanda 195 + 73dp dağılımıyla sınırda kalıyor.
- **Cihaz:** 320dp telefon; 360dp'de fontScale 1.15 ile.
- **Şiddet:** **Yüksek** (çoklu çekim akışında ilerleme butonu zor kullanılır)
- **Öneri:** `Text`'e `Modifier.weight(1f)` + `maxLines = 2` verin; butona `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)` ve `maxLines = 1` uygulayın. <360dp'de `Column`'a düşürün.

### A3 — Katalog sheet'indeki liste sabit 380dp; yatayda ekran dışına taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/SoruEklemeScreen.kt:364-424`
- **Kod:** `ModalBottomSheet(...) { Column(...) { ... LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp), ...) { ... } } }`
- **Ölçüm:** 480×320 yatayda sheet'in kullanılabilir yüksekliği ≈320 − 24 (status) = **296dp**. Sabit yükseklikler: drag handle ≈32 + başlık 48 + 12 + arama alanı 56 + 12 + liste 380 + alt padding 24 = **564dp**. Aradaki **268dp** içerik erişilemez; `ModalBottomSheet` gövdesi scrollable olmadığı için alttaki konulara ulaşılamıyor.
- **Cihaz:** 480×320 yatay telefon; 320×480 portrede de 564dp > 456dp.
- **Şiddet:** **Yüksek**
- **Öneri:** `LazyColumn`'u `Modifier.weight(1f).heightIn(max = 380.dp)` yapın; sheet içeriğini `Column(Modifier.fillMaxHeight(0.9f))` ile sınırlayın. Klavye açıkken `imePadding()` ekleyin.

### A4 — Katalog başlığı uzun ders adlarında 3 satıra çıkıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/SoruEklemeScreen.kt:374-387`
- **Kod:** `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, ...) { Text(text = "${uiState.currentSubjectName} Müfredat Konuları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) ; IconButton(...) }`
- **Ölçüm:** 320dp'de sheet içeriği 280dp; kapat ikonu 48dp → başlığa **232dp** kalıyor. "Matematik Müfredat Konuları" 16sp'de ≈229dp (sınırda), "Türk Dili ve Edebiyatı Müfredat Konuları" ≈348dp → 2 satır; "T.C. İnkılap Tarihi ve Atatürkçülük Müfredat Konuları" ≈470dp → 3 satır. `maxLines` olmadığı için satır sayısı sınırsız.
- **Cihaz:** 320/360dp, uzun ders adlarında.
- **Şiddet:** **Düşük**
- **Öneri:** `Text`'e `Modifier.weight(1f)`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis` verin.

### A5 — Boş görsel kartı sabit 180dp; içerik 320dp'de 190dp istiyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/GorselSeciciSection.kt:87-141`
- **Kod:** `OutlinedCard(... modifier = Modifier.fillMaxWidth().height(180.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), ...) { Icon(... size(44.dp)); Spacer(12.dp); Text("Sorunun net bir fotoğrafını ekleyin", style = bodyMedium); Spacer(14.dp); Row { Button("Kamera"); OutlinedButton("Galeri") } } }`
- **Ölçüm:** 320dp'de kart iç genişliği 224dp. Metin 34 karakter × 6.8dp ≈ **231dp** → 2 satır (40dp). Toplam: 16+44+12+40+14+48+16 = **190dp** > 180dp → alt butonların 10dp'si kırpılıyor. `fontScale = 1.3`'te metin 2 satır 52dp, buton 62dp → **214dp**, 34dp kırpma.
- **Cihaz:** 320dp telefon; 360dp'de fontScale 1.3.
- **Şiddet:** **Orta**
- **Öneri:** `height(180.dp)` yerine `heightIn(min = 180.dp)` kullanın; metni `maxLines = 2` ile sınırlayın.

### A6 — Üç butonlu aksiyon satırında metin alanı 1.3dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/GorselSeciciSection.kt:198-241`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(Modifier.weight(1f)) { Icon(16.dp); Spacer(4.dp); Text("Kırp / Döndür") } ; FilledTonalButton(Modifier.weight(1f)) { ... Text("OCR Tara") } ; FilledTonalButton(Modifier.weight(1f)) { ... Text("AI Etiket") } }`
- **Ölçüm:** 320dp'de kart içi 224dp; 2×8dp boşluk sonrası her butona **69.3dp** düşüyor. Material3 buton varsayılan yatay content padding'i 2×24dp olduğu için içerik alanı **21.3dp**; ikon 16dp + 4dp boşluk = 20dp → metne **1.3dp** kalıyor. "Kırp / Döndür" karakter bazında satırlara kırılıyor, buton yüksekliği ≈200dp'ye çıkabiliyor. 411dp'de buton 99.7dp, metin alanı 31.7dp → "Kırp / Döndür" yine 3 satır.
- **Cihaz:** 320/360/411dp telefonların tamamı.
- **Şiddet:** **Kritik**
- **Öneri:** `contentPadding = PaddingValues(horizontal = 8.dp)` + `maxLines = 1` + `overflow = Ellipsis`; sığmıyorsa 2 satıra bölün (Kırp/OCR üstte, AI Etiket altta) veya ikon-only + `contentDescription` kullanın.

### A7 — Görsel üzerindeki büyüt/sil butonları 36dp
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/GorselSeciciSection.kt:176-192`
- **Kod:** `IconButton(onClick = { zoomImageUrl = soruImagePath }, modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape).size(36.dp)) { Icon(Icons.Default.ZoomIn, ..., modifier = Modifier.size(20.dp)) }` (sil butonu da aynı)
- **Ölçüm:** Görsel üzerindeki iki hedef **36×36dp**; Material önerisi 48×48dp. 320dp'de iki buton + 6dp boşluk = 78dp, görsel genişliğinin %35'i. `Modifier.size(36.dp)` IconButton'un iç 48dp minimumunu dışarıdan kısıtladığı için dokunma hedefi gerçekten 36dp kalıyor.
- **Cihaz:** Tüm dokunmatik cihazlar (özellikle tek elle kullanım).
- **Şiddet:** **Orta**
- **Öneri:** Dış kutu 48dp, görsel daire 36dp olsun: `Box(Modifier.size(48.dp).clickable(...)) { Surface(Modifier.size(36.dp)) { ... } }`. Alternatif: `minimumInteractiveComponentSize()` + `size(40.dp)`.

### A8 — Çözüm görseli satırlarında butonlar ve metin sıkışıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/GorselSeciciSection.kt:264-288` ve `:295-335`
- **Kod:** Boş durum: `Row { OutlinedButton(Modifier.weight(1f)) { Icon(18.dp); Spacer(6.dp); Text("Galeri") } ; OutlinedButton(Modifier.weight(1f)) { ... Text("Çizim Yap") } }`. Dolu durum: `Row { Row(Modifier.weight(1f)) { AsyncImage(Modifier.size(64.dp)); Spacer(12.dp); Text("Çözüm görseli eklendi") } ; Row { IconButton(Crop); IconButton(Delete) } }`
- **Ölçüm:** Boş durumda 224dp içerikte her butona **107dp** düşüyor; "Çizim Yap" (61dp) + ikon 18 + 6 + 2×24 padding = **133dp** → 26dp taşma, metin kırpılıyor. Dolu durumda 224 − 24 (kart padding) = 200dp; 64dp thumb + 12dp + 2×48dp ikon = 172dp → metne **28dp** kalıyor; "Çözüm görseli eklendi" ≈143dp, 28dp genişlikte 5–6 satıra kırılıyor ve satır yüksekliği ≈100dp'ye çıkıyor.
- **Cihaz:** 320/360dp telefonlar.
- **Şiddet:** **Orta**
- **Öneri:** Metne `maxLines = 1, overflow = Ellipsis`; ikon butonları 40dp'ye düşürün veya taşma menüsüne alın. <360dp'de butonları alt alta dizin.

### A9 — Çizim kaydedilmiyor: `onSolutionDrawingSaved` hiçbir zaman çağrılmıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/GorselSeciciSection.kt:66` ve `:347-352`; `app/src/main/java/com/sorudefteri/ui/ortak/DrawingCanvasDialog.kt:77-81`
- **Kod:** `onSolutionDrawingSaved: (String) -> Unit` parametresi tanımlı; çağrı: `DrawingCanvasDialog(imagePath = ..., onDismiss = { isDrawingDialogOpen = false })`. Dialog imzası: `fun DrawingCanvasDialog(imagePath: String, title: String = ..., onDismiss: () -> Unit)` — `onSave` yok.
- **Ölçüm:** Kaydetme yolu **0 adet**. Kullanıcı çizimi tamamlayıp X'e bastığında `paths` listesi `remember` ile dialog kapanınca yok oluyor; `solutionImagePath` boş kalıyor. Çizim ne `GorselDepo`'ya ne de ViewModel'e ulaşıyor. Etkilenen akış: Soru Ekle > Adım 1 > Çözüm Fotoğrafı/Çizim > "Çizim Yap".
- **Cihaz:** Tüm cihazlar (işlev kaybı).
- **Şiddet:** **Kritik**
- **Öneri:** `DrawingCanvasDialog`'a `onSave: (Bitmap) -> Unit` ekleyin; üst çubuğa "Kaydet" butonu koyun; bitmap'i `GorselDepo.kaydetBitmap` ile diske yazıp `onSolutionDrawingSaved(path)` çağırın. Kaydetmeden kapatmada "Çizim kaybolacak" onayı gösterin.

### A10 — "Özel Notlar & Hatırlatıcılar" başlığı 320dp'de iki satıra kırılıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/CozumNotSection.kt:53-66`
- **Kod:** `Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.EditNote, ...); Spacer(Modifier.width(8.dp)); Text("Özel Notlar & Hatırlatıcılar", style = titleMedium, fontWeight = Bold) }`
- **Ölçüm:** 320dp'de kart içi 224dp. Başlık `titleMedium` 16sp'de 28 karakter × 8dp ≈ **224dp** + ikon 24dp + 8dp = **256dp** → 32dp taşma; `maxLines` olmadığı için 2 satıra kırılıyor, ikon iki satırın ortasında kalıyor.
- **Cihaz:** 320dp telefon.
- **Şiddet:** **Düşük**
- **Öneri:** Başlığı "Özel Notlar" olarak kısaltın veya `Text`'e `Modifier.weight(1f)` + `maxLines = 2` verin.

### A11 — Kaynak bilgisi alanları 320dp'de 107dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/KaynakBilgisiSection.kt:72-107`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(..., label = { Text("Yayın / Yayınevi") }, modifier = Modifier.weight(1f)) ; OutlinedTextField(..., label = { Text("Deneme / Kitap") }, modifier = Modifier.weight(1f)) }`
- **Ölçüm:** 320dp'de kart içi 224dp; (224 − 10) / 2 = **107dp** alan. "Yayın / Yayınevi" label'ı 12sp'de ≈102dp + 16dp sol padding = 118dp > 107dp → label sağdan kırpılıyor. Placeholder "Örn: 3D, Apotemi" 14sp'de ≈116dp, metin alanı 107 − 32 = 75dp → 2 satıra kırılıyor. Aynı sorun "Deneme / Kitap" ve "Basım Yılı / Soru No" satırında da var.
- **Cihaz:** 320dp telefon; 360dp'de fontScale 1.3.
- **Şiddet:** **Orta**
- **Öneri:** <360dp'de alanları tek sütuna indirin (`BoxWithConstraints` + `if (maxWidth < 360.dp)`); veya label'ları "Yayın", "Deneme", "Yıl", "No" olarak kısaltın.

### A12 — 5 doğru cevap şıkkı 320dp'de üst üste biniyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/CevapSeciciSection.kt:39-70`
- **Kod:** `val options = listOf("A","B","C","D","E"); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { options.forEach { option -> Surface(..., modifier = Modifier.size(52.dp)) { ... } } }`
- **Ölçüm:** 5 × 52dp = **260dp**; 320dp'de kullanılabilir genişlik **224dp**. 36dp fazla. `SpaceBetween` boşluğu `(224 − 260) / 4 = −9dp` hesapladığı için her daire komşusuna **9dp** giriyor. 320dp'de toplam 36dp örtüşme; A/B/C/D/E dairelerinin dokunma alanları da örtüştüğü için sınır bölgesinde yanlış şık seçilebiliyor.
- **Cihaz:** 320dp telefon (360dp'de 260dp < 264dp olduğu için sınırda kurtarıyor).
- **Şiddet:** **Kritik**
- **Öneri:** `Modifier.weight(1f).aspectRatio(1f).sizeIn(maxWidth = 52.dp)` kullanın; `Arrangement.spacedBy(6.dp)` ile 5×~41.6dp elde edin. Alternatif: `FlowRow` ile 3+2 düzeni.

### A13 — Öğrenci cevabı satırında 6 hedef 32.3×44dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/CevapSeciciSection.kt:80-113`
- **Kod:** `val studentOptions = listOf("A","B","C","D","E","Boş"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { studentOptions.forEach { ... Surface(..., modifier = Modifier.weight(1f).height(44.dp)) { ... } } }`
- **Ölçüm:** 320dp'de (224 − 5×6) / 6 = **32.3dp** genişlik × 44dp yükseklik. Her iki boyut da 48dp'nin altında; "Boş" metni 14sp'de ≈24dp, 1.0×'te sığıyor; `fontScale = 1.3`'te ≈31dp → 32.3dp alanda kırpılıyor. 360dp'de genişlik 39dp, hâlâ <48dp.
- **Cihaz:** 320/360dp telefonlar; 411dp'de 45.8dp (yine <48dp).
- **Şiddet:** **Yüksek**
- **Öneri:** 6 seçeneği 3+3 iki satıra bölün veya `FlowRow` kullanın; her hedef `heightIn(min = 48.dp)` ve `widthIn(min = 48.dp)` olsun. "Boş" yerine "Yok" kısaltması düşünülebilir.

### A14 — OCR "AI ile LaTeX & İmla Düzelt" butonu 320dp'de taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/OcrResultPreviewSection.kt:116-153`
- **Kod:** `OutlinedTextField(..., maxLines = 6, ...)` ; `FilledTonalButton(...) { Icon(16.dp); Spacer(6.dp); Text("AI ile LaTeX & İmla Düzelt") }`
- **Ölçüm:** 320dp'de kart içi 224dp. Buton metni 26 karakter × 6.8dp ≈ **177dp** + ikon 16 + 6 + 2×24 content padding = **247dp** → 23dp taşma; metin iki satıra kırılıyor veya sonu kırpılıyor. `maxLines = 6` alanı 6×20 = 120dp'de sabitliyor; daha uzun OCR metni alan içinde scroll ediyor (bu kısım doğru).
- **Cihaz:** 320dp telefon; 360dp'de fontScale 1.3.
- **Şiddet:** **Orta**
- **Öneri:** Butonu `Modifier.fillMaxWidth()` yapın ve metni "AI ile Düzelt" olarak kısaltın; `maxLines = 1` + `overflow = Ellipsis` ekleyin.

## 4. B) Kamera akışı (15–22)

### B15 — Odak halkası dp değil px ile çiziliyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:717-727`
- **Kod:** `odakNokta?.let { odak -> val yari = 44f; drawCircle(Color.White.copy(alpha = 0.9f), radius = yari, center = odak, style = Stroke(width = 3f)) ... }`
- **Ölçüm:** `DrawScope` içindeki `44f` **piksel**dir, dp değil. xxhdpi (3×) cihazda yarıçap 44/3 = **14.7dp** (çap 29.3dp); mdpi'de 44dp. Aynı şekilde `strokeWidth = 3f` xxhdpi'de 1dp. Yani odak halkası yoğunluğa göre 3× küçülüyor; 320dp telefonda neredeyse görünmez.
- **Cihaz:** Tüm xxhdpi/xhdpi cihazlar (Pixel/Galaxy çoğunluğu).
- **Şiddet:** **Orta**
- **Öneri:** `val yari = 22.dp.toPx()` ve `strokeWidth = 2.dp.toPx()` kullanın; aynı düzeltmeyi `drawLine` kalınlıkları (4f/6f) için de yapın.

### B16 — Kamera modu/flaş/kamera yönü rotasyonda sıfırlanıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:283-286`
- **Kod:** `var kameraModu by remember { mutableStateOf(KameraModu.TEKLI) }; var ayarSayfasiAcik by remember { mutableStateOf(false) }; var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }; var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }`
- **Ölçüm:** Manifest'te `configChanges` yok; ekran döndürüldüğünde Activity yeniden oluşuyor ve `remember` state kayboluyor. Kullanıcı "Çoklu Soru" modunda veya ön kamerada iken döndürürse **TEKLI + arka kamera + flaş AUTO**'ya dönüyor. Ayar sheet'i de kapanıyor.
- **Cihaz:** Tüm cihazlar (özellikle 480×320 yatay kullanım).
- **Şiddet:** **Orta**
- **Öneri:** `rememberSaveable` kullanın (`kameraModu`, `lensFacing`, `flashMode` enum/int olarak); `ayarSayfasiAcik` için de `rememberSaveable`.

### B17 — Kamera çerçevesi alt panelin altında kalıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:408-428` ve `:958-964`
- **Kod:** `KirpmaHesaplayici.statikCerceve(..., ustAlanPx = 96.dp.roundToPx(), altAlanPx = 96.dp.roundToPx(), yanalAlanPx = 24.dp.roundToPx(), oranGen = if (manzara) 4 else 3, oranYuk = if (manzara) 3 else 4, dolguKatsayisi = if (manzara) 0.85 else 0.75)` ; alt panel: `Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) { mod çipleri; ipucu; 80dp deklanşör }`
- **Ölçüm:** Alt panel yüksekliği: nav 24 + 16 + mod çipleri 36 + 6 + ipucu 16–32 + kontrol satırı 108 = **206–222dp**.
  - **320×480 portre:** çerçeve alanı 480 − 192 = 288dp; 0.75 dolgu → 216dp yüksek, y=132–348. Panel üstü 480 − 222 = 258dp → **90dp örtüşme** (ipucu tek satırsa 72dp).
  - **480×320 yatay:** çerçeve alanı 320 − 192 = 128dp; 0.85 → 108.8dp, y=105.6–214.4. Panel üstü 320 − 206 = 114dp → **100dp örtüşme**; çerçevenin neredeyse tamamı panelin arkasında.
- **Cihaz:** 320×480 portre, 480×320 yatay; 360×640'ta 72–74dp.
- **Şiddet:** **Kritik**
- **Öneri:** Çerçeve hesaplamasına panel yüksekliğini parametre olarak verin: `altAlanPx = panelYuksekligi + 24.dp`. Alternatif: çerçeveyi `BoxWithConstraints` içinde panelin üstünde kalan alana göre hesaplayın; yatayda paneli 2 satıra indirin.

### B18 — `PERFORMANCE` (SurfaceView) seçimi overlay Canvas ile çelişiyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:595-604` ve `:615-628`
- **Kod:** `// CameraX Önizleme View'ı (COMPATIBLE: overlay dönüşümleri için güvenilir)` → `it.implementationMode = PreviewView.ImplementationMode.PERFORMANCE`; üstte `Canvas(Modifier.fillMaxSize().pointerInput(...))` ile tarama alanı/odak halkası çiziliyor.
- **Ölçüm:** Yorum "COMPATIBLE" derken kod **PERFORMANCE** seçiyor. PERFORMANCE, `SurfaceView` kullanır; SurfaceView ayrı bir surface penceresine çizdiği için Compose overlay'in z-order'ı, köşe kırpma ve dönüşümler cihaz/üretici bazında farklı davranabilir. Ayrıca `Preview` ve `ImageCapture` için ayrı `ResolutionSelector` yok; yalnız `ImageAnalysis` 1280×720/16:9 seçiyor (`KameraScreen.kt:314-331`). Preview 16:9, ImageCapture 4:3 seçerse ROI eşlemesi kayabilir.
- **Cihaz:** SurfaceView z-order sorunları genellikle bazı Samsung/Xiaomi modellerinde ve çoklu pencere/rotation sırasında görülür.
- **Şiddet:** **Orta** (risk; her cihazda tekrarlanması garanti değil)
- **Öneri:** Yorumla tutarlı olarak `ImplementationMode.COMPATIBLE` (TextureView) kullanın veya overlay'i `PreviewView`'ın kendi overlay katmanına taşıyın. Üç use case'e de aynı `AspectRatioStrategy`/`ResolutionSelector`'ı uygulayın.

### B19 — Kamera izin ekranı yatayda taşıyor, scroll yok
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:196-278`
- **Kod:** `Box(modifier = modifier.fillMaxSize().background(...).padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = CenterHorizontally, verticalArrangement = Center) { ... } }`
- **Ölçüm:** İçerik yüksekliği: ikon 80 + 20 + başlık 29 + 8 + açıklama 40 + 24 + "Kamera İznini Ver" 48 + 12 + "Galeriden Fotoğraf Seç" 48 + 12 + "Vazgeç" 48 = **369dp**. 480×320 yatayda kullanılabilir yükseklik 320 − 48 = **272dp** → **97dp taşma**. Column ortalandığı için üst ve alt kırpılıyor; "Vazgeç" ve galeri butonu erişilemez.
- **Cihaz:** 480×320 yatay; 320×480 portrede 369 < 432 sorun yok.
- **Şiddet:** **Yüksek**
- **Öneri:** Column'a `verticalScroll(rememberScrollState())` + `heightIn(max = ...)` ekleyin; `statusBarsPadding()`/`navigationBarsPadding()` uygulayın. Yatayda ikon+başlığı yan yana, butonları alt alta tutun.

### B20 — Kamera ayarları sheet'i 470dp, yatayda scroll'suz
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:1097-1172`
- **Kod:** `ModalBottomSheet(...) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) { Text("Kamera Ayarları", titleLarge); ... 5 × KameraAyarSatiri(...) } }`
- **Ölçüm:** Başlık 32 + 16 + "Algılama" 20 + 8 + 3 satır × (24 + 2 + 2 satır açıklama 32 = 58) = 174 + 2×12 + 16 + "Çekim & Pil" 20 + 8 + 2 satır × 58 = 116 + 12 + alt padding 24 = **470dp**. 480×320 yatayda sheet yüksekliği ≈296dp → **174dp** içerik erişilemez; Column scrollable değil.
- **Cihaz:** 480×320 yatay; 320×480 portrede 470 < 456? 470 > 456, yine 14dp taşma.
- **Şiddet:** **Yüksek**
- **Öneri:** Sheet içeriğine `verticalScroll(rememberScrollState())` ve `navigationBarsPadding()` ekleyin; `skipPartiallyExpanded = true` yerine yatayda tam ekran sheet kullanın.

### B21 — Mod seçici çipleri 36dp yüksekliğinde
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:968-995`
- **Kod:** `Surface(shape = arkaPlani, color = ..., modifier = Modifier.clickable(...)) { Text(etiket, style = labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }`
- **Ölçüm:** `labelLarge` satır yüksekliği 20dp + 2×8dp padding = **36dp**; `Surface.clickable` minimum boyut uygulamıyor. 48dp'nin 12dp altında. Genişlik "Tekli Soru" ≈106dp, "Çoklu Soru" ≈113dp; yükseklik tüm hedef için 36dp.
- **Cihaz:** Tüm dokunmatik cihazlar.
- **Şiddet:** **Orta**
- **Öneri:** `Modifier.heightIn(min = 48.dp)` + `padding(vertical = 14.dp)`; `Role.RadioButton` ve `selected` semantiği ekleyin.

### B22 — Snackbar deklanşörün üstüne biniyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kamera/KameraScreen.kt:1175-1178`
- **Kod:** `SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())`
- **Ölçüm:** Alt panelde deklanşörün merkezi ekran altından ≈94dp yukarıda; 80dp'lik deklanşör alanı **54–134dp** bandında. Snackbar `navigationBarsPadding()` sonrası 24dp'den başlayıp ≈48–64dp yüksekliğinde **24–88dp** bandını kaplıyor → deklanşörün alt **34dp**'si snackbar arkasında kalıyor. "Kamera izni verilmedi" mesajı çekim sırasında deklanşörü kapatıyor.
- **Cihaz:** Tüm cihazlar.
- **Şiddet:** **Orta**
- **Öneri:** `SnackbarHost`'a `padding(bottom = 220.dp)` verin veya snackbar'ı üst çubuğun altına (`Alignment.TopCenter`) taşıyın.

## 5. C) Dialoglar (23–44)

### C23 — PdfExportDialog tablette 1203dp genişliğe yayılıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:160-176`
- **Kod:** `Dialog(..., properties = DialogProperties(usePlatformDefaultWidth = false)) { Surface(..., modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 20.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp)) { ... } } }`
- **Ölçüm:** `fillMaxWidth(0.94f)` üst sınır olmadan tüm pencereyi kullanıyor: 320dp'de **300.8dp**, 360dp'de 338.4dp, 1280dp'de **1203.2dp**. İçerik genişliği 1280dp'de 1163.2dp; tek satır metin uzunluğu okunabilirlik sınırının (≈600–720dp) neredeyse iki katı. 320dp'de ise içerik 260.8dp'ye sıkışıyor (C24–C26).
- **Cihaz:** 840/1280dp tablet ve Chromebook; 320dp telefon.
- **Şiddet:** **Yüksek**
- **Öneri:** `Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f)` kullanın; geniş ekranda iki sütunlu düzen (solda ayarlar, sağda önizleme) düşünülebilir.

### C24 — PdfExportDialog başlığı 320dp'de kapat butonuyla üst üste biniyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:178-218`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, ...) { Row(...) { Surface(Modifier.size(38.dp)) { Icon(...) }; Column { Text("Detaylı PDF Çıktısı Oluştur", titleMedium); Text("📚 ${sorular.size} Soru Seçildi • A4 Çıktı Formatı", bodySmall) } }; IconButton(onClick = onDismiss) { Icon(Close) } }`
- **Ölçüm:** 320dp'de içerik 260.8dp. İlk Row: ikon 38 + 10 + Column. Column'a 212.8dp kalıyor; başlık 27 karakter × 8dp ≈216dp → 2 satır; alt başlık ≈234dp → 2 satır. İlk Row 260.8dp'yi dolduruyor; kapat ikonu 48dp daha istiyor. `SpaceBetween` boşluğu `(260.8 − 308.8) / 1 = −48dp` → kapat butonu **212.8dp** konumuna, başlığın son 48dp'sinin üstüne biniyor. 360dp'de içerik 298.4dp → butona 38dp kalıyor (48dp'nin altında).
- **Cihaz:** 320dp telefon; 360dp'de kısmi.
- **Şiddet:** **Yüksek**
- **Öneri:** Başlık bloğuna `Modifier.weight(1f)` verin; başlık/alt başlığa `maxLines = 1–2` + `Ellipsis`; kapat ikonunu 48dp sabit tutun.

### C25 — PdfExportDialog'da iki metin alanı 126.4dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:294-312`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(..., label = { Text("Alt Başlık / Konu (Opsiyonel)") }, singleLine = true, modifier = Modifier.weight(1f)) ; OutlinedTextField(..., label = { Text("Öğrenci Adı (Opsiyonel)") }, singleLine = true, modifier = Modifier.weight(1f)) }`
- **Ölçüm:** 320dp'de (260.8 − 8) / 2 = **126.4dp**. "Alt Başlık / Konu (Opsiyonel)" 30 karakter × 6dp ≈180dp + 16dp padding = 196dp → 70dp kırpılıyor. "Öğrenci Adı (Opsiyonel)" ≈132dp + 16 = 148dp → 22dp kırpılıyor. Alanların kimliği okunamıyor.
- **Cihaz:** 320/360dp telefon (360dp'de alan 145.2dp, hâlâ yetersiz).
- **Şiddet:** **Yüksek**
- **Öneri:** <400dp'de alanları tek sütuna indirin; label'ları "Alt Başlık" ve "Öğrenci" olarak kısaltın; `singleLine` korunabilir.

### C26 — PdfExportDialog'da üç aksiyon butonu 76.5/91.8/76.5dp'ye sıkışıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:484-572`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(Modifier.weight(1f)) { Icon(16.dp); Spacer(4.dp); Text("Yazdır", fontSize = 12.5.sp) } ; Button(Modifier.weight(1.2f)) { ... Text("PDF Paylaş", fontSize = 12.5.sp) } ; OutlinedButton(Modifier.weight(1f)) { ... Text("Kaydet", fontSize = 12.5.sp) } }`
- **Ölçüm:** 260.8 − 16 = 244.8dp; ağırlıklar 1 + 1.2 + 1 = 3.2 → **76.5 / 91.8 / 76.5dp**. "PDF Paylaş" 12.5sp'de ≈63dp + ikon 16 + 4 + 2×24 content padding = **131dp** → 91.8dp'ye sığmıyor, 39dp kırpılıyor. "Yazdır"/"Kaydet" ≈45dp + 16 + 4 + 48 = 113dp → 76.5dp'de 36dp kırpılıyor. Üç butonun da metni eksik görünüyor.
- **Cihaz:** 320/360/411dp telefonlar (411dp'de içerik 346.3dp → butonlar 103/124/103dp, "PDF Paylaş" hâlâ 7dp taşıyor).
- **Şiddet:** **Kritik**
- **Öneri:** Butonları 2 satıra bölün (Paylaş tam genişlik, Yazdır/Kaydet yarım) veya `contentPadding = PaddingValues(horizontal = 8.dp)` + `maxLines = 1` + ikonları kaldırın. Metinleri "Yazdır", "Paylaş", "Kaydet" olarak kısaltın.

### C27 — PdfExportDialog klavye açıldığında alt butonları kapatıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:160-176` ve `:220-226`
- **Kod:** `Column(Modifier.fillMaxWidth().padding(20.dp)) { ... Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { ... } ... Row { butonlar } }` — `imePadding()` yok.
- **Ölçüm:** Dialog ayrı bir pencere; activity'nin `adjustResize` ayarı bu pencereyi doğrudan yeniden boyutlandırmıyor. 360×640'ta dialog içeriği ≈600dp; klavye 280dp açıldığında görünür alan ≈320dp kalıyor. Üst başlık ≈100dp + alt butonlar 48dp + ara boşluklar 30dp = 178dp; scroll alanı 142dp'ye düşüyor. Odaklanan alan `bringIntoView` ile kaydırılsa bile **alt butonlar klavyenin arkasında** kalıyor.
- **Cihaz:** 360×640 / 320×480, klavye açık.
- **Şiddet:** **Yüksek**
- **Öneri:** Dialog köküne `Modifier.imePadding()` ekleyin; `DialogProperties(decorFitsSystemWindows = false)` + `safeDrawingPadding()` kullanın. Alternatif: formu `ModalBottomSheet`'e taşıyın.

### C28 — ImageCropperDialog'da yatayda kırpma alanı 114dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/ImageCropperDialog.kt:282-348` ve `:409-545`
- **Kod:** `Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) { Row { kapat 38dp; başlık; 2×38dp döndür }; Spacer(10.dp); BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) { ... }; Spacer(10.dp); oran çipleri; Spacer(8.dp); filtre çipleri; Spacer(12.dp); Row { İptal; Kırp ve Onayla } }`
- **Ölçüm:** 480×320 yatayda içerik yüksekliği 320 − 24 = 296dp. Sabitler: üst çubuk 38 + 10 + oran satırı 28 + 8 + filtre satırı 28 + 12 + butonlar 48 = **172dp**; kırpma alanına **124dp** kalıyor (hesaplanan 114–124dp). A4 bir sayfayı 8 tutamaçla kırpmak için 124dp yükseklik yetersiz; tutamaçlar 32dp yarıçaplı olduğu için alanın %52'sini kaplıyor.
- **Cihaz:** 480×320 yatay; 320×480 portrede 274dp (sorun yok).
- **Şiddet:** **Yüksek**
- **Öneri:** Yatayda oran/filtre şeritlerini tek satırda birleştirin veya `ModalBottomSheet` içine alın; üst çubuğu 48dp'ye indirin; kırpma alanına `heightIn(min = 200.dp)` verin ve gerekirse dışarıyı scroll edin.

### C29 — ImageCropperDialog oran/filtre çipleri 28dp yüksekliğinde
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/ImageCropperDialog.kt:424-452` ve `:472-503`
- **Kod:** `Surface(modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { ... }, ...) { Text(aspect.title, style = labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }`
- **Ölçüm:** `labelSmall` satır yüksekliği 16dp + 2×6dp = **28dp**; `Surface.clickable` minimum boyut uygulamıyor. 48dp'nin 20dp altında. 8 oran + 8 filtre çipi yatay kaydırmada; her hedef 28dp.
- **Cihaz:** Tüm dokunmatik cihazlar.
- **Şiddet:** **Orta**
- **Öneri:** `Modifier.heightIn(min = 48.dp)` + `padding(vertical = 14.dp)`; çipleri `FilterChip` ile değiştirin (Material3 48dp dokunma genişletmesi gelir).

### C30 — ImageCropperDialog "Kırp ve Onayla" butonu 140dp'de taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/ImageCropperDialog.kt:509-545`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(Modifier.weight(1f)) { Text("İptal") } ; Button(Modifier.weight(1f)) { Icon(18.dp); Spacer(6.dp); Text("Kırp ve Onayla") } }`
- **Ölçüm:** 320dp'de içerik 292dp; (292 − 12) / 2 = **140dp** her buton. "Kırp ve Onayla" 14 karakter × 7dp ≈98dp + ikon 18 + 6 + 2×24 content padding = **170dp** → 30dp taşma. `fontScale = 1.3`'te metin 127dp → toplam 199dp, 59dp taşma.
- **Cihaz:** 320dp telefon; 360dp'de 162dp buton, 1.3×'te taşma.
- **Şiddet:** **Yüksek**
- **Öneri:** `contentPadding = PaddingValues(horizontal = 10.dp)` + metni "Kırp" olarak kısaltın; veya butonu tam genişlik yapıp İptal'i metin butonuna çevirin.

### C31 — ImageCropperDialog üst çubuğundaki kapat/döndür butonları 38dp
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/ImageCropperDialog.kt:302-338`
- **Kod:** `IconButton(onClick = onDismiss, modifier = Modifier.background(...).size(38.dp)) { Icon(Close) }` ; döndürme butonları da `Modifier.size(38.dp)`
- **Ölçüm:** Üç hedef de **38×38dp**. `Modifier.size(38.dp)` IconButton'un iç 48dp minimumunu dışarıdan kıstığı için gerçek dokunma hedefi 38dp. 48dp'nin 10dp altında; üst çubukta aralarında 8dp boşluk var, yanlış butona basma riski yüksek.
- **Cihaz:** Tüm dokunmatik cihazlar.
- **Şiddet:** **Orta**
- **Öneri:** `Modifier.size(48.dp)` + görsel daireyi içeride 38dp tutun; veya `minimumInteractiveComponentSize()`'ı `size()`'dan önce kullanın.

### C32 — BatchCropDialog başlığı 320dp'de kapat butonuyla üst üste biniyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/BatchCropDialog.kt:162-202`
- **Kod:** `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, ...) { Row(...) { Surface(Modifier.size(36.dp)) { Icon(ContentCut) }; Column { Text("Çoklu Soru Kırp (Sayfa Bölücü)", titleMedium, Bold); Text("Tek fotoğraftaki soruları otomatik ayıkla", bodySmall) } }; IconButton(onClick = onDismiss) { Icon(Close) } }`
- **Ölçüm:** 320dp'de içerik 288dp. İlk Row: ikon 36 + 8 + Column; Column'a 244dp kalıyor; başlık 30 karakter × 8dp ≈240dp → 2 satır; alt başlık ≈210dp → 1 satır. İlk Row 288dp'yi dolduruyor; kapat ikonu 48dp daha istiyor → `SpaceBetween` boşluğu −48dp, kapat butonu başlığın son 48dp'sinin üstüne biniyor.
- **Cihaz:** 320dp telefon; 360dp'de içerik 328dp → butona 40dp kalıyor.
- **Şiddet:** **Yüksek**
- **Öneri:** Başlık bloğuna `weight(1f)`; başlığa `maxLines = 2` + `Ellipsis`; kapat ikonunu 48dp sabit tutun.

### C33 — BatchCropDialog önizleme alanı yatayda 132dp'ye düşüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/BatchCropDialog.kt:148-160` ve `:251-257`
- **Kod:** `Column(Modifier.fillMaxSize().padding(16.dp)) { üst başlık; Spacer(8.dp); mod çipleri; Spacer(8.dp); Box(Modifier.weight(1f).fillMaxWidth()...) { önizleme }; Spacer(12.dp); Row { "Sayfayı Böl..." } }`
- **Ölçüm:** 480×320 yatayda içerik 288dp. Sabitler: başlık 48 + 8 + çipler 32 + 8 + 12 + buton 48 = **156dp**; önizlemeye **132dp** kalıyor. A4 sayfa 1:1.414 oranında 132dp yükseklikte **93dp** genişliğe düşüyor; kesim çizgileri ve soru sınırları seçilemez.
- **Cihaz:** 480×320 yatay; 320×480 portrede 292dp (sorun yok).
- **Şiddet:** **Yüksek**
- **Öneri:** Yatayda başlığı tek satıra indirin, mod çiplerini `DropdownMenu`'ye alın; önizlemeye `heightIn(min = 200.dp)` verin ve dış Column'u scroll edin.

### C34 — BatchCropDialog alt butonu 1.15× font ölçeğinde taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/BatchCropDialog.kt:374-442`
- **Kod:** `Button(..., modifier = Modifier.fillMaxWidth()) { if (isProcessing) { ... Text("Sorular Ayıklanıyor...") } else { Icon(18.dp); Spacer(8.dp); Text("Sayfayı Böl ve ${selectedMode.count} Soruyu Yükle") } }`
- **Ölçüm:** 320dp'de buton iç genişliği 288dp. Metin 30 karakter × 7dp ≈210dp + ikon 18 + 8 + 2×24 content padding = **284dp** → 1.0×'te 4dp boşlukla sığıyor. `fontScale = 1.15`'te metin 241dp → toplam **315dp**, 27dp taşma; buton metni iki satıra kırılıyor.
- **Cihaz:** 320dp telefon, fontScale ≥1.15.
- **Şiddet:** **Orta**
- **Öneri:** Metni "Sayfayı Böl (${count})" olarak kısaltın; `contentPadding = PaddingValues(horizontal = 12.dp)` + `maxLines = 1` + `Ellipsis`.

### C35 — DrawingCanvasDialog silgisi açık temada koyu iz bırakıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/DrawingCanvasDialog.kt:175-199` ve `:204-208`
- **Kod:** `if (drawPath.isEraser) { drawPath(path = drawPath.path, color = Color(0xFF121212), style = Stroke(...)) }` ; sürükleme sırasında önizleme: `DrawingTool.ERASER -> Color.White.copy(alpha = 0.5f)`
- **Ölçüm:** Dialog arka planı `MaterialTheme.colorScheme.surface` (açık temada ≈`#FFFBFE`). Silgi path'i sabit `#121212` ile çiziliyor; açık temada beyaz zeminde **koyu gri bir çizgi** bırakıyor, yani silmek yerine boyuyor. Koyu temada surface ≈`#1C1B1F` olduğu için `#121212` neredeyse görünmez (doğru davranış). Sürükleme önizlemesi beyaz, bırakınca siyah oluyor → renk sıçraması.
- **Cihaz:** Açık tema kullanan tüm cihazlar.
- **Şiddet:** **Yüksek**
- **Öneri:** Silgiyi path olarak çizmek yerine `BlendMode.Clear` + `saveLayer` ile uygulayın; veya silgi path'lerini ayrı tutup `drawPath` sırasında `blendMode = BlendMode.Clear` kullanın. Alternatif: arka plan rengini `MaterialTheme.colorScheme.surface` olarak geçirin.

### C36 — DrawingCanvasDialog üst çubuğu 320dp'de başlığın yarısını örtüyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/DrawingCanvasDialog.kt:228-284`
- **Kod:** `Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row { Icon(20.dp); Spacer(8.dp); Text(title, titleMedium, Bold) }; Row { IconButton(Undo); IconButton(Delete); IconButton(Close) } }`
- **Ölçüm:** 320dp'de içerik 288dp. Başlık "Soru Üzerinde Çizim & Karalama" 33 karakter × 8dp ≈ **264dp** + ikon 20 + 8 = 292dp → 2 satır. Sağdaki 3 ikon 3×48 = **144dp**. Toplam 436dp; `SpaceBetween` boşluğu `(288 − 436) / 2 = −72dp` → ikon grubu **144dp**'den başlayıp başlığın sağ yarısını örtüyor. 411dp'de içerik 379dp → 57dp örtüşme; 600dp tablette 436 < 568 sorun yok.
- **Cihaz:** 320/360/411dp telefonlar.
- **Şiddet:** **Yüksek**
- **Öneri:** Başlık bloğuna `Modifier.weight(1f)` + `maxLines = 1` + `Ellipsis`; ikonları 40dp'ye düşürün veya "Temizle"yi taşma menüsüne alın. Yatayda başlığı kısaltın ("Çizim").

### C37 — DrawingCanvasDialog alt araç kartı canvas için ayrılan 90dp'yi aşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/DrawingCanvasDialog.kt:116-120` ve `:286-348`
- **Kod:** Canvas kutusu: `Box(Modifier.fillMaxSize().padding(bottom = 90.dp, top = 60.dp))` ; alt kart: `Card(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)) { Column(Modifier.padding(12.dp)) { ToolButton satırı; Spacer(10.dp); renk satırı } }`
- **Ölçüm:** Kart yüksekliği: 12 + araç satırı 34 + 10 + renk satırı 32 + 12 = **100dp**; alt margin 12dp → toplam **112dp**. Canvas yalnızca 90dp rezerve ettiği için kart, canvas'ın alt **22dp**'sini örtüyor. `fontScale = 1.3`'te araç satırı 38dp, kart 128dp → **38dp** örtüşme. Araç butonları 34dp, renk noktaları 26/32dp (48dp altı).
- **Cihaz:** Tüm cihazlar; düşük yoğunluklu/klavyesiz cihazlarda daha belirgin.
- **Şiddet:** **Orta**
- **Öneri:** Rezerve edilen alt boşluğu kart yüksekliğinden hesaplayın (`padding(bottom = 112.dp)` veya `onSizeChanged`); araç butonlarını `heightIn(min = 48.dp)`, renk noktalarını 48dp dokunma alanı içine alın.

### C38 — SimilarQuestionDialog 320dp'de başlığı, 1280dp'de ekranı taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/detay/SimilarQuestionDialog.kt:69-127`
- **Kod:** `Surface(modifier = Modifier.fillMaxSize().padding(16.dp), ...) { Column(Modifier.fillMaxSize().padding(20.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Row { Surface(Modifier.size(40.dp)); Spacer(12.dp); Column { Text("Benzer Soru (AI)"); Text("Aynı kazanımı pekiştirme sorusu") } }; IconButton(onClick = onDismiss) { Icon(Close) } } ... } }`
- **Ölçüm:** 320dp'de içerik 248dp. İlk Row: ikon 40 + 12 + Column 196dp; alt başlık 33 karakter × 6dp ≈198dp → 2 satır. İlk Row 248dp'yi dolduruyor; kapat ikonu 48dp daha istiyor → `SpaceBetween` boşluğu −48dp, kapat butonu alt başlığın son 48dp'sinin üstüne biniyor. 1280dp'de ise Surface 1248dp, içerik **1208dp**; soru/şık metinleri 1200dp'lik tek satırlara yayılıyor.
- **Cihaz:** 320dp telefon (başlık), 840/1280dp tablet (aşırı genişlik).
- **Şiddet:** **Yüksek**
- **Öneri:** `Surface`'a `widthIn(max = 720.dp)` + `fillMaxHeight(0.92f)` verin; başlık bloğuna `weight(1f)`, alt başlığa `maxLines = 1` + `Ellipsis`; kapat ikonunu 48dp sabit tutun.

### C39 — SimilarQuestionDialog tek ekranda 7 KaTeX WebView açıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/detay/SimilarQuestionDialog.kt:206`, `:221-223`, `:279`, `:355`
- **Kod:** Soru: `CombinedMathMarkdownText(text = similarQuestion.questionText, ...)`; şıklar: `optionKeys.forEach { key -> ... CombinedMathMarkdownText(text = optionText, ...) }`; çözüm: `CombinedMathMarkdownText(text = similarQuestion.solution, ...)`
- **Ölçüm:** 1 soru + 5 şık + 1 çözüm = **7 WebView**. Her `KaTeXMathView` kendi `AndroidView`'ını, JS köprüsünü ve `ResizeObserver`'ını kuruyor; WebView başına ≈15–25MB bellek → **105–175MB**. Her biri asenkron `onHeightChange` gönderdiği için dialog açılışında 7 ayrı yükseklik sıçraması oluyor; 320dp'de şıkların boyu 24dp'den 60dp'ye çıkarken scroll konumu kayıyor.
- **Cihaz:** Düşük RAM'li telefonlar (≤4GB), özellikle 320/360dp.
- **Şiddet:** **Yüksek**
- **Öneri:** Şıkları tek `CombinedMathMarkdownText` içinde birleştirin (A–E tek HTML gövdesi) veya şıklarda `SmartMathContent` yerine yerel `RenderFormattedLines` kullanın; toplam WebView sayısını 2'ye (soru + çözüm) indirin.

### C40 — SimilarQuestionDialog yeniden üretimde eski scroll konumunda açılıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/detay/SimilarQuestionDialog.kt:67` ve `:185-188`
- **Kod:** `val scrollState = rememberScrollState()` — dialog seviyesinde, `similarQuestion` anahtarı yok; `Column(Modifier.weight(1f).verticalScroll(scrollState))`
- **Ölçüm:** Kullanıcı çözümü okumak için en alta kaydırıp "Tekrar Dene"ye bastığında yeni soru aynı `scrollState` ile açılıyor; içerik kısa olsa bile `ScrollState.value` eski yüksek değerde kalıyor ve dialog boş görünüyor. Yeni soru üretimi başına **1 kez** yanlış konum.
- **Cihaz:** Tüm cihazlar.
- **Şiddet:** **Orta**
- **Öneri:** `remember(similarQuestion) { ScrollState(0) }` veya `LaunchedEffect(similarQuestion) { scrollState.scrollTo(0) }` kullanın.

### C41 — ZoomableImageDialog alt ipucu nav bar'ın altında kalıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/ZoomableImageDialog.kt:214-239`
- **Kod:** `Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), ...) { Row(...) { Icon(ZoomIn); Text("Çift dokunarak veya kıstırarak yakınlaştırın") } }`
- **Ölçüm:** `navigationBarsPadding()` yok; 3 tuşlu navigasyonda nav bar **48dp**. İpucu ekran altından 32dp yukarıda başlıyor, yani nav bar'ın 0–48dp bandıyla **16dp** örtüşüyor. 320×480'de ipucu yüksekliği 32dp → 32–64dp bandı; nav bar 0–48dp → **32dp** örtüşme.
- **Cihaz:** 3 tuşlu navigasyon kullanan telefonlar.
- **Şiddet:** **Orta**
- **Öneri:** `Modifier.navigationBarsPadding().padding(bottom = 16.dp)` kullanın; `WindowInsets.safeDrawing` ile alt/üst birlikte yönetin.

### C42 — Zoom/pan sınırları görselin gerçek boyutuna göre değil; kapat butonu 40dp
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/ZoomableImageDialog.kt:98-112` ve `app/src/main/java/com/sorudefteri/ui/ortak/FullScreenImageViewer.kt:70-83`, `:143-151`
- **Kod:** `val maxOffsetX = (size.width * (scale - 1f)) / 2f; val maxOffsetY = (size.height * (scale - 1f)) / 2f` ; kapat: `IconButton(..., modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp).clip(CircleShape).background(...).size(40.dp))`
- **Ölçüm:** `ContentScale.Fit` ile görsel ekranın yalnızca bir bölümünü kaplıyor (ör. 16:9 ekranda 4:3 görsel → genişlik %75). `maxOffset` ekran boyutuna göre hesaplandığı için 5× zoom'da görselin dışına **ekran genişliğinin %25'i kadar** boş alana kaydırılabiliyor. Kapat butonu **40×40dp** (48dp'nin 8dp altında); `size(40.dp)` iç minimumu dışarıdan kısıtlıyor.
- **Cihaz:** Tüm cihazlar; özellikle geniş ekran/tablet.
- **Şiddet:** **Orta**
- **Öneri:** Görselin yerleşen boyutunu `onSizeChanged`/`ContentScale.Fit` hesabıyla çıkarıp `maxOffset = (imageSize * scale - containerSize) / 2` ile sınırlayın. Kapat butonunu 48dp yapın.

### C43 — DenemeEkleDialog başlığı ve 4 çipi 232dp içerikte taşıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/istatistik/components/DenemeEkleDialog.kt:87-142`
- **Kod:** `AlertDialog(title = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Yeni Deneme Sınavı", titleLarge, Bold); Surface(...) { Text("Net: $toplamNet", titleMedium, Bold) } } }, text = { ... Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("TYT","AYT","LGS","Diğer").forEach { FilterChip(...) } } ... })`
- **Ölçüm:** AlertDialog içeriği telefonlarda **232dp**. Başlık "Yeni Deneme Sınavı" 22sp'de ≈170dp + "Net: 0.0" rozeti ≈84dp = **254dp** → 22dp taşma; `SpaceBetween` negatif boşlukla rozet başlığın üstüne biniyor. Çipler: TYT ≈56 + AYT ≈56 + LGS ≈56 + Diğer ≈64 + 3×8 = **256dp** > 232dp → son çipe **40dp** kalıyor; "Diğer" metni (≈36dp) + 16dp padding = 52dp istediği için kırpılıyor.
- **Cihaz:** 320/360/411dp telefonların tamamı (AlertDialog 280dp'de sabit).
- **Şiddet:** **Yüksek**
- **Öneri:** Başlıkta `Text`'e `weight(1f)` + `maxLines = 2`; rozeti başlığın altına alın. Çipleri `FlowRow` ile 2 satıra bölün veya `horizontalScroll` kullanın.

### C44 — TeacherShareDialog 320dp yükseklikte scroll'suz ve butonlar klavye altında
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/TeacherShareHelper.kt:548-672`
- **Kod:** `AlertDialog(..., text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Surface { ... Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Senin Cevabın: ..."); Text("Doğru Cevap: ...") } }; Text("Hocana iletmek istediğin not:"); OutlinedTextField(..., minLines = 3, maxLines = 5) } }, ...)`
- **Ölçüm:** İçerik yüksekliği ≈**384–424dp** (başlık 64 + özet 100 + etiket 20 + alan 92 + boşluklar 36 + butonlar 52 + padding 48). 480×320 yatayda dialogun kullanılabilir yüksekliği ≈272dp → **112–152dp** içerik erişilemez; `Column`'da `verticalScroll` yok. Klavye açıldığında 360×640'ta görünür alan ≈360dp; alt butonlar klavyenin arkasında kalıyor. Özet satırındaki iki `Text` `weight` taşımadığı için 1.3× fontta 232dp'lik alanda üst üste biniyor.
- **Cihaz:** 480×320 yatay, 360×640 klavye açık; 1.3× font.
- **Şiddet:** **Yüksek**
- **Öneri:** `text` bloğunu `verticalScroll(rememberScrollState())` + `heightIn(max = 360.dp)` yapın; `Modifier.imePadding()` ekleyin; özet satırındaki Text'lere `weight(1f)` + `maxLines = 2` verin.

## 6. D) Ortak matematik bileşenleri (45–48)

### D45 — `KaTeXMathView` aynı `modifier`'ı iki kez uyguluyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/math/KaTeXMathView.kt:899` ve `:931-933`
- **Kod:** `BoxWithConstraints(modifier = modifier.fillMaxWidth()) { ... Box(modifier = modifier.fillMaxWidth().then(if (contentHeightDp != null) Modifier.heightIn(min = contentHeightDp!!) else Modifier.defaultMinSize(...))) { AndroidView(...) } }`
- **Ölçüm:** `modifier` hem dış `BoxWithConstraints`'a hem iç `Box`'a veriliyor. Bugünkü çağıranlar `Modifier.fillMaxWidth()` geçtiği için görsel etki yok (idempotent); ancak `Modifier.padding(horizontal = 16.dp)` geçen bir çağıran **32dp** padding alır. 320dp'de içerik 288dp yerine 256dp'ye düşer; `height`/`background`/`clickable` gibi modifier'larda da çift uygulama riski var.
- **Cihaz:** Tüm cihazlar (gizli hata; bugün tetiklenmiyor).
- **Şiddet:** **Orta**
- **Öneri:** Dış `BoxWithConstraints`'a `modifier`, iç `Box`'a yalnız `Modifier.fillMaxWidth()` verin.

### D46 — Yükseklik yalnızca büyüyor; genişlik artınca eski yükseklik kalıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/math/KaTeXMathView.kt:969-988`
- **Kod:** `val mevcut = contentHeightDp ?: 0.dp; contentHeightDp = maxOf(mevcut, calculatedDp)`
- **Ölçüm:** `maxOf` sayfa yüklemesi boyunca yalnızca büyümeye izin veriyor; genişlik değişiminde (`remeasureToken`) taban sıfırlanmıyor. 320dp'de 6 satır ≈180dp ölçülen bir paragraf, tablette 840dp'ye dönünce 2 satıra (≈60dp) düşüyor ama `contentHeightDp` **180dp** kalıyor → **120dp** boşluk. Aynı durum split-screen genişliğinde de oluyor.
- **Cihaz:** Tablet döndürme, foldable açma, split-screen.
- **Şiddet:** **Orta**
- **Öneri:** `LaunchedEffect(measuredWidth)` içinde `contentHeightDp = null` yapıp tek ölçümle yeniden hesaplayın; görünür sıçramayı önlemek için eski yüksekliği `heightIn(min)` olarak koruyup yeni ölçümde küçültün.

### D47 — `fitToWidth` 0.4 altında ölçeklemiyor; geniş formül kırpılıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/math/KaTeXMathView.kt:705-721`
- **Kod:** `if (avail > 0 && w > avail) { var ratio = avail / w; if (ratio > 0.4) { m.style.transform = "scale(" + ratio + ")"; } }`
- **Ölçüm:** 320dp'de içerik 224dp; 900px genişliğinde bir formül için `ratio = 224 / 900 = 0.249` → 0.4'ten küçük olduğu için **hiç ölçeklenmiyor**. WebView `NonTouchableWebView` (`onTouchEvent = false`) ve `isHorizontalScrollBarEnabled = false` olduğu için yatay kaydırma da yok; formülün **%75'i** görünmüyor. 2.5× genişlikte (ratio 0.4) sınırda kalıyor.
- **Cihaz:** 320/360dp telefon, uzun integral/kesirli formüller.
- **Şiddet:** **Yüksek**
- **Öneri:** Eşiği 0.2'ye düşürün veya eşik altında `scale(0.4)` uygulayıp yatay kaydırma imkânı verin. En iyisi: `NonTouchableWebView` yerine dokunulabilir WebView + `overflow-x: auto`.

### D48 — `MathMarkdownText` bölüm başına ayrı WebView açıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/ortak/math/MathMarkdownText.kt:121-137` ve `:88-101`
- **Kod:** `sections.forEach { section -> when (section.type) { ... SectionType.FORMULAS -> FormulasCard(...); ... } }` ; her kart içinde `SmartMathContent(...)` → `KaTeXMathView(...)`
- **Ölçüm:** AI çözümü tipik olarak 6 bölüm (Doğru Cevap, Formüller, Adımlar, Hatalar, İpuçları, Genel) üretiyor → **6 WebView**. `CombinedMathMarkdownText` aynı içeriği **1 WebView**'de render eden optimize sürüm olarak mevcut, ancak `AiCozumTab.kt:319` ve `:472` ile `BenzerSorularTab.kt:229/310/368` hâlâ `MathMarkdownText` kullanıyor. 6 × ≈20MB = **120MB**; 6 asenkron yükseklik geri çağrısı scroll sırasında ardışık relayout üretiyor.
- **Cihaz:** Düşük RAM'li telefonlar; AI çözüm sekmesi ve benzer sorular.
- **Şiddet:** **Yüksek**
- **Öneri:** `AiCozumTab` ve `BenzerSorularTab`'daki çağrıları `CombinedMathMarkdownText`'e geçirin; `MathMarkdownText`'i yalnız geri dönüş (fallback) olarak bırakın.

## 7. E) Inset ve klavye davranışı (49–52)

### E49 — Compact dalda üst inset tüketiliyor ama uygulanmıyor; tüm üst çubuklar status bar altında
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/navigation/AppNavHost.kt:207-228`
- **Kod:** `Scaffold(modifier = modifier.fillMaxSize(), bottomBar = { if (isMainNavDestination) BottomNavBar(...) }) { innerPadding -> AppNavGraph(..., modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()).consumeWindowInsets(innerPadding)) }`
- **Ölçüm:** Host Scaffold'un `topBar`'ı yok; `ScaffoldDefaults.contentWindowInsets` = `systemBars` olduğu için `innerPadding.top` = status bar yüksekliği (**24dp** gesture, **48dp** 3 tuşlu/çentik). Kod yalnız `bottom` padding'i uyguluyor, ardından `consumeWindowInsets(innerPadding)` ile **dört tarafı tüketiyor**. Alt ekranlardaki `TopAppBar` (varsayılan `windowInsets = statusBars`) ve `Modifier.statusBarsPadding()` çağrıları tüketilmiş inset gördüğü için **0dp** döndürüyor.
  - `SoruEklemeTopBar` y=0'dan başlıyor; 64dp'lik barın ilk 24–48dp'si status bar simgelerinin altında.
  - `KameraScreen` üst kontrol satırı `padding(vertical = 12.dp)` ile y=12'de; kapat/flaş/kamera/ayar butonlarının üst yarısı status bar altında.
  - `ZoomableImageDialog` ve `FullScreenImageViewer` `decorFitsSystemWindows = false` olduğu için onlar etkilenmiyor; ancak NavHost içindeki tüm ekranlar etkileniyor.
- **Cihaz:** Tüm compact (320/360/411dp) telefonlar; çentikli cihazlarda kamera deliği başlıkla çakışır.
- **Şiddet:** **Kritik**
- **Öneri:** `padding(bottom = ...)` yerine `padding(innerPadding)` uygulayın; veya yalnız alt tarafı tüketin: `consumeWindowInsets(innerPadding.only(WindowInsetsSides.Bottom))`. Alternatif: host Scaffold'a `contentWindowInsets = WindowInsets(0)` verip inset sahipliğini ekranlara bırakın. Her iki durumda da `TopAppBar`'ın kendi `windowInsets`'ını koruyun.

### E50 — Tablet dalında alt nav bar iki kez, klavye açıkken üç kez sayılıyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/navigation/AppNavHost.kt:185-204` ve `app/src/main/java/com/sorudefteri/ui/soruekle/SoruEklemeScreen.kt:151-172`
- **Kod:** Tablet dalında host Scaffold yok; `SoruEklemeScreen` kendi `Scaffold`'unu kuruyor: `Column(Modifier.fillMaxSize().padding(innerPadding).imePadding().navigationBarsPadding()...)`. `innerPadding.bottom` = nav bar 24dp; ayrıca `navigationBarsPadding()` = 24dp.
- **Ölçüm:** Klavye kapalı: 24 + 24 = **48dp** alt boşluk (gerekli 24dp) → 24dp fazla. Klavye açık: `imePadding` 280dp + `navigationBarsPadding` 24dp + `innerPadding.bottom` 24dp = **328dp**; IME nav bar bölgesini zaten kapsadığı için 24dp gereksiz. 600×960 tablette içerik yüksekliği 960 − 88 (top bar) − 48 = 824dp yerine 800dp; klavye açıkken 544dp.
- **Cihaz:** 600/700/840dp tablet ve foldable, özellikle klavye açık.
- **Şiddet:** **Orta**
- **Öneri:** `navigationBarsPadding()`'i kaldırın; `imePadding()` zaten nav bar'ı kapsar. Tek sahiplik için `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` + `Modifier.consumeWindowInsets(innerPadding)` kullanın.

### E51 — Compact dalda alt bar + IME aynı anda 304–360dp rezerve ediyor
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/navigation/AppNavHost.kt:217-228` ve `app/src/main/java/com/sorudefteri/ui/soruekle/SoruEklemeScreen.kt:164-169`
- **Kod:** Host: `.padding(bottom = innerPadding.calculateBottomPadding()).consumeWindowInsets(innerPadding)` ; ekran: `.padding(innerPadding).imePadding().navigationBarsPadding()`
- **Ölçüm:** Ana destinasyonlarda `innerPadding.bottom` = NavigationBar yüksekliği **80dp**; ana olmayan destinasyonlarda nav bar **24dp**. `imePadding()` 280dp daha ekliyor: **304dp** (SoruEkleme) veya **360dp** (Kütüphane/Reels/Test/İstatistik/Ayarlar). 360×640'ta üst bar 64dp düşüldükten sonra görünür içerik: 640 − 64 − 360 = **216dp**. Klavye zaten nav bar'ı kapladığı için alt barın 80dp'si tamamen boşa gidiyor.
- **Cihaz:** 360×640 / 320×480, klavye açık, alt nav bar görünür ekranlar.
- **Şiddet:** **Yüksek**
- **Öneri:** `imePadding()`'i host Scaffold seviyesine taşıyıp `consumeWindowInsets` ile tüketin; veya `WindowInsets.ime.exclude(WindowInsets.navigationBars)` kullanın. Klavye açıkken alt nav bar'ı gizleyin (`WindowInsets.ime.getBottom(density) > 0` kontrolü).

### E52 — Dialog pencerelerinde IME yönetimi yok
- **Dosya:** `app/src/main/java/com/sorudefteri/ui/kutuphane/components/PdfExportDialog.kt:160-176`; `app/src/main/java/com/sorudefteri/ui/istatistik/components/DenemeEkleDialog.kt:87-120`; `app/src/main/java/com/sorudefteri/ui/ortak/TeacherShareHelper.kt:548-630`
- **Kod:** Üç dialogda da kök `Column`/`AlertDialog` üzerinde `imePadding()`, `windowInsetsPadding()` veya `safeDrawingPadding()` yok; yalnız `verticalScroll`/`fillMaxWidth` var.
- **Ölçüm:** `AlertDialog` ve `Dialog` ayrı pencere açar; activity'nin `adjustResize`'ı bu pencerelerin yüksekliğini doğrudan değiştirmez. 360×640'ta klavye 280dp açıldığında görünür alan ≈360dp:
  - `PdfExportDialog` içeriği ≈600dp → alt butonlar klavye altında (C27).
  - `TeacherShareDialog` içeriği ≈424dp → "WhatsApp ile Gönder" klavye altında (C44).
  - `DenemeEkleDialog` içeriği ≈500dp → "Kaydet" klavye altında (C43).
- **Cihaz:** 360×640 / 320×480, klavye açık.
- **Şiddet:** **Yüksek**
- **Öneri:** Dialog içerik köklerine `Modifier.imePadding()` ekleyin; `DialogProperties(decorFitsSystemWindows = false)` + `Modifier.safeDrawingPadding()` kullanın. Uzun formlar için `AlertDialog` yerine `ModalBottomSheet` + `imePadding` tercih edin.

## 8. En kritik 5 sorun

| # | ID | Dosya:satır | Sorun | Ölçülen etki | Şiddet |
|---:|---|---|---|---|---|
| 1 | **E49** | `ui/navigation/AppNavHost.kt:207-228` | Compact dalda `innerPadding.top` uygulanmıyor ama `consumeWindowInsets(innerPadding)` ile tüketiliyor | Tüm ekranlarda üst çubuk status bar'ın **24–48dp** altında başlıyor; `TopAppBar`/`statusBarsPadding()` 0dp dönüyor | Kritik |
| 2 | **A9** | `ui/soruekle/components/GorselSeciciSection.kt:66`; `ui/ortak/DrawingCanvasDialog.kt:77-81` | `onSolutionDrawingSaved` parametresi hiç çağrılmıyor; dialogda `onSave` yok | Kaydetme yolu **0 adet**; çizim kapatınca tamamen kayboluyor (işlev kaybı) | Kritik |
| 3 | **B17** | `ui/kamera/KameraScreen.kt:408-428`, `:958-964` | Kılavuz çerçeve sabit `96dp` üst/alt alanla hesaplanıyor; alt panel 206–222dp | 480×320'de **≈100dp**, 360×640'ta **≈72dp**, 320×480'de **≈90dp** örtüşme; çerçevenin tamamı panel arkasında kalabiliyor | Kritik |
| 4 | **A6** | `ui/soruekle/components/GorselSeciciSection.kt:198-241` | Üç buton `weight(1f)` + varsayılan 2×24dp content padding | 320dp'de her buton **69.3dp**, metne **1.3dp** kalıyor; "Kırp / Döndür" karakter bazında kırılıyor | Kritik |
| 5 | **A12** | `ui/soruekle/components/CevapSeciciSection.kt:39-70` | 5 × `size(52.dp)` + `Arrangement.SpaceBetween` | 320dp'de 260dp içerik 224dp alana sığmıyor; **−9dp** aralık, toplam **36dp** örtüşme → yanlış şık seçimi | Kritik |

> **6. sırada C26:** `PdfExportDialog.kt:484-572` — üç aksiyon butonu 320dp'de **76.5 / 91.8 / 76.5dp**'ye sıkışıyor, "PDF Paylaş" 39dp kırpılıyor. Yeni bir kritik sayılmadığı için listede 6. sırada anıldı.

**Neden bu beşi önce:** E49 tüm uygulamayı etkileyen tek kök neden; A9 ve B17 doğrudan veri/işlev kaybı; A6 ve A12 ise kullanıcıyı yanlış işlem yapmaya yönlendiren dokunma hataları.

## 9. MaxLines eksikleri

Aşağıdaki metinlerde `maxLines`/`overflow` tanımlı olmadığı için satır sayısı içerik uzunluğuna göre sınırsız büyüyor; sabit yükseklikli veya `SpaceBetween` kullanan satırlarda komşu öğelerin üstüne binme ya da kırpılma üretiyor.

| Bulgu | Dosya:satır | Metin | Mevcut durum / ölçüm | Öneri |
|---|---|---|---|---|
| A4 | `ui/soruekle/SoruEklemeScreen.kt:379-383` | `"${currentSubjectName} Müfredat Konuları"` | `maxLines` yok; "T.C. İnkılap Tarihi…" ≈470dp → **3 satır** | `Modifier.weight(1f)`, `maxLines = 2`, `TextOverflow.Ellipsis` |
| A8 | `ui/soruekle/components/GorselSeciciSection.kt:320-324` | `"Çözüm görseli eklendi"` | `maxLines` yok; 224dp satırda metne **28dp** kalıyor → **5–6 satır**, satır yüksekliği ≈100dp | `maxLines = 1`, `Ellipsis`; ikonları 40dp'ye düşür |
| C24 | `ui/kutuphane/components/PdfExportDialog.kt:202-211` | `"Detaylı PDF Çıktısı Oluştur"` + `"📚 ${sorular.size} Soru Seçildi • A4 Çıktı Formatı"` | `maxLines` yok; 320dp'de başlık **2**, alt başlık **2** satır → kapat butonuyla **−48dp** örtüşme | Başlık `maxLines = 2`, alt başlık `maxLines = 1`, ikisi de `Ellipsis`; bloğa `weight(1f)` |
| C32 | `ui/soruekle/components/BatchCropDialog.kt:186-190` | `"Çoklu Soru Kırp (Sayfa Bölücü)"` | `maxLines` yok; 320dp'de **2 satır**, kapat ikonuyla **−48dp** örtüşme | `maxLines = 2`, `Ellipsis`, başlık bloğuna `weight(1f)` |
| C36 | `ui/ortak/DrawingCanvasDialog.kt:245-250` | `"Soru Üzerinde Çizim & Karalama"` | `maxLines` yok; 320dp'de **2 satır** (292dp), 3×48dp ikon grubu başlığın sağ yarısını örtüyor | `maxLines = 1`, `Ellipsis`; yatayda "Çizim" kısaltması |
| C38 | `ui/detay/SimilarQuestionDialog.kt:108-117` | `"Aynı kazanımı pekiştirme sorusu"` | `maxLines` yok; 320dp'de **2 satır** (≈198dp), kapat butonuyla **−48dp** örtüşme | `maxLines = 1`, `Ellipsis`, `weight(1f)` |
| C43 | `ui/istatistik/components/DenemeEkleDialog.kt:95-99` | `"Yeni Deneme Sınavı"` + `"Net: $toplamNet"` rozeti | `maxLines` yok; toplam **254dp** > 232dp içerik → rozet başlığın üstüne biniyor | Başlık `maxLines = 2` + `weight(1f)`; rozeti alt satıra al |
| C44 | `ui/ortak/TeacherShareHelper.kt:597-608` | `"Senin Cevabın: …"` / `"Doğru Cevap: …"` | `maxLines` **ve** `weight` yok; 1.3× fontta 232dp alanda iki metin üst üste biniyor | Her `Text`'e `weight(1f)`, `maxLines = 2`, `Ellipsis` |
| B17/B22 | `ui/kamera/KameraScreen.kt:798-820` | 7 farklı durum metni (`"Düşük donanım — yüksek doğruluk otomatik kapalı"` vb.) | `maxLines` yok; 320dp'de uzun metin **2 satır** → alt panel yüksekliği 16dp oynuyor, çerçeve örtüşmesi büyüyor | `maxLines = 2`, `Ellipsis`; panel üst bandına sabit `heightIn(min = 40.dp)` |

**Toplam:** 9 metin grubu; bunlardan 4'ü (C24, C32, C36, C38) doğrudan başlık/kapat butonu çakışmasına, 2'si (A8, B17) yükseklik oynamasına, 1'i (C43) rozet taşmasına yol açıyor.

## 10. 48dp altı dokunma hedefleri

Material önerisi minimum **48×48dp**. Aşağıdaki hedefler `Modifier.size(...)`, `padding` veya `Surface.clickable` ile bu eşiğin altına sabitlenmiş; Material3'ün varsayılan `minimumInteractiveComponentSize()` genişletmesi ya dışarıdan kısıtlandığı ya da hiç uygulanmadığı için gerçek dokunma alanı tabloda verilen ölçüdür.

| Bulgu | Dosya:satır | Öğe | Ölçülen hedef | Öneri |
|---|---|---|---|---|
| A7 | `ui/soruekle/components/GorselSeciciSection.kt:176-192` | Görsel üzeri "Büyüt" / "Sil" | **36×36dp** | Dış `Box(48.dp).clickable`, iç `Surface(36.dp)` |
| A13 | `ui/soruekle/components/CevapSeciciSection.kt:80-113` | Öğrenci cevabı A–E + "Boş" | **32.3×44dp** (320dp), 39×44dp (360dp) | 3+3 `FlowRow`, `heightIn(min = 48.dp)`, `widthIn(min = 48.dp)` |
| B21 | `ui/kamera/KameraScreen.kt:968-995` | "Tekli Soru" / "Çoklu Soru" mod çipleri | **36dp** yükseklik | `heightIn(min = 48.dp)` + `padding(vertical = 14.dp)` |
| C29 | `ui/soruekle/components/ImageCropperDialog.kt:424-452`, `:472-503` | 8 oran + 8 filtre çipi | **28dp** yükseklik | `FilterChip` (48dp dokunma genişletmesi) veya `heightIn(min = 48.dp)` |
| C31 | `ui/soruekle/components/ImageCropperDialog.kt:302-338` | Kapat / sola döndür / sağa döndür | **38×38dp** | `size(48.dp)` dış kutu, görsel daire 38dp içeride |
| C37 | `ui/ortak/DrawingCanvasDialog.kt:286-348` | Araç butonları (kalem/silgi/renk) | **34dp**; renk noktaları **26dp** | `heightIn(min = 48.dp)`; renk noktalarına 48dp `clickable` kutusu |
| C42 | `ui/ortak/ZoomableImageDialog.kt:214-239`; `ui/ortak/FullScreenImageViewer.kt:143-151` | Kapat butonu | **40×40dp** | `size(48.dp)` |
| A14 ek | `ui/soruekle/components/OcrResultPreviewSection.kt:91-95` | "Metni Temizle" (`IconButton`) | **32×32dp** | `size(48.dp)`; ikon 18dp içeride kalsın |
| — | `ui/soruekle/SoruEklemeScreen.kt:449-452` | Katalog konu satırı | **44dp** yükseklik (20dp metin + 2×12dp padding) | `heightIn(min = 48.dp)` |

**Toplam:** 9 öğe grubu, **17 ayrı hedef** (A13'te 6, C29'da 16 çip aynı kalıptan). En yaygın kök neden, `IconButton`/`Surface` üzerine dışarıdan `size(...)` veya küçük `padding` verilmesi; `minimumInteractiveComponentSize()` yalnız Material bileşenlerinde ve dış kısıt yoksa çalışıyor.

---

**Kapanış:** Bu rapor yalnız statik okuma ve hesaplanmış dp ölçümlerine dayanır; proje dosyalarında hiçbir değişiklik yapılmadı. Ölçümler ±%10 toleransla değerlendirilmeli, kritik maddeler (E49, A9, B17, A6, A12) düzeltildikten sonra 320dp ve 480×320 yatayda yeniden görsel doğrulama yapılmalıdır.
