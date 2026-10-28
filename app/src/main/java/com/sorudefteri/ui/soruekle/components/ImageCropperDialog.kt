package com.sorudefteri.ui.soruekle.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sorudefteri.data.images.ImageFilterProcessor
import com.sorudefteri.data.images.ImageFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

private const val CROP_MAX_DIMENSION = 1600

/**
 * Desteklenen kırpma oranları.
 */
enum class CropAspectRatio(val title: String, val ratio: Float?) {
    FREE("Serbest", null),
    RATIO_1_1("1:1", 1.0f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_A4_COLUMN("A4 Sütun (1:2)", 0.5f)
}

/**
 * 8 noktalı kırpma tutamaçları ve merkez taşıma alanı.
 */
private enum class CropHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT,
    CENTER
}

/**
 * Gelişmiş 8 Noktalı Kırpma & Filtreleme Dialogu:
 * - 4 köşe ve 4 kenar olmak üzere 8 etkileşimli tutamaç.
 * - 3x3 1/3 kuralı (rule of thirds) rehber çizgileri.
 * - Dokunma / stylus hareketleri ile serbest yeniden boyutlandırma ve sürükleyerek taşıma.
 * - En boy oranı sabitleme: Serbest, 1:1, 4:3, 16:9, A4 Sütun (1:2).
 * - Saat yönü (+90°) ve saat yönünün tersi (-90°) döndürme kontrolleri.
 * - Anlık önizlemeli filtre şeridi: Orijinal, Fotokopi (B&W Scanner), Negatif (Gece), Canlı Belge.
 * - Yüksek çözünürlüklü piksel hassasiyetinde gerçek kırpma ve filtre uygulama.
 */
@Composable
fun ImageCropperDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var baseBitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    // Kırpma alanı: Gösterilen görsel içinde [0f..1f] normalize koordinatlar
    var cropRectNorm by remember { mutableStateOf(Rect(0.04f, 0.04f, 0.96f, 0.96f)) }
    var selectedAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var selectedFilter by remember { mutableStateOf(ImageFilterType.ORIGINAL) }

    DisposableEffect(Unit) {
        onDispose {
            currentBitmap?.let { if (it !== baseBitmap && !it.isRecycled) try { it.recycle() } catch (_: Exception) {} }
            baseBitmap?.let { if (!it.isRecycled) try { it.recycle() } catch (_: Exception) {} }
            currentBitmap = null
            baseBitmap = null
        }
    }

    // Görseli sampled decode ile yükle (OOM korumalı)
    LaunchedEffect(imagePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                val sampled = if (file.exists()) {
                    decodeSampledBitmap(file.absolutePath, CROP_MAX_DIMENSION)
                } else {
                    try {
                        val uri = android.net.Uri.parse(imagePath)
                        decodeSampledBitmapFromUri(context, uri, CROP_MAX_DIMENSION)
                    } catch (_: Exception) { null }
                }
                withContext(Dispatchers.Main) {
                    currentBitmap?.let { old ->
                        if (old !== baseBitmap && !old.isRecycled) try { old.recycle() } catch (_: Exception) {}
                    }
                    baseBitmap?.let { old -> if (!old.isRecycled) try { old.recycle() } catch (_: Exception) {} }
                    baseBitmap = sampled
                    currentBitmap = sampled
                    cropRectNorm = Rect(0.04f, 0.04f, 0.96f, 0.96f)
                    selectedAspectRatio = CropAspectRatio.FREE
                    selectedFilter = ImageFilterType.ORIGINAL
                    isLoading = false
                }
            } catch (_: OutOfMemoryError) {
                withContext(Dispatchers.Main) { isLoading = false }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // Döndürme işlevi (+90° CW veya -90° CCW)
    // PERF: createBitmap ana iplikte jank yapıyordu; Default'a taşındı.
    fun rotateImage(clockwise: Boolean) {
        val bmp = currentBitmap ?: return
        if (bmp.isRecycled || isProcessing) return
        isProcessing = true
        val degrees = if (clockwise) 90f else -90f
        coroutineScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                try {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                } catch (oom: OutOfMemoryError) {
                    oom.printStackTrace()
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (rotated != null) {
                if (currentBitmap !== baseBitmap && currentBitmap?.isRecycled == false) {
                    try { currentBitmap?.recycle() } catch (_: Exception) {}
                }
                currentBitmap = rotated
                // Döndürme sonrası kırpma alanını ortala ve yeniden boyutlandır
                cropRectNorm = Rect(0.04f, 0.04f, 0.96f, 0.96f)
                if (selectedAspectRatio != CropAspectRatio.FREE) {
                    cropRectNorm = applyAspectRatioToRect(
                        cropRectNorm,
                        selectedAspectRatio,
                        rotated.width.toFloat(),
                        rotated.height.toFloat()
                    )
                }
            }
            isProcessing = false
        }
    }

    // Kırp ve Onayla işlemi: arka planda tam çözünürlüklü kırpma ve filtre uygulama
    fun performCropAndConfirm() {
        val bmp = currentBitmap ?: return
        if (bmp.isRecycled || isProcessing) return

        isProcessing = true
        coroutineScope.launch {
            withContext(Dispatchers.Default) {
                try {
                    val bmpW = bmp.width
                    val bmpH = bmp.height

                    val cropLeft = (cropRectNorm.left * bmpW).roundToInt().coerceIn(0, bmpW - 1)
                    val cropTop = (cropRectNorm.top * bmpH).roundToInt().coerceIn(0, bmpH - 1)
                    val cropWidth = ((cropRectNorm.right - cropRectNorm.left) * bmpW).roundToInt().coerceIn(1, bmpW - cropLeft)
                    val cropHeight = ((cropRectNorm.bottom - cropRectNorm.top) * bmpH).roundToInt().coerceIn(1, bmpH - cropTop)

                    // Yüksek çözünürlüklü gerçek kırpma
                    val cropped = Bitmap.createBitmap(bmp, cropLeft, cropTop, cropWidth, cropHeight)

                    // Seçilen filtreyi uygula
                    val filtered = ImageFilterProcessor.applyFilter(cropped, selectedFilter)

                    // Filtre yeni bir bitmap ürettiyse ara kırpma kopyasını temizle
                    if (filtered !== cropped && !cropped.isRecycled) {
                        try { cropped.recycle() } catch (_: Exception) {}
                    }

                    val finalResult = if (filtered === bmp) {
                        bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) ?: bmp
                    } else {
                        filtered
                    }

                    withContext(Dispatchers.Main) {
                        onCropped(finalResult)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        try {
                            val fallback = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) ?: bmp
                            onCropped(fallback)
                        } catch (_: Exception) {
                            onDismiss()
                        }
                    }
                } catch (oom: OutOfMemoryError) {
                    oom.printStackTrace()
                    withContext(Dispatchers.Main) {
                        onDismiss()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Üst Çubuk
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }

                    Text(
                        text = "Kırp & İyileştir",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { rotateImage(clockwise = false) },
                            enabled = currentBitmap != null && !isLoading && !isProcessing,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Sola Döndür (-90°)", tint = Color.White)
                        }

                        IconButton(
                            onClick = { rotateImage(clockwise = true) },
                            enabled = currentBitmap != null && !isLoading && !isProcessing,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Sağa Döndür (+90°)", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Görsel & 8 Noktalı Kırpma Alanı
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        currentBitmap != null -> {
                            val bmp = currentBitmap!!
                            if (!bmp.isRecycled) {
                                val containerW = constraints.maxWidth.toFloat()
                                val containerH = constraints.maxHeight.toFloat()
                                val bmpW = bmp.width.toFloat()
                                val bmpH = bmp.height.toFloat()

                                val scale = min(containerW / bmpW, containerH / bmpH)
                                val dispW = bmpW * scale
                                val dispH = bmpH * scale
                                val offX = (containerW - dispW) / 2f
                                val offY = (containerH - dispH) / 2f

                                // Filtre önizlemesini doğrudan GPU üzerinde ColorFilter ile uygula
                                val previewColorFilter = remember(selectedFilter) {
                                    if (selectedFilter == ImageFilterType.ORIGINAL) {
                                        null
                                    } else {
                                        ColorFilter.colorMatrix(ColorMatrix(ImageFilterProcessor.getColorMatrixArray(selectedFilter)))
                                    }
                                }

                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Görsel Önizleme",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = previewColorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Etkileşimli 8 Noktalı Kırpma Katmanı
                                CropOverlay(
                                    displayedWidth = dispW,
                                    displayedHeight = dispH,
                                    offsetX = offX,
                                    offsetY = offY,
                                    bmpWidth = bmpW,
                                    bmpHeight = bmpH,
                                    cropRectNorm = cropRectNorm,
                                    aspectRatio = selectedAspectRatio,
                                    onCropRectChanged = { cropRectNorm = it }
                                )
                            }
                        }
                        else -> {
                            Text("Görsel yüklenemedi", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // En Boy Oranı Sabitleme Şeridi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Oran:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray,
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    CropAspectRatio.values().forEach { aspect ->
                        val isSelected = selectedAspectRatio == aspect
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedAspectRatio = aspect
                                    currentBitmap?.let { bmp ->
                                        cropRectNorm = applyAspectRatioToRect(
                                            cropRectNorm,
                                            aspect,
                                            bmp.width.toFloat(),
                                            bmp.height.toFloat()
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f),
                            border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = aspect.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filtre Seçim Şeridi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtre:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray,
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    ImageFilterType.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedFilter = filter },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.10f),
                            border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = filter.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Alt Aksiyon Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text("İptal")
                    }

                    Button(
                        onClick = { performCropAndConfirm() },
                        modifier = Modifier.weight(1f),
                        enabled = currentBitmap != null && !isLoading && !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("İşleniyor...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kırp ve Onayla")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8 noktalı tutamaç, 3x3 kılavuz çizgileri ve karartma maskesi içeren interaktif kırpma katmanı.
 */
@Composable
private fun CropOverlay(
    displayedWidth: Float,
    displayedHeight: Float,
    offsetX: Float,
    offsetY: Float,
    bmpWidth: Float,
    bmpHeight: Float,
    cropRectNorm: Rect,
    aspectRatio: CropAspectRatio,
    onCropRectChanged: (Rect) -> Unit
) {
    val density = LocalDensity.current
    val touchRadiusPx = remember(density) { with(density) { 32.dp.toPx() } }

    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }

    val currentRectNorm by rememberUpdatedState(cropRectNorm)
    val currentAspect by rememberUpdatedState(aspectRatio)
    val currentDispW by rememberUpdatedState(displayedWidth)
    val currentDispH by rememberUpdatedState(displayedHeight)
    val currentOffX by rememberUpdatedState(offsetX)
    val currentOffY by rememberUpdatedState(offsetY)
    val currentBmpW by rememberUpdatedState(bmpWidth)
    val currentBmpH by rememberUpdatedState(bmpHeight)

    // Ekran piksel koordinatları
    val cropScreenLeft = offsetX + cropRectNorm.left * displayedWidth
    val cropScreenTop = offsetY + cropRectNorm.top * displayedHeight
    val cropScreenWidth = (cropRectNorm.right - cropRectNorm.left) * displayedWidth
    val cropScreenHeight = (cropRectNorm.bottom - cropRectNorm.top) * displayedHeight
    val cropScreenRight = cropScreenLeft + cropScreenWidth
    val cropScreenBottom = cropScreenTop + cropScreenHeight

    fun hitTest(pos: Offset): CropHandle? {
        fun distSq(p: Offset, hx: Float, hy: Float): Float {
            val dx = p.x - hx
            val dy = p.y - hy
            return dx * dx + dy * dy
        }
        val rSq = touchRadiusPx * touchRadiusPx

        val midX = (cropScreenLeft + cropScreenRight) / 2f
        val midY = (cropScreenTop + cropScreenBottom) / 2f

        // 1. 4 Köşe
        if (distSq(pos, cropScreenLeft, cropScreenTop) <= rSq) return CropHandle.TOP_LEFT
        if (distSq(pos, cropScreenRight, cropScreenTop) <= rSq) return CropHandle.TOP_RIGHT
        if (distSq(pos, cropScreenLeft, cropScreenBottom) <= rSq) return CropHandle.BOTTOM_LEFT
        if (distSq(pos, cropScreenRight, cropScreenBottom) <= rSq) return CropHandle.BOTTOM_RIGHT

        // 2. 4 Kenar
        if (distSq(pos, midX, cropScreenTop) <= rSq) return CropHandle.TOP
        if (distSq(pos, midX, cropScreenBottom) <= rSq) return CropHandle.BOTTOM
        if (distSq(pos, cropScreenLeft, midY) <= rSq) return CropHandle.LEFT
        if (distSq(pos, cropScreenRight, midY) <= rSq) return CropHandle.RIGHT

        // 3. Merkez alan (taşıma)
        if (pos.x in cropScreenLeft..cropScreenRight && pos.y in cropScreenTop..cropScreenBottom) {
            return CropHandle.CENTER
        }

        return null
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeHandle = hitTest(offset)
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val handle = activeHandle ?: return@detectDragGestures
                        val updated = updateCropRect(
                            handle = handle,
                            dragAmount = dragAmount,
                            displayedWidth = currentDispW,
                            displayedHeight = currentDispH,
                            aspectRatio = currentAspect,
                            bmpWidth = currentBmpW,
                            bmpHeight = currentBmpH,
                            currentRect = currentRectNorm
                        )
                        onCropRectChanged(updated)
                    }
                )
            }
    ) {
        val scrimColor = Color.Black.copy(alpha = 0.60f)

        // 1. Karartma Alanı (Kırpma alanı dışı)
        if (cropScreenTop > offsetY) {
            drawRect(
                color = scrimColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(displayedWidth, cropScreenTop - offsetY)
            )
        }
        if (offsetY + displayedHeight > cropScreenBottom) {
            drawRect(
                color = scrimColor,
                topLeft = Offset(offsetX, cropScreenBottom),
                size = Size(displayedWidth, (offsetY + displayedHeight) - cropScreenBottom)
            )
        }
        if (cropScreenLeft > offsetX) {
            drawRect(
                color = scrimColor,
                topLeft = Offset(offsetX, cropScreenTop),
                size = Size(cropScreenLeft - offsetX, cropScreenHeight)
            )
        }
        if (offsetX + displayedWidth > cropScreenRight) {
            drawRect(
                color = scrimColor,
                topLeft = Offset(cropScreenRight, cropScreenTop),
                size = Size((offsetX + displayedWidth) - cropScreenRight, cropScreenHeight)
            )
        }

        // 2. Kırpma Dikdörtgeni Çerçevesi
        drawRect(
            color = Color.White,
            topLeft = Offset(cropScreenLeft, cropScreenTop),
            size = Size(cropScreenWidth, cropScreenHeight),
            style = Stroke(width = 2.dp.toPx())
        )

        // 3. 3x3 Kuralı Kılavuz Çizgileri
        val gridColor = Color.White.copy(alpha = 0.38f)
        val col1 = cropScreenLeft + cropScreenWidth / 3f
        val col2 = cropScreenLeft + 2f * cropScreenWidth / 3f
        drawLine(gridColor, Offset(col1, cropScreenTop), Offset(col1, cropScreenBottom), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(col2, cropScreenTop), Offset(col2, cropScreenBottom), strokeWidth = 1.dp.toPx())

        val row1 = cropScreenTop + cropScreenHeight / 3f
        val row2 = cropScreenTop + 2f * cropScreenHeight / 3f
        drawLine(gridColor, Offset(cropScreenLeft, row1), Offset(cropScreenRight, row1), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(cropScreenLeft, row2), Offset(cropScreenRight, row2), strokeWidth = 1.dp.toPx())

        // 4. 8 Noktalı Tutamaçlar
        val cornerLen = 18.dp.toPx()
        val cornerThick = 4.dp.toPx()
        val handleColor = Color.White

        // Köşe L-braketleri
        // Top-Left
        drawLine(handleColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft + cornerLen, cropScreenTop), cornerThick)
        drawLine(handleColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft, cropScreenTop + cornerLen), cornerThick)
        // Top-Right
        drawLine(handleColor, Offset(cropScreenRight - cornerLen, cropScreenTop), Offset(cropScreenRight, cropScreenTop), cornerThick)
        drawLine(handleColor, Offset(cropScreenRight, cropScreenTop), Offset(cropScreenRight, cropScreenTop + cornerLen), cornerThick)
        // Bottom-Left
        drawLine(handleColor, Offset(cropScreenLeft, cropScreenBottom), Offset(cropScreenLeft + cornerLen, cropScreenBottom), cornerThick)
        drawLine(handleColor, Offset(cropScreenLeft, cropScreenBottom - cornerLen), Offset(cropScreenLeft, cropScreenBottom), cornerThick)
        // Bottom-Right
        drawLine(handleColor, Offset(cropScreenRight - cornerLen, cropScreenBottom), Offset(cropScreenRight, cropScreenBottom), cornerThick)
        drawLine(handleColor, Offset(cropScreenRight, cropScreenBottom - cornerLen), Offset(cropScreenRight, cropScreenBottom), cornerThick)

        // Kenar Tutamaç Çubukları (Pills)
        val pillLen = 26.dp.toPx()
        val pillThick = 4.dp.toPx()
        val midX = (cropScreenLeft + cropScreenRight) / 2f
        val midY = (cropScreenTop + cropScreenBottom) / 2f

        drawLine(handleColor, Offset(midX - pillLen / 2f, cropScreenTop), Offset(midX + pillLen / 2f, cropScreenTop), pillThick, cap = StrokeCap.Round)
        drawLine(handleColor, Offset(midX - pillLen / 2f, cropScreenBottom), Offset(midX + pillLen / 2f, cropScreenBottom), pillThick, cap = StrokeCap.Round)
        drawLine(handleColor, Offset(cropScreenLeft, midY - pillLen / 2f), Offset(cropScreenLeft, midY + pillLen / 2f), pillThick, cap = StrokeCap.Round)
        drawLine(handleColor, Offset(cropScreenRight, midY - pillLen / 2f), Offset(cropScreenRight, midY + pillLen / 2f), pillThick, cap = StrokeCap.Round)

        // Köşelerde belirgin dairesel vurgular
        val dotRadius = 4.5.dp.toPx()
        drawCircle(handleColor, dotRadius, Offset(cropScreenLeft, cropScreenTop))
        drawCircle(handleColor, dotRadius, Offset(cropScreenRight, cropScreenTop))
        drawCircle(handleColor, dotRadius, Offset(cropScreenLeft, cropScreenBottom))
        drawCircle(handleColor, dotRadius, Offset(cropScreenRight, cropScreenBottom))
    }
}

/**
 * Tutamaç hareketine göre yeni normalize kırpma alanını hesaplar.
 */
private fun updateCropRect(
    handle: CropHandle,
    dragAmount: Offset,
    displayedWidth: Float,
    displayedHeight: Float,
    aspectRatio: CropAspectRatio,
    bmpWidth: Float,
    bmpHeight: Float,
    currentRect: Rect
): Rect {
    val dNormX = dragAmount.x / displayedWidth
    val dNormY = dragAmount.y / displayedHeight
    val minSize = 0.05f

    val targetRatio = aspectRatio.ratio
    if (targetRatio == null) {
        // Serbest Kırpma
        var l = currentRect.left
        var t = currentRect.top
        var r = currentRect.right
        var b = currentRect.bottom

        when (handle) {
            CropHandle.TOP_LEFT -> {
                l = (l + dNormX).coerceIn(0f, r - minSize)
                t = (t + dNormY).coerceIn(0f, b - minSize)
            }
            CropHandle.TOP -> {
                t = (t + dNormY).coerceIn(0f, b - minSize)
            }
            CropHandle.TOP_RIGHT -> {
                r = (r + dNormX).coerceIn(l + minSize, 1f)
                t = (t + dNormY).coerceIn(0f, b - minSize)
            }
            CropHandle.RIGHT -> {
                r = (r + dNormX).coerceIn(l + minSize, 1f)
            }
            CropHandle.BOTTOM_RIGHT -> {
                r = (r + dNormX).coerceIn(l + minSize, 1f)
                b = (b + dNormY).coerceIn(t + minSize, 1f)
            }
            CropHandle.BOTTOM -> {
                b = (b + dNormY).coerceIn(t + minSize, 1f)
            }
            CropHandle.BOTTOM_LEFT -> {
                l = (l + dNormX).coerceIn(0f, r - minSize)
                b = (b + dNormY).coerceIn(t + minSize, 1f)
            }
            CropHandle.LEFT -> {
                l = (l + dNormX).coerceIn(0f, r - minSize)
            }
            CropHandle.CENTER -> {
                val w = r - l
                val h = b - t
                l = (l + dNormX).coerceIn(0f, 1f - w)
                t = (t + dNormY).coerceIn(0f, 1f - h)
                r = l + w
                b = t + h
            }
        }
        return Rect(l, t, r, b)
    } else {
        // Oran Korumalı Kırpma: normW / normH = normRatio
        val normRatio = targetRatio * (bmpHeight / bmpWidth)
        var l = currentRect.left
        var t = currentRect.top
        var r = currentRect.right
        var b = currentRect.bottom

        when (handle) {
            CropHandle.CENTER -> {
                val w = r - l
                val h = b - t
                l = (l + dNormX).coerceIn(0f, 1f - w)
                t = (t + dNormY).coerceIn(0f, 1f - h)
                return Rect(l, t, l + w, t + h)
            }
            CropHandle.BOTTOM_RIGHT -> {
                var newW = (r + dNormX - l).coerceAtLeast(minSize)
                var newH = newW / normRatio
                if (l + newW > 1f) {
                    newW = 1f - l
                    newH = newW / normRatio
                }
                if (t + newH > 1f) {
                    newH = 1f - t
                    newW = newH * normRatio
                }
                if (newW >= minSize && newH >= minSize) {
                    r = l + newW
                    b = t + newH
                }
            }
            CropHandle.TOP_LEFT -> {
                var newW = (r - (l + dNormX)).coerceAtLeast(minSize)
                var newH = newW / normRatio
                if (r - newW < 0f) {
                    newW = r
                    newH = newW / normRatio
                }
                if (b - newH < 0f) {
                    newH = b
                    newW = newH * normRatio
                }
                if (newW >= minSize && newH >= minSize) {
                    l = r - newW
                    t = b - newH
                }
            }
            CropHandle.TOP_RIGHT -> {
                var newW = (r + dNormX - l).coerceAtLeast(minSize)
                var newH = newW / normRatio
                if (l + newW > 1f) {
                    newW = 1f - l
                    newH = newW / normRatio
                }
                if (b - newH < 0f) {
                    newH = b
                    newW = newH * normRatio
                }
                if (newW >= minSize && newH >= minSize) {
                    r = l + newW
                    t = b - newH
                }
            }
            CropHandle.BOTTOM_LEFT -> {
                var newW = (r - (l + dNormX)).coerceAtLeast(minSize)
                var newH = newW / normRatio
                if (r - newW < 0f) {
                    newW = r
                    newH = newW / normRatio
                }
                if (t + newH > 1f) {
                    newH = 1f - t
                    newW = newH * normRatio
                }
                if (newW >= minSize && newH >= minSize) {
                    l = r - newW
                    b = t + newH
                }
            }
            CropHandle.TOP, CropHandle.BOTTOM -> {
                val deltaY = if (handle == CropHandle.TOP) -dNormY else dNormY
                val curH = b - t
                var newH = (curH + deltaY).coerceAtLeast(minSize)
                var newW = newH * normRatio
                if (newW > 1f) {
                    newW = 1f
                    newH = newW / normRatio
                }
                val midX = (l + r) / 2f
                var newL = (midX - newW / 2f).coerceIn(0f, 1f - newW)
                var newR = newL + newW
                if (handle == CropHandle.TOP) {
                    var newT = b - newH
                    if (newT < 0f) {
                        newT = 0f
                        newH = b
                        newW = newH * normRatio
                        newL = (midX - newW / 2f).coerceIn(0f, 1f - newW)
                        newR = newL + newW
                    }
                    t = newT
                } else {
                    var newB = t + newH
                    if (newB > 1f) {
                        newB = 1f
                        newH = 1f - t
                        newW = newH * normRatio
                        newL = (midX - newW / 2f).coerceIn(0f, 1f - newW)
                        newR = newL + newW
                    }
                    b = newB
                }
                l = newL
                r = newR
            }
            CropHandle.LEFT, CropHandle.RIGHT -> {
                val deltaX = if (handle == CropHandle.LEFT) -dNormX else dNormX
                val curW = r - l
                var newW = (curW + deltaX).coerceAtLeast(minSize)
                var newH = newW / normRatio
                if (newH > 1f) {
                    newH = 1f
                    newW = newH * normRatio
                }
                val midY = (t + b) / 2f
                var newT = (midY - newH / 2f).coerceIn(0f, 1f - newH)
                var newB = newT + newH
                if (handle == CropHandle.LEFT) {
                    var newL = r - newW
                    if (newL < 0f) {
                        newL = 0f
                        newW = r
                        newH = newW / normRatio
                        newT = (midY - newH / 2f).coerceIn(0f, 1f - newH)
                        newB = newT + newH
                    }
                    l = newL
                } else {
                    var newR = l + newW
                    if (newR > 1f) {
                        newR = 1f
                        newW = 1f - l
                        newH = newW / normRatio
                        newT = (midY - newH / 2f).coerceIn(0f, 1f - newH)
                        newB = newT + newH
                    }
                    r = newR
                }
                t = newT
                b = newB
            }
        }
        return Rect(l, t, r, b)
    }
}

/**
 * Mevcut kırpma alanına istenen en boy oranını merkezleyerek uygular.
 */
private fun applyAspectRatioToRect(
    current: Rect,
    aspectRatio: CropAspectRatio,
    bmpWidth: Float,
    bmpHeight: Float
): Rect {
    val targetRatio = aspectRatio.ratio ?: return current
    val normRatio = targetRatio * (bmpHeight / bmpWidth)

    val curW = current.width
    val curH = current.height
    val centerX = (current.left + current.right) / 2f
    val centerY = (current.top + current.bottom) / 2f

    var newW = curH * normRatio
    var newH = curH
    if (newW > 1f || newW > curW * 1.5f) {
        newW = curW.coerceAtMost(1f)
        newH = newW / normRatio
    }
    if (newH > 1f) {
        newH = 1f
        newW = newH * normRatio
    }
    if (newW > 1f) {
        newW = 1f
        newH = newW / normRatio
    }

    val minSize = 0.05f
    newW = newW.coerceAtLeast(minSize)
    newH = newH.coerceAtLeast(minSize)

    val left = (centerX - newW / 2f).coerceIn(0f, 1f - newW)
    val top = (centerY - newH / 2f).coerceIn(0f, 1f - newH)
    return Rect(left, top, left + newW, top + newH)
}

/**
 * Geriye dönük uyumluluk: String dosya yolu döndüren eski çağrılar için sarmalayıcı.
 */
@Composable
fun ImageCropperDialogLegacy(
    imagePath: String,
    onDismiss: () -> Unit,
    onCroppedPath: (String) -> Unit
) {
    val context = LocalContext.current
    ImageCropperDialog(
        imagePath = imagePath,
        onDismiss = onDismiss,
        onCropped = { bitmap: Bitmap ->
            try {
                val outFile = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                onCroppedPath(outFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                onDismiss()
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                onDismiss()
            } finally {
                if (!bitmap.isRecycled) {
                    try { bitmap.recycle() } catch (_: Exception) {}
                }
            }
        }
    )
}

// ------- Bitmap Çözümleme Yardımcıları -------

private fun decodeSampledBitmap(filePath: String, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return try { BitmapFactory.decodeFile(filePath) } catch (_: OutOfMemoryError) { null }
        }
        var inSampleSize = 1
        while ((bounds.outWidth / inSampleSize) > maxDim || (bounds.outHeight / inSampleSize) > maxDim) {
            inSampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        try {
            BitmapFactory.decodeFile(filePath, opts)
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            opts.inSampleSize = inSampleSize * 2
            try { BitmapFactory.decodeFile(filePath, opts) } catch (_: OutOfMemoryError) { null }
        }
    } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }
}

private fun decodeSampledBitmapFromUri(context: android.content.Context, uri: android.net.Uri, maxDim: Int): Bitmap? {
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val fd = pfd.fileDescriptor
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(fd, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var inSampleSize = 1
            while ((bounds.outWidth / inSampleSize) > maxDim || (bounds.outHeight / inSampleSize) > maxDim) {
                inSampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                BitmapFactory.decodeFileDescriptor(fd, null, opts)
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                opts.inSampleSize = inSampleSize * 2
                try { BitmapFactory.decodeFileDescriptor(fd, null, opts) } catch (_: OutOfMemoryError) { null }
            }
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
}
