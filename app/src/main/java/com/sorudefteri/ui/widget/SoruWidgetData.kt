package com.sorudefteri.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.dao.SoruDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Widget verisini sağlayan Hilt EntryPoint (receiver'dan bağımlılık okumak için).
 * Widget receiver'lar Hilt enjekte edilemediğinden EntryPointAccessors kullanılır.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoruWidgetEntryPoint {
    fun soruDao(): SoruDao
    fun ayarlarDataStore(): AyarlarDataStore
}

/** Widget'ta gösterilen veriler. */
data class SoruWidgetData(
    val currentStreak: Int = 0,
    val dueReviewCount: Int = 0,
    val examDaysRemaining: Long = -1L // -1 = sınav tarihi ayarlanmamış
)

/**
 * Widget verisini Room + DataStore'dan yükler (IO dispatcher'da çağrılmalı).
 */
suspend fun soruWidgetVerisiniYukle(context: Context): SoruWidgetData =
    withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SoruWidgetEntryPoint::class.java
        )
        val dao = entryPoint.soruDao()
        val ayarlar = entryPoint.ayarlarDataStore()

        val streak = ayarlar.currentStreak.first()
        val dueCount = dao.getDueReviewCountDirect(System.currentTimeMillis())
        val examDate = ayarlar.getExamTargetDate()

        // Tam gün farkı: her iki an da gün başına (00:00) çekilip bölünür
        val todayStart = dayStart(System.currentTimeMillis())
        val examDaysRemaining = if (examDate > 0L) {
            (dayStart(examDate) - todayStart) / MILLIS_PER_DAY
        } else -1L

        SoruWidgetData(
            currentStreak = streak,
            dueReviewCount = dueCount,
            examDaysRemaining = examDaysRemaining
        )
    }

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

private fun dayStart(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Widget güncelleme yardımcıları.
 * Çağrılması önerilen noktalar:
 *  - MainActivity.onCreate (uygulama her açıldığında)
 *  - ReminderWorker (günlük bildirim çalıştığında)
 *  - Sınav tarihi her set edildiğinde (AyarlarViewModel)
 */
object SoruWidgetUpdater {
    suspend fun updateAllNow(context: Context) {
        try {
            SoruDefteriWidget().updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateAllNowAsync(context: Context) {
        SoruWidgetScope.scope.launch {
            updateAllNow(context.applicationContext)
        }
    }
}

/**
 * PERF: Widget güncellemeleri için paylaşılan uygulama-ömürlü scope.
 * Önceden her çağrıda `CoroutineScope(Dispatchers.IO).launch` ile çıplak
 * scope açılıyordu (yapısal eşzamanlılık ihlali + iptal edilemez işler).
 */
private object SoruWidgetScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
