# Derin Web Araştırması — Otomatik Soru/Belge Bölgesi Tespiti

**Tarih:** 2026-10-18
**Tür:** Araştırma belgesi (kod değişikliği yok)
**İlişkili plan:** `docs/KAMERA_OTOMATIK_TESPIT_PLANI.md`
**İlişkili handoff:** `KAMERA_TESPIT_HANDOFF.md`
**Yöntem:** 4 paralel derin-tarama (ML Kit + Document Scanner / CameraX+OCR hattı / OpenCV klasik / TFLite + UX desenleri), 60+ kaynak tarandı.

---

## 0. Yönetici özeti

1. **"Soruyu bul" diye hazır bir ML Kit API'si yok.** Text Recognition v2 blok/satır/kelime + kutu verir; soru sınırını uygulama katmanı çizer. Document Scanner API sayfa bulur, soru bulmaz.
2. **En çok tekrar eden üretim reçetesi:** sabit analiz çözünürlüğü (1280x720) + KEEP_ONLY_LATEST + tek-işlem kilidi + ROI içinde metinli tespit + çok-kare stabilizasyon + deklanşörde tam çözünürlükte refine + güven düşükse statik çerçeveye düşüş.
3. **Bizim en büyük israfımız teyit edildi:** ML Kit metni zaten okuyor, biz çöpe atıyoruz. Soru-no/şık regex'i, sektörde bu işin standart çözümü.
4. **Kritik uyarı (çevrimdışı):** unbundled (thin) model ilk kullanımdan önce Play Services'ten iner; inmeden yapılan istekler **sessizce sonuç üretmez**. "Bazen hiç çalışmıyor" şikâyetinin bir adayı bu.
5. **Koordinat kayıklığının resmi çözümü var:** `ViewPort` + `UseCaseGroup` (WYSIWYG) ve `ML Kit Analyzer` (`COORDINATE_SYSTEM_VIEW_REFERENCED`).
6. **OpenCV ve TFLite v1'de yok:** OpenCV APK'ya ~25–66 MB ekler ve soru sınırını yine vermez; TFLite 50–birkaç yüz etiketli fotoğraf ister. İkisi de ertelendi.

---

## 1. ML Kit Text Recognition v2 — bölge seçen gerçek örnekler

### 1.1. Hiyerarşi (resmi)

`Text → TextBlock (≈ paragraf/sütun) → Line → Element (kelime) → Symbol`, her seviyede `text`, `boundingBox`, `cornerPoints`, `confidence` (Symbol/Line), `rotationDegree`, dil kodları.
Bizim kod şu an yalnız `Line.boundingBox` kullanıyor; `block.text`, `block.boundingBox`, `element.text`, `confidence` hiç tüketilmiyor.

- https://developers.google.com/ml-kit/vision/text-recognition/v2
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android

### 1.2. Örnek projeler

| Proje | Kullanılan teknik | Hangi sorunu çözmüş | Sınırlama |
|---|---|---|---|
| `cxtms/example-ml-kit-vertical-text-astra` (POC) | Küçültülmüş kopyada connected-components → karakter gruplama → en iyi 5 aday bölge → yalnız o bölgeye OCR (tek geçiş, medyan 7.8 ms → 3.1 ms) | Otomatik bölge bulma + kırpma; gürültü/gecikme azaltma | Geometrik sezgisel; perspektif düzeltmez; POC, üretim değil |
| "Exploring OCR Capabilities of ML Kit using CameraX" (Medium) | Canlı akışı ekrandaki overlay kutusuna göre kırpıp yalnız orayı ML Kit'e verme | Tüm kare yerine sınırlı bölge → az gürültü, az gecikme | Bölgeyi kullanıcı belirler, otomatik tespit yok |
| Stack Overflow "Rectangular box on camera preview" | Kırpılmış görüntüyü verme **veya** `boundingBox` overlap testi (kesişim hesabı) | Belirli bölgeye odaklanma | Kutuyu yine uygulama/kullanıcı belirler |
| VIN tarayıcı POC (atomicrobot) | Backpressure + CameraX + Compose, sonra alan doğrulama (check-digit) | Gerçek zamanlı kuyruk/gecikme | Bölge tespiti yok, "oku + doğrula" odaklı |
| `itsVnp/Android-OCR-Text-Recognition-Scanner` | ML Kit / Tesseract / Cloud Vision karşılaştıran uygulama | Motor seçimi | Bizim soruna doğrudan değil |

- https://github.com/cxtms/example-ml-kit-vertical-text-astra
- https://medium.com/@sdptd20/exploring-ocr-capabilities-of-ml-kit-using-camera-x-9949633af0fe
- https://stackoverflow.com/questions/67752505/rectangular-box-to-scan-analyze-on-the-camera-preview-with-flutter-google-ml-kit
- https://github.com/itsVnp/Android-OCR-Text-Recognition-Scanner

---

## 2. ML Kit Document Scanner API — ne yapar, nerede durur

- Paket: `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`; modeller+mantık+UI Play Services ile dinamik iner (~300 KB uygulama etkisi); **tamamı on-device**; **kamera izni gerekmez**; RAM ≥ 1.7 GB ister (altında `UNSUPPORTED`).
- Yetenek: otomatik yakalama (`CAPTURE_MODE_AUTO/MANUAL`), hassas kenar tespiti, perspektif düzeltme, otomatik rotasyon, gölge/leke/parmak temizleme (`SCANNER_MODE_FULL`), JPEG/PDF çıktı. Google Drive ve Pixel Camera kullanıyor.
- Modlar: `SCANNER_MODE_BASE` (3) / `BASE_WITH_FILTER` (2) / `FULL` (1, varsayılan).
- **Kritik sınırlama: sayfa bazlıdır, TEK SORU kırpmaz.** Issue #977 (çekim başına kırpma onayı yok), #853 (otomatik kırpma ekranı yok), #980 (`pageLimit` kırılganlığı) bunu doğruluyor.

Örnekler: `googlesamples/mlkit → android/documentscanner` (quickstart), `shubhanshu24510/ScanKit`, `MyricSeptember/MlKitDocumentScanner-Final`, Flutter `google_mlkit_document_scanner`.

- https://developers.google.com/ml-kit/vision/doc-scanner/android
- https://developers.google.com/ml-kit/vision/doc-scanner
- https://android-developers.googleblog.com/2024/02/ml-kit-document-scanner-api.html
- https://github.com/googlesamples/mlkit/tree/master/android/documentscanner
- https://github.com/googlesamples/mlkit/issues/977
- https://github.com/googlesamples/mlkit/issues/853

**Projeye çıkarım:** Document Scanner "sayfayı düzelt" katmanı olarak v2'de düşünülebilir; soru seçimi yine Text Recognition + regex ile yapılır.

---

## 3. Model indirme ve çevrimdışı (bizim için kritik)

Üç yol: **unbundled/thin** (`play-services-mlkit-text-recognition:19.0.1`, ~260 KB, Play Services indirir) / **bundled/thick** (`com.google.mlkit:text-recognition:16.0.1`, ~4 MB, anında hazır) / dynamic. Biz **unbundled** kullanıyoruz.

- **"İndirme tamamlanmadan yapılan istekler sonuç üretmez"** (resmi). Çevrimdışı ilk açılışta sessiz boş sonuç verir — bizim "bazen hiç çalışmıyor"un açıklaması olabilir.
- Çözümler: manifest'e `<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="ocr" />` (kurulumda indir) veya `ModuleInstallClient` ile açık kontrol; ilk çalışması çevrimdışı şartsa **bundled'a geçiş** öneriliyor.
- Bilinen sorunlar: thin varyant Bitmap-dışı formatlarda daha yavaş; CameraX/Camera2'de Camera1 kadar iyi değil; task callback Activity yok edildikten sonra çalışabilir.

- https://developers.google.com/ml-kit/tips/installation-paths
- https://transloadit.com/devtips/implementing-ocr-in-android-apps-with-google-ml-kit/
- https://developers.google.com/ml-kit/known-issues
- https://stackoverflow.com/questions/66086003/recognize-text-in-images-with-ml-kit-on-android-on-device-offline

**Projeye çıkarım (v1.1 adayı):** manifest meta-data + açılışta model-hazır kontrolü + UI uyarısı ("çevrimdışı model indiriliyor"). Girdi kalite kuralı: karakter başına ≥16x16 px, mektup-boy belge için ~720x1280.

---

## 4. CameraX + OCR hattı — sahada kanıtlanmış desenler

### 4.1. Çözünürlük (bizim en büyük açık)

- `ImageAnalysis` **varsayılan hedef 640x480** (resmi) — OCR için yetersiz; `setTargetResolution(1280x720)` + `STRATEGY_KEEP_ONLY_LATEST` standart reçete. `setTargetResolution` minimum sınır dener; aynı anda `setTargetAspectRatio` ile kullanılamaz.
- Topluluk vakası (anekdot, bağımsız doğrulanmadı): bütçe telefonda OCR **%34 → %79** (çözünürlük zorlama + odak kilitleme + çok-kare birleştirme + Laplacian skorlama).
- ML Kit kuralı: karakter ≥16x16 px yeterli, >24x24 px genelde faydasız; 4K, inference öncesi ~18 ms ekler — 1280x720 tatlı nokta.
- Bizde `setTargetResolution` YOK → ilk v1.1 işi.

- https://developer.android.com/media/camera/camerax/analyze
- https://developer.android.com/media/camera/camerax/configuration
- https://docs.bswen.com/blog/2026-03-11-camerax-ocr-low-end-phones/
- https://mvpfactory.io/blog/wiring-android-s-camerax-to-a-quantized-ocr-pipeline-real-time-document-text

### 4.2. Throttle + kilit + ImageProxy disiplini

- `STRATEGY_KEEP_ONLY_LATEST` ML Kit dokümanında açıkça önerilir; `isProcessing` bayrağı + timestamp cooldown standart.
- **Her kod yolunda `ImageProxy.close()`** (Task'in `OnComplete`'inde); `Media.Image.close()` YASAK. Kapatılmayan ~10 kareden sonra kamera durur. Bizim `MetinAnalizci` zaten uyumlu — korunacak.
- AF oturması için ~300 ms bekleme + ortalama çekim ~450 ms sayıları topluluk pratiği.

- https://tutorials.dodatech.com/quick-fix/android-camera-x-analyze/
- https://github.com/android/camera-samples/discussions/639

### 4.3. Koordinat uyumsuzluğu (bizim "kesme kayık" sorunu)

- **Resmi çözüm: `ViewPort` + `UseCaseGroup`** — preview/analysis/capture aynı sensör bölgesine hizalanır; FILL_CENTER'ta WYSIWYG. Biz şu an elle oran matematiği yapıyoruz (`KirpmaHesaplayici`); ViewPort'a geçiş v1.2 adayı.
- `ML Kit Analyzer` + `COORDINATE_SYSTEM_VIEW_REFERENCED` dönüşümü otomatik verir.
- **Deklanşörde refine deseni** (üretim standardı): canlı analiz yalnız önizleme için; nihai OCR yüksek çözünürlüklü çekim karesinde. `Img-to-text` projesi aynı desen. Analysis stream'ini sonradan yükseltmenin iyi yolu yok (Stack Overflow 77467239) — o yüzden refine çekim karesinde olur.
- EXIF: `imageInfo.rotationDegrees` (analiz) / `Exif.createFromFile` (çekim); bizim `exifDerecesi()` uyumlu.

- https://developer.android.com/media/camera/camerax/transform-output
- https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform
- https://developer.android.com/media/camera/camerax/mlkitanalyzer
- https://github.com/Cileder/Img-to-text
- https://stackoverflow.com/questions/77467239/how-to-get-a-higher-resolution-version-of-the-image-that-is-used-for-image-analy

### 4.4. Düşük ışık

- Night extension + `setPostviewEnabled` + capture progress; CameraX 1.4+ `enableLowLightBoostAsync` (AE, FPS düşer); torch (`enableTorch`, flash unit kontrolü); pozlama telafisi (`setExposureCompensationIndex`); odak kilitleme (`startFocusAndMetering`, auto-cancel 0).
- Çok-kare: 3 kare çek → **Laplacian variance** en keskinini seç (`maxByOrNull`); eşik altıysa reddet + retry (max 3). Bizim uzun pozlama (NCC + medyan) bunun akrabası; Laplacian "en keskin" yedeği zaten var — korunacak.
- Cihaza-duyarlı tier (HIGH/MED/LOW, eşik 0.7/0.6/0.5) deseni.

- https://developer.android.com/agents/skills/camera/camerax/references/low-light
- https://github.com/Kotohibi/Extract_sharpest_frame
- https://opencv.org/autofocus-using-opencv-a-comparative-study-of-focus-measures-for-sharpness-assessment/
- https://docs.bswen.com/blog/2026-03-11-camerax-budget-device-failures/

---

## 5. OpenCV / klasik görüntü işleme — ne kullanılmış

### 5.1. Belge sınırı (standart hat)

Gri → GaussianBlur → Otsu → Canny(50,150) → `findContours` → alana göre ilk 5 → `approxPolyDP(0.02·çevre)` ile 4 köşe → `getPerspectiveTransform` + `warpPerspective`. Girdi ~1080 px'e küçültülür (mobil optimizasyonu). Uyarı: parlak zemin/düşük kontrast/karmaşık arka planda kırılgan → üretimde ML tabanlı kenar öneriliyor (Scanbot).

Android örnekleri: `LiveEdgeDetection` (~368★, canlı kenar + kırpma), `aashari/example-android-camera-scanner` (CamScanner benzeri + OCR), `liltof/android-scanner` (elle köşe düzeltme).

- https://learnopencv.com/automatic-document-scanner-using-opencv/
- https://scanbot.io/techblog/document-edge-detection-with-opencv/
- https://github.com/adityaarora1/LiveEdgeDetection
- https://github.com/aashari/example-android-camera-scanner

### 5.2. Satır → paragraf/soru kümeleme (3 teknik)

- **Projeksiyon profili:** satır/dikey piksel toplamları; metin bantları vs boşluklar; dikey profil iki kolonlu sayfada sütun ayırır. O(rows×cols).
- **Greedy kutu birleştirme:** y'ye göre sırala, dikey boşluk < eşik (0.5–1.0 × medyan yükseklik) + yatay örtüşme → birleştir; önce satıra kümele, satır içinde soldan sağa. Bizim `SoruAlgilayici` bu ailenin en basit üyesi.
- **DBSCAN/k-means:** kutu merkezleri, `eps` metin yüksekliğine ölçekli; akademik iki-seviyeli GCN (kelime→satır→paragraf, arXiv 2203.09638).

- https://www.geeksforgeeks.org/python/projection-profile-method/
- https://stackoverflow.com/questions/55376338/how-to-join-nearby-bounding-boxes-in-opencv-python
- https://arxiv.org/abs/2203.09638

### 5.3. İçerik + geometri hibritleri (bizim v1.1'in akrabaları)

- **OMR (en ucuz hibrit):** perspektif düzelt → sabit ızgara → `THRESH_BINARY_INV` + `countNonZero` → satırda `argmax` = işaretli şık.
- **`ocr-qa-segmentation`:** satır özellikleri (girinti, dikey boşluk, `Q1` regex + fuzzy Q→O/0, noktalama, kelime sayısı) + BIO etiketleme → ~%90 F1, ~1 sayfa/sn CPU, 0.5–2 GB RAM.
- **`segmentation_olm_regex`:** güçlü işaret (`Q1`, `Question 1`) anında sınır; zayıf (`1.`, `2)`) sıra kurallarıyla; iç-liste bastırma.
- Türkçe Android OMR: `yusufaytin/Android-Optical-Form-Reader`, `okulyonetim/optikokuyucu`; ticari: Test Plus, ABC Optik.

- https://pyimagesearch.com/2016/10/03/bubble-sheet-multiple-choice-scanner-and-test-grader-using-omr-python-and-opencv/
- https://github.com/Abhigyan-Shekhar/ocr-qa-segmentation
- https://github.com/Sakshikhairnar02/segmentation_olm_regex
- https://github.com/yusufaytin/Android-Optical-Form-Reader

### 5.4. Gölge/kırışıklık/eğim

- `adaptiveThreshold` (yalnız tek kanal — Android'de renkli verilirse patlar), CLAHE (`clipLimit 2.0`, 8x8 tile; Java API'de `org.opencv.imgproc.CLAHE` var), morfolojik arka-plan bölme (hafif, mobil-dostu), Multi-Scale Retinex (ağır, küçültülmüş görüntüde), deskew (`minAreaRect`/Hough + `warpAffine`).

- https://docs.opencv.org/4.13.0/d7/d4d/tutorial_py_thresholding.html
- https://opencv.org/shadow-correction-using-opencv/
- https://stackoverflow.com/questions/44752240/how-to-remove-shadow-from-scanned-images-using-opencv

### 5.5. Maliyet

Resmi OpenCV ~50–66 MB APK; `opencv-mobile` 303 MB → 25 MB; ABI filtresi (~4× tasarruf) + App Bundle (35 MB → 8–15 MB) + R8 (%50–70). Canny/kontur hafif; GrabCut saniyeler sürer; MSR büyük kernelde yavaş.

- https://github.com/nihui/opencv-mobile
- https://stackoverflow.com/questions/42019028/how-to-reduce-the-apk-size-of-my-android-app-with-opencv-library-integrated

**Projeye çıkarım:** OpenCV soru sınırını vermez, APK şişirir → v1'de yok. Hibrit regex+geometri fikri doğrulandı.

---

## 6. TFLite / YOLO ile özel tespit — ne kullanılmış

- Denge: SSD-MobileNet en hızlı/en düşük mAP; YOLOv8n mobilde gerçek zamanlıya yakın; YOLOv8m/EfficientDet en doğru ama yavaş. INT8 ~4× küçülme, ~3× hız (ama küçük ağda doğruluk kaybı uyarısı).
- Ölçümler (cihaza özgü): YOLOv8n INT8 320x320 ≈ 28 ms (~35 fps), 640x640 ≈ 68 ms; YOLOv8s 640 ≈ 145 ms (Snapdragon 870, NNAPI; NNAPI GPU'dan 2–4× hızlı).
- Veri: transfer learning ile sınıf başına ~50 görüntü ilk model, birkaç yüz görüntü üretim kalitesi; INT8 kalibrasyon 100–200 görüntü.
- Belgeye özel: YOLO-Pose (doğrudan 4 köşe), YOLO-Seg (maskeden köşe), DocLayout-YOLO (sayfa-içi bölge).
- **ML Kit sınırı:** Object Detection'da özel model yalnız sınıflandırıcı olur, bbox üreticisi değişmez → domain kutu (soru bölgesi) için TFLite şart.

- https://arxiv.org/html/2409.16808v1
- https://deepparmar.in/blogs/object-detection-android-yolo-tflite
- https://roboflow.com/blog/how-to-train-a-tensorflow-lite-object-detection-model
- https://developers.google.com/ml-kit/vision/object-detection
- https://github.com/oboard/YOLO-Based_Corner_Extraction_Methods_for_Document_Image_Rectification

**Projeye çıkarım:** son çare olarak planda duruyor; v1'de yok.

---

## 7. Otomatik kırpma UX desenleri — uygulamalar ne yapıyor

### 7.1. Kanıtlanmış 4 katman

1. **Statik kılavuz + snap:** canlı quad overlay, köşe yüksek-kontrast kenara yapışınca haptik "detent"; 400 ms sabitlikte oto-çekim (Scanee); durum makinesi `DETECTING → STAY_STILL → CAPTURED` + talimat kodları (`MOVE_CLOSER`, `HOLD_STILL`…) (docuSnap); Adobe Scan mavi köşe noktaları + "Looking for document → hold steady".
2. **Temporal smoothing:** EMA (`α·yeni + (1-α)·eski`, α 0.1–0.5) + Kalman hız tahmini + kayıp doldurma (Roboflow Stabilizer; docuSnap 8× Kalman1D, Q=0.01 R=12.0); **IoU ≥ 0.85 + alan-delta ≤ 0.15, 3 ardışık kare** → oto-çekim (Dynamsoft `QuadStabilizer`). Bizim değerler (EMA 0.35, IoU 0.72) sektör aralığında ama gevşek tarafta.
3. **Güven + fallback:** kalite kapısı (keskinlik/parlaklık/parlama, 10 ardışık geçen kare) olmadan yakalama yok; tespit yoksa 500 ms sonra ham kare + "No document detected" (Dynamsoft); Adobe "Capture manually"ye düşer. = Bizim "statik kılavuza düşüş" planımızla birebir.
4. **Kilit-sonrası quad editör:** 4 bağımsız köşe + offset loupe (64 px üstte 2× büyüteç, 48 px dokunma hedefi); "Auto-Detect" tekrarı + Straighten (Adobe); retake + çok-sayfa.

- https://github.com/petermartis/docuSnap
- https://docs.roboflow.com/workflows/blocks/blocks/video-processing/detections-stabilizer
- https://www.dynamsoft.com/codepool/build-flutter-mobile-document-scanner.html
- https://www.adobe.com/devnet-docs/adobescan/android/en/scan.html
- https://dpopstudio.co/blog/inside-scanee-architecting-mobile-document-scanner-ux-figma-react-native

### 7.2. Uygulamalar

- **Photomath:** statik yeniden-boyutlandırılabilir viewfinder (sürükle-büyüt), oto-detect yok gibi; 2–3 sn sonuçsuzsa manuel klavyeye düşüş.
- **Microsoft Lens:** turuncu çerçeve → oto kırp + perspektif + kontrast + gölge temizleme → manuel crop/rotate/filtre.
- **Google Drive (2026):** Smart Batch (hover ile ardışık oto-çekim), Auto-Best Frame, Duplicate Detection; tamamı on-device (ikincil kaynak; ≥8 GB RAM iddiası teyitli değil).
- **Scanbot SDK:** klasik Canny'den ML kenar tespitine geçmiş — zor koşul dayanıklılığı için.

- https://support.google.com/photomath/answer/14333327
- https://support.microsoft.com/en-us/lens/microsoft-lens-for-android
- https://scanbot.io/techblog/document-edge-detection-with-opencv/

---

## 8. Projeye somut çıkarımlar (öncelik sırasıyla)

| # | Araştırma bulgusu | Bizdeki karşılık | Plan |
|---|---|---|---|
| 1 | 640x480 varsayılan OCR'ı öldürür; 1280x720 standart | `setTargetResolution` yok | v1.1-1: sabitle + kare süresi ölç |
| 2 | Soru sınırı = regex + geometri (sektör standardı) | Metin çöpe gidiyor | v1.1-2: `Satir(metin,kutu)` + soru/şık regex |
| 3 | Unbundled model inmeden istekler sessiz başarısız | "Bazen hiç çalışmıyor" + çevrimdışı şartı | v1.1-3: manifest meta-data + hazır-kontrol + UI uyarısı |
| 4 | Refine = nihai OCR çekim karesinde (üretim standardı) | Preview/analiz/çekim uyumsuzluğu | v1.2: deklanşörde tam çözünürlükte yeniden-tanıma |
| 5 | IoU 0.85 + 3 stabil kare + kalite kapısı | IoU 0.72, kapı yok | v1.2: oylama + güven eşiği + oto-çekim guard'ı |
| 6 | Fallback = statik/ham kare + açık mesaj | Toggle ile kısmen yapıldı (v1.0) | Chip mesajları + model-yok uyarısı eklenecek |
| 7 | ViewPort+UseCaseGroup WYSIWYG | Elle oran matematiği | v1.2'de değerlendir (risk: bağlama mimarisi değişir) |
| 8 | Quad editör + loupe (kilit sonrası sigorta) | `ImageCropperDialog` var ama çekim-öncesi bağlı değil | v2: çekim-öncesi onay ekranı |
| 9 | OpenCV ~25–66 MB, soru sınırı vermez | Yok | Ertelendi |
| 10 | TFLite 50–birkaç yüz etiket ister | Yok | Son çare |

---

## 9. Doğrulanamayan / temkinli notlar

- %34→%79, 300/450 ms, tier eşikleri (bswen blogları) anekdot; kendi cihazımızda A/B ölçümü şart.
- DodaTech'teki `setTargetFrameRate()` resmi API olarak doğrulanamadı.
- Photomath/Lens/Drive iç algoritmaları kapalı kutu; yalnız kullanıcı-dokümanı davranışı.
- Drive 2026 "8 GB RAM" iddiası tek ikincil kaynak.
- Sürüm numaraları (19.0.1 / 16.0.0 / 16.0.1) araştırma anındaki resmi dokümandan; entegrasyonda günceli kontrol edilecek.
