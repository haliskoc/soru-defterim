package com.sorudefteri.ui.ortak

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class DrawingTool {
    PEN, HIGHLIGHTER, ERASER
}

data class DrawPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val isEraser: Boolean = false
)

/**
 * Soru görseli üzerinde öğrencinin karalama, işlem yapma ve altını çizme yapmasını sağlayan çizim tahtası.
 * Path lineTo ile O(1) ekleme; liste copy kaldırıldı.
 */
@Composable
fun DrawingCanvasDialog(
    imagePath: String,
    title: String = "Soru Üzerinde Çizim & Karalama",
    onDismiss: () -> Unit,
    onSave: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isSaving by remember { mutableStateOf(false) }

    val colors = listOf(
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFFFDD835),
        Color(0xFFFFFFFF),
        Color(0xFF212121)
    )

    // Path tabanlı çizim: O(n) liste copy yerine Path.lineTo ile O(1)
    var currentPath by remember { mutableStateOf<Path?>(null) }

    fun saveDrawing() {
        if (isSaving) return
        if (onSave == null) {
            onDismiss()
            return
        }
        isSaving = true
        val snapshotPaths = paths.toList()
        val width = canvasSize.width.takeIf { it > 0 } ?: 1080
        val height = canvasSize.height.takeIf { it > 0 } ?: 1440
        val bgColorInt = surfaceColor.toArgb()

        coroutineScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val baseCanvas = android.graphics.Canvas(resultBitmap)
                    baseCanvas.drawColor(bgColorInt)

                    if (imagePath.isNotBlank()) {
                        val imgFile = File(imagePath)
                        if (imgFile.exists()) {
                            val baseBmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                            if (baseBmp != null) {
                                val scale = min(
                                    width.toFloat() / baseBmp.width.toFloat(),
                                    height.toFloat() / baseBmp.height.toFloat()
                                )
                                val dstW = baseBmp.width * scale
                                val dstH = baseBmp.height * scale
                                val left = (width - dstW) / 2f
                                val top = (height - dstH) / 2f
                                val dstRect = RectF(left, top, left + dstW, top + dstH)
                                baseCanvas.drawBitmap(baseBmp, null, dstRect, null)
                                baseBmp.recycle()
                            }
                        }
                    }

                    val strokeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val strokeCanvas = android.graphics.Canvas(strokeBitmap)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }

                    snapshotPaths.forEach { dp ->
                        paint.strokeWidth = dp.strokeWidth
                        if (dp.isEraser) {
                            paint.color = android.graphics.Color.TRANSPARENT
                            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        } else {
                            paint.color = dp.color.toArgb()
                            paint.xfermode = null
                        }
                        strokeCanvas.drawPath(dp.path.asAndroidPath(), paint)
                    }

                    baseCanvas.drawBitmap(strokeBitmap, 0f, 0f, null)
                    strokeBitmap.recycle()

                    val outDir = File(context.filesDir, "drawings").apply { mkdirs() }
                    val outFile = File(outDir, "drawing_${System.currentTimeMillis()}.png")
                    FileOutputStream(outFile).use { fos ->
                        resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    resultBitmap.recycle()
                    outFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            isSaving = false
            if (savedPath != null) {
                onSave.invoke(savedPath)
            }
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Hard-coded 0xFF121212 yerine theme surface kullan (dark theme uyumu)
                // Eski: Color(0xFF121212) ; Yeni: MaterialTheme.colorScheme.surface veya background
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val imageModel = remember(imagePath) {
                if (imagePath.startsWith("http") || imagePath.startsWith("content://")) imagePath else File(imagePath)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp, top = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imagePath.isNotBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .pointerInput(currentTool, selectedColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Yeni Path başlat - lineTo ile devam edecek, liste copy yok
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    // O(1) ekleme
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    currentPath?.let { path ->
                                        // Path boş değilse kaydet
                                        if (!path.isEmpty) {
                                            val effectiveColor = when (currentTool) {
                                                DrawingTool.HIGHLIGHTER -> selectedColor.copy(alpha = 0.35f)
                                                DrawingTool.ERASER -> Color.Transparent
                                                DrawingTool.PEN -> selectedColor
                                            }
                                            val effectiveWidth = when (currentTool) {
                                                DrawingTool.HIGHLIGHTER -> strokeWidth * 3f
                                                DrawingTool.ERASER -> strokeWidth * 4f
                                                DrawingTool.PEN -> strokeWidth
                                            }
                                            paths.add(
                                                DrawPath(
                                                    path = path,
                                                    color = effectiveColor,
                                                    strokeWidth = effectiveWidth,
                                                    isHighlighter = currentTool == DrawingTool.HIGHLIGHTER,
                                                    isEraser = currentTool == DrawingTool.ERASER
                                                )
                                            )
                                        }
                                    }
                                    currentPath = null
                                },
                                onDragCancel = {
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    // Kaydedilen çizgileri çiz (Path doğrudan)
                    paths.forEach { drawPath ->
                        if (drawPath.isEraser) {
                            drawPath(
                                path = drawPath.path,
                                color = Color.Transparent,
                                blendMode = BlendMode.Clear,
                                style = Stroke(
                                    width = drawPath.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else {
                            drawPath(
                                path = drawPath.path,
                                color = drawPath.color,
                                style = Stroke(
                                    width = drawPath.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Anlık çizilen çizgiyi göster
                    currentPath?.let { activePath ->
                        if (!activePath.isEmpty) {
                            val previewWidth = when (currentTool) {
                                DrawingTool.HIGHLIGHTER -> strokeWidth * 3f
                                DrawingTool.ERASER -> strokeWidth * 4f
                                DrawingTool.PEN -> strokeWidth
                            }
                            if (currentTool == DrawingTool.ERASER) {
                                drawPath(
                                    path = activePath,
                                    color = Color.Transparent,
                                    blendMode = BlendMode.Clear,
                                    style = Stroke(
                                        width = previewWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else {
                                val previewColor = when (currentTool) {
                                    DrawingTool.HIGHLIGHTER -> selectedColor.copy(alpha = 0.35f)
                                    DrawingTool.ERASER -> surfaceColor
                                    DrawingTool.PEN -> selectedColor
                                }
                                drawPath(
                                    path = activePath,
                                    color = previewColor,
                                    style = Stroke(
                                        width = previewWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Geri Al",
                            tint = if (paths.isNotEmpty()) Color.White else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = { paths.clear() },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Temizle",
                            tint = if (paths.isNotEmpty()) Color.White else Color.Gray
                        )
                    }

                    if (onSave != null) {
                        IconButton(
                            onClick = { saveDrawing() },
                            enabled = !isSaving && paths.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Kaydet",
                                tint = if (paths.isNotEmpty()) Color(0xFF66BB6A) else Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButton(
                            title = "Kalem",
                            icon = Icons.Default.Edit,
                            isSelected = currentTool == DrawingTool.PEN,
                            onClick = { currentTool = DrawingTool.PEN }
                        )

                        ToolButton(
                            title = "Vurgula",
                            icon = Icons.Default.Brush,
                            isSelected = currentTool == DrawingTool.HIGHLIGHTER,
                            onClick = { currentTool = DrawingTool.HIGHLIGHTER }
                        )

                        ToolButton(
                            title = "Silgi",
                            icon = Icons.Default.AutoFixHigh,
                            isSelected = currentTool == DrawingTool.ERASER,
                            onClick = { currentTool = DrawingTool.ERASER }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentTool != DrawingTool.ERASER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colors.forEach { color ->
                                val isSelected = selectedColor == color
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 32.dp else 26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Gray.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray
            )
        }
    }
}
