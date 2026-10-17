# 🚀 Soru Defterim — Kapsamlı Mimari Tasarım, Özellik Yol Haritası ve Master Geliştirme Planı

**Belge Sürümü:** 1.2.0  
**Tarih:** 6 Eylül 2026  
**Hedef Platform:** Android 14+ / 15 (Kotlin, Jetpack Compose, Material 3, Room, Hilt, Coroutines, WorkManager)  
**Özel Donanım Hedefi:** **Lenovo Yoga Tab (TB-710FU)** & **Lenovo Dokunmatik Kalemi (Lenovo Tab Pen Pro / Precision Pen - Kapasitif Çift Tıklama Sensörlü)** + Evrensel Android Stylus  
**Vizyon:** 'Soru Defterim' uygulamasını Türkiye'nin ve dünyanın 1 numaralı yapay zeka destekli, pedagojik aralıklı tekrara (Spaced Repetition) dayalı, tablet ve donanım kalemiyle (Stylus) kusursuz çalışan **YKS / Sınav Hazırlık Ekosistemi** haline getirmek.

---

## 📌 İçindekiler
1. [Yönetici Özeti & Mimari Durum Röntgeni](#1-yönetici-özeti--mimari-durum-röntgeni)
2. [Faz 1: Güvenlik Sıkılaştırma, Depolama Kurtarma & Çekirdek Pedagojik Bağlantılar (P0)](#faz-1-güvenlik-sıkılaştırma-depolama-kurtarma--çekirdek-pedagojik-bağlantılar-p0)
3. [Faz 2: ÖSYM Tipi PDF Deneme Kitapçığı & UI/UX Donanım Temelleri (P0)](#faz-2-ösym-tipi-pdf-deneme-kitapçığı--uiux-donanım-temelleri-p0)
4. [Faz 3: Sokratik Yapay Zeka, Chatbot & Lenovo Yoga Tab (TB-710FU) Dokunmatik Kalem Stüdyosu (P1)](#faz-3-sokratik-yapay-zeka-chatbot--lenovo-yoga-tab-tb-710fu-dokunmatik-kalem-stüdyosu-p1)
5. [Faz 4: Oyunlaştırma (Gamification), Zaman Baskısı & İleri Düzey Analitik (P1)](#faz-4-oyunlaştırma-gamification-zaman-baskısı--ileri-düzey-analitik-p1)
6. [Faz 5: Performans Optimizasyonu, Bulut Senkronizasyonu, Sosyal Ekosistem & Tam Doğrulama (P2)](#faz-5-performans-optimizasyonu-bulut-senkronizasyonu-sosyal-ekosistem--tam-doğrulama-p2)
7. [Master Görev ve Uygulama Takip Listesi (Checklist)](#master-görev-ve-uygulama-takip-listesi-checklist)

---

## 1. Yönetici Özeti & Mimari Durum Röntgeni

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 SORU DEFTERİM — GENEL MİMARİ MATRİSİ                                    │
├──────────────────────────┬────────────────────────────────────────┬──────────────────────────────────────┤
│ Alan / Disiplin          │ Mevcut Durum & Kritik Eksiklikler      │ Uygulanacak Nihai Çözüm              │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 1. Sistem & Güvenlik     │ • Release modda MITM zafiyeti (user CA)│ • Sertifika sıkılaştırma & pinning   │
│                          │ • 5 tam ZIP yedeği cihazda (~4 GB yer) │ • Tek yedek rotasyonu (3 GB tasarruf)│
│                          │ • Room destructive migration açık      │ • Güvenli migration testleri         │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 2. Eğitim Pedagojisi     │ • Test bitince Leitner aşaması artmıyor│ • Otomatik Kademe Atlama/Sıfırlama   │
│                          │ • Sorular rastgele (shuffled)          │ • Günün Tekrar Paketi (Due Queue)    │
│                          │ • Sıfır oyunlaştırma (Streak/XP = 0)   │ • Streak 🔥, XP, Rozetler, Net Lift  │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 3. Analitik & İstatistik │ • test_sonuclari tablosu okunmuyor     │ • Test geçmişini istatistiğe bağlama │
│                          │ • Konu bazlı ısı haritası yok          │ • Konu Isı Haritası (Heatmap)        │
│                          │ • Soru başı hız takibi kopuk           │ • Bilişsel Hız Radarı & YKS Tahmini  │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 4. Donanım & Tablet      │ • Basmalı buton yok, dokunmatik alan var│• Lenovo Yoga Tab TB-710FU Stüdyosu  │
│ (Lenovo TB-710FU & Stylus) • Palm Rejection ve basınç yok          │ • Kapasitif Çift Tıklama (Double-Tap)│
│                          │ • Tablette split-screen yok (260dp)    │ • 8192 Basınç, Eğim, Kalem Titreşimi │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 5. Yapay Zeka & OCR      │ • Statik çözüm (soru sorulamıyor)      │ • Çok turlu Sokratik AI Chatbot      │
│                          │ • Doğrudan cevap (anlama illüzyonu)    │ • 4 Kademeli Koç Modu (Hint Ladder)  │
│                          │ • BYOK kırılganlığı & fallback yok     │ • SmartAiRouter (Gemini->Groq->NIM)  │
├──────────────────────────┼────────────────────────────────────────┼──────────────────────────────────────┤
│ 6. PDF & Sosyal Ekosistem│ • Projede %0 PDF altyapısı             │ • ÖSYM Tipi 2 Sütunlu A4 PDF Deneme  │
│                          │ • Kütüphanede toplu PDF oluşturma yok  │ • Çoklu seçimden fasikül basma       │
│                          │ • Öğretmene soru kartı şablonu yok     │ • "Hocam Bakar Mısınız?" WhatsApp    │
└──────────────────────────┴────────────────────────────────────────┴──────────────────────────────────────┘
```

---

## FAZ 1: Güvenlik Sıkılaştırma, Depolama Kurtarma & Çekirdek Pedagojik Bağlantılar (P0)

> **Amaç:** Kullanıcı verilerini sızıntıdan ve silinmekten korumak, depolama şişmesini anında durdurmak ve aralıklı tekrar algoritmasını çalışır hale getirmek.

### 1.1. Ağ ve Veritabanı Güvenliği Sıkılaştırma
* **Hedef Dosyalar:** `app/src/main/res/xml/network_security_config.xml`, `app/src/main/java/com/sorudefteri/di/DatabaseModule.kt`
* **Yapılacak İşlemler:**
  1. `network_security_config.xml` dosyasından release bloğundaki `<certificates src="user" />` kaldırıldı (yalnızca `<debug-overrides>` altına sınırlandı).
  2. `DatabaseModule.kt` içerisindeki `.fallbackToDestructiveMigration()` kaldırıldı. Olası migrasyon eksikliklerinde öğrenci sorularının sessizce silinmesi engellendi.

### 1.2. Depolama Şişmesini Durdurma & Yedekleme Rotasyonu
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/backup/AutoBackupWorker.kt`, `app/src/main/java/com/sorudefteri/data/images/GorselDepo.kt`
* **Yapılacak İşlemler:**
  1. `AutoBackupWorker.kt` içindeki `MAX_SAVED_BACKUPS` değeri 5'ten **1**'e indirildi. Yeni bir otomatik yedek oluşturulduğunda eski yedeğin anında silinmesi sağlandı (tek adımda 2.5 - 3 GB dahili hafıza kurtarılacaktır).
  2. Soru ekleme ekranında (`SoruEklemeViewModel.kt`) kullanıcı görsel seçtikten sonra kaydetmeden çıkarsa kaydedilen geçici dosya temizlenecek (`onCleared` override edilecek).

### 1.3. Aralıklı Tekrar (Leitner) Motorunu Test Çözmeye Bağlama
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/test/TestSonucViewModel.kt`, `app/src/main/java/com/sorudefteri/ui/test/TestCozViewModel.kt`, `app/src/main/java/com/sorudefteri/domain/repository/SoruRepository.kt`, `SoruDao.kt`
* **Yapılacak İşlemler:**
  1. `SoruDao.kt` içerisine `regressReviewStageAtomik` ve `regressReviewStageBatch` sorguları eklendi.
  2. `SoruRepository.kt` içerisine `regressReviewStage(soruId: Long)` ve `regressReviewStageBatch(ids: List<Long>)` metotları eklenir:
     - Yanlış yapılan soruyu `reviewStage = 0` seviyesine çeker ve `nextReviewDate = now + 1 gün` olarak randevu verir.
  3. `TestSonucViewModel.kt` sınıfında test tamamlandığında:
     - **Doğru yapılan sorular için:** Otomatik olarak `advanceReviewStage(soruId)` çağrılır (1 gün -> 3 gün -> 7 gün -> 14 gün -> 30 gün / Usta).
     - **Yanlış yapılan sorular için:** Otomatik olarak `regressReviewStage(soruId)` çağrılır.
  4. `TestOlusturViewModel.kt` içindeki tamamen rastgele soru çekme mantığı (`shuffled()`) güncellenir: `isDueForReview == true` olan sorulara seçim önceliği verilir.

### 1.4. Test Sonuçlarını İstatistik Ekranına Bağlama
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/istatistik/IstatistikViewModel.kt`, `app/src/main/java/com/sorudefteri/ui/istatistik/IstatistikScreen.kt`
* **Yapılacak İşlemler:**
  1. `TestSonucDao.getAllTestResults()` fonksiyonu `SoruRepository` üzerinden `IstatistikViewModel`'e akıtılır (`Flow<List<TestSonucEntity>>`).
  2. `IstatistikScreen`'e son çözülen testlerin özet kartı, ortalama test neti ve başarı trendi eklenir.

---

## FAZ 2: ÖSYM Tipi PDF Deneme Kitapçığı & UI/UX Donanım Temelleri (P0)

> **Amaç:** Öğrencilerin en büyük ihtiyacı olan kağıt üstünde deneme çözme alışkanlığını karşılamak, kütüphaneden tek tıkla baskıya hazır kitapçık üretmek ve kırpma aracını gerçek bir doküman aracına dönüştürmek.

### 2.1. ÖSYM Tipi A4 2 Sütunlu PDF Deneme Kitapçığı Motoru
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/pdf/PdfDenemeKitapcikGenerator.kt` (Yeni), `app/src/main/java/com/sorudefteri/ui/kutuphane/KutuphaneScreen.kt`
* **Teknik Tasarım:**
  - Android yerel `android.graphics.pdf.PdfDocument` API'si (Sıfır ek kütüphane bağımlılığı).
  - **Sayfa Mizanpajı:** Standart A4 dikey format (595 x 842 pt).
  - **2 Sütunlu Düzen:** Sayfa ortasında ince dikey ayraç çizgisi, sağ ve sol sütunlarda sorular.
  - **Soru Dizgisi:** Soru numarası (1, 2, 3...), ders ve konu etiketi, altında soru görseli ve öğrencinin işlem yapabilmesi için boş çözüm kutusu (ızgaralı/noktalı alan opsiyonu).
  - **Kapak Sayfası:** Öğrencinin adı, hedefi (*"Hedef: Cerrahpaşa Tıp"*), deneme tarihi ve soru adedi.
  - **Arka Kapak:** Optik form görünümünde **Cevap Anahtarı** ve **Konu Dağılım Matrisi**.
  - `PrintManager` entegrasyonu ile Wi-Fi yazıcıya doğrudan gönderme veya PDF olarak dışa aktarma.

### 2.2. Kütüphanede Toplu Seçimden "Kitapçık Oluştur"
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/kutuphane/KutuphaneScreen.kt`, `KutuphaneViewModel.kt`
* **Yapılacak İşlemler:**
  1. Kütüphanedeki çoklu seçim alt çubuğuna (`BottomAppBar`) **"📄 PDF Deneme Kitapçığı Yap"** aksiyonu eklenir.
  2. Seçilen 10-20 soruyu parametre olarak alıp `PdfDenemeKitapcikGenerator` üzerinden PDF oluşturur ve yazdırma/paylaşım modalı açar.

### 2.3. Gerçek 8 Noktalı İnteraktif Kırpma Aracı (`ComposeImageCropper`)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/soruekle/components/ImageCropperDialog.kt`
* **Yapılacak İşlemler:**
  1. Mevcut sahte %92 merkez kırpma tamamen kaldırılır.
  2. Kullanıcının parmağı veya kalemiyle 4 köşe ve 4 kenardan tutup sürükleyebildiği, 3x3 kılavuz çizgili interaktif kırpma tuvali oluşturulur.
  3. Oran kilitleme seçenekleri: Serbest, 1:1, 4:3, 16:9, A4 Sütun.
  4. 90 derece saat yönünde / tersine döndürme butonları eklenir.

### 2.4. Sınav Kağıdı Yüksek Kontrast Filtresi (Scanner Mode)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/images/ImageFilterProcessor.kt` (Yeni)
* **Yapılacak İşlemler:**
  1. Sarı masa lambası gölgelerini ve kağıt kırışıklıklarını yok eden yüksek kontrastlı **Siyah-Beyaz Fotokopi Filtresi** geliştirilir.
  2. Koyu mod kullanıcıları için gece soru okumayı kolaylaştıran **Negatif/Ters Renk Kağıt Filtresi** eklenir.

### 2.5. "Günün Kurtarma Paketi" (Daily Due Queue)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/test/TestOlusturScreen.kt`, `app/src/main/java/com/sorudefteri/ui/kutuphane/KutuphaneScreen.kt`
* **Yapılacak İşlemler:**
  1. Kütüphane ve Test ekranlarının en tepesine dinamik bir kart yerleştirilir:
     *"🎯 Bugünün Kurtarma Paketi: Unutulmaya yakın 12 soru seni bekliyor!"* -> **[Hemen Başla]**
  2. 19:00 hatırlatıcı bildirimine tıklandığında kullanıcıyı boş test ekranına değil, doğrudan bu paketle başlayan test oturumuna yönlendirir (`deepLink = "sorudefteri://test/due"`).

---

## FAZ 3: Sokratik Yapay Zeka, Chatbot & Lenovo Yoga Tab (TB-710FU) Dokunmatik Kalem Stüdyosu (P1)

> **Amaç:** AI'ı tek yönlü bir metin üreticisinden çıkarıp öğrenciye özel Sokratik mentöre dönüştürmek; **Lenovo Yoga Tab (TB-710FU)** ve **Lenovo Dokunmatik Kalemi**nin (Lenovo Tab Pen Pro) 144Hz ekranını, 8.192 basınç seviyesini, eğimini, **gövdedeki kapasitif dokunmatik çift tıklama (double-tap) sensörünü** ve dahili haptik titreşim motorunu soru çözüm katmanına kusursuz entegre etmek.

### 3.1. Soruya Özel Çok Turlu İnteraktif AI Hoca (Chatbot)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/local/entity/SoruChatMessageEntity.kt` (Yeni), `SoruChatDao.kt` (Yeni), `app/src/main/java/com/sorudefteri/ui/detay/components/SoruChatBottomSheet.kt` (Yeni)
* **Mimari:**
  - **Veritabanı Tablosu (`soru_chat_mesajlari`):** `id`, `soruId`, `sender` (USER/AI), `messageText`, `timestamp`.
  - **Prompt Stratejisi:** *"Sen öğrencinin YKS özel ders öğretmenisin. Soru metni ve çözüm yukarıda mevcuttur. Öğrencinin anlamadığı adımı pedagojik olarak, doğrudan cevabı vermeden rehberlik ederek açıkla."*
  - **Arayüz:** Soru Detay altında açılabilir sohbet alanı ve hızlı soru çipleri (*"3. adımı anlamadım"*, *"Neden bu formülü kullandık?"*, *"Bu soru sınavda çıkar mı?"*).

### 3.2. Kademeli İpucu Sistemi: "Koç Modu" (Socratic Hint Ladder)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/ai/AiPromptManager.kt`, `app/src/main/java/com/sorudefteri/ui/detay/components/AiCozumTab.kt`
* **Tasarım:**
  - Çözüm ekranında tek tıkla cevabı vermek yerine 4 basamaklı kilitli kartlar sunulur:
    1. *1. Kademe:* Konu Kavramı ve Formül Hatırlatması
    2. *2. Kademe:* İlk Adım Stratejisi
    3. *3. Kademe:* En Büyük Çeldirici Tuzağı
    4. *4. Kademe:* Tam Çözüm ve Doğru Yanıt
  - Tam çözümü açmadan soruyu çözen öğrenciye ekstra XP ödülü verilir.

### 3.3. Akıllı Çoklu Sağlayıcı ve Model Fallback Motoru (`SmartAiRouter`)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/ai/SmartAiRouter.kt` (Yeni), `AiRepository.kt`
* **İşleyiş:**
  1. Birincil Sağlayıcı (örn. Gemini 3.7 Flash) çağrılır.
  2. Kota aşımı (`HTTP 429`) veya sunucu hatası (`5xx`) durumunda anında Groq Llama-3.2-90B Vision'a yönlendirilir.
  3. O da yanıt vermezse Nvidia NIM modeline geçilir. Öğrenci asla teknik hata görmez.

### 3.4. Lenovo Yoga Tab (TB-710FU) & Kapasitif Dokunmatik Kalem Entegrasyonu
* **Hedef Donanım Künyesi:**
  - **Tablet:** **Lenovo Yoga Tab / Xiaoxin Pad Pro GT (Model: TB-710FU)** — *11.1 inç, 3.2K (3200x2000), 144Hz LTPS ekran, Snapdragon 8 Gen 3, Android 15.*
  - **Stylus (Kalem):** **Lenovo Tab Pen Pro (Model AP600U)** & *Lenovo Precision Pen 3 / Tab Pen Plus.*
  - **Donanım Gerçeği:** **Kalem üzerinde basmalı fiziksel buton YOKTUR.** Kalem gövdesinde işaret parmağının oturduğu yerde **Kapasitif Dokunmatik Alan (Capacitive Touch Sensor / Tap Surface)** yer almaktadır.

* **Hedef Dosyalar:** 
  - `app/src/main/java/com/sorudefteri/ui/ortak/drawing/LenovoStylusHardwareController.kt` (Yeni)
  - `app/src/main/java/com/sorudefteri/ui/ortak/drawing/DrawingOverlayCanvas.kt` (Yeni)
  - `app/src/main/java/com/sorudefteri/ui/detay/SoruDetayScreen.kt`
  - `app/src/main/java/com/sorudefteri/ui/test/TestCozScreen.kt`
  - `app/src/main/java/com/sorudefteri/ui/ayarlar/AyarlarScreen.kt`

* **Özel Donanım Yeteneklerinin Entegrasyonu:**
  1. **Gövdedeki Dokunmatik Çift Tıklama (Double-Tap) Hareketi ve Görev Atama:**
     - Kalem üzerindeki dokunmatik sensöre parmakla iki kez hafifçe vurulduğunda (Double-Tap) Android Stylus Gesture API'si ve MotionEvent tetiklenir.
     - **Kullanıcı Tarafından Seçilebilir 4 Farklı Çift Tıklama Eylemi (`AyarlarScreen`):**
       - 🔄 **Seçenek 1 (Varsayılan): Kalem ↔ Silgi Hızlı Geçişi:** Çift tıklandığında anında Silgi moduna geçer (`BlendMode.Clear`), tekrar çift tıklandığında önceki kaleme geri döner. Ekrandaki silgi butonuna basma ihtiyacını tamamen ortadan kaldırır!
       - ↩️ **Seçenek 2: Son İşlemi Geri Al (Undo):** Çift tıklandığında son yapılan çizgi veya silme işlemini geri alır.
       - 🎨 **Seçenek 3: Hızlı Renk / Araç Paleti:** Çift tıklandığında kalemin tam ucunda mini bir radyal menü (Mavi, Kırmızı, Siyah mürekkep ve Vurgulayıcı) açar.
       - 💡 **Seçenek 4: Koç Modu İpucu Aç:** Çift tıklandığında Sokratik Koç modundaki sıradaki ipucu basamağını çözer.
  2. **Dahili Kalem Titreşim Motoru (Pen Haptic Feedback):**
     - Lenovo Tab Pen Pro üzerindeki dahili titreşim motoru, çift tıklama hareketi algılandığında parmağa anlık, tatmin edici bir **"klik" hissi (Haptic Tick)** gönderir. Böylece öğrenci ekrana bakmadan silgiye veya kaleme geçtiğini hisseder.
     - Doğru şık işaretlendiğinde tok bir başarı titreşimi, süre bitimine 15 saniye kala ritmik nabız titreşimi verir.
  3. **Donanımsal Palm Rejection (Avuç İçi Engelleme):**
     - `event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS` filtresi uygulanır.
     - Öğrenci 11.1 inçlik ekranda soru çözerken el ayasını ekrana tamamen yaslasa dahi parmak dokunuşları (`TOOL_TYPE_FINGER`) çizim motorundan izole edilir. Sadece kalemin ucu yazar; parmak hareketleri yalnızca iki parmakla görseli kaydırma/yakınlaştırma (pan/zoom) için kullanılır.
  4. **8.192 Basınç Seviyesi & 144Hz Ultra Düşük Gecikme (Low-Latency Ink):**
     - 8.192 kademeli basınç verisi (`event.getPressure()`) okunarak dinamik spline eğrisiyle çizgi kalınlığı modüle edilir (Tükenmez kalem, kurşun kalem hissi).
     - 144Hz ekran tazeleme oranından tam verim almak için Compose `drawWithContent` ve donanım hızlandırmalı Path buffer kullanılır; gecikme (latency) 3 milisaniyeye indirilir.
  5. **Eğim Algılama (Tilt Detection - `AXIS_TILT` & `AXIS_ORIENTATION`):**
     - Geometri ve matematik sorularında karalama yaparken kalemin eğim açısı okunur. Kalem yatay tutulduğunda kurşun kalem ucu otomatik olarak geniş gölgelendirme moduna geçer.
  6. **Kalıcı Karalama Katmanı (Persistent Scratchpad):**
     - Soru üzerine alınan tüm kalem karalamaları soruya ait `drawing_overlay_path` olarak Room veritabanında saklanır; *"Karalamaları Gizle / Göster"* butonu ile tek dokunuşla açılıp kapatılabilir.

### 3.5. Tabletler İçin Çift Kanatlı Çalışma Masası (Split-Pane Studio)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/detay/SoruDetayScreen.kt`
* **Tasarım:**
  - Lenovo Yoga Tab 11.1 inç yatay modda (Landscape):
    - **Sol Panel (%55 Genişlik):** Tam boy, Lenovo kalemi ile üzerine çizilebilir yüksek çözünürlüklü soru görseli ve karalama katmanı.
    - **Sağ Panel (%45 Genişlik):** Leitner aşaması, AI Sokratik Çözümü, sohbet botu ve öğrenci notları.

### 3.6. Yerel KaTeX Matematik & Kimya Motoru
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/ortak/math/KaTeXMathView.kt` (Yeni)
* **Tasarım:**
  - APK assets klasörüne gömülü yerel KaTeX JS/CSS ile çalışan hafif Android WebView Compose bileşeni.
  - İnternetsiz, sıfır gecikmeli, matrisleri, integralleri ve kimyasal tepkimeleri ÖSYM kitapçığı dizgisinde render eden vektörel görünüm.

---

## FAZ 4: Oyunlaştırma (Gamification), Zaman Baskısı & İleri Düzey Analitik (P1)

> **Amaç:** Öğrencinin uygulamaya her gün geri dönmesini sağlayan dopamin döngüsünü kurmak, sınavdaki zaman baskısını simüle etmek ve veli/koç düzeyinde analitik sunmak.

### 4.1. Günlük Seri (Streak 🔥) ve Seri Kalkanı
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/datastore/AyarlarDataStore.kt`, `app/src/main/java/com/sorudefteri/ui/ortak/StreakBadge.kt` (Yeni), `ReminderWorker.kt`
* **Mekanizma:**
  - Öğrenci o gün en az 5 soru çözdüğünde veya 3 soru eklediğinde serisi artar (🔥 14 Günlük Seri!).
  - Saat 21:00'e kadar serisini tamamlamamışsa akıllı bildirim: *"🔥 14 günlük serin sönmek üzere! 5 dakikanı ayır, serini koru!"*
  - Haftada 1 gün seriyi kurtaran "Seri Kalkanı" hakkı.

### 4.2. TYT / AYT Zaman Baskısı Modu (Speed Run)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/test/TestCozScreen.kt`, `TestCozViewModel.kt`
* **Özellikler:**
  - Test oluştururken **"Zaman Baskısı Modu"** seçeneği:
    - **TYT Modu:** Soru başına **75 saniye** geri sayım.
    - **AYT Modu:** Soru başına **120 saniye** geri sayım.
  - Son 15 saniyede nabız animasyonu ve haptik titreşim uyarısı.
  - Sonuç ekranında *"Süre Tuzağı Sorular: 4. soruda 3.5 dakika kaybettin!"* raporu.

### 4.3. XP, Seviyeler ve Başarım Rozetleri (Badges)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/domain/model/Badge.kt` (Yeni), `BadgeManager.kt` (Yeni), `IstatistikScreen.kt`
* **Puanlama:**
  - Yeni soru ekleme: **+10 XP** | Test bitirme: **+25 XP** | Soruyu Usta (5. Aşama) yapma: **+50 XP** | 7 Günlük Seri: **+100 XP**
* **Seviyeler:** Çırak (1) -> Hata Avcısı (2) -> Analiz Uzmanı (3) -> Derece Adayı (4) -> Sınav Fatihi (5).
* **Rozetler:** 🎯 *İşlem Hatası Avcısı*, 📐 *Geometri Bükücü*, 🦉 *Gece Kuşu*, 🛡️ *Hata Geçirmez*.

### 4.4. Konu Yetkinlik Isı Haritası (Subject Mastery Heatmap)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/istatistik/components/KonuIsiHaritasiCard.kt` (Yeni), `IstatistikViewModel.kt`
* **Görselleştirme:**
  - `konular.json` üniteleri bazında GitHub katkı grafiği formatında 4 renk skalası:
    - 🟥 `%0 - %40`: Kritik Eksik
    - 🟨 `%41 - %70`: Pekiştirilmeli
    - 🟦 `%71 - %89`: Güçlü
    - 🟩 `%90 - %100`: Tam Ustalık

### 4.5. ÖSYM Ağırlıklı "Acil Eylem Matrisi" (Priority Quadrant)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/istatistik/components/AcilEylemMatrisiCard.kt` (Yeni)
* **Analiz:**
  - Son 5 yılın ÖSYM soru frekansı ile öğrencinin hata sayısını 2x2 matriste kesiştirip *"Sınavı kazandıracak / kaybettirecek acil 3 konu"* uyarısı üretme.

### 4.6. "Hocam Bakar Mısınız?" — Akıllı WhatsApp Soru Kartı
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/ortak/TeacherShareHelper.kt` (Yeni), `SoruDetayScreen.kt`
* **İçerik:**
  - Tek tuşla öğretmene gönderilmek üzere oluşturulan kompozit görsel kartı:
    - Üstte soru fotoğrafı, altta: Ders/Konu, Benim Cevabım: C, Doğru Cevap: E, Öğrencinin Özel Notu: *"Hocam sağ el kuralında indüksiyon akımının yönünü ters buluyorum, bakar mısınız?"*

---

## FAZ 5: Performans Optimizasyonu, Bulut Senkronizasyonu, Sosyal Ekosistem & Tam Doğrulama (P2)

> **Amaç:** Uygulamanın depolama ve bellek tüketimini minimize etmek, arama hızını zirveye çıkarmak, sosyal/dijital platformlarla entegre olmak ve **tüm sistemi birim testler, Android derlemesi ve sıfır hata ile mühürlemek**.

### 5.1. WebP + Çift Kademeli Küçük Resim (Thumbnail) Motoru
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/images/GorselDepo.kt`, `SoruKarti.kt`
* **Yapılacaklar:**
  1. Görselleri JPEG %85 yerine kayıplı WebP %75 kalitesinde kaydet (%50 depolama tasarrufu).
  2. Her soru için 360x360 px WebP küçük resim (`thumb_UUID.webp`) üret.
  3. Kütüphane listelerinde 1600px'lik dev görseller yerine thumbnail'ı yükle (60/120/144 FPS akıcı kaydırma).

### 5.2. Paging 3 ve FTS5 Tam Metin Arama Motoru
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/local/entity/SoruFtsEntity.kt` (Yeni), `SoruDao.kt`, `KutuphaneViewModel.kt`
* **Yapılacaklar:**
  1. `KutuphaneViewModel` içindeki tüm soruları RAM'e çeken `getAllSorular()` kodunu kaldır; `collectAsLazyPagingItems()` ile gerçek Paging 3'e geç.
  2. OCR metinleri ve soru açıklamaları için Room FTS5 sanal tablosu kur; `LIKE '%query%'` yerine `MATCH` sorgusu ile aramaları 2 milisaniyeye indir.

### 5.3. Anki & Flashcard CSV Dışa Aktarımı (.apkg)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/export/AnkiExportHelper.kt` (Yeni)
* **Yapılacaklar:**
  - Soruları tek tıkla Anki ve Quizlet formatında Flashcard destesine dönüştür (Ön yüz: Soru görseli + OCR, Arka yüz: Doğru cevap + AI açıklaması).

### 5.4. Instagram / WhatsApp Story Başarı Kartı (9:16)
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/ui/istatistik/components/StoryCardGenerator.kt` (Yeni)
* **Yapılacaklar:**
  - Test bitiminde veya haftalık karnede öğrencinin başarısını ve serisini (🔥 15 Günlük Seri!) Instagram Story'de paylaşabileceği 9:16 oranında trend gradyanlı görsel üretici.

### 5.5. Sessiz Google Drive AppData Senkronizasyonu
* **Hedef Dosyalar:** `app/src/main/java/com/sorudefteri/data/backup/GoogleDriveBackupHelper.kt` (Yeni)
* **Yapılacaklar:**
  - Kullanıcının Google Drive hesabındaki gizli uygulama klasörüne (`DriveScopes.DRIVE_APPDATA`) haftalık sessiz bulut yedeği al. Telefon kaybolsa dahi yeni cihazda tek tıkla geri yükleme sağla.

### 5.6. Tam Sistem Doğrulaması, Birim Testler & Derleme Mührü (Zero-Regression)
* **Hedef Görevler:**
  1. Tüm Faz 1, Faz 2, Faz 3, Faz 4 ve Faz 5 bileşenleri için JUnit & MockK birim testlerinin yazılması/güncellenmesi.
  2. `./gradlew testDebugUnitTest` çalıştırılarak tüm birim testlerin (110+ test) %100 yeşil geçtiğinin doğrulanması.
  3. `./gradlew assembleDebug` çalıştırılarak APK paketlemesinin sıfır hata ve sıfır derleme uyarısıyla tamamlanması.

---

## 📋 Master Görev ve Uygulama Takip Listesi (Checklist)

### 🔴 Faz 1: Güvenlik, Depolama & Çekirdek Pedagoji (P0)
- [x] `network_security_config.xml` release moddan `<certificates src="user" />` kaldırıldı.
- [x] `DatabaseModule.kt` içinden `.fallbackToDestructiveMigration()` kaldırıldı.
- [x] `AutoBackupWorker.kt` yedek sayısı 1'e düşürüldü, eski yedekler temizlendi.
- [x] `SoruEklemeViewModel.kt` iptal edilen görsel temizleme mantığı eklendi.
- [x] `SoruDao.kt` içine `regressReviewStageAtomik` ve `regressReviewStageBatch` eklendi.
- [x] `SoruRepository.kt` içine `regressReviewStage(soruId)` bağlandı.
- [x] `TestSonucViewModel.kt` test bitiminde doğru/yanlış sorular için otomatik aşama ilerleme/gerileme bağlandı.
- [x] `TestOlusturViewModel.kt` tekrarı gelen (`isDueForReview`) sorulara öncelik verildi.
- [x] `TestSonucDao.getAllTestResults()` fonksiyonu `IstatistikScreen`'e bağlandı.

### 🔴 Faz 2: ÖSYM Tipi PDF Deneme & Donanım Temelleri (P0)
- [x] `PdfDenemeKitapcikGenerator.kt` yazıldı (A4, 2 sütunlu, cevap anahtarlı ve işlem boşluklu).
- [x] Kütüphane çoklu seçim çubuğuna *"PDF Deneme Kitapçığı Yap"* butonu eklendi.
- [x] `ImageCropperDialog.kt` 8 noktalı gerçek serbest kırpıcıya dönüştürüldü.
- [x] `ImageFilterProcessor.kt` sınav kağıdı siyah-beyaz fotokopi filtresi eklendi.
- [x] Ana ekrana ve test sekmesine *"Günün Kurtarma Paketi"* kartı ve 19:00 deep link'i eklendi.

### 🟡 Faz 3: Sokratik AI, Chatbot & Lenovo Yoga Tab (TB-710FU) Dokunmatik Kalem Stüdyosu (P1)
- [x] `SoruChatMessageEntity` ve `SoruChatDao` Room veritabanına eklendi.
- [x] `SoruChatBottomSheet` ile soruya özel interaktif AI öğretmen sohbeti inşa edildi.
- [x] `AiPromptManager` ve `AiCozumTab` için 4 kademeli "Koç Modu" (Hint Ladder) geliştirildi.
- [x] `SmartAiRouter` (Gemini -> Groq -> Nvidia NIM otomatik fallback) devreye alındı.
- [x] **Lenovo Yoga Tab (TB-710FU)** ve **Dokunmatik Kalem** kontrolcüsü (`LenovoStylusHardwareController.kt`) yazıldı.
- [x] **Kapasitif Dokunmatik Çift Tıklama (Double-Tap)** hareketi algılandı ve ayarlanabilir eylemlere (Kalem/Silgi geçişi, Geri al, Renk paleti, İpucu) bağlandı.
- [x] `DrawingOverlayCanvas.kt` üzerinden **Palm Rejection**, **8.192 Basınç Seviyesi** ve **Eğim (`AXIS_TILT`)** entegre edildi.
- [x] Kalem dahili titreşim motoru ile çift tıklamada ve araç değişiminde dokunsal geri bildirim (Haptic Tick) bağlandı.
- [x] `SoruDetayScreen` Lenovo 11.1 inç 144Hz ekran için çift kanatlı stüdyo düzenine (%55 soru & kalem / %45 AI & notlar) kavuşturuldu.
- [x] Soru üzerine yapılan karalamaların veritabanına kaydedilmesi ve *"Karalamaları Gizle/Göster"* butonu eklendi.
- [x] `KaTeXMathView.kt` yerel KaTeX WebView bileşeni entegre edildi.

### 🟡 Faz 4: Oyunlaştırma, Zaman Baskısı & İleri Düzey Analitik (P1)
- [x] `AyarlarDataStore` üzerinde Günlük Seri (Streak 🔥) ve Seri Kalkanı sistemi kuruldu.
- [x] Saat 21:00 seri koruma bildirimi eklendi.
- [x] `TestCozScreen` için TYT (75 sn) ve AYT (120 sn) Zaman Baskısı Geri Sayım Modu yazıldı.
- [x] XP puanlama motoru, seviyeler ve başarım rozetleri (Badges) eklendi.
- [x] `KonuIsiHaritasiCard.kt` ile konu bazlı ustalık ısı haritası çizildi.
- [x] `AcilEylemMatrisiCard.kt` ÖSYM soru frekansı x öğrenci hatası matrisi eklendi.
- [x] `TeacherShareHelper.kt` ile "Hocam Bakar Mısınız?" WhatsApp soru kartı oluşturuldu.

### 🟢 Faz 5: Performans, Depolama, Sosyal Ekosistem & Tam Doğrulama (P2)
- [x] `GorselDepo.kt` WebP %75 dönüşümü ve 360x360 px thumbnail üretimi devreye alındı.
- [x] `SoruFtsEntity` ile Room FTS5 tam metin arama ve Paging 3 kütüphane listesine bağlandı.
- [x] `AnkiExportHelper.kt` ile Anki (.apkg) ve flashcard CSV dışa aktarımı yazıldı.
- [x] `StoryCardGenerator.kt` ile Instagram/WhatsApp Story 9:16 başarı kartı üretildi.
- [x] `GoogleDriveBackupHelper.kt` ile sessiz AppData bulut yedeklemesi sağlandı.
- [x] **Birim Testler:** `./gradlew testDebugUnitTest` çalıştırıldı ve tüm testlerin geçtiği doğrulandı.
- [x] **Derleme Doğrulaması:** `./gradlew assembleDebug` çalıştırıldı ve APK hatasız derlendi.

---
*Bu belge, Soru Defterim projesinin yaşayan master mimari planıdır. Her bir faz tamamlandıkça ilgili kontrol kutuları işaretlenerek ilerlenecektir.*
