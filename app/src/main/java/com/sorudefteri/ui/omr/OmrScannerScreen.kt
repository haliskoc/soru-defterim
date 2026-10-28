package com.sorudefteri.ui.omr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.domain.omr.vision.FiducialDetector
import java.util.concurrent.Executors

/**
 * Kamera Tabanlı Optik Okuyucu (OMR Scanner) Ekranı.
 *
 * Özellikler:
 * - CameraX PreviewView ve Y-plane ImageAnalysis ile canlı 4 köşe Fiducial takibi.
 * - Animasyonlu HUD vizör çerçevesi ve köşe köşegen kilit kutucukları (Yeşil = Kilitlendi, Sarı/Beyaz = Aranıyor).
 * - Eşleşen deneme sınavı bilgi çipi ve deneme seçici dialog.
 * - 📸 "Optik Formu Tara" (Kamera karesini çekip analiz eder).
 * - 🖼️ "Galeriden Seç" (Görsel seçip CV motoruna iletir).
 * - 🧪 "Örnek Optik Simülasyonu" (Fiziksel kağıt olmadan emülatörde anında uçtan uca test imkanı).
 * - İşlem süresince aşamalı yükleme göstergesi.
 */
@Composable
fun OmrScannerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSonuc: (Long) -> Unit,
    initialDenemeId: Long? = null,
    initialTestId: String? = null,
    viewModel: OmrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allDenemeler by viewModel.allDenemeler.collectAsStateWithLifecycle()

    var showDenemePicker by remember { mutableStateOf(false) }

    // Başlangıç parametrelerini viewModel'e aktar
    LaunchedEffect(initialDenemeId, initialTestId) {
        viewModel.initScanner(initialDenemeId, initialTestId)
    }

    // Kamera İzni Durumu
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Galeri Seçici Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.analyzeBitmap(bitmap) { savedId ->
                            onNavigateToSonuc(savedId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OmrScannerScreen", "Görsel yüklenirken hata oluştu", e)
            }
        }
    }

    // CameraX bileşenleri
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Kamera Yaşam Döngüsü Bağlantısı
    if (hasCameraPermission) {
        DisposableEffect(lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val planes = imageProxy.planes
                            if (planes.isNotEmpty()) {
                                val buffer = planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val detection = FiducialDetector.detectFromYPlane(
                                    yPlane = bytes,
                                    width = imageProxy.width,
                                    height = imageProxy.height,
                                    rowStride = planes[0].rowStride,
                                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                )
                                viewModel.onLiveFrameDetection(detection)
                            }
                        } catch (e: Exception) {
                            Log.e("OmrScannerScreen", "Frame analizi hatası", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("OmrScannerScreen", "Kamera bağlanamadı", e)
            }

            onDispose {
                cameraProvider.unbindAll()
                analysisExecutor.shutdown()
            }
        }
    }

    // Flaş Aç/Kapa Tetikleyicisi
    LaunchedEffect(isFlashOn, camera) {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isFlashOn)
            }
        }
    }

    // Fotoğraf çekip OMR analizine gönderen yardımcı fonksiyon
    fun captureFrameAndAnalyze() {
        if (uiState.isProcessing) return
        val mainExecutor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val rawBitmap = imageProxy.toBitmap()
                    val correctedBitmap = if (rotationDegrees != 0) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        Bitmap.createBitmap(
                            rawBitmap,
                            0,
                            0,
                            rawBitmap.width,
                            rawBitmap.height,
                            matrix,
                            true
                        )
                    } else {
                        rawBitmap
                    }
                    imageProxy.close()
                    viewModel.analyzeBitmap(correctedBitmap) { savedId ->
                        onNavigateToSonuc(savedId)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("OmrScannerScreen", "Fotoğraf çekilemedi", exception)
                }
            }
        )
    }

    // =========================================================================
    // EKRAN GÖRÜNÜMÜ
    // =========================================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // 1. Canlı Kamera Önizleme
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 2. HUD Vizör Çerçevesi ve 4 Köşe Kilit Kutucukları
            OmrVisorOverlay(
                detectionResult = uiState.liveDetection,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Kamera İzni Yoksa Şık Bilgilendirme Kartı
            CameraPermissionCard(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSimulate = {
                    viewModel.simulateScan(onScanCompleted = onNavigateToSonuc)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        // 3. Üst Bar: Geri, Başlık, Flaş Butonu
        OmrTopBar(
            title = "Optik Form Okuyucu",
            isFlashOn = isFlashOn,
            onToggleFlash = { isFlashOn = !isFlashOn },
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 4. Deneme Eşleşme Çipi
        OmrDenemeMatchChip(
            selectedTitle = uiState.examTitle,
            sinavTuru = uiState.sinavTuru,
            testId = uiState.selectedTestId,
            isMatched = uiState.selectedDenemeId != null || uiState.selectedTestId.isNotBlank(),
            onClick = { showDenemePicker = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        )

        // 5. Alt Kontrol Paneli (📸 Tara, 🖼️ Galeri, 🧪 Simülasyon)
        OmrBottomBar(
            isProcessing = uiState.isProcessing,
            hasCamera = hasCameraPermission,
            onCapture = { captureFrameAndAnalyze() },
            onPickGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSimulate = {
                viewModel.simulateScan(onScanCompleted = onNavigateToSonuc)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // 6. Yükleme ve Analiz Durumu Göstergesi
        if (uiState.isProcessing) {
            OmrProcessingOverlay(
                statusMessage = uiState.statusMessage ?: "Optik form okunuyor, cevaplar analiz ediliyor...",
                modifier = Modifier.fillMaxSize()
            )
        }

        // 7. Hata Bildirimi (Varsa)
        uiState.errorMessage?.let { error ->
            OmrErrorBanner(
                message = error,
                onDismiss = { /* viewModel hata sıfırlama */ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp, start = 16.dp, end = 16.dp)
            )
        }
    }

    // Deneme Seçici Dialog
    if (showDenemePicker) {
        DenemePickerDialog(
            denemeler = allDenemeler,
            selectedId = uiState.selectedDenemeId,
            onSelect = { selected ->
                viewModel.selectExistingDeneme(selected)
                showDenemePicker = false
            },
            onDismiss = { showDenemePicker = false }
        )
    }
}

/**
 * Optik form hizalaması için HUD vizör çerçevesi ve 4 köşe Fiducial kilit animasyonu.
 */
@Composable
private fun OmrVisorOverlay(
    detectionResult: FiducialDetector.DetectionResult?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(modifier = modifier) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // A4 en-boy oranı (~1 : 1.414)
            var visorW = w * 0.84f
            var visorH = visorW * 1.414f
            if (visorH > h * 0.68f) {
                visorH = h * 0.68f
                visorW = visorH / 1.414f
            }

            val left = (w - visorW) / 2f
            val top = (h - visorH) / 2f
            val right = left + visorW
            val bottom = top + visorH

            // 1. Vizör dışını yarı saydam siyah ile karart (kılavuz pencere efekti)
            val scrimColor = Color.Black.copy(alpha = 0.52f)
            drawRect(scrimColor, Offset(0f, 0f), Size(w, top))
            drawRect(scrimColor, Offset(0f, bottom), Size(w, h - bottom))
            drawRect(scrimColor, Offset(0f, top), Size(left, visorH))
            drawRect(scrimColor, Offset(right, top), Size(w - right, visorH))

            // 2. İnce kılavuz vizör çerçevesi
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(left, top),
                size = Size(visorW, visorH),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f))
                )
            )

            // 3. 4 Köşe Fiducial Kilit Kutucukları
            val boxSize = 42.dp.toPx()
            val bracketLen = 22.dp.toPx()
            val strokeW = 4.dp.toPx()

            val corners = listOf(
                Offset(left, top),                  // Sol-Üst
                Offset(right - boxSize, top),       // Sağ-Üst
                Offset(right - boxSize, bottom - boxSize), // Sağ-Alt
                Offset(left, bottom - boxSize)       // Sol-Alt
            )

            val isFullyLocked = detectionResult?.isLocked == true
            val lockedList = detectionResult?.cornerLocked ?: listOf(false, false, false, false)

            for (i in 0 until 4) {
                val pt = corners[i]
                val locked = isFullyLocked || lockedList.getOrElse(i) { false }

                val boxColor = if (locked) {
                    Color(0xFF00E676) // Kilitlendi: Canlı Zümrüt Yeşili
                } else {
                    // Aranıyor: Beyaz / Sarı animasyonlu yanıp sönme
                    Color(0xFFFFD54F).copy(alpha = pulseAlpha)
                }

                // Köşe kilit kutucuğu zemini
                drawRoundRect(
                    color = boxColor.copy(alpha = if (locked) 0.25f else 0.12f),
                    topLeft = pt,
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                // Köşe L-braketleri
                val bracketPath = Path()
                when (i) {
                    0 -> { // Sol-Üst
                        bracketPath.moveTo(pt.x, pt.y + bracketLen)
                        bracketPath.lineTo(pt.x, pt.y)
                        bracketPath.lineTo(pt.x + bracketLen, pt.y)
                    }
                    1 -> { // Sağ-Üst
                        bracketPath.moveTo(pt.x + boxSize - bracketLen, pt.y)
                        bracketPath.lineTo(pt.x + boxSize, pt.y)
                        bracketPath.lineTo(pt.x + boxSize, pt.y + bracketLen)
                    }
                    2 -> { // Sağ-Alt
                        bracketPath.moveTo(pt.x + boxSize, pt.y + boxSize - bracketLen)
                        bracketPath.lineTo(pt.x + boxSize, pt.y + boxSize)
                        bracketPath.lineTo(pt.x + boxSize - bracketLen, pt.y + boxSize)
                    }
                    3 -> { // Sol-Alt
                        bracketPath.moveTo(pt.x + bracketLen, pt.y + boxSize)
                        bracketPath.lineTo(pt.x, pt.y + boxSize)
                        bracketPath.lineTo(pt.x, pt.y + boxSize - bracketLen)
                    }
                }

                drawPath(
                    path = bracketPath,
                    color = boxColor,
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Kilitlendiğinde merkez nokta göstergesi
                if (locked) {
                    drawCircle(
                        color = Color(0xFF00E676),
                        radius = 4.dp.toPx(),
                        center = Offset(pt.x + boxSize / 2f, pt.y + boxSize / 2f)
                    )
                }
            }
        }

        // Vizör altı hizalama talimat hapı
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = (screenH * 0.42f).dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val isLocked = detectionResult?.isLocked == true
                Icon(
                    imageVector = if (isLocked) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFF00E676) else Color(0xFFFFD54F),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLocked) "4 Köşe Kilitlendi! Şimdi Tara'ya dokunun." else "Formun 4 köşesini vizör çerçevesine hizalayın",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Üst Bar (Geri, Başlık, Flaş)
 */
@Composable
private fun OmrTopBar(
    title: String,
    isFlashOn: Boolean,
    onToggleFlash: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White
                )
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(onClick = onToggleFlash) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flaş",
                    tint = if (isFlashOn) Color(0xFFFFD54F) else Color.White
                )
            }
        }
    }
}

/**
 * Eşleşen deneme bilgi çipi.
 */
@Composable
private fun OmrDenemeMatchChip(
    selectedTitle: String,
    sinavTuru: String,
    testId: String,
    isMatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isMatched) Color(0xDD1E293B) else Color(0xBB0F172A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMatched) Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.Assignment,
                contentDescription = null,
                tint = if (isMatched) Color(0xFF00E676) else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = if (isMatched) "Eşleşen Deneme: $selectedTitle ($sinavTuru)" else "Otomatik Eşleştirme (Test ID / 2D Kod)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (testId.isNotBlank()) {
                    Text(
                        text = "Kod: $testId",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Değiştir",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Alt Kontrol Barı: 📸 Optik Formu Tara, 🖼️ Galeriden Seç, 🧪 Simülasyon
 */
@Composable
private fun OmrBottomBar(
    isProcessing: Boolean,
    hasCamera: Boolean,
    onCapture: () -> Unit,
    onPickGallery: () -> Unit,
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xEA0F172A),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Ana Tarama Butonu (Kamera İzni Varsa)
            Button(
                onClick = onCapture,
                enabled = !isProcessing && hasCamera,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Optik Formu Tara",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // İkincil Butonlar: Galeriden Seç & Örnek Simülasyon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickGallery,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Galeriden Seç", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onSimulate,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF67E8F9)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Örnek Simülasyon", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Kamera İzni İstem Kartı
 */
@Composable
private fun CameraPermissionCard(
    onRequestPermission: () -> Unit,
    onPickGallery: () -> Unit,
    onSimulate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Kamera İzni Gerekli",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Optik cevap formunu kameranızla tarayıp netlerinizi otomatik hesaplamak için kamera izni gerekiyor.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kamera İzni Ver")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickGallery) {
                    Text("🖼️ Galeriden Seç", color = Color(0xFF94A3B8))
                }
                TextButton(onClick = onSimulate) {
                    Text("🧪 Test Simülasyonu", color = Color(0xFF67E8F9))
                }
            }
        }
    }
}

/**
 * Analiz süresince tam ekran karartma ve ilerleme kartı.
 */
@Composable
private fun OmrProcessingOverlay(
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = false) {}
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Optik Form Okunuyor...",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusMessage,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Hata bildirim bannerı
 */
@Composable
private fun OmrErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF7F1D1D),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Mevcut denemeler arasından seçim yapma dialogu.
 */
@Composable
private fun DenemePickerDialog(
    denemeler: List<DenemeSinaviEntity>,
    selectedId: Long?,
    onSelect: (DenemeSinaviEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Eşleşecek Deneme Sınavını Seçin", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedId == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "⚡ Otomatik Tespit (Kod veya Yeni Deneme)",
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(denemeler) { deneme ->
                    val isCurrent = deneme.id == selectedId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(deneme) }
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "${deneme.baslik} (${deneme.sinavTuru})",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${deneme.yayinEvi} • ${deneme.toplamSoru} Soru",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
