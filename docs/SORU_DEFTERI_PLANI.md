# 📘 Soru Defterim — YKS Soru Arşivi Uygulaması (Plan Dokümanı)

> Amaç: Öğrencilerin kağıttan soru defterine soru kesip yapıştırması yerine, çözemedikleri soruların fotoğrafını çekip sınıflandırdıkları, istatistiklerini gördükleri ve istediklerinde AI'dan çözüm istedikleri bir Android uygulaması.
> Teknoloji: **Kotlin + Jetpack Compose** (Android-only). Bu doküman yalnızca planlama içindir, kod içermez.

---

## 1. Hedef Kitle ve Temel Kullanıcı Hikayesi

- **Kullanıcı:** YKS'ye hazırlanan öğrenci (Sayısal / EA / Sözel / Dil).
- **Ana akış:**
  1. Öğrenci bir soruyu çözemez.
  2. Uygulamada sağ alttaki **+ (FAB)** butonuna basar.
  3. **Galeri** veya **Kamera** seçeneğinden sorunun fotoğrafını ekler (varsa hocanın/kendisinin çözüm görselini de ekleyebilir).
  4. Soruyu sınıflandırır: **TYT/AYT → Ders → Konu → Hata tipi**, opsiyonel **Cevap Şıkkı (A/B/C/D/E)** ve **Çözüm Görseli** (opsiyonel not ile birlikte).
  5. Soru kütüphanede arşivlenir; gerekirse **AI'dan adım adım çözüm** ister (cevap şıkkı biliniyorsa AI çözümü buna göre doğrular).
  6. İstatistik ekranından hangi dersten kaç yanlış yaptığını takip eder.

---

## 2. Sürüm Kapsamı

### 2.1 V1 (MVP) — Kesin kapsam
| Özellik | Açıklama |
|---|---|
| Soru ekleme | FAB (+) → Alt sayfa: **Galeri** (Photo Picker) veya **Kamera** (CameraX) ile soru görseli çekme/seçme |
| Çözüm görseli | Soru eklerken veya sonradan Soru Detay ekranında opsiyonel **çözüm fotoğrafı** (Kamera/Galeri) ekleyebilme |
| Cevap şıkkı | Sorunun doğru cevabını seçebilme (**A, B, C, D, E** çipleri; opsiyonel) |
| Sınıflandırma | TYT/AYT → Ders → Konu (katalog önerisi + serbest metin) + Hata tipi |
| Kütüphane ekranı | Fotoğraflı soru listesi, **filtreleme** (oturum, ders, hata tipi, çözüldü/çözülmedi), arama, sıralama |
| Soru detay | Büyük soru ve çözüm fotoğrafları (sekmeli/kart görünüm), cevap şıkkı rozeti, not düzenleme, "çözdüm" işaretleme, silme |
| İstatistik | Derse göre soru sayısı, hata tipi dağılımı, çözüldü/çözülmedi, haftalık ekleme trendi |
| AI çözüm | Ayarlardan girilen **API anahtarıyla** (Gemini veya Groq) soru fotoğrafına adım adım çözüm (varsa doğru şık hedeflenerek) |
| Ayarlar | Alan seçimi (Sayısal/EA/Sözel/Dil), AI sağlayıcı + API anahtarı + model seçimi |
| Veri | **Sadece cihazda** (Room DB + uygulama içi görsel deposu) |
| Dil | Tamamen Türkçe arayüz |

### 2.2 V1 dışında bırakılanlar (sonraki sürümler → Bölüm 11)
- OCR ile soru metni okuma (ML Kit) — seçilmedi, gelecekte.
- Kaynak bilgisi (hangi yayın/deneme) — yetenek menüsünde, v1'de "not" alanına yazılabilir.
- Fotoğraf kırpma/döndürme.
- Yedekleme (JSON/PDF dışa aktarma, Drive senkronu).

---

## 3. Ekranlar ve Navigasyon Akışı

```
┌─────────────────────────────────────────────────────────────┐
│  Bottom Bar:  [Kütüphane]  [İstatistik]  [Ayarlar]          │
├─────────────────────────────────────────────────────────────┤
│  KÜTÜPHANE (ana ekran)                                      │
│  • Filtre çipleri: Tümü | TYT | AYT | Ders | Hata tipi      │
│  • Arama kutusu (not/konu içinde)  • Sıralama (tarih veya   │
│    ders) → Soru kartları (küçük foto + etiketler + şık +    │
│    çözüm ikonu + rozet)                                     │
│  • Sağ altta FAB (+)                                        │
│       └─ Alt sayfa (bottom sheet): 📷 Kamera  |  🖼 Galeri  │
│              ├─ Kamera → CameraX ekranı → çek → "Soru       │
│              │   Bilgileri" ekranı                          │
│              └─ Galeri → Photo Picker → seç → "Soru         │
│                  Bilgileri" ekranı                          │
│       "Soru Bilgileri": oturum→ders→konu→hata tipi→         │
│       cevap şıkkı (A-E)→çözüm görseli (opsiyonel)→not→      │
│       kaydet → kütüphaneye dön                              │
│                                                             │
│  SORU DETAY: büyük soru ve çözüm fotoğrafları (Tab/sekmeli  │
│  veya kaydırılabilir kart), cevap şıkkı rozeti, "Çözüm      │
│  Fotoğrafı Ekle/Değiştir" butonu, "çözdüm" butonu,          │
│  "AI Çözüm İste" butonu, not düzenle, sil                   │
│                                                             │
│  İSTATİSTİK: kartlar (toplam/çözüldü/çözülmedi) + bar/      │
│  donut grafikler (ders dağılımı, hata tipi, son 7 gün)      │
│                                                             │
│  AYARLAR: alan seçimi, AI ayarları (sağlayıcı, anahtar,     │
│  model), veri temizleme                                     │
└─────────────────────────────────────────────────────────────┘
```

Navigasyon: **Navigation Compose 2.9.8 + tip güvenli `@Serializable` rotalar**. Alt bar yalnızca Kütüphane/İstatistik/Ayarlar arasında; detay, kamera ve ekleme ekranları üst düzey (full-screen) rotalardır.

---

## 4. Veri Modeli (Room)

```kotlin
@Entity(tableName = "sorular")
data class SoruEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,                  // soru görseli dosya yolu (filesDir/images/soru_...)
    val solutionImagePath: String? = null,  // çözüm görseli dosya yolu (filesDir/images/cozum_...) (opsiyonel)
    val correctAnswer: String? = null,      // cevap anahtarı şıkkı: "A" | "B" | "C" | "D" | "E" (opsiyonel)
    val examType: String,                   // "TYT" | "AYT"
    val subjectId: String,                  // ders (örn. "matematik_ayt")
    val topic: String,                      // konu (serbest metin)
    val mistakeType: String?,               // hata tipi (null = seçilmedi)
    val note: String?,                      // öğrenci notu / çözüm notu
    val isSolved: Boolean = false,          // sonradan çözebildi mi
    val solvedAt: Long?,                    // çözülme tarihi
    val aiExplanation: String?,             // AI çözüm metni (istenirse cache)
    val createdAt: Long,                    // eklenme zamanı (ms)
    val updatedAt: Long
)
```

- **Hata tipi enum'ları:** `İŞLEM_HATASI`, `KONU_EKSİĞİ`, `BİLGİ_EKSİKLİĞİ`, `DİKKAT_HATASI`, `SORUYU_ANLAMAMA`, `DİĞER`
- **Cevap şıkkı seçenekleri:** `A`, `B`, `C`, `D`, `E` (UI'da hızlı seçim çipleri ve temizleme butonu).
- **DAO sorguları:** tümü, filtreli liste (`examType`, `subjectId`, `mistakeType`, `isSolved`), arama (`LIKE`), istatistik için `GROUP BY` toplamları (ders/hata tipi/çözüldü), Flow döner (`Flow<List<Soru>>`) → Compose otomatik güncellenir.

---

## 5. TYT/AYT — Ders Taksonomisi

- **TYT dersleri tüm alanlar için aynıdır** (9 ders): Türkçe, Matematik, Fizik, Kimya, Biyoloji, Tarih, Coğrafya, Felsefe, Din Kültürü.
- **AYT dersleri kullanıcının alanına göre değişir** (Ayarlar → Alan):

| Alan | AYT Dersleri |
|---|---|
| Sayısal | Matematik, Fizik, Kimya, Biyoloji |
| Eşit Ağırlık | Matematik, Türk Dili ve Edebiyatı, Tarih-1, Coğrafya-1 |
| Sözel | Türk Dili ve Edebiyatı, Tarih-1, Coğrafya-1, Tarih-2, Coğrafya-2, Felsefe Grubu, Din Kültürü |
| Dil | YDT İngilizce, YDT Almanca, YDT Fransızca (dil alanı otomatik AYT=YDT varsayılır) |

- **Veri kaynağı:** Bu taksonomi v1'de kod içinde sabit (object `DersTaksonomisi`: `List<Ders>`, `data class Ders(id, ad)`). Alan değişince ayarlardan güncellenir.
- **Konu seçimi:** **Hazır liste + serbest metin birlikte.** Uygulamaya asset olarak paketlenen `konular.json` (TYT'nin 9 dersi + AYT Sayısal dersleri; 13 ders / 404 konu), ders seçildiğinde hazır konu önerisi kaynağıdır; kullanıcı istediğinde serbest metin de girebilir. Öneri listesi = hazır katalog konuları ∪ kullanıcının o ders için daha önce girdiği konular (Room'dan `DISTINCT` çekilir).
- **Katalog kapsam notu:** `konular.json` yalnızca TYT + AYT Sayısal'ı kapsar. AYT'de alan Eşit Ağırlık / Sözel / Dil (YDT) seçiliyse ilgili derslerde hazır liste yoktur → yalnızca serbest metin + kullanıcı geçmişi önerisi sunulur. Kapsam genişletmesi Bölüm 11'de planlıdır.
- **Alan değişikliği politikası:** Taksonomi yalnızca **yeni kayıt** formunu (ders listesi ve konu önerilerini) etkiler; kütüphane/filtre/istatistik DB'deki kayıtlara göre çalışır. Alan değişince eski AYT kayıtları görünür kalır; taksonomide artık yer almayan bir `subjectId` için ders adı çözümlenemezse id üzerinden fallback etiket gösterilir.

---

## 6. AI Çözüm Özelliği (Gemini + Groq)

### 6.1 Tasarım
- **Ayarlar → AI:** Sağlayıcı seçimi (`Gemini` / `Groq`), API anahtarı alanı, model seçimi (sağlayıcıya göre değişen dropdown).
- Kullanıcı anahtarını **Google AI Studio** (Gemini, ücretsiz kademe) veya **Groq** (ücretsiz geliştirici anahtarı) üzerinden kendisi alır, uygulamaya girer.
- Anahtar DataStore'da tutulur (v1'de düz metin; ileride Keystore şifrelemesi — Bölüm 11).

### 6.2 Akış
1. Soru detay ekranında **"AI Çözüm İste"** → anahtar/model yoksa Ayarlar'a yönlendir.
2. ViewModel soru fotoğrafını (küçültülmüş bitmap, base64) + Türkçe prompt'u (ders, konu ve varsa doğru cevap şıkkı bilgisi ile) ilgili sağlayıcıya gönderir.
3. Yanıt metni `aiExplanation` alanına kaydedilir, detayda kaydırılabilir kartta gösterilir. Tekrar istenebilir.

### 6.3 Sağlayıcı soyutlaması
```kotlin
interface CozumProvider {
    suspend fun cozumIste(imageBytes: ByteArray, prompt: String): String
}
```
- **GeminiProvider:** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=…` — vision destekli model (örn. `gemini-2.5-flash`), `contents` içinde `inline_data` (image/png|jpeg).
- **GroqProvider:** `POST https://api.groq.com/openai/v1/chat/completions` — OpenAI uyumlu, görüntü destekli model (örn. `meta-llama/llama-4-scout-17b-16e-instruct`; eski `llama-3.2-*-vision` modelleri Groq'tan emekli edildi, model listesi sürüm güncellemelerinde periyodik olarak doğrulanmalı).
- HTTP: **OkHttp + kotlinx-serialization-json** (iki uç nokta için Retrofit'e gerek yok).
- **Örnek prompt:** *"Bu fotoğraftaki soruyu çöz. Bir YKS sorusudur. {cevapVarIse: "Doğru cevap {correctAnswer} şıkkıdır."} Önce cevabı söyle, sonra adım adım Türkçe ve öğretici şekilde anlat. Konu: {ders}/{konu}."*
- Not: Gemini ücretsiz kademesi RPM/günlük kota sınırlıdır; hata durumunda kullanıcıya anlaşılır Türkçe mesaj (örn. "Kota aşıldı, birazdan tekrar dene").

---

## 7. Görsel Saklama Stratejisi (Scoped Storage)

- Gallery: `ActivityResultContracts.PickVisualMedia` (Photo Picker) — **izin gerektirmez**, eski `READ_MEDIA_IMAGES` kullanılmaz.
- Kamera: CameraX (`camera-camera2` + `camera-lifecycle` + `camera-view`) uygulama içi tam ekran önizleme + `ImageCapture` → JPEG dosya.
- **Kopyalama & Saklama:**
  - Soru görseli: `filesDir/images/soru_{UUID}.jpg`
  - Çözüm görseli (opsiyonel): `filesDir/images/cozum_{UUID}.jpg`
  - Dosya adında DB `id` kullanılmaz (UUID ile tavuk-yumurta problemi çözülür).
  - Bitmap küçültme: max kenar ~1600px, JPEG kalite ~85 (depolama tasarrufu).
- Coil (`AsyncImage`) ile yükleme.
- **Silme:** Soru silindiğinde hem soru görseli (`imagePath`) hem de varsa çözüm görseli (`solutionImagePath`) diskten silinir.
- Bildirim verisi `android:exported="false"`, manifest'te yalnızca `CAMERA` izni (runtime) → dosyalar kurtarılabilir, izinler minimal.

---

## 8. Teknik Mimari

- **Mimari:** MVVM — Unidirectional Data Flow. `ViewModel` (StateFlow) + `Repository` + Room/DataStore.
- **Katmanlar:**
  - `data/local` → Room (entity, dao, database), DataStore (ayarlar)
  - `data/ai` → `CozumProvider` interface + Gemini/Groq implementasyonları
  - `data/images` → görsel kopyalama/küçültme/silme yardımcıları
  - `domain` → model sınıfları, `DersTaksonomisi`
  - `ui/` → tema, navigasyon, ekranlar (kütüphane, ekleme, detay, kamera, istatistik, ayarlar)
  - `di/` → Hilt modülleri (Room, DataStore, OkHttp, provider binding)
- **DI:** Hilt (Dagger 2.60+). **Navigation:** Navigation Compose + `@Serializable` rotalar (kütüphane artık maintenance modunda, compose-first kalır).
- **İstatistik grafikleri:** Başta harici kütüphane yok — basit bar/donut grafikler Compose `Canvas` ile çizilir (bağımlılık azalır). İhtiyaç olursa Vico eklenebilir.

---

## 9. Kütüphane Sürümleri (Haziran-Ağustos 2026 araştırması)

| Bileşen | Sürüm | Not |
|---|---|---|
| Kotlin | 2.4.x (KGP'de Compose compiler gömülü) | KAPT değil **KSP** |
| AGP / Gradle | AGP 9.3.x / Gradle 9.7.x | AGP 9 yeni temel |
| Compose BOM | 2026.08.00 (Compose 1.12) + Material3 1.4.0 | compileSdk 37 gerekir |
| compile/target/minSdk | compile 37 / target 36 / min 23 | Play, yeni uygulamalarda target 36 zorunlu |
| Room | 2.8.x (KSP) | `room-ktx` ayrı eklenmez, kod üretimi varsayılan |
| CameraX | 1.6.x | — |
| Coil | 3.5.x (`io.coil-kt.coil3:coil-compose`) | — |
| Hilt | 2.60+ | KSP |
| Navigation Compose | 2.9.8 | type-safe routes |
| DataStore | 1.2.x | Preferences |
| Photo Picker | `activity` (PickVisualMedia) | izinsiz |
| HTTP/JSON | OkHttp + kotlinx-serialization | en güncel stabil |

> **Not:** Tablodaki sürümler, planlama tarihi (Ağustos 2026) itibarıyla hedeflenen değerlerdir; proje iskeleti kurulurken güncel stabil sürümler teyit edilecektir.

---

## 10. Önerilen Proje Yapısı (v1, tek modül)

```
soru_uyg/
├── app/
│   ├── build.gradle.kts  (libs.versions.toml: sürümler)
│   └── src/main/
│       ├── AndroidManifest.xml  (CAMERA izni)
│       ├── assets/konular.json  (hazır konu kataloğu — öneri kaynağı)
│       └── java/com/sorudefteri/
│           ├── SoruDefteriApplication.kt        (@HiltAndroidApp)
│           ├── data/local/  SoruEntity.kt, SoruDao.kt, SoruDatabase.kt, AyarlarDataStore.kt
│           ├── data/ai/     CozumProvider.kt, GeminiProvider.kt, GroqProvider.kt
│           ├── data/images/ GorselDepo.kt
│           ├── domain/      DersTaksonomisi.kt, KonuKatalog.kt (asset JSON parse + öneri), modeller
│           ├── di/          AppModule.kt
│           └── ui/
│               ├── theme/  (renk, tipografi — Material3, koyu tema dahil)
│               ├── navigation/ AppNavHost.kt
│               ├── ortak/   (filtre çipleri, soru kartı, empty state)
│               ├── kutuphane/  KutuphaneScreen.kt + KutuphaneViewModel.kt
│               ├── soruekle/   SoruEklemeScreen.kt + ViewModel (oturum→ders→konu→hata tipi formu)
│               ├── kamera/     KameraScreen.kt (CameraX)
│               ├── detay/      SoruDetayScreen.kt + ViewModel (çözüm, çözdüm, AI)
│               ├── istatistik/ IstatistikScreen.kt + ViewModel (Canvas grafikler)
│               └── ayarlar/    AyarlarScreen.kt + ViewModel (alan, AI anahtarı, model)
└── docs/SORU_DEFTERI_PLANI.md   (bu dokümanın proje kopyası, onay sonrası)
```

---

## 11. Gelecek Fikirleri (v1 sonrası yapılacaklar listesi)

- **OCR** (ML Kit text-recognition-v2, bundled): fotoğraftan soru metnini çıkar → arama ve filtre güçlenir.
- **Kaynak bilgisi** alanı (yayın/deneme/yıl) + kaynağa göre filtre.
- **Fotoğraf düzenleme:** kırpma (soru alanına odaklanma), 90° döndürme.
- **Yedekleme:** JSON dışa/içe aktarma; sonra Google Drive senkronu (hesap girişi).
- **Uzay tekrar / tekrar hatırlatma:** 7 gün sonra "tekrar zamanı" bildirimi; çözülenlerin takibi.
- **Konu kataloğu kapsam genişletmesi:** AYT Eşit Ağırlık / Sözel (Türk Dili ve Edebiyatı, Tarih-1/2, Coğrafya-1/2, Felsefe Grubu, Din Kültürü) ve YDT (İngilizce/Almanca/Fransızca) konu listelerinin `konular.json`'a eklenmesi.
- **AI yanıt streaming** (token token yazılması) ve hata/kota bilgisi.
- **API anahtarını Keystore/EncryptedSharedPreferences ile şifreleme.**
- Sınav modu ile soruları **deneme çekmecesi** gibi rastgele tekrar etme.
- İstatistiklere yıl bazlı karşılaştırma (2025 TYT'den kaç soru vs 2026).

---

## 12. Doğrulama / Test Planı

1. **Build:** `./gradlew assembleDebug` — sürümler, KSP, Hilt derlemesi temiz.
2. **Manuel akışlar:**
   - FAB → Galeri/Kamera → fotoğraf seç/çek → TYT → Matematik → konu seç veya yaz → hata tipi → **cevap şıkkı seç (örn. C)** → **opsiyonel çözüm görseli ekle** → not yaz → kaydet → listede görünür.
   - Filtreler: TYT/AYT, ders, hata tipi, çözüldü/çözülmedi, arama.
   - Detay: büyük soru ve çözüm görsellerini incele, not düzenle, çözüm görseli ekle/değiştir, "çözdüm" işaretle, sil (her iki görsel de diskten silinir).
   - AI: Soru şıkkı biliniyorsa AI prompt'unda doğru şıkka yönelik çözüm üretimi; anahtarsız uyarı; Gemini ve Groq akışı.
   - Ayarlar: alan Sayısal→Sözel değişince AYT ders listesi değişir.
   - İstatistik: 2-3 soru ekle, sayılar ve grafikler güncellenir.
3. **Birim test:** `DersTaksonomisi` (alan→ders listesi), `KonuKatalog` (asset parse + ders bazlı öneri), `SoruDao` filtre sorguları, görsel küçültme.
4. **Enstrümante (opsiyonel):** Hilt + Compose UI testi temel akışta.

---

## 13. Açık Kararlar (uygulama başlarken netleşecek)

- Uygulama adı: "Soru Defterim" (öneri) — isim/ikon/renk ayrıca belirlenecek.
- Paket adı: `com.sorudefteri` (öneri).

**Kararı verilmiş olanlar (kaldırıldı):**
- Groq görüntü destekli model → belirlendi: `meta-llama/llama-4-scout-17b-16e-instruct` (§6.3).
- Konu girişi → karar: v1'de `konular.json` asset olarak gömülür; hazır öneri + serbest metin birlikte (§5). EA/Sözel/YDT kapsamı sonraki sürümde genişletilir (§11).