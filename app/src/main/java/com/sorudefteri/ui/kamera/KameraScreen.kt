package com.sorudefteri.ui.kamera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.kamera.KameraModu
import com.sorudefteri.domain.kamera.KirpmaHesaplayici
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.KutuOrani
import com.sorudefteri.domain.kamera.TaramaDurumu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * CameraX tabanlı tam ekran kamera arayüzü.
 *
 * Modlar:
 *  - TEKLİ Soru: uyarlanan 4 köşeli tarama alanı (ML Kit metin satırları) +
 *    bölge kilitleme + otomatik çekim (ayarlanabilir) + mini çerçeveye kırpma.
 *  - ÇOKLU Soru: hiçbir kırpma olmayan geniş kare ("tek karede birden çok soru").
 *
 * Ayarlar (⚙): Uzun Pozlama (çok kare → medyan birleştirme) ve Otomatik Çekim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KameraScreen(
    modifier: Modifier = Modifier,
    isSolution: Boolean = false,
    onImageCaptured: (imagePath: String) -> Unit = {},
    onPhotoCaptured: ((String) -> Unit)? = null,
    onClose: () -> Unit = {}
) {
    val handleCapture = onPhotoCaptured ?: onImageCaptured
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val viewModel: KameraViewModel = hiltViewModel()
    val uzunPozlama by viewModel.uzunPozlamaAkisi.collectAsStateWithLifecycle()
    val otomatikCekim by viewModel.otomatikCekimAkisi.collectAsStateWithLifecycle()
    val taramaBilgi by viewModel.taramaBilgi.collectAsStateWithLifecycle()

    // Kamera İzni Durumu
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            scope.launch { snackbarHostState.showSnackbar("Kamera izni verilmedi") }
        }
    }

    // Galeriden Seçici
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleCapture(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        // İzin Yoksa Gösterilecek Ekran
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Kamera İzni Gerekli",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSolution) "Çözümün fotoğrafını çekebilmek için lütfen kamera erişimine izin verin."
                    else "Sorunun fotoğrafını çekebilmek için lütfen kamera erişimine izin verin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kamera İznini Ver")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galeriden Fotoğraf Seç")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onClose) {
                    Text("Vazgeç")
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    // ==================== Kamera Durumları ====================
    var kameraModu by remember { mutableStateOf(KameraModu.TEKLI) }
    var ayarSayfasiAcik by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var flashShutterEffect by remember { mutableStateOf(false) }
    var islemMesaji by remember { mutableStateOf<String?>(null) }
    var odakNokta by remember { mutableStateOf<Offset?>(null) }

    val analizKontrolcu = remember { AnalizKontrolcu() }
    var onizlemeBoyut by remember { mutableStateOf(IntSize.Zero) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val metinAnalizci = remember(viewModel) {
        MetinAnalizci(analizKontrolcu) { satirlar, g, y ->
            viewModel.onAnalizSonuc(satirlar, g, y)
        }
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderInstance?.unbindAll()
            } catch (_: Exception) {}
            try {
                metinAnalizci.kapat()
            } catch (_: Exception) {}
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(imageAnalysis, metinAnalizci, cameraExecutor) {
        imageAnalysis.setAnalyzer(cameraExecutor, metinAnalizci)
    }

    // Mod <—> analiz aktiflik; mod değişince tarama stabilitesi sıfırlanır
    LaunchedEffect(kameraModu) {
        analizKontrolcu.acik = kameraModu == KameraModu.TEKLI
        viewModel.taramayiSifirla()
    }

    // Lens değişince yansıtma işareti + tarama sıfırlama
    LaunchedEffect(lensFacing) {
        analizKontrolcu.guncelle(lensFacing)
        viewModel.taramayiSifirla()
    }

    // Bağlama (Lens / Önizleme hazır olunca)
    LaunchedEffect(lensFacing, lifecycleOwner, previewView) {
        val currentPreview = previewView ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderInstance = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = currentPreview.surfaceProvider
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                imageCapture.flashMode = flashMode

                cameraProvider.unbindAll()
                cameraInstance = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Önizleme boyutu + statik kılavuz çerçevesi (px)
    val density = LocalDensity.current
    val cerceveKutu: Kutu? = remember(onizlemeBoyut) {
        if (onizlemeBoyut == IntSize.Zero) null else {
            val manzara = onizlemeBoyut.width > onizlemeBoyut.height
            with(density) {
                val yanal = 24.dp.roundToPx()
                val dikey = 96.dp.roundToPx()
                KirpmaHesaplayici.statikCerceve(
                    gorunumGen = onizlemeBoyut.width,
                    gorunumYuk = onizlemeBoyut.height,
                    ustAlanPx = dikey,
                    altAlanPx = dikey,
                    yanalAlanPx = yanal,
                    oranGen = if (manzara) 4 else 3,
                    oranYuk = if (manzara) 3 else 4,
                    dolguKatsayisi = if (manzara) 0.85 else 0.75
                )
            }
        }
    }

    // Algılanan tarama alanının overlapping px kutusu (çizim için)
    val aynaliMi = lensFacing == CameraSelector.LENS_FACING_FRONT
    val taramaAlanKutuPx: Kutu? = remember(
        taramaBilgi.alan, taramaBilgi.analizGen, taramaBilgi.analizYuk, onizlemeBoyut, aynaliMi
    ) {
        val alan = taramaBilgi.alan
        if (alan == null || taramaBilgi.analizGen <= 0 || onizlemeBoyut == IntSize.Zero) null
        else KirpmaHesaplayici.gorselOraniGorunumPxne(
            alan,
            taramaBilgi.analizGen,
            taramaBilgi.analizYuk,
            onizlemeBoyut.width,
            onizlemeBoyut.height,
            aynaliMi
        )
    }

    // ==================== Çekim hattı ====================
    /** Şu anki kadraj seçimini kapsayan hedef oranı üreticisi. */
    val hedefOraniUret: (Int, Int) -> KutuOrani = { tamGen, tamYuk ->
        if (kameraModu == KameraModu.COKLU) {
            KutuOrani(0.0, 0.0, 1.0, 1.0)
        } else {
            KameraGoruntuIslemleri.hedefOrani(
                soruOrani = taramaBilgi.alan,
                cerceveKutu = cerceveKutu,
                tamGen = tamGen,
                tamYuk = tamYuk,
                onizlemeGen = onizlemeBoyut.width,
                onizlemeYuk = onizlemeBoyut.height,
                aynaliMi = lensFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    val cekimBitti: (String) -> Unit = { yolu ->
        flashShutterEffect = false
        islemMesaji = null
        isCapturing = false
        handleCapture(yolu)
    }

    val yakalayici: () -> Unit = {
        if (!isCapturing) {
            isCapturing = true
            flashShutterEffect = true
            scope.launch {
                try {
                    if (uzunPozlama) {
                        islemMesaji = "Sabit tutun — kareler alınıyor…"
                        val kareYollari = ArrayList<String>(UZUN_POZLAMA_KARE_SAYISI)
                        repeat(UZUN_POZLAMA_KARE_SAYISI) {
                            val kare = File(context.cacheDir, "soru_${UUID.randomUUID()}.jpg")
                            if (tekKareCek(kare, imageCapture, cameraExecutor)) {
                                kareYollari.add(kare.absolutePath)
                            } else {
                                try {
                                    kare.delete()
                                } catch (_: Exception) {}
                            }
                        }
                        if (kareYollari.isEmpty()) {
                            throw IllegalStateException("Kareler çekilemedi")
                        }
                        islemMesaji = "Kareler birleştiriliyor…"
                        val sonuc = withContext(Dispatchers.Default) {
                            KameraGoruntuIslemleri.uzunPozlamaIsle(
                                kareYollari,
                                hedefOraniUret,
                                context.cacheDir
                            )
                        } ?: run {
                            // Hizalama/birleştirme başarısız → en keskin kareyi kırp
                            val ilk = kareYollari.first()
                            val yol = withContext(Dispatchers.Default) {
                                KameraGoruntuIslemleri.tekCekimIsle(ilk, hedefOraniUret)
                            }
                            kareYollari.drop(1).forEach { p ->
                                try {
                                    File(p).delete()
                                } catch (_: Exception) {}
                            }
                            yol
                        }
                        cekimBitti(sonuc)
                    } else {
                        val kare = File(context.cacheDir, "soru_${UUID.randomUUID()}.jpg")
                        val cekildi = tekKareCek(kare, imageCapture, cameraExecutor)
                        if (!cekildi) throw IllegalStateException("Fotoğraf çekilemedi")
                        val sonuc = withContext(Dispatchers.Default) {
                            KameraGoruntuIslemleri.tekCekimIsle(kare.absolutePath, hedefOraniUret)
                        }
                        cekimBitti(sonuc)
                    }
                } catch (t: Throwable) {
                    flashShutterEffect = false
                    islemMesaji = null
                    isCapturing = false
                    snackbarHostState.showSnackbar("Fotoğraf çekilemedi: ${t.message ?: "bilinmeyen hata"}")
                }
            }
        }
    }

    // Otomatik çekim: KILITLI'ya gelince biraz sonra tetikle (belirtilen kararlılık penceresi)
    LaunchedEffect(otomatikCekim, kameraModu, taramaBilgi.durum) {
        if (otomatikCekim &&
            kameraModu == KameraModu.TEKLI &&
            taramaBilgi.durum == TaramaDurumu.KILITLI
        ) {
            delay(600)
            if (kameraModu == KameraModu.TEKLI &&
                taramaBilgi.durum == TaramaDurumu.KILITLI
            ) {
                yakalayici()
            }
        }
    }

    // Odak halkası bir süre sonra kaybolur
    LaunchedEffect(odakNokta) {
        if (odakNokta != null) {
            delay(1200)
            odakNokta = null
        }
    }

    // ==================== Arayüz ====================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Önizleme View'ı (COMPATIBLE: overlay dönüşümleri için güvenilir)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also {
                    it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    it.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = it
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { onizlemeBoyut = it },
            update = {
                imageCapture.flashMode = flashMode
            }
        )

        // Tarama katmanı: dokun-odak + pinch zoom + uyarlanan alan çizimi
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraInstance) {
                    detectTapGestures(onTap = { offset ->
                        val kamera = cameraInstance ?: return@detectTapGestures
                        val view = previewView ?: return@detectTapGestures
                        val nokta = view.meteringPointFactory.createPoint(offset.x, offset.y)
                        kamera.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(nokta).build()
                        )
                        odakNokta = offset
                    })
                }
                .pointerInput(cameraInstance) {
                    detectTransformGestures { _, _, olcekDegisimi, _ ->
                        val kamera = cameraInstance ?: return@detectTransformGestures
                        val zoomDurumu = kamera.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val yeni = (zoomDurumu.zoomRatio * olcekDegisimi)
                            .coerceIn(zoomDurumu.minZoomRatio, zoomDurumu.maxZoomRatio)
                        if (abs(yeni - zoomDurumu.zoomRatio) > 0.001f) {
                            kamera.cameraControl.setZoomRatio(yeni)
                        }
                    }
                }
        ) {
            val genislik = size.width
            val yukseklik = size.height
            if (genislik <= 0 || yukseklik <= 0) return@Canvas

            if (kameraModu == KameraModu.TEKLI) {
                val kutu = taramaAlanKutuPx
                    ?: cerceveKutu?.let { Kutu(it.sol, it.ust, it.genislik, it.yukseklik) }
                if (kutu != null) {
                    val vurgu = when (taramaBilgi.durum) {
                        TaramaDurumu.KILITLI -> Color(0xFF4CAF50)
                        TaramaDurumu.BULUNDU -> Color(0xFFFFCA28)
                        TaramaDurumu.ARANIYOR -> Color.White.copy(alpha = 0.6f)
                    }

                    // Alan dışını karart
                    val k = kutu
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, 0f), size.copy(width = genislik, height = k.ust.toFloat().coerceAtLeast(0f)))
                    drawRect(
                        Color.Black.copy(alpha = 0.45f),
                        Offset(0f, (k.ust + k.yukseklik).toFloat()),
                        size.copy(width = genislik, height = (yukseklik - k.ust - k.yukseklik).coerceAtLeast(0f))
                    )
                    drawRect(
                        Color.Black.copy(alpha = 0.45f),
                        Offset(0f, k.ust.toFloat()),
                        size.copy(width = k.sol.toFloat().coerceAtLeast(0f), height = k.yukseklik.toFloat())
                    )
                    drawRect(
                        Color.Black.copy(alpha = 0.45f),
                        Offset((k.sol + k.genislik).toFloat(), k.ust.toFloat()),
                        size.copy(
                            width = (genislik - k.sol - k.genislik).coerceAtLeast(0f),
                            height = k.yukseklik.toFloat()
                        )
                    )

                    // Kalın köşe braketleri
                    val kal = if (taramaBilgi.durum == TaramaDurumu.ARANIYOR) 4f else 6f
                    val bo = kal * 5f
                    val kSol = k.sol.toFloat()
                    val kUst = k.ust.toFloat()
                    val kSag = (k.sol + k.genislik).toFloat()
                    val kAlt = (k.ust + k.yukseklik).toFloat()
                    // 4 köşe: (startX, startY, (dikeyYön, yatayYön))
                    val koseler = listOf(
                        Triple(kSol, kUst, 1f to 1f),
                        Triple(kSag, kUst, -1f to 1f),
                        Triple(kSol, kAlt, 1f to -1f),
                        Triple(kSag, kAlt, -1f to -1f)
                    )
                    koseler.forEach { (kx, ky, yonler) ->
                        val (yYon, xYon) = yonler
                        drawLine(
                            vurgu,
                            Offset(kx, ky),
                            Offset(kx + bo * xYon, ky),
                            strokeWidth = kal
                        )
                        drawLine(
                            vurgu,
                            Offset(kx, ky),
                            Offset(kx, ky + bo * yYon),
                            strokeWidth = kal
                        )
                    }

                    // Kılıf çizgisi (ince)
                    drawRoundRect(
                        color = vurgu.copy(alpha = 0.55f),
                        topLeft = Offset(kSol, kUst),
                        size = androidx.compose.ui.geometry.Size(k.genislik.toFloat(), k.yukseklik.toFloat()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                        style = Stroke(width = 2f)
                    )
                }

                // Odak halkası
                odakNokta?.let { odak ->
                    val yari = 44f
                    drawCircle(
                        Color.White.copy(alpha = 0.9f),
                        radius = yari,
                        center = odak,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(Color.White.copy(alpha = 0.6f), radius = 4f, center = odak)
                }
            } else {
                // ÇOKLU: köşe vurguları yalnızca çerçevelendirmeye yardımcı olur
                val ken = 28.dp.toPx()
                val ic = 20.dp.toPx()
                val koseler = listOf(
                    Triple(ic, ic, 1f to 1f),
                    Triple(genislik - ic, ic, -1f to 1f),
                    Triple(ic, yukseklik - ic, 1f to -1f),
                    Triple(genislik - ic, yukseklik - ic, -1f to -1f)
                )
                koseler.forEach { (kx, ky, yonler) ->
                    val (yYon, xYon) = yonler
                    drawLine(
                        Color.White.copy(alpha = 0.5f),
                        Offset(kx, ky),
                        Offset(kx + ken * xYon, ky),
                        strokeWidth = 4f
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.5f),
                        Offset(kx, ky),
                        Offset(kx, ky + ken * yYon),
                        strokeWidth = 4f
                    )
                }
            }
        }

        // Deklanşör Beyaz Flaş Efekti
        AnimatedVisibility(
            visible = flashShutterEffect,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }

        // Durum çipi + "Yeniden tara"
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (kameraModu == KameraModu.TEKLI && taramaBilgi.durum != TaramaDurumu.ARANIYOR) {
                        val renk = when (taramaBilgi.durum) {
                            TaramaDurumu.KILITLI -> Color(0xFF4CAF50)
                            else -> Color(0xFFFFCA28)
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(renk)
                        )
                    }
                    Text(
                        text = when {
                            kameraModu == KameraModu.COKLU ->
                                "Çoklu mod — sayfanın tamamı tek karede"
                            taramaBilgi.durum == TaramaDurumu.KILITLI ->
                                if (otomatikCekim) "Soru kilitlendi — çekiliyor…"
                                else "Soru kilitlendi — deklanşöre basın"
                            taramaBilgi.durum == TaramaDurumu.BULUNDU ->
                                "Soru algılandı — sabit tutun"
                            else ->
                                if (isSolution) "Adım 2 — Soru/madeni çerçeveye getirin"
                                else "Adım 1 — Soruyu çerçeveye getirin"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }

            if (kameraModu == KameraModu.TEKLI &&
                taramaBilgi.durum == TaramaDurumu.KILITLI &&
                !isCapturing
            ) {
                TextButton(
                    onClick = { viewModel.taramayiSifirla() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f)
                    )
                ) {
                    Text("Yeniden tara", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Üst Kontrol Çubuğu (Kapat, Flaş, Kamera Değiştir, Ayarlar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kapat Butonu
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }

            // Takma başlık (izın üstünde duran boş merkez)
            Spacer(modifier = Modifier.width(96.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Flaş Modu Değiştirme
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                            imageCapture.flashMode = flashMode
                        }
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                                else -> Icons.Default.FlashAuto
                            },
                            contentDescription = "Flaş Modu",
                            tint = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> Color.White.copy(alpha = 0.6f)
                                else -> Color(0xFFFFD54F)
                            }
                        )
                    }
                }

                // Ön / Arka Kamera Geçişi
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Kamerayı Çevir",
                            tint = Color.White
                        )
                    }
                }

                // Kamera ayarları (⚙)
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    IconButton(
                        onClick = { ayarSayfasiAcik = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Kamera Ayarları",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // İşleme örtüsü (uzun pozlama aşamalarında)
        if (islemMesaji != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = islemMesaji ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Alt Panel: Mod seçici + kontrol çubuğu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mod Seçici: TEKLİ | ÇOKLU
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val arkaPlani = remember { RoundedCornerShape(22.dp) }
                listOf(
                    KameraModu.TEKLI to "Tekli Soru",
                    KameraModu.COKLU to "Çoklu Soru"
                ).forEach { (modadi, etiket) ->
                    val secili = kameraModu == modadi
                    Surface(
                        shape = arkaPlani,
                        color = if (secili) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) {
                            if (kameraModu != modadi && !isCapturing) kameraModu = modadi
                        }
                    ) {
                        Text(
                            text = etiket,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (secili) MaterialTheme.colorScheme.onPrimary
                            else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (kameraModu == KameraModu.TEKLI)
                    "Soru alanı otomatik algılanır · dokun-odak · sıkıstır-zoom"
                else
                    "Kırpma yok · tüm sayfa tek karede kaydedilir",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            // Kontrol Çubuğu (Galeri, Deklanşör)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Galeri Butonu
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Galeriden Seç",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Deklanşör (Shutter) Butonu
                val shutterScale by animateFloatAsState(
                    targetValue = if (isCapturing) 0.88f else 1f,
                    animationSpec = tween(100),
                    label = "shutterScale"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .clickable(
                            enabled = !isCapturing,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ) {
                            yakalayici()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (uzunPozlama) Color(0xFFB2DFDB) else Color.White,
                        modifier = Modifier.size(66.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black.copy(alpha = 0.2f))
                    ) {
                        if (isCapturing) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (uzunPozlama) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "4×",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00695C)
                                )
                            }
                        }
                    }
                }

                // Dengeli yerleşim için boş alan
                Spacer(modifier = Modifier.size(52.dp))
            }
        }

        // Kamera Ayarları sheet
        if (ayarSayfasiAcik) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { ayarSayfasiAcik = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Kamera Ayarları",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    KameraAyarSatiri(
                        baslik = "Uzun Pozlama",
                        aciklama = "Deklanşöre bastığınızda birden çok kare alınır ve akıllıca birleştirilir; düşük ışıkta netlik belirgin artar. Çekim 1-2 saniye uzar.",
                        secili = uzunPozlama,
                        onDegisti = { viewModel.setUzunPozlama(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    KameraAyarSatiri(
                        baslik = "Otomatik Çekim",
                        aciklama = "Soru kilitlendiğinde fotoğraf otomatik çekilir. Yalnız \"Tekli Soru\" modunda etkili.",
                        secili = otomatikCekim,
                        onDegisti = { viewModel.setOtomatikCekim(it) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

@Composable
private fun KameraAyarSatiri(
    baslik: String,
    aciklama: String,
    secili: Boolean,
    onDegisti: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = baslik,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = aciklama,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = secili, onCheckedChange = onDegisti)
    }
}

/** Tek kare çekimini callback'ten suspend'e dönüştürür (recommended sequential kullanım). */
private suspend fun tekKareCek(hedef: File, imageCapture: ImageCapture, executor: Executor): Boolean {
    return suspendCancellableCoroutine { devam ->
        val secenekler = ImageCapture.OutputFileOptions.Builder(hedef).build()
        imageCapture.takePicture(
            secenekler,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (devam.isActive) devam.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (devam.isActive) devam.resume(false)
                }
            }
        )
    }
}

private const val UZUN_POZLAMA_KARE_SAYISI = 4
