package com.sorudefteri.ui.soruekle.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class BatchSplitMode(val title: String, val count: Int) {
    TWO_VERTICAL("2 Sütun (Sol / Sağ)", 2),
    TWO_HORIZONTAL("2 Soru (Üst / Alt)", 2),
    THREE_HORIZONTAL("3 Soru (Yatay Şerit)", 3),
    FOUR_GRID("4 Soru (2x2 Izgara)", 4),
    CUSTOM_HORIZONTAL("Özel Yatay Kesim", 2)
}

@Composable
fun BatchCropDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onBatchCropped: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(BatchSplitMode.TWO_VERTICAL) }
    var customSplitRatio by remember { mutableFloatStateOf(0.5f) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Görseli arka planda yükle
    LaunchedEffect(imagePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(imagePath, opts)
                    var sample = 1
                    while (opts.outWidth / sample > 2000 || opts.outHeight / sample > 2000) {
                        sample *= 2
                    }
                    val loadOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeFile(imagePath, loadOpts)
                    if (bmp != null) {
                        // EXIF döndürme kontrolü
                        val exif = ExifInterface(imagePath)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val matrix = Matrix()
                        when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        }
                        loadedBitmap = if (!matrix.isIdentity) {
                            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        } else bmp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 1. Üst Başlık Barı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Çoklu Soru Kırp (Sayfa Bölücü)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tek fotoğraftaki soruları otomatik ayıkla",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Mod Seçim Çipleri
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BatchSplitMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (selectedMode == BatchSplitMode.CUSTOM_HORIZONTAL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Bölme Çizgisi: %${(customSplitRatio * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = customSplitRatio,
                            onValueChange = { customSplitRatio = it },
                            valueRange = 0.15f..0.85f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Önizleme ve Kesim Çizgileri Alanı
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = loadedBitmap
                    if (bmp != null) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerWidth = constraints.maxWidth.toFloat()
                            val containerHeight = constraints.maxHeight.toFloat()

                            val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
                            val containerRatio = containerWidth / containerHeight

                            val displayWidth: Float
                            val displayHeight: Float
                            if (bmpRatio > containerRatio) {
                                displayWidth = containerWidth
                                displayHeight = containerWidth / bmpRatio
                            } else {
                                displayHeight = containerHeight
                                displayWidth = containerHeight * bmpRatio
                            }

                            Box(
                                modifier = Modifier
                                    .size(
                                        width = (displayWidth / LocalContext.current.resources.displayMetrics.density).dp,
                                        height = (displayHeight / LocalContext.current.resources.displayMetrics.density).dp
                                    )
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Önizleme",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Kesim Çizgileri Katmanı
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                                    val cutLineColor = Color(0xFFFF5252)

                                    when (selectedMode) {
                                        BatchSplitMode.TWO_VERTICAL -> {
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(w * 0.5f, 0f),
                                                end = Offset(w * 0.5f, h),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        }
                                        BatchSplitMode.TWO_HORIZONTAL -> {
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(0f, h * 0.5f),
                                                end = Offset(w, h * 0.5f),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        }
                                        BatchSplitMode.THREE_HORIZONTAL -> {
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(0f, h * 0.333f),
                                                end = Offset(w, h * 0.333f),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(0f, h * 0.666f),
                                                end = Offset(w, h * 0.666f),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        }
                                        BatchSplitMode.FOUR_GRID -> {
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(w * 0.5f, 0f),
                                                end = Offset(w * 0.5f, h),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(0f, h * 0.5f),
                                                end = Offset(w, h * 0.5f),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        }
                                        BatchSplitMode.CUSTOM_HORIZONTAL -> {
                                            drawLine(
                                                color = cutLineColor,
                                                start = Offset(0f, h * customSplitRatio),
                                                end = Offset(w, h * customSplitRatio),
                                                strokeWidth = 3.dp.toPx(),
                                                pathEffect = dashEffect
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Alt İşlem Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val bmp = loadedBitmap ?: return@Button
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val croppedPaths = mutableListOf<String>()
                                try {
                                    val rects = calculateCropRects(
                                        bmp.width,
                                        bmp.height,
                                        selectedMode,
                                        customSplitRatio
                                    )
                                    val filesDir = context.filesDir

                                    rects.forEachIndexed { index, rect ->
                                        val croppedBmp = Bitmap.createBitmap(
                                            bmp,
                                            rect.left.toInt(),
                                            rect.top.toInt(),
                                            rect.width.toInt(),
                                            rect.height.toInt()
                                        )
                                        val destFile = File(filesDir, "soru_crop_${System.currentTimeMillis()}_$index.jpg")
                                        FileOutputStream(destFile).use { out ->
                                            croppedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                        }
                                        croppedPaths.add(destFile.absolutePath)
                                    }

                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onBatchCropped(croppedPaths)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = loadedBitmap != null && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sorular Ayıklanıyor...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sayfayı Böl ve ${selectedMode.count} Soruyu Yükle")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seçilen moda göre görsel üzerindeki kırpma dikdörtgenlerini piksel cinsinden hesaplar
 */
internal fun calculateCropRects(
    width: Int,
    height: Int,
    mode: BatchSplitMode,
    customRatio: Float
): List<Rect> {
    val w = width.toFloat()
    val h = height.toFloat()

    return when (mode) {
        BatchSplitMode.TWO_VERTICAL -> listOf(
            Rect(0f, 0f, w * 0.5f, h),
            Rect(w * 0.5f, 0f, w, h)
        )
        BatchSplitMode.TWO_HORIZONTAL -> listOf(
            Rect(0f, 0f, w, h * 0.5f),
            Rect(0f, h * 0.5f, w, h)
        )
        BatchSplitMode.THREE_HORIZONTAL -> listOf(
            Rect(0f, 0f, w, h * 0.333f),
            Rect(0f, h * 0.333f, w, h * 0.666f),
            Rect(0f, h * 0.666f, w, h)
        )
        BatchSplitMode.FOUR_GRID -> listOf(
            Rect(0f, 0f, w * 0.5f, h * 0.5f),
            Rect(w * 0.5f, 0f, w, h * 0.5f),
            Rect(0f, h * 0.5f, w * 0.5f, h),
            Rect(w * 0.5f, h * 0.5f, w, h)
        )
        BatchSplitMode.CUSTOM_HORIZONTAL -> listOf(
            Rect(0f, 0f, w, h * customRatio),
            Rect(0f, h * customRatio, w, h)
        )
    }
}
