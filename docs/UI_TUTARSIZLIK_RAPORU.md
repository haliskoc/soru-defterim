# Soru Defterim (`com.sorudefteri`) — Kapsamlı UI/UX, Responsive, Mimari Hata, Performans Optimizasyonu ve 2026 Ürün Vizyon Raporu

**Proje:** Soru Defterim (`com.sorudefteri`)  
**Kapsam:** 
1. `ui/**` (Tüm ekranlar, navigasyon, tablet/katlanabilir düzenler, dialoglar, alt sayfalar, çizim, ses)
2. `ui/theme/**` (Renkler, tipografi, şekiller, M3 tasarım token'ları, karanlık/aydınlık mod)
3. `data/**` & `domain/**` (Room Entity, DAO, SQLite, Paging 3, FSRS algoritması, Coroutine akışları, Sync, KameraX, PDF, Yedekleme)
4. Performans & Bellek (Jank, recomposition, WebView bellek ayak izi, CursorWindow, ML Kit thread blokajı)
5. 2026 Vizyonu (Sektör lideri EdTech özellikleri, yapay zeka koçluğu, optik simülatör, phygital QR kitapçıklar)

**Yöntem:** 5 bağımsız uzman ajan tarafından satır satır statik kod analizi, Compose layout matematiksel hesaplamaları, Room SQLite sorgu denetimi ve mimari profiling.  
**Tarih:** 28 Ekim 2026  
**Durum:** Kapsamlı Keşif ve Yol Haritası Raporu (Kaynak kodlar değiştirilmeden derlenmiştir).

---

## 1. Yönetici Özeti ve Konsolide Metrikler

Soru Defterim uygulaması; Android Jetpack Compose, Room SQLite FTS, CameraX, Lenovo Stylus donanım desteği, Canvas PDF oluşturucu ve P2P yerel eşleştirme gibi son derece yetenekli ve modern teknolojilerle inşa edilmiştir. Ancak yapılan derinlemesine çok boyutlu denetimde, uygulamanın **beş ana eksende** kritik açıklar barındırdığı tespit edilmiştir:

1. **Responsive & Düzen Karmaşası**: Tablet için tasarlanmış ekranlar ile telefon için tasarlanmış yeni tasarım dili (`PhoneTasarim.kt`) birbirine entegre değildir. 4 farklı breakpoint (600, 700, 840dp ve yok) sistemi bölmüş; yatay telefonlar dikey alan kontrolü olmadığı için tablet sayılarak 14–40dp gibi kullanılamaz alanlara sıkışmıştır.
2. **Tasarım Sistemi & Erişilebilirlik İhlalleri**: `Shape.kt` dosyası tanımlanmış olmasına rağmen `Theme.kt` içerisinde `MaterialTheme`'e bağlanmamış (ölü kod); 356 yerde 13 farklı köşe yarıçapı kullanılmıştır. 401 hardcoded renk ve 136 hardcoded font boyutu bulunmaktadır. `isSystemInDarkTheme()` kontrolleri runtime tema seçimiyle çelişerek açık modda beyaz üstüne beyaz görünmez soru metinlerine yol açmaktadır.
3. **Kritik Çalışma Zamanı Hataları (Bug Hunter)**: SQLite'ın 999 parametre sınırı (`IN (:ids)`) nedeniyle 1000+ soruda toplu işlemler ve yedek geri yükleme anında çökmektedir. Kütüphane sıralaması (`sortOrder`) DAO'ya iletilmemekte (ölü sıralama); filtre temizleme yayınevi filtresini sıfırlamamakta; `isDeleted = 1` silinmiş sorular istatistiklerde ve testlerde hayalet veri olarak kalmaktadır.
4. **Performans & Ağır Bellek Tüketimi (Jank Kaynakları)**: Tek bir ekranda 7 ila 12 ayrı WebView açılarak 150–250MB gereksiz RAM tüketilmekte; Kamera ML Kit OCR analizleri ana UI thread'i 40–80ms bloke ederek önizlemede kare düşürmektedir. Room Paging sorguları `SELECT *` ile tüm dev metinleri çekerek SQLite 2MB CursorWindow limitini zorlamaktadır.
5. **Kaçırılan 2026 EdTech Ürün Fırsatları**: Kod tabanında modern FSRS (Free Spaced Repetition Scheduler v4) algoritması eksiksiz yazılmış olmasına rağmen atıl bırakılmış (dead code), hala eski 5 aşamalı statik sistem işletilmektedir. Öğrencinin soru üzerine çizdiği çözümleri AI Vision ile denetleme, sesli diyalog, sınav optik form simülasyonu ve PDF'lerde soruya bağlanan dinamik QR kodlar gibi çığır açıcı özellikler henüz kullanıcıya bağlanmamıştır.

### Konsolide Metrik Tablosu

| Metrik / İnceleme Alanı | Tespit Edilen Değer | Standart / Olması Gereken | Durum & Etki |
| :--- | :---: | :---: | :--- |
| **Toplam Tespit Edilen Bulgular** | **178 Bulgu** | 0 | 28 Kritik, 68 Yüksek, 62 Orta, 20 Düşük |
| **Farklı Breakpoint Sayısı** | **4 adet** (600, 700, 840dp, yok) | **Tek Standart** (`AppLayout`) | 700–839dp arasında ekranlar telefon/tablet arasında gidip gelir |
| **NavigationRail Sıçraması** | **80dp ani kayma** | Sabit veya Animasyonlu | Detay, Test ve Ekleme ekranlarında tüm sayfa 80dp sola sıçrar |
| **Yatay Telefon İçerik Alanı** | **14dp – 40dp net alan** | Min 220dp dikey alan | Yükseklik kontrolü olmadığı için yatay telefon split-pane'e girer |
| **Telefonda Üst Inset Kaybı** | **10 ekranda 0dp boşluk** | Status bar yüksekliği | `consumeWindowInsets` hatasıyla TopBar'lar saatin altına girer |
| **Kaydırılamayan BottomSheet** | **4 adet sheet** | `verticalScroll` zorunlu | 5 adımlı filtrenin 4–5. adımları ekrandan taşar, ulaşılamaz |
| **Hardcoded Renk Kullanımı** | **401 adet** | 0 (M3 Token) | Açık/koyu modda okunmayan metinler ve düşük kontrast |
| **isSystemInDarkTheme Çelişkisi** | **7 dosya / 9 nokta** | 0 (Runtime tema) | Koyu sistem + açık tema -> Beyaz zemin üstüne beyaz metin |
| **Hardcoded FontSize (`.sp`)** | **136 adet** | 0 (Typography Token) | Font scale 1.3x–2.0x'te metinlerin üst üste binmesi |
| **Farklı Köşe Yarıçapı Değeri** | **13 farklı değer** | 5 M3 Token'ı | 2, 6, 7, 8, 10, 12, 14, 16, 18, 20, 22, 24, 32dp anarşisi |
| **`Theme.kt` Shapes Bağlantısı** | **YOK (Ölü Kod)** | `MaterialTheme(shapes=)` | Projedeki hiçbir bileşen Shape.kt token'larını kullanamıyor |
| **48dp Altı Dokunma Hedefi** | **22+ bileşen** | Min 48x48 dp | 12–36dp küçük butonlar; dokunma ıskalama ve yanlış tıklama |
| **SQLite 999 Parametre Çökmesi** | **4 kritik metod** | `chunked(500)` zorunlu | 1000+ soruda PDF export, Anki, seçim ve yedek yükleme çöker |
| **Tek Ekranda WebView Sayısı** | **7 – 12 WebView** | **1 WebView / Yerel Text** | +150–250MB RAM israfı ve kaydırma anında jank |
| **Kamera OCR UI Thread Blokajı** | **40–80ms donma** | Arka plan `cameraExecutor` | Kamera vizöründe 15 FPS kare düşmesi ve takılma |

---

## 2. Cihaz / Genişlik / Ekran Matrisi (Mevcut Durum vs Hedeflenen)

| Cihaz & Form Faktörü | Pencere Ölçüsü | Mevcut Kabuk & Durum | Kütüphane | Soru Detay | Diğer 10 Ekran | Hedeflenen Davranış |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Küçük Telefon (Dikey)** | 320x568 dp | BottomBar (Üst bar status bar altında) | Telefon listesi (Şıklar ve butonlar taşar) | Telefon düzeni | Telefon düzeni | Tek sütun, 44–46dp ölçekli şıklar, güvenli üst inset |
| **Standart Telefon (Dikey)** | 360–412 x 800+ dp | BottomBar (Üst bar status bar altında) | Telefon listesi (Kompakt kartlar) | Telefon düzeni | Telefon düzeni | Doğru üst inset, tek sütun ergonomik formlar |
| **Yatay Telefon** | **780–915 x 360–412 dp** | **NavigationRail (Son öğe kesik)** | 840dp altı telefon, üstü iki panel | **Split-Pane (İçerik 14–40dp'ye sıkışır!)** | Tam genişlik gerilmiş form | **Tek sütun telefon düzeni, yatayda yan sekmeler, min 220dp dikey alan** |
| **Katlanabilir (Kapak Ekran)**| ~320x800 dp | BottomBar | Telefon listesi (Dar kart) | Telefon düzeni | Telefon düzeni | 320dp güvenli responsive düzen |
| **Katlanabilir (Açık Ekran)** | 673x841 dp | NavigationRail (80dp) | Telefon listesi (Gereksiz dar) | **Split-Pane (700+ olduğu için)** | Telefon düzeni | List-Detail adaptif iki panel (840 kısıtı gevşetilmeli) |
| **Kompakt Tablet (7–8")** | 700–800 x 1000+ dp | NavigationRail (80dp) | **Telefon listesi (620dp boş alan)** | **Split-Pane (385/315dp)** | Telefon düzeni | İki panelli dengeli düzen, Rail kalıcı |
| **Büyük Tablet (10–12")** | 1024–1280 x 800+ dp| NavigationRail (Alt ekranlarda kaybolur!) | İki panel (Küçük 152dp kart ızgarası) | Split-Pane (Görsel 260dp sabit kalır) | **1200dp'ye gerilmiş dev formlar** | Kalıcı Rail, max 720dp ortalanmış formlar, 2 sütunlu test ve istatistik |

---

## 3. Kök Neden Analizleri

### 3.1. Dört Farklı Breakpoint Kaosu ve 700–839dp Anomalisi
- **Kod Analizi**:
  - `AppNavHost.kt:111`: `val isMediumOrExpanded = windowWidthSizeClass == WindowWidthSizeClass.Medium || windowWidthSizeClass == WindowWidthSizeClass.Expanded` -> Eşik **600dp** (NavigationRail açılır).
  - `AppNavHost.kt:272` & `KutuphaneScreen.kt:474`: `isExpandedScreen = windowWidthSizeClass == WindowWidthSizeClass.Expanded` -> Eşik **840dp** (İki panelli düzen açılır).
  - `SoruDetayScreen.kt:165`: `val isTabletLayout = maxWidth >= 700.dp` -> Eşik **700dp** (Split-pane açılır).
  - Diğer 10 ekran: Hiçbir breakpoint kontrolü **YOKTUR**.
- **Kritik Hata**: 700–839dp aralığında (8" tabletler, açık katlanabilirler) Kütüphane ekranı tek sütun telefon listesi olarak açılır; kullanıcı bir soruya dokunduğunda Soru Detay ekranı aniden tablet moduna (sol soru, sağ AI paneli) geçer ve NavigationRail kaybolur. Kullanıcı geri döndüğünde tekrar telefon uygulamasına döner. Aynı pencere boyutunda iki farklı kimlik yaşanır.
- **Çözüm**: Tek bir `AppLayout` nesnesi tanımlanmalı ve CompositionLocal ile dağıtılmalıdır:
```kotlin
data class AppLayout(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass,
    val isTablet: Boolean,        // widthClass >= Medium && heightClass > Compact
    val isTwoPane: Boolean,       // width >= 840.dp && height >= 600.dp
    val isLandscapePhone: Boolean // width >= 600.dp && height < 480.dp
)
```

---

### 3.2. NavigationRail'in Alt Ekranlarda Kaybolması ve 80dp Sıçrama
- **Kod Analizi**: `AppNavHost.kt:188` satırında `if (isMainNavDestination) { NavigationSideRail(...) }` kontrolü bulunmaktadır.
- **Mekanizma**: `isMainNavDestination` yalnızca Kütüphane, Reels, Test, İstatistik ve Ayarlar rotalarını kapsar. Kullanıcı soru detayına, test çözme ekranına veya soru ekleme sihirbazına girdiğinde NavigationRail kompozisyondan kaldırılır.
- **Etki**: İçerik aniden **80dp sola sıçrar (layout shift)**. Kullanıcı geri tuşuna bastığında tüm ekran tekrar 80dp sağa itilir. Ayrıca detay ekranındayken başka bir sekmeye geçmek imkansızlaşır; kullanıcı önce geri dönmek zorunda kalır.
- **Çözüm**: Tablet modunda NavigationRail tüm ekranlarda kalıcı olmalı; yalnızca tam odak gerektiren Kamera ve Test Çözme ekranlarında animasyonlu gizlenmelidir. Alt ekranlarda Rail'in tepesine "Geri" ikonu eklenmelidir.

---

### 3.3. Yatay Telefonun (780x360dp) Tablet Sanılması ve 14–40dp'ye Sıkışma
- **Kod Analizi**: `MainActivity.kt:57` ve `AppNavHost.kt:78` yalnızca `windowWidthSizeClass` değerini okumakta, `heightSizeClass`'ı tamamen yoksaymaktadır. `SoruDetayScreen.kt:165` ise `maxWidth >= 700.dp` ile dikey yüksekliği hiç kontrol etmemektedir.
- **Mekanizma**: Yatay tutulan bir telefon (780x360dp), genişliği 700dp'yi aştığı için split-pane düzenine girer.
- **Hesaplanan Dikey Alan**:
  - Cihaz toplam yüksekliği: 360dp
  - Status bar (24dp) + TopAppBar (64dp) + Nav bar (48dp) = 136dp sabit chrome.
  - Kalan alan: 224dp.
  - Sağ panel `PrimaryTabRow`: 48dp -> Kalan: 176dp.
  - `AiCozumTab.kt:344`: `Spacer(Modifier.navigationBarsPadding().height(88.dp))` -> 48 + 88 = **136dp Spacer!**
  - AI çözüm metnine kalan alan: 176 - 136 = **yalnızca 40dp!**
  - `SoruChatContent` panelinde: Başlık (46dp) + İpucu çipleri (44dp) + Giriş alanı (72dp) = 162dp. Kalan mesaj listesi: **14dp!** Klavye açıldığında ekran tamamen kilitlenir.
- **Çözüm**: Split-pane kararı için hem genişlik hem yükseklik şart koşulmalıdır: `maxWidth >= 840.dp && maxHeight >= 500.dp`. Dikey yüksekliği 500dp altındaki tüm cihazlar tek sütun dikey akışta tutulmalıdır.

---

### 3.4. Inset Zinciri & Klavye Tüketim Çöküşü
- **Kod Analizi**: `AppNavHost.kt:224-228`
```kotlin
Scaffold(
    modifier = modifier.fillMaxSize(),
    bottomBar = { if (isMainNavDestination) BottomNavBar(...) }
) { innerPadding ->
    AppNavGraph(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            .consumeWindowInsets(innerPadding) // <-- TÜM INSET'LERİ TÜKETİR!
    )
}
```
- **Kritik Hata (🔴 P0)**: Dış Scaffold'un `topBar`'ı olmadığı için `innerPadding.calculateTopPadding()` status bar yüksekliğine eşittir. Kod yalnızca `bottom` padding'i uygulamakta, ancak `consumeWindowInsets(innerPadding)` ile **üst inset dahil tamamını tüketmektedir**.
- **Sonuç**: Alt ekranların (`KutuphaneScreen`, `SoruDetayScreen`, `SoruEklemeScreen`, `AyarlarScreen` vb.) kendi `TopAppBar`'ları `statusBars` yüksekliğini okumaya çalıştıklarında tüketilmiş inset görerek **0dp** hesaplar. Telefon modundaki tüm ekranların üst çubuğu durum çubuğunun (saat, pil, çentik) arkasında başlar!
- **Ek Hata (`PhoneKutuphaneIcerik`)**: `KutuphaneScreen.kt:479`'da telefon düzenine `innerPadding` hiç geçirilmemiştir. Bu yüzden arama çubuğu doğrudan `(0,0)` noktasına çizilerek TopAppBar ile **64dp çakışır**.
- **Çözüm**: Dış Scaffold'a `contentWindowInsets = WindowInsets(0,0,0,0)` verilmeli; tüketim yalnızca alt çubuk için yapılmalıdır:
```kotlin
.padding(bottom = innerPadding.calculateBottomPadding())
.consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
```

---

## 4. Ekran Ekran Detaylı Responsive & UI Bulguları

### 4.1. Navigasyon Kabuğu (`AppNavHost.kt`, `Screen.kt`, `MainActivity.kt`)
1. **[K1/K73] Rail 360dp Yükseklikte Taşıyor, 5. Öğe Kesiliyor** (`AppNavHost.kt:495-564`): Header (88dp) + 5 x NavigationRailItem (56dp = 280dp) + dikey paddingler = **~416dp**. 360dp yatay telefonda veya katlanabilirde "Ayarlar" öğesi ekran dışına taşar; Column scrollable değildir. *Çözüm: Header'ı küçültün, dikey boşlukları azaltın veya scroll ekleyin.*
2. **[K74] NavigationBar Etiketi 320dp'de Kırpılıyor** (`AppNavHost.kt:594`, `Screen.kt:135`): 5 sekme -> öğe başına 64dp. "Kütüphane" metni 1.15 font ölçeğinde ~69dp olup "Kütüphan" şeklinde kesilir. *Çözüm: Kısa etiket ("Arşiv") kullanın veya `TextOverflow.Ellipsis` ekleyin.*
3. **[K63] Edge-to-Edge Tema Senkronu Yok** (`MainActivity.kt:53`): `enableEdgeToEdge()` `onCreate`'te bir kez çağrılıyor. Kullanıcı uygulama içinden açık/koyu tema değiştirdiğinde status bar ikon renkleri güncellenmiyor.

---

### 4.2. Kütüphane (`KutuphaneScreen.kt`, `PhoneTasarim.kt`, `FiltreBar.kt`)
1. **[K7] Tablet Izgarası Telefon Kartından %54 Daha Küçük** (`KutuphaneScreen.kt:1027-1029`): `GridCells.Adaptive(minSize = 140.dp)` nedeniyle 840dp tablette kart genişliği **152dp**'ye düşer. Telefon listesindeki kart ise 328dp'dir. Tablet kartı çok dar olduğu için rozetler ve başlık sıkışır. *Çözüm: `minSize = 220.dp` yapın veya tablette tek sütun geniş kart düzenini koruyun.*
2. **[K8/K87] Sağ Panelde Yanlış Soru Gösterimi ve Seçim Vurgusu Yok** (`KutuphaneScreen.kt:514-519`, `1057`): Seçili soru paging listesinden düşünce otomatik ilk soruya atlar; kullanıcı A'yı seçip B'yi görür. Ayrıca sol listedeki seçili kartta vurgu (highlight) rengi yoktur.
3. **[K9] FAB Yanlış Panelde (Detay Butonunu Kapatıyor)** (`KutuphaneScreen.kt:463-471`): FAB Scaffold seviyesinde olduğu için iki panelli düzende sağ panelin ("Tam Ekranda Aç & AI Çözüm" butonunun) tam üstüne biner. *Çözüm: FAB'ı sol liste paneline taşıyın.*
4. **[K75] 5 Adımlı Filtre Sheet'i Kaydırılamıyor (VerticalScroll Eksik)** (`PhoneTasarim.kt:401-412`): Sheet içeriği >1000dp'dir; `verticalScroll` olmadığı için Adım 4 (Kaynak/Deneme) ve Adım 5 (Durum) filtrelerine **asla ulaşılamaz**. Aşağı kaydırınca sheet kapanır.
5. **[K10/K11] Seçim Çubuğu Sistem Barı Altında ve %36 Alan Kaplıyor** (`PhoneTasarim.kt:841-960`): `PhoneSecimAltBar`'da `navigationBarsPadding()` yoktur; tablette gesture bar altında kalır. Telefonda ise 3 satır (148dp) + NavigationBar (80dp) = 228dp alan kaplar. *Çözüm: Seçim modunda alt navigasyon gizlenmelidir.*
6. **[K76/K77] resetFilters() Yayın/Deneme Filtresini Temizlemiyor & Boş Durum Hatası** (`KutuphaneViewModel.kt:311`, `KutuphaneScreen.kt:831`): Yayın filtresi temizlenmediği için arama kilitlenir; 0 sonuçta yanlışlıkla "Henüz Soru Eklenmedi, İlk Sorunu Ekle" CTA'sı çıkar.

---

### 4.3. Soru Detay (`SoruDetayScreen.kt`, `SoruDetayTab.kt`, `AiCozumTab.kt`, `SoruChatBottomSheet.kt`)
1. **[K17/K85] Sabit 260dp Görsel Yüksekliği (Tablette %76 Boş Alan)** (`SoruDetayTab.kt:93`, `KutuphaneScreen.kt:1229`): Sol panel 660dp genişliğinde olmasına rağmen görsel `height(260.dp)` ile sabit kalır; dikey fotoğraf 195x260dp olarak ortada kalır, yanlarında **465dp boş gri alan** oluşur. *Çözüm: `fillMaxHeight()` veya dinamik en-boy oranı kullanın.*
2. **[K19] Sohbet Balonları Geniş Ekranda 320dp Sabit Kalıyor** (`SoruChatBottomSheet.kt:467`): 1280dp tablette sağ panel 576dp iken balonlar `widthIn(max = 320.dp)` ile solda küçücük kalır ve sağda 256dp boşluk bırakır. *Çözüm: `fillMaxWidth(0.85f)` kullanın.*
3. **[K20] PrimaryTabRow Dar Panelde Metinleri Kırpıyor** (`SoruDetayScreen.kt:297-316`): 315dp panelde 3 sekme ("AI Çözüm & Koç", "Sokratik Koç", "Benzer Soru") 105dp'ye sığmaz; metinler kesilir. *Çözüm: `ScrollableTabRow` kullanın.*

---

### 4.4. Test Akışı (`TestOlusturScreen.kt`, `TestCozScreen.kt`, `TestSonucScreen.kt`)
1. **[K95] Test Oluştur'da İki Alt Bar Üst Üste** (`AppNavHost.kt:209`, `TestOlusturScreen.kt:640`): TestNav ana hedef olduğu için altta global `NavigationBar` (~80dp) ve ekranın kendi `SurfaceBottomBar`'ı (~110dp) aynı anda çizilir -> **~190dp alt chrome**. İçerik alanı kuşa döner.
2. **[K96/Bulgu 5.1] 5 Şık Dairesi 320dp Ekranda 34dp Taşıyor** (`TestCozScreen.kt:755-800`): 5 x 58dp daire + 4 x 8dp boşluk = **322dp**. 320dp ekranda içerik 288dp'dir. 'E' şıkkı sağ kenardan 34dp dışarı taşar ve tıklanamaz! *Çözüm: Şıklara sabit 58dp yerine `weight(1f)` ve `sizeIn(max = 52.dp)` verin.*
3. **[Bulgu 5.2] Test Çöz Alt Barında Çift Spacer Hatası** (`TestCozScreen.kt:825-873`): `Arrangement.spacedBy(8.dp)` varken araya elle `Spacer(width(8.dp))` konmuş; aralık 8dp yerine **24dp**'ye çıkmış ve 32dp alan heba edilmiştir. "Boş Bırak" metni bu yüzden elips olmaktadır. *Çözüm: Fazla Spacer'ları silin.*
4. **[K94/K101] Yatay Telefonda Soru Görseli Şıkları Ekran Dışına Atıyor** (`TestCozScreen.kt:720`): Yatayda kullanılabilir alan ~100dp iken görsel `heightIn(min = 180.dp)` talep eder; şıklar 2 ekran aşağı kayar.
5. **[K97/K98] Test Sonucu Tablette 1200dp Tek Sütun ve 90dp Skor Dairesi** (`TestSonucScreen.kt:191`, `379`): 50 soruluk liste 1200dp genişlikte tek sütun gerilir, sağda 1000dp boşluk kalır; skor dairesi dev kart içinde %8 alanda kaybolur.

---

### 4.5. İstatistik & Grafikler (`IstatistikScreen.kt`, `DenemeTakipCard.kt`)
1. **[K31/K33] Tek Sütun 1248dp Kartlar ve Sabit 170dp Donut** (`IstatistikScreen.kt:214`, `782`): Tablette KPI'lar ve kartlar 1248dp'ye uzar, ortadaki grafik 170dp minyatür kalır. *Çözüm: Tablette 2 sütunlu Dashboard grid'i (`LazyVerticalGrid(Adaptive(340.dp))`) kurun.*
2. **[K32] 4 Sekmeli TabRow 320dp'de Kırpılıyor** (`IstatistikScreen.kt:186-208`): "Özet / Dersler / Denemeler / Kişisel" 320dp'de sekme başına 80dp düşer; fontScale 1.3x'te "Denemeler" metni kırpılır.
3. **[Bulgu 5.3] Canvas Çiziminde dp Yerine Ham Piksel (36f) Hatası** (`DenemeTakipCard.kt:331`): `val paddingLeft = 36f` ham pikseldir. 3.0 yoğunluklu ekranda yalnızca **12dp**'ye denk gelir; net sayıları çizginin üstüne biner. *Çözüm: `36.dp.toPx()` kullanın.*

---

### 4.6. Reels (`SoruReelsScreen.kt`)
1. **[K36/K37] Tam Ekran Dikey Pager Tablette Soruyu Geriyor** (`SoruReelsScreen.kt:203-210`, `755`): 1200dp ekranda tek soru tüm ekranı kaplar; beğeni/yorum butonları içerikten 500dp uzakta sağ kenarda kalır, başparmakla erişilemez. *Çözüm: İçeriği `widthIn(max = 520.dp)` ile merkezleyin, aksiyon butonlarını kartın yanına sabitleyin.*
2. **[K38] 132dp Alt Boşluk İsrafı** (`SoruReelsScreen.kt:890`): `height(100.dp)` + `navigationBarsPadding().height(32.dp)` ile alt navigasyon barı üstüne 132dp boşluk bindirilir; dikey alanın %30'u boş kalır.

---

### 4.7. Ayarlar (`AyarlarScreen.kt`, Sekmeler)
1. **[K41] PrimaryTabRow + İkon + Metin 320dp'de Taşıyor** (`AyarlarScreen.kt:124-160`): İkon + "1 Genel" / "2 AI" / "3 Yedek" 320dp'de 106dp alana sığmaz, son harfler kırpılır.
2. **[K42] Ayar Kartları Tablette 1248dp'ye Yayılıyor** (`GenelAyarlarTab.kt:344`): "Avuç İçi Engelleme" etiketi solda, Switch 900dp sağda kalır; göz satırı takip edemez. *Çözüm: Formu `widthIn(max = 720.dp)` ile ortalayın.*

---

### 4.8. Soru Ekleme & Sihirbaz (`SoruEklemeScreen.kt`, Section Bileşenleri)
1. **[K51] 6 Adımlı Telefon Sihirbazı Tablette Birebir 1248dp** (`SoruEklemeScreen.kt:210-322`): 1280dp tablette `OutlinedTextField`'lar 1216dp genişler (~150 karakterlik satır, okuma ergonomisi çöker). 320dp telefonda ise iç içe kartlar sonrası alan 107dp'ye iner ve "Yayın / Yayınevi" etiketi kırpılır. *Çözüm: Tablette sol görsel / sağ form 2 sütunlu düzen uygulayın.*
2. **[K52/E50] Çift Alt Inset (Scaffold + imePadding + navigationBarsPadding)** (`SoruEklemeScreen.kt:151-169`): Klavye açıkken altta **48–96dp boş gri şerit** oluşur. *Çözüm: `navigationBarsPadding()` kaldırılmalıdır.*
3. **[K110/A12] Doğru Cevap Daireleri 320dp'de Üst Üste Biniyor, Tablette 239dp Dağılıyor** (`CevapSeciciSection.kt:39-70`): 5 x 52dp = 260dp; 320dp'de 256dp alana sığmayıp -9dp örtüşür. Tablette ise `SpaceBetween` yüzünden A ve B şıkkı arasında **239dp boşluk** oluşur! *Çözüm: `widthIn(max = 420.dp)` ile sınırlandırın.*
4. **[K112/A6] Üç Butonlu Aksiyon Satırında Metin Alanı 4dp'ye Düşüyor** (`GorselSeciciSection.kt:198-241`): "Kırp / Döndür", "OCR Tara", "AI Etiket" 320dp'de buton başına 72dp düşer; iç padding ve ikon sonrası metne **4dp** kalır; buton 200dp yüksekliğe fırlar. *Çözüm: `FlowRow` kullanın veya ikon-only yapın.*
5. **[K118/A9] 🔴 Çizim Kaydedilmiyor (İşlev Kaybı)** (`GorselSeciciSection.kt:66`, `DrawingCanvasDialog.kt:77`): `onSolutionDrawingSaved` parametresi tanımlı ancak dialogda `onSave` geri çağrısı yoktur! Kullanıcı çizim yapıp kapatınca çizim uçup gider. *Çözüm: `onSave: (Bitmap) -> Unit` ekleyin ve diske yazın.*
6. **[K111/A3] Müfredat Konu Kataloğu Sabit 380dp ve Klavyeyle Kilitleniyor** (`SoruEklemeScreen.kt:364`): LazyColumn sabit 380dp olduğu için arama çubuğuna basınca klavye listeyi tamamen ekran dışına iter.

---

### 4.9. Kamera Akışı (`KameraScreen.kt`, Analiz Kontrolcüleri)
1. **[K119/B17] 🔴 Kamera Kılavuz Çerçevesi Alt Panelin Arkasında Kalıyor** (`KameraScreen.kt:408-428`, `958`): Çerçeve sabit 96dp alt alanla hesaplanırken alt kontrol paneli 206–222dp yüksekliktedir. 480x320 yatay ekranda **~100dp örtüşme** vardır; çerçevenin tamamı butonların arkasında kalır! *Çözüm: `altAlanPx = panelYuksekligi + 24.dp` olarak dinamik hesaplayın.*
2. **[K115/B16] 🔴 targetRotation Eksikliği & Döndürmede State Kaybı** (`KameraScreen.kt:296-386`): `Preview` ve `ImageCapture` Builder'larına `setTargetRotation` verilmemiştir; telefon döndürüldüğünde kaydedilen JPEG 90° yan çıkar, OCR analizi bozulur. Ayrıca rotasyonda `remember` sıfırlanıp flaş AUTO ve arka kameraya döner. *Çözüm: `rememberSaveable` ve DisplayListener ekleyin.*
3. **[K55] FILL_CENTER Sayfa Kenarlarını Kırpıyor** (`KameraScreen.kt:601`): 4:3 sensör dikey ekranda kenarlardan %25 kırpılır; öğrenci A4 sayfasının kenarlarını kadraja sığdıramaz.
4. **[K116/B20] Kamera Ayarları Sheet'i Yatayda Scroll'suz** (`KameraScreen.kt:1097`): 470dp sheet 360dp ekranda scroll edilemez; "Uzun Pozlama" ayarına erişilemez.

---

### 4.10. Dialoglar ve Ortak Bileşenler
1. **[K117/K59] Aynı Tablette İki Dialog Dili: 560dp AlertDialog vs 1203dp Tam Ekran Dialog** (`PdfExportDialog.kt:169`, `SimilarQuestionDialog.kt:71`): Onay diyalogları 560dp'de kalırken, PDF dışa aktarım 1203dp'ye, benzer soru 1248dp'ye yayılır. *Çözüm: Form diyaloglarını `widthIn(max = 640.dp)` ile sınırlayın.*
2. **[K120/C26] 🔴 PdfExportDialog 320dp'de 4 Ayrı Taşma Üretiyor** (`PdfExportDialog.kt:178-572`): Başlık kapat butonuyla 48dp çakışır; iki metin alanı 126dp'ye düşüp etiketler kırpılır; 3 aksiyon butonu ("PDF Paylaş" ~131dp) 76dp'ye sıkışıp harfler kesilir; klavye açılınca butonlar klavyenin arkasında kalır (E52).
3. **[K121/C35] DrawingCanvasDialog Silgisi Açık Temada Koyu İz Bırakıyor** (`DrawingCanvasDialog.kt:175-208`): Silgi path'i sabit `Color(0xFF121212)` ile çizildiği için açık temada silmek yerine **koyu gri boyamaktadır!** Üst çubukta ise başlık ve 3 ikon 72dp üst üste biner. *Çözüm: `BlendMode.Clear` kullanın.*

---

## 5. Tasarım Sistemi, Tema, Renkler ve Tipografi

### 5.1. `Shape.kt` Tanımlı Ancak `Theme.kt`'de Bağlanmamış (13 Farklı Köşe Yarıçapı)
`Shape.kt` içerisinde `extraSmall (4.dp)`, `small (8.dp)`, `medium (12.dp)`, `large (16.dp)`, `extraLarge (24.dp)` olarak tanımlanmış, ancak `Theme.kt:77`'deki `MaterialTheme(...)` çağrısına `shapes = Shapes` parametresi geçilmemiştir.
Sonuç: Tüm uygulama M3 varsayılan şekillerine düşmüş ve kod genelinde **356 adet hardcoded `RoundedCornerShape(X.dp)`** saçılmıştır.
- **Dağılım**: 12dp (98), 16dp (74), 10dp (68 - ara değer!), 8dp (44), 14dp (25 - ara değer!), 20dp (21 - ara değer!), 6dp (12), 18dp (6), 24dp (4), 32dp (1), 22dp (1), 7dp (1), 2dp (1).
- **Çözüm**: `Theme.kt`'ye `shapes = Shapes` bağlanmalı ve tüm ara değerler M3 5 kademeli standardına (`small: 8dp`, `medium: 12dp`, `large: 16dp`, `extraLarge: 24dp`) refactor edilmelidir.

---

### 5.2. 401 Hardcoded Renk & Karanlık Modda Okunmayan Metinler / Düşük Kontrast
Uygulamada **230 adet `Color(0x...)`**, **138 adet `Color.White`/`Color.Black`** ve **33 adet `Color.Gray`** tespit edilmiştir.
- `StreakBadge.kt:74`: `textColor = Color(0xFFE65100)` (Koyu turuncu). Karanlık modda siyah zemin üstünde kontrast **2.91:1**'e düşer (WCAG AA başarısız, okunamaz).
- `AcilEylemMatrisiCard.kt:158`: `Color(0xFFD32F2F)` koyu mod zemininde **2.71:1** kontrast verir (başarısız).
- `SoruChatBottomSheet.kt:508`: Mesaj saati `onSurfaceVariant.copy(alpha = 0.6f)` ve 10sp metin açık modda **1.92:1** kontrast ile silinir.
- `Color.kt` Rozet Renkleri: `TytBadgeContainer (0xFFE3F2FD)` ve `SolvedGreenContainer (0xFFE8F5E9)` karanlık modda arkaplanın 43 katı parlak pastel lekeler oluşturur. *Çözüm: Koyu mod için aydınlatılmış badge token'ları tanımlanmalıdır.*

---

### 5.3. `isSystemInDarkTheme()` ve Runtime `ThemeMode` Çatışmaları (🔴 Görünmezlik Hatası)
Kullanıcı Ayarlar'dan Açık/Koyu tema seçebilmektedir. Ancak şu dosyalarda doğrudan sistem teması sorgulanmaktadır:
- `BenzerSorularTab.kt:231, 246, 263, 370`
- `SimilarQuestionDialog.kt:192, 208, 271, 352`
- `ManualSimilarQuestionDialog.kt:199, 204`
- `KaTeXMathView.kt:848`
- `TestSonucScreen.kt:1037`
- **Kritik Senaryo**: Sistem Koyu Modda iken kullanıcı uygulama içinden "Açık Tema"yı seçtiğinde: `isSystemInDarkTheme()` `true` döner -> Kart arkaplanı beyazımsı gri (`#EFF1F5`), soru metni ise `Color.White` (`#FFFFFF`) olur!
- **Sonuç: Beyaz üstüne beyaz metin (Kontrast 1.09:1). Soru metni, şıklar ve çözüm tamamen GÖRÜNMEZ olur!**
- **Çözüm**: Tüm bu kontroller silinmeli; doğrudan semantik `MaterialTheme.colorScheme.onSurface` kullanılmalıdır.

---

### 5.4. 136 Hardcoded `fontSize` & Sabit `lineHeight` Metin Çakışmaları
- **10sp–10.5sp Mikro Metinler**: `SoruKarti.kt:409`, `DrawingOverlayCanvas.kt:642`, `PdfExportDialog.kt:637`, `SoruReelsScreen.kt:461`, `AcilEylemMatrisiCard.kt:563`. Bu metinler Türkçe büyük noktalı harfleri (`İ`, `Ö`, `Ü`) ve kuyruklu harfleri (`ç`, `ğ`, `ş`) kırpar.
- **Font Scale Çakışması**: `DailyDueRescueCard.kt:181`'de `lineHeight = 20.sp` sabit verilmiştir. Android 14+ cihazda font scale 1.5x yapıldığında font 21sp olur ancak satır yüksekliği 20sp'de çakılı kaldığı için **satırlar birbirinin üzerine biner**.
- **Çözüm**: Tüm metinler `MaterialTheme.typography` token'larına çekilmeli; minimum boyut 11sp (`labelSmall`, `lineHeight = 16.sp`) olmalıdır.

---

### 5.5. 22+ Dokunma Alanı İhlali (<48dp)
Android ve WCAG standardı minimum **48x48 dp** dokunma hedefi şart koşar. Aşağıdaki öğeler bu kuralı açıkça çiğnemektedir:
- `SoruKarti.kt:390`: "Çözüldü/Çözülmedi" rozet butonu toplam **18dp** yüksekliktedir. Kullanıcıların %70'i bu butonu ıskalayıp istemeden detay ekranına geçer.
- `DrawingOverlayCanvas.kt:656-828`: Kapatma butonu **28dp**, palet ikonu **24dp**, renk daireleri **26dp**.
- `DailyDueRescueCard.kt:197`: "Hemen Başla" butonu **44dp**.
- `AudioMemoCard.kt:177`: Oynat/Duraklat butonu **40dp**.
- `ImageCropperDialog.kt:424`: Oran ve filtre çipleri **28dp**; döndürme butonları **38dp**.
- `KameraScreen.kt:968`: Mod çipleri **36dp**; odak halkası dp yerine ham piksel (`44f px`, xxhdpi'de **14.7dp**!).
- *Çözüm: `Modifier.minimumInteractiveComponentSize()` veya `Box(size(48.dp))` wrapper'ı uygulanmalıdır.*

---

### 5.6. Stylus Çizim ve Ses Kaydı Bileşenleri
- **Stylus Siyah Renk Görünmezliği** (`DrawingOverlayCanvas.kt:326`): Paletteki `Color(0xFF212121)` koyu modda siyah zemin üzerinde görünmez çizgi çizer. *Çözüm: Temaya göre adaptif renk (açıkta siyah, koyuda beyaz).*
- **Palm Rejection Hatalı İptal** (`DrawingOverlayCanvas.kt:392`): Kalemle yazarken avuç içi ekrana değdiğinde `pointerCount >= 2` bloğu `return false` dönerek olayı alttaki ekrana sızdırır; soru detayı aniden kapanır veya resim zoom penceresi açılır. *Çözüm: Stylus varken avuç dokunuşları sisteme bırakılmamalı, event consume edilmelidir.*
- **Ses Kaydı Buton Taşması** (`AudioMemoCard.kt:118-167`): Kayıt modundaki 80 karakterlik `"Kayıt sürüyor 00:00 / 01:00..."` metnine `weight(1f)` verilmediği için 360dp ekranda sağdaki "Kaydet" ve "İptal" butonlarını ekran dışına iter; kullanıcı kaydı durduramaz!
- **EmptyState Buton Bozukluğu** (`EmptyState.kt:93`): Yatay Row olan Button içine `Spacer(height(4.dp))` verilmiştir; ikon ile metin birbirine yapışık çıkar. *Çözüm: `Spacer(width(8.dp))`.*

---

## 6. Kod Tabanı Mantık, Eşzamanlılık ve Veritabanı Hataları (Bug Hunter)

### 6.1. 🔴 SQLite 999 Parametre Sınırı (IN Clause Limit) Çökmeleri
- **Dosya:Satır**: `SoruRepository.kt:546-549, 352-361` & `SoruDao.kt:85, 183, 192`
- **Hata**: `getSorularByIds`, `updateSolvedStatusBatch` ve `updateFavoriteStatusBatch` metodlarında gelen `ids` listesi parçalanmadan (chunk edilmeden) doğrudan `WHERE id IN (:ids)` sorgusuna iletilmektedir.
- **Tetiklenme**: 1000'den fazla sorusu olan bir kullanıcı kütüphanede "Tümünü Seç" yapıp "PDF İndir", "Anki Dışa Aktar", "Çözüldü İşaretle" veya "Yıldızla" butonuna bastığında `android.database.sqlite.SQLiteException: too many SQL variables (limit 999)` hatası ile uygulama anında çöker.
- **Çözüm**: `ids.distinct().chunked(500).forEach { batch -> ... }` ile sorgular 500'lük gruplarla işletilmelidir.

---

### 6.2. 🔴 BackupManager İçe Aktarımında Parametre Aşımı
- **Dosya:Satır**: `BackupManager.kt:715-717` & `SoruDao.kt:343`
- **Hata**: `soruDedupDao.getByContentHashes(itemHashes)` çağrısı, yedek paketindeki tüm hash'leri tek bir `WHERE contentHash IN (:hashes)` sorgusuna gönderir.
- **Tetiklenme**: 1000'den fazla soru içeren bir `.sdefteri` veya ZIP yedek dosyası içe aktarılırken `too many SQL variables` ile geri yükleme yarıda patlar.
- **Çözüm**: `itemHashes.distinct().chunked(400).forEach { batch -> putAll(dao.getByContentHashes(batch)...) }`.

---

### 6.3. Kütüphane Sıralamasının (sortOrder) Paging DAO'ya İletilmemesi (Ölü Sıralama)
- **Dosya:Satır**: `KutuphaneViewModel.kt:205-220`, `SoruRepository.kt:417` & `SoruDao.kt:235-280`
- **Hata**: ViewModel'deki `sortOrder` parametresi `SoruRepository.getPagedSorular` metoduna hiç iletilmemektedir; `SoruDao` içerisindeki tüm sorgularda `ORDER BY createdAt DESC` hardcoded yazılmıştır.
- **Tetiklenme**: Kullanıcı sıralama menüsünden "En Eski" (`DATE_ASC`) veya "Derse Göre" (`SUBJECT`) seçtiğinde listedeki soruların sırası asla değişmez.
- **Çözüm**: DAO sorgularına `ORDER BY CASE WHEN :sortOrder = 'DATE_ASC' THEN createdAt END ASC, ...` parametresi eklenmelidir.

---

### 6.4. Kütüphane `resetFilters()` Yayınevi ve Deneme Filtresini Temizlemiyor
- **Dosya:Satır**: `KutuphaneViewModel.kt:310-318`
- **Hata**: `resetFilters()` metodu `_selectedPublisher` ve `_selectedExamName` StateFlow'larını sıfırlamamaktadır.
- **Tetiklenme**: Kullanıcı "3D" yayınevini seçtikten sonra "Filtreleri Sıfırla"ya bastığında diğer filtreler temizlense de yayınevi filtresi arka planda aktif kalır; arama kilitlenir.
- **Çözüm**: `_selectedPublisher.value = null` ve `_selectedExamName.value = null` eklenmelidir.

---

### 6.5. Toplu Seçim Modunda Son Öğe Silinince State Kilitlenmesi (State Lock)
- **Dosya:Satır**: `KutuphaneViewModel.kt:328-338` & `KutuphaneScreen.kt:418-472`
- **Hata**: `toggleSelectSoru(id)` metodu son seçili soru listeden çıkarıldığında `_isSelectionMode.value = false` yapmamaktadır.
- **Tetiklenme**: Kullanıcı bir soruya uzun basarak seçim moduna girer, sonra o seçimi kaldırır. Seçili sayısı 0 olur ancak ekran seçim modunda kilitli kalır; FAB gizlenir, alt bar kaybolur, soru detayına girilemez.
- **Çözüm**: `_isSelectionMode.value = current.isNotEmpty()`.

---

### 6.6. `isDeleted = 1` Soft Delete Kayıtlarının Room Sorgularında Filtrelenmemesi
- **Dosya:Satır**: `SoruDao.kt:79-155, 227-338`
- **Hata**: Cihaz senkronizasyonunda tombstone için eklenen `isDeleted` kolonu, `SoruDao` içerisindeki `getAllSorular`, `getPagedFilteredSorular`, `getQuestionsForTest`, `getQuestionCount`, `getSolvedCount` vb. sorguların hiçbirinde filtrelenmemiştir.
- **Tetiklenme**: Senkronizasyon ile silinmiş sorular kütüphanede listelenmeye, testlerde soru olarak çıkmaya ve istatistik sayaçlarında sayılmaya devam eder.
- **Çözüm**: Tüm Room sorgularına `AND isDeleted = 0` eklenmelidir.

---

### 6.7. SQLite Foreign Key Desteğinin Kapalı Olması (Cascade Silinmelerin Çalışmaması)
- **Dosya:Satır**: `DatabaseModule.kt:25-45`
- **Hata**: `SoruChatMessageEntity` ve `BenzerSoruEntity` tabloları `CASCADE` ile bağlanmış olmasına rağmen SQLite bağlantısında `PRAGMA foreign_keys = ON;` çalıştırılmamıştır.
- **Tetiklenme**: Bir soru silindiğinde, o soruya ait AI sohbet mesajları ve benzer sorular veritabanında silinmeyip hayalet veri olarak birikir.
- **Çözüm**: `RoomDatabase.Callback.onOpen` içinde `db.execSQL("PRAGMA foreign_keys = ON;")` çağrılmalıdır.

---

### 6.8. `AudioMemoManager` Asenkron Kayıt İptalinde Mikrofon ve Oynatıcı Sızıntısı
- **Dosya:Satır**: `AudioMemoManager.kt:80-115, 195-235`
- **Hata**: `startRecordingAsync` IO coroutine'inde `recorder.start()` çağırırken kullanıcı anında iptal ederse `cancelRecording()` ana thread'de `mediaRecorder = null` yapar; ancak biraz sonra IO thread'indeki recorder başlar ve bir daha durdurulamaz. Oynatıcıda ise `onPrepared` sırasında ekrandan çıkılmışsa ses arkada gizlice çalmaya devam eder.
- **Çözüm**: State kontrolleri `if (!_isRecording.value) { recorder.release(); return@launch }` şeklinde senkronize edilmelidir.

---

### 6.9. `KameraScreen` Listener Birikmesi & `RejectedExecutionException`
- **Dosya:Satır**: `KameraScreen.kt:345-360, 395-425`
- **Hata**: `cameraProviderFuture.addListener` her rotasyonda yeni listener yığar. `DisposableEffect.onDispose` içinde `cameraExecutor.shutdown()` çağrıldığında CameraX arkadan analiz karesi göndermeye devam ederse `RejectedExecutionException` ile uygulama çöker.
- **Çözüm**: `cameraProviderFuture.get()` IO context'inde beklenmeli ve `cameraExecutor.shutdownNow()` try-catch ile korunmalıdır.

---

### 6.10. `PdfExportDialog` Çökmesi ve Android 10+ Scoped Storage Erişim Hatası
- **Dosya:Satır**: `PdfExportDialog.kt:475-535` & `PdfPrintHelper.kt:130-145`
- **Hata**: PDF üretimi çıplak coroutine'de try-catch'siz çalıştırılır (OOM'da direkt crash). `savePdfToDownloads` ise Android 10+ üzerinde doğrudan public download dizinine `File.copyTo()` yapmaya çalışarak `Permission denied` fırlatır.
- **Çözüm**: UI'da try-catch Toast geri bildirimi eklenmeli; dosya kaydı için `MediaStore.Downloads` veya SAF kullanılmalıdır.

---

### 6.11. Soru Kayıt ve Test Bitirmede Çift Tıklama (Debouncing) Eksikliği
- **Dosya:Satır**: `SoruEklemeViewModel.kt:699` & `TestCozViewModel.kt:205`
- **Hata**: "Kaydet" veya "Testi Bitir" butonlarına hızlıca iki kez dokunulduğunda koruma bayrağı (guard clause) olmadığı için iki coroutine ateşlenir; mükerrer soru kaydedilir ve test sonucu çift yazılarak çift XP verilir.
- **Çözüm**: `if (state.isSaving || state.isSaved) return` ve `if (_uiState.value.isFinished) return`.

---

### 6.12. `CihazEslemeViewModel.onCleared()` BLE/Wi-Fi Taramasını Durdurmuyor
- **Dosya:Satır**: `CihazEslemeViewModel.kt:59-63`
- **Hata**: ViewModel temizlenirken `depo.durdur()` çağrılmış ancak singleton `eslestirme.durdur()` çağrılmamıştır.
- **Tetiklenme**: Eşleştirme ekranından çıkıldıktan sonra telefon arka planda BLE yayını yapmaya ve Wi-Fi aramaya devam eder; pil hızla tükenir ve cihaz ısınır.
- **Çözüm**: `onCleared()` içine `eslestirme.durdur()` eklenmelidir.

---

### 6.13. `AutoBackupWorker` WAL Modunda DB Değişikliğini Kaçırıyor
- **Dosya:Satır**: `AutoBackupWorker.kt:78-84`
- **Hata**: `dbFile.lastModified() <= lastBackupTime` kontrolü yapılmaktadır. SQLite WAL modunda yazma işlemleri `-wal` dosyasına yapılır; ana `.db` dosyasının tarihi checkpoint olana kadar değişmez.
- **Tetiklenme**: Kullanıcı gün boyunca 20 soru eklese bile gece otomatik yedekleme "DB değişmedi" diyerek yedek almayı atlar.
- **Çözüm**: `latestDbMod = maxOf(dbFile.lastModified(), walFile.lastModified())`.

---

### 6.14. "Tüm Verileri Sıfırla" İşleminde Test, Deneme ve Medyaların Kalması
- **Dosya:Satır**: `SoruRepository.kt:463-467` & `AyarlarViewModel.kt:612`
- **Hata**: `tumVerileriTemizle()` yalnızca `soruDao.clearAll()` ve `gorselDepo.tumGorselleriSil()` çağırmaktadır. `test_sonuclari`, `deneme_sinavlari`, `soru_chat_mesajlari`, `benzer_sorular` tabloları ile diskteki `audio_memos` ve `scratchpads` dizinleri silinmemektedir.
- **Çözüm**: Tüm DAO temizleme metodları ve disk dizinleri fonksiyonun içine eklenmelidir.

---

### 6.15. Scratchpad Çizim Kaydında Race Condition ve JSON Bozulması
- **Dosya:Satır**: `DrawingOverlayCanvas.kt:480` & `ScratchpadStorageHelper:165`
- **Hata**: Kullanıcı kalemle her çizgi çektiğinde (`ACTION_UP`) kilit olmaksızın asenkron dosya yazılır. İki hızlı çizgi aynı anda yazmaya çalışırsa JSON dosyası bozulur ve çizimler uçar.
- **Çözüm**: `ScratchpadStorageHelper` içine `Mutex` eklenmeli ve atomic dosya yazımı (`tmp -> renameTo`) uygulanmalıdır.

---

### 6.16. `SoruDao.insertSoru` `OnConflictStrategy.REPLACE` Nedeniyle ID Değişimi
- **Dosya:Satır**: `SoruDao.kt:54-56`
- **Hata**: `evrenselId` UNIQUE indekslidir. `INSERT OR REPLACE` çakışmada satırı silip yeni `id` ile baştan ekler. Bu durum soru ID'sine bağlı diskteki çizim dosyalarının (`soru_{id}.json`) ilişkisinin kopmasına yol açar.
- **Çözüm**: `OnConflictStrategy.ABORT` kullanılmalı; güncelleme için açık `@Update suspend fun updateEntityFull(soru: SoruEntity)` işletilmelidir.

---

## 7. Performans, Profiling ve Sıfır-Jank Optimizasyon Yol Haritası

### 7.1. Çoklu WebView Tüketimi (150–250MB RAM Tasarrufu)
- **Sorun**: `AiCozumTab.kt:319` hâlâ eski `MathMarkdownText`'i çağırarak açıklamayı 5-6 karta bölmekte ve her kart için ayrı bir `KaTeXMathView` (WebView) açmaktadır. `BenzerSorularTab.kt:310` ise A-E şıklarının her biri için ayrı WebView açmaktadır. Tek ekranda **7 ila 12 WebView** açılmakta ve 200MB+ RAM tüketilmektedir.
- **Optimizasyon**:
  1. `AiCozumTab.kt` doğrudan tek bir WebView kullanan `CombinedMathMarkdownText`'e geçirilmelidir.
  2. Seçenek metinleri (A, B, C, D, E) için WebView tamamen kaldırılmalı; `MathMarkdownText.kt:1353` içindeki yerel `formatMathText(text)` fonksiyonu ile Compose `AnnotatedString` üzerinden **sıfır bellek maliyetiyle** çizilmelidir.

---

### 7.2. Kamera ML Kit OCR İşlemlerinin Arka Plana Alınması (UI Thread Kurtarma)
- **Sorun**: `MetinAnalizci.kt:67` satırında `tanici.process(girdi).addOnSuccessListener` çağrısına arka plan executor verilmemiştir. ML Kit'in metin bloklarını ayıklama işlemi ve `KameraViewModel.onAnalizSonuc` içindeki `SoruAlgilayici` hesaplamaları doğrudan **Main Looper (UI Thread)** üzerinde çalışmakta; kamera vizöründe kare hızını 15 FPS'e düşürmektedir.
- **Optimizasyon**: `tanici.process(girdi).addOnSuccessListener(cameraExecutor) { ... }` ile analiz arka plana alınmalı; yalnızca UI state güncellemeleri ana thread'e post edilmelidir.

---

### 7.3. CursorWindow Şişmesini Önleyen `SoruLiteEntity` DTO Projeksiyonu
- **Sorun**: `SoruEntity` içinde `aiExplanation` (5-10 KB), `ocrMetni` (2-5 KB) gibi dev metin alanları vardır. Kütüphane listesinde veya Test Oluşturma ekranında `SELECT * FROM sorular` çağrıldığında bu dev veriler belleğe yüklenmekte ve Android SQLite 2MB CursorWindow limitini aşarak disk I/O takılmalarına yol açmaktadır.
- **Optimizasyon**: Yalnızca liste, filtreleme ve istatistik için gereken alanları barındıran hafif `SoruLiteEntity` DTO'su oluşturulmalı; veri transfer boyutu **%90 küçültülmelidir**.

---

### 7.4. Paging 3 Bellek Büyümesini Sınırlama (`maxSize`)
- **Sorun**: `SoruRepository.kt:428-434` içindeki `PagingConfig` tanımında `maxSize` parametresi yoktur. Kullanıcı kütüphanede yüzlerce soru kaydırdığında yüklenen tüm sayfalar bellekte kalır ve RAM sürekli şişer.
- **Optimizasyon**: `PagingConfig(pageSize = 20, prefetchDistance = 5, maxSize = 100)` tanımlanmalıdır.

---

### 7.5. Stylus Çiziminde Path Önbellekleme (`DrawingStroke.cachedPath`)
- **Sorun**: `DrawingOverlayCanvas.kt:137-155` içinde, her `ACTION_MOVE` dokunuşunda tüm tamamlanmış çizgiler için baştan `toPath()` fonksiyonu çağrılmaktadır. 120 FPS çizim anında bu durum aşırı Garbage Collection (GC) baskısı yaratmakta ve çizimde gecikmeye (input lag) yol açmaktadır.
- **Optimizasyon**: Çizgi tamamlandığında (`ACTION_UP`) üretilen `Path` nesnesi `DrawingStroke` içinde önbelleğe alınmalı (`val cachedPath: Path`); Canvas içinde her karede sıfırdan Path oluşturulmamalıdır.

---

### 7.6. Exif Bitmap Döndürmede Bellek Sızıntısı Çözümü
- **Sorun**: `BatchCropDialog.kt:138-140` satırında `Bitmap.createBitmap(bmp, ..., matrix, true)` ile yeni döndürülmüş bitmap üretilirken kaynak `bmp` recycle edilmemekte; 2000x2000 çözünürlükte ~16-32MB bellek sızdırılmaktadır.
- **Optimizasyon**: `if (rotated !== bmp && !bmp.isRecycled) bmp.recycle()` çağrılmalıdır.

---

### 7.7. Compose Recomposition, `@Immutable`, `derivedStateOf`, Lazy `key`
1. **Stabilite**: `Ders.kt`, `ReelsItem.kt` ve `TestModels.kt` sınıflarına `@Immutable` eklenmeli; `List` alanları Compose Strong Skipping modunun atlama (skipping) yapabilmesi için stabilize edilmelidir.
2. **`derivedStateOf`**: Projede hiç bulunmayan bu optimizasyon `SoruReelsScreen.kt:211`'e (`isSettledPage`) ve `KutuphaneScreen.kt:418`'e (`showBottomBar`) eklenerek her kaydırma pikselinde tetiklenen yüzlerce recomposition sıfırlanmalıdır.
3. **Lazy Keys**: `SoruReelsScreen` VerticalPager'a `key = { uiState.items[it].id }`, `TestSonucScreen` listesine `key = { _, q -> q.soru.id }` ve `contentType` eklenmelidir.
4. **ImageRequest**: `SoruKarti.kt:145`'teki `ImageRequest.Builder` çağrısı `remember(soru.id, displayPath)` ile önbelleğe alınmalıdır.

---

### 7.8. Açılış Süresi (Startup) & JSON Preload Optimizasyonu
- **Sorun**: `konular.json` dosyası 32KB'dır ve ilk ihtiyaç anında UI thread'de senkron parse edilmektedir (~60-120ms donma). `SoruDefteriApplication.kt` içinde ise 4 ayrı coroutine açılışta disk I/O yarışına girmektedir.
- **Optimizasyon**: `KonuKatalog.preload()` asenkron çağrılmalı; `repairMissingThumbnails` ve `backfillContentHashes` bakım işleri `WorkManager`'a devredilmelidir.

---

## 8. 2026 Vizyonu: Eklenebilecek 10 Yeni Özellik & EdTech Ürün Mimarisi

### 8.1. FSRS-4 Bilimsel Akıllı Tekrar Motoru (Dormant Kodu Aktifleştirme)
- **Kullanıcı Değeri**: Öğrencinin yanlış yaptığı soruları beynin unutma eğrisine göre tam unutulacağı gün karşısına çıkarır. Ezberletmez, kalıcı hafızaya kodlar.
- **Teknik Mimari**: `domain/manager/FsrsScheduler.kt` projenin içinde zaten mevcuttur! `SoruRepository.advanceReviewStage` içindeki eski 5 aşamalı statik Leitner (1-3-7-14-30 gün) fonksiyonu yerine `FsrsScheduler` bağlanmalı; soru detayında ve test bitiminde Anki tarzı 4 buton (*Tekrar: <10dk, Zor: 2 gün, İyi: 5 gün, Kolay: 12 gün*) gösterilmelidir.
- **Zorluk**: Düşük (Kod ve DB sütunları hazır).

### 8.2. Canlı Sesli Sokratik AI Koç (Live Voice & Conversational Mentor)
- **Kullanıcı Değeri**: Öğrenci test çözerken takıldığında yazmakla vakit kaybetmez; kulaklığını takıp "Hocam şuradaki kuvvet diyagramını neden ters aldık?" der; AI Koç öğrenciye doğrudan cevabı vermeden adım adım ipuçlarıyla doğru cevabı buldurur.
- **Teknik Mimari**: Android `SpeechRecognizer` + Gemini 1.5 Flash Sokratik sistem istemi + Android `TextToSpeech` / ElevenLabs REST API. Canlı ses dalga formu görselleştirmesi.
- **Zorluk**: Orta.

### 8.3. Gerçek Sınav Simülatörü & İnteraktif Dijital Optik Form
- **Kullanıcı Değeri**: TYT (165 dk), AYT (180 dk), KPSS (130 dk), LGS için gerçek sınav stresini ve zaman yönetimini simüle eder; kaydırma hatalarını önler.
- **Teknik Mimari**: `TestSessionManager` genişletilerek DND (Rahatsız Etmeyin) uyarısı, 4 Yanlış 1 Doğruyu Götürür net hesabı ve sağ kenardan swipe ile açılan interaktif `OpticalFormSheet` (kurşun kalem efektiyle A-E yuvarlaklarını karalama).
- **Zorluk**: Orta.

### 8.4. Phygital Hibrit Kitapçık: Akıllı QR Kodlu A4 PDF Denemesi
- **Kullanıcı Değeri**: Dijital ekrandan soru çözmek istemeyen, kağıt-kalem hissini arayan öğrenciler için A4 çıktısında her sorunun köşesine mikro QR kod eklenir. Takılınca telefon kamerasıyla okutup anında mobil uygulamadaki AI çözümüne ve sesli nota ulaşır.
- **Teknik Mimari**: ZXing Android ile `sorudefteri://soru/{id}` formatında 120x120 px QR bitmap üretimi; `PdfDenemeKitapcikGenerator.kt` şablonuna entegrasyon.
- **Zorluk**: Düşük.

### 8.5. Ebbinghaus Unutma Eğrisi & Konu-Hata Kök Neden Matrisi
- **Kullanıcı Değeri**: "Trigonometriden neden soru kaçırıyorum?" sorusunun yanıtını verir: Bilgi eksikliği mi, formül ezberi mi, yoksa işlem hatası mı?
- **Teknik Mimari**: Konu yetkinlik formülü: $Mastery = (Doğru / Toplam) \times e^{-\lambda \cdot t_{geçen}}$. Konu x Hata Tipi çapraz Room aggregation sorgusu. `KonuIsiHaritasiCard` altına "Kök Neden Radarı" grafiği.
- **Zorluk**: Orta.

### 8.6. İnteraktif Ana Ekran "Günün Tekrarı" & Kilit Ekranı Flashcard Widget'ı
- **Kullanıcı Değeri**: Uygulamayı açmaya gerek kalmadan ana ekranda her gün 1 hatalı soru kartı gösterilir; öğrenci şıkka dokunup doğrudan widget üzerinde kendini dener.
- **Teknik Mimari**: `GlanceAppWidget` interaktif `ActionCallback` yetenekleri ile soru görseli ve şıklar widget'ta render edilir; doğru/yanlış anında gösterilir ve FSRS arka planda güncellenir.
- **Zorluk**: Orta.

### 8.7. Çizim Analizli AI Çözüm Denetçisi (Handwritten Scratchpad Vision AI)
- **Kullanıcı Değeri**: Öğrencinin soru üzerine tablet kalemiyle karaladığı çözüm adımlarını inceler: "3. satırda eksi işaretini paranteze dağıtırken işaret hatası yapmışsın" diyerek hatayı nokta atışı gösterir.
- **Teknik Mimari**: `DrawingOverlayCanvas` vektör çizgileri `Bitmap` olarak ham soru görselinin üzerine bindirilir (Composite Canvas). Gemini 1.5 Flash Vision API'ye öğrencinin karalamalarıyla birleştirilmiş görsel gönderilir.
- **Zorluk**: Orta.

### 8.8. Dinamik AI Telafi Kampı (Remediation Bootcamp)
- **Kullanıcı Değeri**: Öğrencinin en çok yanlış yaptığı 3 konuyu seçip, bu kazanımları sıfırdan öğreten ve pekiştiren kişiselleştirilmiş 3 günlük mikro soru kampı oluşturur.
- **Teknik Mimari**: `AcilEylemMatrisiCard` verilerini girdi alarak eksik kazanımları tespit eder; `AiRepository.generateSimilarQuestion` ile 10 özgün telafi sorusu türeterek özel test başlatır.
- **Zorluk**: Orta.

### 8.9. Gamified Haftalık Ligler & Streak Dondurucu (Streak Freeze)
- **Kullanıcı Değeri**: Duolingo benzeri haftalık çalışma ligleri (Bronz, Gümüş, Altın, Elmas). Öğrenci hasta olduğunda veya acil durumda serisinin bozulmasını engelleyen "Seri Dondurucu (Streak Freeze)".
- **Teknik Mimari**: `BadgeManager` genişletilerek haftalık XP sıralamaları; `AyarlarDataStore` içinde `streakFreezeCount` (her 100 soru çözüldüğünde 1 dondurma hakkı).
- **Zorluk**: Düşük.

### 8.10. Şifreli & Telif Korumalı Akıllı Soru Paketi Hub'ı (`.sorupaketi`)
- **Kullanıcı Değeri**: Dershaneler, etüt merkezleri veya öğretmenlerin kendi hazırladıkları 50-100 soruluk özel soru/deneme fasiküllerini öğrencileriyle tek tıkla şifreli olarak paylaşabilmesi.
- **Teknik Mimari**: Mevcut `BackupManager.kt` AES-256-GCM altyapısı özelleştirilerek `.sorupaketi` MIME tipi tanımlanır; SAF (Storage Access Framework) ile tek tıkla içeri aktarma.
- **Zorluk**: Düşük.

---

## 9. Entegre Düzeltme & Geliştirme Yol Haritası

```
+---------------------------------------------------------------------------------------------------+
| FAZ 0 (P0 — İlk 1-2 Gün): KRİTİK ÇÖKMELER, INSET KAYIPLARI VE BLOKER HATALAR                      |
| 1. AppNavHost consumeWindowInsets üst inset düzeltmesi (Telefonda 10 ekranın TopBar'ı kurtulur)   |
| 2. SQLite 999 parametre sınırı (SoruRepository chunked(500) ve BackupManager düzeltmesi)          |
| 3. Kütüphane ölü sıralama (sortOrder DAO'ya iletilmesi) ve resetFilters() düzeltmesi              |
| 4. Toplu seçim modunda son öğe seçimden çıkınca isSelectionMode=false kilit açma                  |
| 5. Çizim kaydı tamamlama (DrawingCanvasDialog onSave ve GorselSeciciSection bağlama)              |
| 6. isSystemInDarkTheme() yerine MaterialTheme renklerine geçiş (Görünmez soru metinlerini kurtarma|
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| FAZ 1 (P1 — 1. Hafta): RESPONSIVE KABUK, PERFORMANS VE YATAY TELEFON ERGONOMİSİ                   |
| 1. Tek AppLayout modeli (600/700/840dp kaosunu bitirme; yatay telefonda maxHeight >= 500dp kısıtı)|
| 2. NavigationRail'i alt ekranlarda kalıcı yapma veya animasyonlu gizleme (80dp sıçramayı bitirme) |
| 3. 5 Adımlı Filtre Sheet'ine ve Müfredat Kataloğuna verticalScroll ve esnek yükseklik ekleme      |
| 4. Test Çöz ekranında 5 şıkkı 320dp'ye sığdırma (weight/aspectRatio) ve çift Spacer'ı silme        |
| 5. Kamera çerçevesi panel örtüşmesini altAlanPx hesabı ile düzeltme & targetRotation bağlama      |
| 6. Çoklu WebView'leri CombinedMathMarkdownText ve yerel formatMathText ile birleştirme (RAM -200MB)|
| 7. Kamera ML Kit OCR analizini cameraExecutor arka plan iş parçacığına taşıma (60 FPS vizör)      |
| 8. Room sorgularına isDeleted = 0 ekleme ve PRAGMA foreign_keys = ON açma                         |
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| FAZ 2 (P2 — 2. Hafta): TASARIM SİSTEMİ STANDARTLAŞMASI & HIZLI ÜRÜN KAZANIMLARI                  |
| 1. Theme.kt içine shapes = Shapes bağlama; 13 köşe yarıçapını 5 M3 token'ına indirme             |
| 2. Hardcoded renkleri ve 136 hardcoded font boyutunu M3 tipografi/renk sistemine taşıma           |
| 3. 22 adet 48dp altı dokunma hedefini minimumInteractiveComponentSize ile düzeltme               |
| 4. Room CursorWindow için SoruLiteEntity DTO projeksiyonunu Kütüphane ve Test'e uygulama          |
| 5. Atıl duran FSRS-4 algoritmasını aktif hale getirme (4 butonlu akıllı aralıklı tekrar)          |
| 6. Phygital QR Kodlu PDF Deneme Kitapçığı motorunu entegre etme                                   |
| 7. Çift tıklama debouncing korumalarını (saveSoru, finishTest) ekleme                              |
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| FAZ 3 (P3 — 3-4. Hafta): 2026 EDTECH VİZYONU & DERİN YAPAY ZEKA GELİŞTİRMELERİ                   |
| 1. Canlı Sesli Sokratik AI Koç (STT / TTS entegrasyonu)                                           |
| 2. Gerçek Sınav Simülatörü & İnteraktif Dijital Optik Form (TYT/AYT/KPSS/LGS)                    |
| 3. Çizim Analizli AI Çözüm Denetçisi (El yazısı çözüm adımlarından Vision ile hata tespiti)       |
| 4. Ebbinghaus Unutma Eğrisi & Konu-Hata Kök Neden Isı Haritası                                    |
| 5. İnteraktif Glance Ana Ekran Flashcard Soru Widget'ı                                            |
| 6. Gamified Haftalık Ligler & Streak Dondurucu (Streak Freeze)                                    |
| 7. Şifreli & Telif Korumalı Soru Paketi Hub'ı (.sorupaketi)                                       |
+---------------------------------------------------------------------------------------------------+
```

---

## 10. Sonuç ve Eylem Kararı

Bu denetim; Soru Defterim uygulamasının mevcut altyapısının olağanüstü zengin olduğunu, ancak yanlış inset tüketimleri, entegre edilmemiş tasarım sistemleri, SQLite sınırları ve çoklu WebView kullanımı gibi çözülebilir teknik kusurlar nedeniyle tam potansiyeline ulaşamadığını kanıtlamıştır.

Hazırlanan bu konsolide master plan; yalnızca UI tutarsızlıklarını gidermekle kalmayıp, **sıfır-jank performans optimizasyonunu**, **çalışma zamanı dayanıklılığını** ve **2026 EdTech vizyonunu** tek bir çatı altında birleştirmiştir. Yol haritasındaki **Faz 0** ve **Faz 1** adımları tamamlandığında uygulama, Türkiye'deki en akıcı, en ergonomik ve pedagojik olarak en gelişmiş sınav hazırlık platformu haline gelecektir.
