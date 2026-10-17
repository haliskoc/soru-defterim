# 📚 Soru Defterim (Smart Exam Preparation & Question Archive)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.1.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%26%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose UI" />
  <img src="https://img.shields.io/badge/DI-Dagger%20Hilt%202.55-green?style=for-the-badge" alt="Hilt DI" />
  <img src="https://img.shields.io/badge/AI-Gemini%202.5%20%7C%20Groq%20LLaMA%203.2-orange?style=for-the-badge" alt="AI Engine" />
  <img src="https://img.shields.io/badge/Security-AES--256%20GCM-red?style=for-the-badge&logo=lock" alt="AES-256 Security" />
</p>

---

## 🎯 Proje Hakkında (About the Project)

**Soru Defterim**, YKS (TYT, AYT Sayısal, Eşit Ağırlık, Sözel, YDT), LGS ve KPSS sınavlarına hazırlanan öğrencilerin yapamadıkları ve hatalı çözdükleri soruları arşivleyip, **Yapay Zeka (AI)** ve **Aralıklı Tekrar (Spaced Repetition)** yöntemleriyle kalıcı öğrenmeye dönüştüren yeni nesil akıllı mobil sınav asistanıdır.

---

## 🏗️ Sistem Mimarisi (Architecture Overview)

Uygulama, **Clean Architecture**, **Single-Activity Architecture** ve **MVI / MVVM** desenlerini modern Android pratikleriyle uygular:

```mermaid
graph TD
    subgraph UI_Layer ["🎨 UI Layer (Jetpack Compose & Material 3)"]
        A[MainActivity & WindowSizeClass] --> B[AppNavHost]
        B --> C1[OnboardingScreen]
        B --> C2[KutuphaneScreen - List/Detail Pane]
        B --> C3[SoruEklemeScreen & CameraX]
        B --> C4[SoruDetayScreen & Canvas & AI Solution]
        B --> C5[TestOlusturScreen & TestCozScreen]
        B --> C6[IstatistikScreen]
        B --> C7[AyarlarScreen]
    end

    subgraph Domain_Layer ["🧠 Domain Layer"]
        D1[DersTaksonomisi]
        D2[KonuKatalog]
        D3[SpacedRepetition SM-2]
        D4[TestSessionManager]
        D5[AiRepository Interface]
    end

    subgraph Data_Layer ["💾 Data & Local Storage Layer"]
        E1[Room Database - SoruDao & TestSonucDao]
        E2[AyarlarDataStore & EncryptedSharedPreferences AES-256]
        E3[GorselDepo - Scoped Storage]
        E4[BackupManager - ZIP Import/Export]
        E5[WorkManager - ReminderWorker 19:00]
    end

    subgraph AI_Engine ["⚡ Multi-Provider AI Engine"]
        F1[GeminiProvider - Google Gemini 2.5 Flash]
        F2[GroqProvider - LLaMA 3.2 Vision]
        F3[ML Kit OCR Text Recognition]
    end

    UI_Layer --> Domain_Layer
    Domain_Layer --> Data_Layer
    Data_Layer --> AI_Engine
```

---

## ✨ Temel Özellikler (Key Features)

### 🚀 1. Onboarding & Zorunlu Alan Seçimi (P1-6)
- **4 Aşamalı Modern Tanıtım Carousel**: AI Soru Analizi, Spaced Repetition, Akıllı Kütüphane ve Şifreli Güvenlik özelliklerini interaktif kartlarla anlatır.
- **Zorunlu Alan (Track) Seçimi**: 
  1. Sayısal (YKS TYT + AYT)
  2. Eşit Ağırlık (YKS TYT + AYT)
  3. Sözel (YKS TYT + AYT)
  4. YDT (Yabancı Dil Testi)
  5. LGS (8. Sınıf Liselere Geçiş)
  6. KPSS (Genel Yetenek & Genel Kültür)
- Seçim yapılmadan ana ekrana geçilemez; seçim `AyarlarDataStore` içinde saklanır ve tüm müfredat ders listesi seçilen alana göre dinamik uyarlanır.

### ⏰ 2. Spaced Repetition & WorkManager Hatırlatıcı (P1-7)
- **SuperMemo-2 (SM-2)** tabanlı aralıklı tekrar algoritması (1, 3, 7, 14 ve 30 günlük aşamalar).
- **WorkManager PeriodicWorker (`ReminderWorker`)**:
  - Her gün saat **19:00**'da arka planda tetiklenir.
  - Vakti gelen soruları Room DAO üzerinden sorgular.
  - Varsa kullanıcıya bildirim gönderir: *"Tekrar Vakti! 📚 Bugün tekrar etmen gereken X soru var. Şimdi çözmeye başla!"*
  - Bildirime dokunulduğunda doğrudan test çözme ekranına yönlendirir.

### 📚 3. Kapsamlı Ders & Konu Kataloğu v1.2 (P1-10)
- `assets/konular.json` dosyası üzerinden tüm sınavlar için kapsamlı konu ağacı:
  - **TYT**: Türkçe, Matematik, Geometri, Fizik, Kimya, Biyoloji, Tarih, Coğrafya, Felsefe, Din Kültürü
  - **AYT**: Matematik, Geometri, Fizik, Kimya, Biyoloji, Türk Dili ve Edebiyatı, Tarih-1, Coğrafya-1, Tarih-2, Coğrafya-2, Felsefe Grubu (Mantık, Psikoloji, Sosyoloji), Din Kültürü, YDT İngilizce
  - **LGS**: Türkçe, Matematik, Fen Bilimleri, T.C. İnkılap Tarihi, Din Kültürü, İngilizce
  - **KPSS**: Genel Yetenek (Türkçe, Matematik), Genel Kültür (Tarih, Coğrafya, Vatandaşlık, Güncel Bilgiler)
- Konu kataloğu çevrimdışı, thread-safe önbellek ve *"Katalog hazırlanıyor..."* güvenli Fallback desteği sunar.

### 📱 4. Tablet & Responsive Tasarım (P1-11)
- `androidx.compose.material3.windowsizeclass` entegrasyonu:
  - **Kompakt Ekranlar (Telefon)**: Standart alt menü (`NavigationBar`).
  - **Orta ve Geniş Ekranlar (Tablet / Katlanabilir / Landscape)**: Yan menü (`NavigationRail`).
  - **Tablet Geniş Ekran Modu (List-Detail Pane)**: Kütüphane ekranında sol sütunda filtreli soru listesi, sağ sütunda seçilen sorunun tam detay ve AI çözüm önizleme kartı.

### 🤖 5. Yapay Zeka (AI) & LaTeX Matematiksel Formül Render (P0-3, P0-4)
- **Çoklu Sağlayıcı (Multi-Provider)**: Google Gemini (gemini-2.5-flash) ve Groq (LLaMA 3.2 11B Vision).
- **Canlı Model Listesi**: API anahtarı girildiğinde Google AI Studio ve Groq API'lerinden güncel modelleri dinamik olarak çeker.
- **LaTeX Math Render**: $...$ ve $$...$$ matematiksel ve fiziksel formüllerini WebView destekli KaTeX motoruyla kusursuz render eder.
- **Benzer Soru Üretimi**: Yapay zekaya hatalı sorulan sorunun benzerini (4 seçenekli ve çözümlü) ürettirir.

### 🔐 6. Donanım Destekli Şifreleme & Tam Yedekleme (P0-1, P0-2)
- **EncryptedSharedPreferences (AES-256 GCM)**: API anahtarları Android Keystore donanım anahtarlarıyla şifrelenir.
- **Şifreli ZIP Yedekleme**: Tüm Room veritabanı JSON manifesti ve soru görselleri tek bir `.sdefteri` / `.zip` dosyasında paketlenir.
- **Bulut / Drive Senkronizasyonu**: Android Sharesheet ile Google Drive, Telegram vb. bulut platformlarına tek tıkla aktarılabilir.

---

## 🛠️ Kurulum ve Çalıştırma (Setup & Run)

### Gereksinimler
- Android Studio Ladybug (2024.2.1+) veya üzeri
- JDK 17 (Eclipse Temurin / OpenJDK)
- Android SDK 35 (Android 15) & Min SDK 24 (Android 7.0)

### Projeyi Derleme
```bash
# Projeyi klonlayın
git clone https://github.com/haliskoc/soru-defterim.git
cd soru-defterim

# Birim testlerini çalıştırın
./gradlew testDebugUnitTest

# Debug APK derleyin
./gradlew assembleDebug

# Release APK ve App Bundle derleyin
./gradlew assembleRelease bundleRelease
```

---

## 🧪 Birim Testleri (Unit Tests)

Uygulama, Room Mapping, Spaced Repetition, Matematiksel LaTeX Parser ve Sınav Taksonomisi için kapsamlı test suite içerir:
```bash
./gradlew testDebugUnitTest --continue
```
- `DersTaksonomisiTest.kt`: TYT, AYT, YDT, LGS, KPSS ders haritalama testleri.
- `SpacedRepetitionTest.kt`: SM-2 aralıklı tekrar aşama ve gün hesaplama testleri.
- `MathMarkdownParserTest.kt`: LaTeX formül ve markdown dönüştürme testleri.
- `SoruEntityMappingTest.kt`: Room entity ve domain model dönüşüm testleri.
- `BackupManifestSerializationTest.kt`: ZIP yedekleme JSON manifesti testleri.

---

## 📦 CI/CD Pipeline (GitHub Actions)

`.github/workflows/android.yml` dosyasında yapılandırılmış otomatik CI/CD süreci:
1. Otomatik Kod İnceleme & Lint (`lintDebug`)
2. Birim Testlerinin Koşulması (`testDebugUnitTest`)
3. Release APK ve Google Play App Bundle (AAB) Üretimi (`assembleRelease`, `bundleRelease`)
4. Test ve Lint Raporlarının Artifact Olarak Yüklenmesi

---

## 📄 Lisans (License)
Bu proje MIT lisansı altında lisanslanmıştır.

---

## 🛠️ Düşük RAM Derleme (8 GB sistemler)

Bu proje 8 GB RAM'e sığacak şekilde yapılandırılmıştır. `gradle.properties`
içindeki bellek sınırları bilinçli olarak mütevazıdır:

| Bileşen | Önce | Şimdi |
|---|---|---|
| Gradle Daemon | 1536m + 512m meta | **1024m + 512m meta** |
| Kotlin Daemon | 2048m + 512m meta | **1024m + 512m meta** |
| Toplam tavan | ~4.5 GB | **~3.1 GB** |
| İşçi paralelliği | 1 | 1 (`parallel=false`) |

Ayrıca `SerialGC`, artımlı derleme ve yerel derleme önbelleği açık.

### Neden `release` R8 kucultmesi kullanmiyor?

`minifyReleaseWithR8` tek basina 2 GB'tan fazla bellek istiyor. 8 GB'lik
makinede, masaustu ortami acikken Gradle daemon'i OOM killer tarafindan
olduruluyordu:

* tepe bellek ~6.7 GB / 7.2 GB, yogun swap
* `Gradle build daemon disappeared unexpectedly`
* 1024m ile denendiginde: **Metaspace tukeniyor**, derleme 22+ dakika takili kaliyor

Bu yuzden `./derle.sh release`, `-PlowRam=true` ile R8'i ve kaynak
sikistirmayi kapatir. Cikti yine de gecerli bir Release APK'dir (debug flag
yok, imzali, kurulabilir) - yalnizca kucultulmemistir. Tam kucultme gerekirse
Android Studio / emulator / tarayici kapaliyken `./derle.sh release-full`
kullanin.

`gradle.properties` kalici olarak degismez; `-PlowRam` yalnizca o calistirmayi
etkiler.

Kullanim:

```bash
./derle.sh            # Debug APK
./derle.sh release    # Release APK (R8 kucultme KAPALI - 8 GB icin guvenli)
./derle.sh release-full  # Tam R8 kucultmeli Release (cok bellek ister)
./derle.sh temiz      # Tam temiz Debug derlemesi
./derle.sh test       # Birim testleri
./derle.sh serbest    # Arka plan daemon'larini kapat (~1.5 GB RAM geri ver)
```

> **Önemli:** `./derle.sh serbest` boşta bekleyen Gradle/Kotlin daemon'larını
> kapatır. Bunlar boştayken ~1.5 GB RAM'i elinde tutarlar ve 8 GB'lık
> sistemde tarayıcı/IDE ile birlikte kasmaya yol açar.

### Ölçülen sonuçlar (8 GB RAM)

| Derleme | Süre | Tepe RAM |
|---|---|---|
| Temiz Debug | ~2 dk 54 sn | ~5.8 GB (masaüstü dahil) |
| Debug (tekrar) | ~30 sn | — |
| Release (R8'siz) | `./derle.sh release` | ~5.5 GB |

İlk kurulumda `--stop` sonrası diskte kalan bayat Gradle kilidi
(`~/.gradle/caches/journal-1/journal-1.lock`) "Timeout waiting to lock journal
cache" hatası verirse: `./derle.sh serbest` çalıştırın.
