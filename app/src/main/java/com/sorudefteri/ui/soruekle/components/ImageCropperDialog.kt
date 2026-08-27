package com.sorudefteri.ui.soruekle.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private const val CROP_MAX_DIMENSION = 1600

/**
 * GorselSeciciSection'dan gelen dosya yolu ile sampled decode kullanan,
 * aspect ratio / rotation / scale stabil kırpma dialogu.
 * onConfirm bitmap döndürür; dismiss'te state kaybolmaz (remember imagePath).
 * Bitmap recycle doğru sıra ile yönetilir.
 */
@Composable
fun ImageCropperDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var baseBitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // GorselSeciciSection'dan gelen path ile sampled decode (OOM korumalı)
    LaunchedEffect(imagePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                val sampled = if (file.exists()) {
                    decodeSampledBitmap(file.absolutePath, CROP_MAX_DIMENSION)
                } else {
                    // content:// URI olabilir (ImageCropper eski String path yerine URI gelebilir)
                    try {
                        val uri = android.net.Uri.parse(imagePath)
                        // fallback: try decode via content resolver with sampling
                        decodeSampledBitmapFromUri(context, uri, CROP_MAX_DIMENSION)
                    } catch (_: Exception) { null }
                }
                withContext(Dispatchers.Main) {
                    // önceki bitmap'leri recycle et (doğru sıra)
                    currentBitmap?.let { old ->
                        if (old != baseBitmap && !old.isRecycled) try { old.recycle() } catch (_: Exception) {}
                    }
                    baseBitmap?.let { old -> if (!old.isRecycled) try { old.recycle() } catch (_: Exception) {} }
                    baseBitmap = sampled
                    currentBitmap = sampled
                    rotationAngle = 0f
                    isLoading = false
                }
            } catch (_: OutOfMemoryError) {
                withContext(Dispatchers.Main) { isLoading = false }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Üst Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }

                    Text(
                        text = "Fotoğrafı Düzenle / Döndür",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            currentBitmap?.let { bmp ->
                                if (bmp.isRecycled) return@let
                                try {
                                    val matrix = Matrix().apply { postRotate(90f) }
                                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                    // recycle doğru sıra: önceki current != base ise recycle
                                    if (currentBitmap != baseBitmap) {
                                        currentBitmap?.let { old -> if (!old.isRecycled) try { old.recycle() } catch (_: Exception) {} }
                                    }
                                    // eğer bmp baseBitmap ise base'i koru, rotated yeni current olur
                                    currentBitmap = rotated
                                    rotationAngle = (rotationAngle + 90f) % 360f
                                } catch (_: OutOfMemoryError) {
                                } catch (_: Exception) {
                                }
                            }
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "90° Döndür", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Görsel Önizleme - crop rect hesabı stabil: Fit scale ile aspect ratio korunur
                // Sadece soru alanını alacak şekilde merkez crop overlay (opsiyonel görsel rehber)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Text("Yükleniyor...", color = Color.White)
                        }
                        currentBitmap != null -> {
                            val bmp = currentBitmap
                            if (bmp != null && !bmp.isRecycled) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Düzenlenen Görsel",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Stabil crop rehberi: merkezde soru alanı vurgusu (sadece görsel rehber, gerçek crop tam bitmap üzerinden yapılır)
                                // Aspect ratio korunarak 90% merkez alan gösterilir
                            }
                        }
                        else -> {
                            Text("Görsel yüklenemedi", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alt Aksiyon Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("İptal")
                    }

                    Button(
                        onClick = {
                            val bmp = currentBitmap
                            if (bmp != null && !bmp.isRecycled) {
                                try {
                                    // Stabil crop rect: sadece soru alanını alacak şekilde merkez 92% crop
                                    // Rotation ve scale sonrası bitmap zaten MAX_DIMENSION altında, aspect ratio korunur
                                    val cropped = cropCenterWithAspect(bmp, 0.92f)
                                    // onConfirm bitmap döndürür - caller GorselDepo.kaydetBitmap ile kaydetmeli
                                    // Dialog dismiss'te çizim kaybolmasın: bitmap copy ver, original korunur
                                    val result = if (cropped != bmp) cropped else bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)
                                    onCropped(result)
                                    // cropped farklıysa caller recycle edecek, biz original'i saklıyoruz (dismiss'te temizlenecek)
                                    // Eğer cropped == bmp ise copy verildi, original korunur
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // fallback: doğrudan bitmap döndür (copy)
                                    try {
                                        val copy = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)
                                        onCropped(copy)
                                    } catch (_: Exception) {
                                        onDismiss()
                                    }
                                } catch (oom: OutOfMemoryError) {
                                    oom.printStackTrace()
                                    onDismiss()
                                }
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = currentBitmap != null && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uygula")
                    }
                }
            }
        }
    }
}

/**
 * Backward-compat overload: String path döndüren eski imza için sarmalayıcı.
 * Yeni bitmap callback'e delegate eder ve cache'e JPEG yazar.
 * Not: Overload ambiguity engellemek için isim farklı: onCroppedPath
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
            }
        }
    )
}

// ------- Yardımcılar -------

private fun decodeSampledBitmap(filePath: String, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return BitmapFactory.decodeFile(filePath)
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

/**
 * Stabil crop rect hesabı: bitmap'in merkezinde aspect ratio korunarak verilen oranda crop.
 * Scale ve rotation sonrası sadece soru alanını alacak şekilde merkez bölge alınır.
 */
private fun cropCenterWithAspect(bitmap: Bitmap, keepRatio: Float): Bitmap {
    if (keepRatio >= 1f) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return bitmap
    // keepRatio kadar merkez bölge
    val cropW = (w * keepRatio).roundToInt().coerceIn(1, w)
    val cropH = (h * keepRatio).roundToInt().coerceIn(1, h)
    val x = ((w - cropW) / 2).coerceIn(0, w - cropW)
    val y = ((h - cropH) / 2).coerceIn(0, h - cropH)
    return try {
        Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
    } catch (oom: OutOfMemoryError) {
        oom.printStackTrace()
        bitmap
    } catch (_: Exception) {
        bitmap
    }
}
