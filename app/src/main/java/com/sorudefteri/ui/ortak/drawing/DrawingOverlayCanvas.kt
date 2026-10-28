package com.sorudefteri.ui.ortak.drawing

import android.content.Context
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.AyarlarDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DrawingCanvasSettingsEntryPoint {
    fun ayarlarDataStore(): AyarlarDataStore
}

@Serializable
enum class DrawingToolType {
    PEN,
    HIGHLIGHTER,
    ERASER
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0f,
    val orientation: Float = 0f
)

@Serializable
data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val colorArgb: Long,
    val baseWidth: Float,
    val toolType: DrawingToolType = DrawingToolType.PEN,
    val averagePressure: Float = 0.5f,
    val averageTilt: Float = 0f
) {
    @Transient
    val cachedPath: Path by lazy { toPath() }

    fun toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            path.lineTo(points[0].x + 0.5f, points[0].y + 0.5f)
            return path
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadraticTo(prev.x, prev.y, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)
        return path
    }
}

@Serializable
data class QuestionDrawingData(
    val soruId: Long,
    val strokes: List<DrawingStroke> = emptyList(),
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Karalamaların kalıcı olarak depolanmasını yöneten yardımcı nesne.
 * context.filesDir/scratchpads/soru_{soruId}.json dosyasına asenkron yazar.
 */
object ScratchpadStorageHelper {
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun saveDrawing(
        context: Context,
        soruId: Long,
        strokes: List<DrawingStroke>
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val dir = File(context.filesDir, "scratchpads")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "soru_${soruId}.json")
                val tmpFile = File(dir, "soru_${soruId}.json.tmp")
                val data = QuestionDrawingData(
                    soruId = soruId,
                    strokes = strokes,
                    lastModified = System.currentTimeMillis()
                )
                val text = json.encodeToString(QuestionDrawingData.serializer(), data)
                tmpFile.writeText(text)
                if (!tmpFile.renameTo(file)) {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun loadDrawing(
        context: Context,
        soruId: Long
    ): List<DrawingStroke> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = File(context.filesDir, "scratchpads/soru_${soruId}.json")
                if (!file.exists()) return@withLock emptyList()
                val text = file.readText()
                val data = json.decodeFromString(QuestionDrawingData.serializer(), text)
                data.strokes
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun clearDrawing(
        context: Context,
        soruId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = File(context.filesDir, "scratchpads/soru_${soruId}.json")
                if (file.exists()) file.delete() else true
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Lenovo Yoga Tab TB-710FU & Lenovo Tab Pen Pro için optimize edilmiş çizim ve karalama katmanı.
 *
 * Özellikler:
 * - Donanımsal Palm Rejection (Avuç İçi Engelleme): Yalnızca stylus vuruşlarını çizer; 2 parmak hareketlerine (yakınlaştırma/kaydırma) izin verir.
 * - 8.192 Basınç Seviyesi Desteği: event.getPressure() verisiyle yumuşatılmış dinamik çizgi genişliği eğrisi.
 * - Tilt Desteği (Açı Algılama): event.getAxisValue(AXIS_TILT) ile yatık kalem açılarında gölgeleme ve genişletilmiş çizgi.
 * - Düşük Gecikmeli Compose Path Buffering (quadratic bezier ara noktalar).
 * - Kalıcı Scratchpad (soruId bazlı disk kaydetme ve otomatik yükleme).
 * - "Karalamaları Gizle / Göster", "Geri Al", "Temizle", araç ve renk paleti kontrolleri.
 * - Lenovo Stylus Çift Tıklama (Kalem ↔ Silgi, Geri Al, Renk Paleti, Koç İpucu) & Haptik Geri Bildirim.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingOverlayCanvas(
    soruId: Long,
    modifier: Modifier = Modifier,
    isDrawingActive: Boolean = false,
    onDrawingActiveChange: ((Boolean) -> Unit)? = null,
    onCoachHintRequested: (() -> Unit)? = null,
    doubleTapActionOverride: StylusDoubleTapAction? = null,
    palmRejectionOverride: Boolean? = null,
    hapticFeedbackOverride: Boolean? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // DataStore Ayarları
    val ayarlarDataStore = remember(context) {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DrawingCanvasSettingsEntryPoint::class.java
            ).ayarlarDataStore()
        } catch (_: Exception) {
            null
        }
    }

    val storeDoubleTapAction by ayarlarDataStore?.stylusDoubleTapActionFlow
        ?.collectAsStateWithLifecycle(initialValue = StylusDoubleTapAction.PEN_ERASER_TOGGLE)
        ?: remember { mutableStateOf(StylusDoubleTapAction.PEN_ERASER_TOGGLE) }

    val storePalmRejection by ayarlarDataStore?.stylusPalmRejectionFlow
        ?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) }

    val storeHapticFeedback by ayarlarDataStore?.stylusHapticFeedbackFlow
        ?.collectAsStateWithLifecycle(initialValue = true)
        ?: remember { mutableStateOf(true) }

    val effectiveDoubleTapAction = doubleTapActionOverride ?: storeDoubleTapAction
    val effectivePalmRejection = palmRejectionOverride ?: storePalmRejection
    val effectiveHapticFeedback = hapticFeedbackOverride ?: storeHapticFeedback

    // Stylus Hardware Controller
    val stylusController = remember(effectivePalmRejection, effectiveHapticFeedback) {
        LenovoStylusHardwareController(
            context = context,
            isHapticFeedbackEnabled = effectiveHapticFeedback,
            isPalmRejectionEnabled = effectivePalmRejection
        )
    }

    // Çizim Durumları
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    // Çizim Aktiflik Durumu: Soru açıldığında varsayılan olarak PASİF / KAPALI (false)
    var internalDrawingActive by rememberSaveable(soruId) { mutableStateOf(isDrawingActive) }

    LaunchedEffect(isDrawingActive) {
        internalDrawingActive = isDrawingActive
    }

    val currentDrawingActive = if (onDrawingActiveChange != null) isDrawingActive else internalDrawingActive
    val updateDrawingActive: (Boolean) -> Unit = { active ->
        internalDrawingActive = active
        onDrawingActiveChange?.invoke(active)
    }

    var isCanvasVisible by remember { mutableStateOf(true) } // Karalamaları Gizle / Göster
    var isToolbarExpanded by remember { mutableStateOf(false) } // Toolbar Açık / Küçültülmüş
    var showColorPalette by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var currentTool by remember { mutableStateOf(DrawingToolType.PEN) }
    var previousNonEraserTool by remember { mutableStateOf(DrawingToolType.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFE53935)) } // Varsayılan Kırmızı
    var baseStrokeWidth by remember { mutableFloatStateOf(6f) }

    // Aktif çizim yolu ve noktalar (Düşük gecikmeli tampon)
    var activeStrokePath by remember { mutableStateOf<Path?>(null) }
    val activeStrokePoints = remember { mutableListOf<StrokePoint>() }
    var activeStrokeWidth by remember { mutableFloatStateOf(6f) }
    var activeStrokeColor by remember { mutableStateOf(selectedColor) }

    // Renk Paleti Seçenekleri
    val paletteColors = listOf(
        Color(0xFFE53935), // Kırmızı
        Color(0xFF1E88E5), // Mavi
        Color(0xFF43A047), // Yeşil
        Color(0xFFFB8C00), // Turuncu
        Color(0xFF8E24AA), // Mor
        Color(0xFFFDD835), // Sarı
        Color(0xFF212121), // Koyu Gri / Siyah
        Color(0xFFECEFF1)  // Beyazımsı / Açık Gri
    )

    // Soru ID değiştiğinde kalıcı karalamaları otomatik yükle
    LaunchedEffect(soruId) {
        strokes.clear()
        val loaded = ScratchpadStorageHelper.loadDrawing(context, soruId)
        strokes.addAll(loaded)
    }

    // Çift tıklama eylemini gerçekleştirme fonksiyonu
    val handleDoubleTapAction: () -> Unit = {
        stylusController.executeDoubleTapAction(
            action = effectiveDoubleTapAction,
            onTogglePenEraser = {
                if (currentTool == DrawingToolType.ERASER) {
                    currentTool = previousNonEraserTool
                } else {
                    previousNonEraserTool = currentTool
                    currentTool = DrawingToolType.ERASER
                }
            },
            onUndo = {
                if (strokes.isNotEmpty()) {
                    strokes.removeAt(strokes.lastIndex)
                    coroutineScope.launch {
                        ScratchpadStorageHelper.saveDrawing(context, soruId, strokes.toList())
                    }
                }
            },
            onOpenColorPalette = {
                showColorPalette = !showColorPalette
                if (!isToolbarExpanded) isToolbarExpanded = true
            },
            onShowCoachHint = {
                onCoachHintRequested?.invoke()
            },
            view = view
        )
    }

    // Ekran üzerinde çizim ve kontroller
    val isDarkCanvas = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Çizim Kanvası Katmanı
        if (isCanvasVisible) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .then(
                        if (currentDrawingActive) {
                            Modifier.pointerInteropFilter { motionEvent ->
                                val pointerCount = motionEvent.pointerCount
                                val actionMasked = motionEvent.actionMasked

                                val stylusPointerIndex = (0 until pointerCount).firstOrNull { idx ->
                                    stylusController.isStylusToolType(motionEvent.getToolType(idx))
                                }

                                // İki parmakla yakınlaştırma veya kaydırma yapılıyorsa (ve ekranda stylus yoksa) sisteme bırak
                                if (pointerCount >= 2 && effectivePalmRejection && stylusPointerIndex == null) {
                                    return@pointerInteropFilter false
                                }

                                val pointerIndex = stylusPointerIndex ?: motionEvent.actionIndex.coerceIn(0, pointerCount - 1)
                                val toolType = motionEvent.getToolType(pointerIndex)
                                val isStylus = stylusController.isStylusToolType(toolType)

                                // Palm Rejection: Kalem değilse ve avuç içi engelleme açıksa yut
                                if (effectivePalmRejection && !isStylus) {
                                    return@pointerInteropFilter true
                                }

                                // Çoklu dokunuşta aksiyon kalem olmayan parmağa aitse (avuç içi değmesi/kalkması) yut ve çizimi bozma
                                val actionIndex = motionEvent.actionIndex.coerceIn(0, pointerCount - 1)
                                if ((actionMasked == MotionEvent.ACTION_POINTER_DOWN || actionMasked == MotionEvent.ACTION_POINTER_UP) &&
                                    effectivePalmRejection &&
                                    !stylusController.isStylusToolType(motionEvent.getToolType(actionIndex))
                                ) {
                                    return@pointerInteropFilter true
                                }

                                // Stylus donanımsal çift tıklama ve buton kontrolü
                                val doubleTapDetected = stylusController.processMotionEvent(motionEvent) {
                                    handleDoubleTapAction()
                                }
                                if (doubleTapDetected) {
                                    return@pointerInteropFilter true
                                }

                                val x = motionEvent.getX(pointerIndex)
                                val y = motionEvent.getY(pointerIndex)
                                val pressure = motionEvent.getPressure(pointerIndex).coerceIn(0.05f, 1.0f)
                                val tilt = motionEvent.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex).coerceAtLeast(0f)
                                val orientation = motionEvent.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)

                                when (actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                        activeStrokePoints.clear()
                                        activeStrokePoints.add(StrokePoint(x, y, pressure, tilt, orientation))

                                        val newPath = Path().apply { moveTo(x, y) }
                                        activeStrokePath = newPath

                                        // Basınç ve eğim tabanlı dinamik genişlik ve renk hesapla
                                        val dynamicWidth = calculateDynamicStrokeWidth(
                                            baseWidth = baseStrokeWidth,
                                            pressure = pressure,
                                            tilt = tilt,
                                            toolType = currentTool
                                        )
                                        activeStrokeWidth = dynamicWidth

                                        val modulatedColor = calculateModulatedColor(
                                            baseColor = selectedColor,
                                            tilt = tilt,
                                            toolType = currentTool
                                        )
                                        activeStrokeColor = modulatedColor
                                        true
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        if (activeStrokePoints.isNotEmpty()) {
                                            val prev = activeStrokePoints.last()
                                            val midX = (prev.x + x) / 2f
                                            val midY = (prev.y + y) / 2f

                                            activeStrokePath?.quadraticTo(prev.x, prev.y, midX, midY)
                                            activeStrokePoints.add(StrokePoint(x, y, pressure, tilt, orientation))

                                            // Dinamik genişlik güncelle
                                            activeStrokeWidth = calculateDynamicStrokeWidth(
                                                baseWidth = baseStrokeWidth,
                                                pressure = pressure,
                                                tilt = tilt,
                                                toolType = currentTool
                                            )
                                        }
                                        true
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                        activeStrokePoints.add(StrokePoint(x, y, pressure, tilt, orientation))
                                        val path = activeStrokePath
                                        if (path != null && activeStrokePoints.isNotEmpty()) {
                                            val avgPressure = activeStrokePoints.map { it.pressure }.average().toFloat()
                                            val avgTilt = activeStrokePoints.map { it.tilt }.average().toFloat()

                                            val finalizedStroke = DrawingStroke(
                                                points = activeStrokePoints.toList(),
                                                colorArgb = activeStrokeColor.toArgb().toLong(),
                                                baseWidth = activeStrokeWidth,
                                                toolType = currentTool,
                                                averagePressure = avgPressure,
                                                averageTilt = avgTilt
                                            )
                                            strokes.add(finalizedStroke)

                                            // Disk üzerine asenkron otomatik kaydetme
                                            coroutineScope.launch {
                                                ScratchpadStorageHelper.saveDrawing(context, soruId, strokes.toList())
                                            }
                                        }
                                        activeStrokePoints.clear()
                                        activeStrokePath = null
                                        true
                                    }

                                    MotionEvent.ACTION_CANCEL -> {
                                        activeStrokePoints.clear()
                                        activeStrokePath = null
                                        true
                                    }

                                    else -> false
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                // 1. Kaydedilmiş Çizgileri Çiz
                strokes.forEach { stroke ->
                    val path = stroke.cachedPath
                    val rawColor = Color(stroke.colorArgb)
                    val strokeColor = if (isDarkCanvas && (stroke.colorArgb and 0x00FFFFFFL) == 0x00212121L) {
                        Color(0xFFF5F5F5).copy(alpha = rawColor.alpha)
                    } else {
                        rawColor
                    }
                    when (stroke.toolType) {
                        DrawingToolType.ERASER -> {
                            drawPath(
                                path = path,
                                color = Color.Transparent,
                                blendMode = BlendMode.Clear,
                                style = Stroke(
                                    width = stroke.baseWidth * 3.2f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        DrawingToolType.HIGHLIGHTER -> {
                            drawPath(
                                path = path,
                                color = strokeColor.copy(alpha = 0.35f),
                                style = Stroke(
                                    width = stroke.baseWidth * 2.8f,
                                    cap = StrokeCap.Square,
                                    join = StrokeJoin.Bevel
                                )
                            )
                        }
                        DrawingToolType.PEN -> {
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = stroke.baseWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // 2. Anlık Çizilmekte Olan Vuruşu Çiz
                activeStrokePath?.let { path ->
                    val effectiveActiveColor = if (isDarkCanvas && (activeStrokeColor.toArgb().toLong() and 0x00FFFFFFL) == 0x00212121L) {
                        Color(0xFFF5F5F5).copy(alpha = activeStrokeColor.alpha)
                    } else {
                        activeStrokeColor
                    }
                    when (currentTool) {
                        DrawingToolType.ERASER -> {
                            drawPath(
                                path = path,
                                color = Color.Transparent,
                                blendMode = BlendMode.Clear,
                                style = Stroke(
                                    width = activeStrokeWidth * 3.2f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        DrawingToolType.HIGHLIGHTER -> {
                            drawPath(
                                path = path,
                                color = effectiveActiveColor.copy(alpha = 0.35f),
                                style = Stroke(
                                    width = activeStrokeWidth * 2.8f,
                                    cap = StrokeCap.Square,
                                    join = StrokeJoin.Bevel
                                )
                            )
                        }
                        DrawingToolType.PEN -> {
                            drawPath(
                                path = path,
                                color = effectiveActiveColor,
                                style = Stroke(
                                    width = activeStrokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }

        // Floating Dokunmatik Stylus Kontrol Paneli (Sağ Üst / Sabitlenmiş - Yalnızca karalama açıkken gösterilir)
        if (currentDrawingActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ana Hızlı Eylem Butonu (Küçültülmüş Mod)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    isToolbarExpanded = !isToolbarExpanded
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Karalama Çubuğu",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Karalama Modu",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (strokes.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${strokes.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Karalama Modunu Kapat Butonu
                            IconButton(
                                onClick = {
                                    updateDrawingActive(false)
                                    isToolbarExpanded = false
                                    stylusController.performHapticTick(view)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Karalama Modunu Kapat",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Genişletilmiş Çizim Araç Çubuğu
                    AnimatedVisibility(
                        visible = isToolbarExpanded,
                        enter = fadeIn() + slideInVertically { height -> -height / 3 },
                        exit = fadeOut() + slideOutVertically { height -> -height / 3 }
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.width(280.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Mod & Görünürlük Satırı
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                updateDrawingActive(false)
                                                isToolbarExpanded = false
                                                stylusController.performHapticTick(view)
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Çizimi Kapat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Çizimi Kapat",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Karalamaları Gizle / Göster Butonu
                                        IconButton(
                                            onClick = {
                                                isCanvasVisible = !isCanvasVisible
                                                stylusController.performHapticTick(view)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCanvasVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (isCanvasVisible) "Karalamaları Gizle" else "Karalamaları Göster",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Toolbar Kapat ve Moddan Çık
                                        IconButton(
                                            onClick = {
                                                isToolbarExpanded = false
                                                updateDrawingActive(false)
                                                stylusController.performHapticTick(view)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Kapat",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                            // 2. Araç Seçici (Kalem, Fosforlu, Silgi)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Kalem
                                ToolButton(
                                    icon = Icons.Default.Edit,
                                    label = "Kalem",
                                    isSelected = currentTool == DrawingToolType.PEN,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    currentTool = DrawingToolType.PEN
                                    previousNonEraserTool = DrawingToolType.PEN
                                    stylusController.performHapticTick(view)
                                }

                                // Fosforlu Kalem
                                ToolButton(
                                    icon = Icons.Default.Highlight,
                                    label = "Fosforlu",
                                    isSelected = currentTool == DrawingToolType.HIGHLIGHTER,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    currentTool = DrawingToolType.HIGHLIGHTER
                                    previousNonEraserTool = DrawingToolType.HIGHLIGHTER
                                    stylusController.performHapticTick(view)
                                }

                                // Silgi
                                ToolButton(
                                    icon = Icons.Default.AutoFixHigh,
                                    label = "Silgi",
                                    isSelected = currentTool == DrawingToolType.ERASER,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    previousNonEraserTool = currentTool
                                    currentTool = DrawingToolType.ERASER
                                    stylusController.performHapticTick(view)
                                }
                            }

                            // 3. Renk Seçici & Palet
                            if (currentTool != DrawingToolType.ERASER) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Renkler & Kalınlık",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = { showColorPalette = !showColorPalette },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Palette,
                                                contentDescription = "Palet",
                                                tint = selectedColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Hızlı Renk Seçimi
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        paletteColors.take(6).forEach { color ->
                                            val isSelected = selectedColor == color
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        selectedColor = color
                                                        stylusController.performHapticTick(view)
                                                    }
                                            )
                                        }
                                    }

                                    // Kalınlık Seçimi
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(3f to "İnce", 6f to "Orta", 10f to "Kalın").forEach { (width, label) ->
                                            val isSelected = baseStrokeWidth == width
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 2.dp)
                                                    .clickable {
                                                        baseStrokeWidth = width
                                                        stylusController.performHapticTick(view)
                                                    }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Alt Eylemler (Geri Al & Temizle)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Geri Al Butonu
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = strokes.isNotEmpty()) {
                                            if (strokes.isNotEmpty()) {
                                                strokes.removeAt(strokes.lastIndex)
                                                stylusController.performHapticTick(view)
                                                coroutineScope.launch {
                                                    ScratchpadStorageHelper.saveDrawing(context, soruId, strokes.toList())
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Geri Al",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Geri Al",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                                        )
                                    }
                                }

                                // Temizle Butonu
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = strokes.isNotEmpty()) {
                                            showClearConfirmDialog = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Temizle",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Temizle",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Temizleme Onay İletişim Kutusu
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Karalamaları Temizle", fontWeight = FontWeight.Bold) },
            text = { Text("Bu soru için yapılmış tüm çizim ve notlar silinecek. Emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokes.clear()
                        showClearConfirmDialog = false
                        stylusController.performHapticTick(view)
                        coroutineScope.launch {
                            ScratchpadStorageHelper.clearDrawing(context, soruId)
                        }
                    }
                ) {
                    Text("Temizle", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 8.192 Basınç Seviyesi & Kalem Eğimi (Tilt) formülüne göre dinamik genişlik hesaplama.
 */
internal fun calculateDynamicStrokeWidth(
    baseWidth: Float,
    pressure: Float,
    tilt: Float,
    toolType: DrawingToolType
): Float {
    if (toolType == DrawingToolType.ERASER) return baseWidth * 3.5f
    if (toolType == DrawingToolType.HIGHLIGHTER) return baseWidth * 2.8f

    // 8.192 basınç seviyesi için spline / non-lineer power curve:
    val clampedPressure = pressure.coerceIn(0.05f, 1.0f)
    val pressureFactor = 0.35f + 1.25f * Math.pow(clampedPressure.toDouble(), 1.35).toFloat()

    // Tilt (eğim açısı): Kalem yatırıldığında (tilt > 0.4 rad) çizgiyi genişlet (kurşun kalem gölgelemesi)
    val tiltFactor = if (tilt > 0.4f) {
        1f + ((tilt - 0.4f) / (Math.PI.toFloat() / 2f - 0.4f)).coerceIn(0f, 1f) * 1.6f
    } else {
        1f
    }

    return (baseWidth * pressureFactor * tiltFactor).coerceIn(baseWidth * 0.3f, baseWidth * 3.5f)
}

/**
 * Kalem eğimine (Tilt) göre gölgeleme / saydamlık modülasyonu.
 */
internal fun calculateModulatedColor(
    baseColor: Color,
    tilt: Float,
    toolType: DrawingToolType
): Color {
    if (toolType == DrawingToolType.ERASER) return Color.Transparent
    if (toolType == DrawingToolType.HIGHLIGHTER) return baseColor.copy(alpha = 0.35f)

    // Kalem yatıkken daha yumuşak gölgeleme
    return if (tilt > 0.45f) {
        val tiltRatio = ((tilt - 0.45f) / (Math.PI.toFloat() / 2f - 0.45f)).coerceIn(0f, 1f)
        val modulatedAlpha = (baseColor.alpha * (1f - tiltRatio * 0.4f)).coerceIn(0.2f, 1f)
        baseColor.copy(alpha = modulatedAlpha)
    } else {
        baseColor
    }
}
