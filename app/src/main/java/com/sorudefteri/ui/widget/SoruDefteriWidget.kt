package com.sorudefteri.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sorudefteri.MainActivity

/**
 * Ana ekran widget'ı (Glance): 🔥 streak, ⏰ tekrarı gelen soru sayısı,
 * 🎯 sınav geri sayımı. Veriler soruWidgetVerisiniYukle() ile Room + DataStore'dan
 * yüklenir; MainActivity onCreate, ReminderWorker ve AyarlarViewModel tarih
 * değişimlerinde SoruWidgetUpdater.updateAll çağrılarak yenilenir.
 */
class SoruDefteriWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = soruWidgetVerisiniYukle(context.applicationContext)
        provideContent {
            GlanceTheme {
                WidgetContent(context.applicationContext, data)
            }
        }
    }

    /** Widget'a dokunulduğunda uygulamayı açar (navigate_to parametresi opsiyonel). */
    private fun openAppIntent(context: Context, navigateTo: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            // Widget host'tan aktivite başlatma için zorunlu
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (navigateTo != null) putExtra("navigate_to", navigateTo)
        }

    @Composable
    private fun WidgetContent(context: Context, data: SoruWidgetData) {
        val container = ColorProvider(Color(0xFF1E212B).copy(alpha = 0.94f))
        val onContainer = ColorProvider(Color(0xFFE8E9EC))
        val muted = ColorProvider(Color(0xFF9AA1AC))
        val warn = ColorProvider(Color(0xFFFFB74D))
        val accent = ColorProvider(Color(0xFF8FC7FF))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(container)
                .cornerRadius(16.dp)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Başlık — dokunulduğunda uygulamayı açar
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity(openAppIntent(context))),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(text = "📚", style = TextStyle(fontSize = 16.sp))
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "Soru Defterim",
                        style = TextStyle(
                            color = onContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))

                // 🔥 Streak
                WidgetRow(
                    context = context,
                    emoji = "🔥",
                    text = "${data.currentStreak} günlük seri",
                    textColor = onContainer,
                    navigateTo = null,
                    fontSize = 13.sp
                )
                Spacer(modifier = GlanceModifier.height(8.dp))

                // ⏰ Tekrar vakti — dokunulduğunda günün tekrar testini başlatır
                WidgetRow(
                    context = context,
                    emoji = "⏰",
                    text = if (data.dueReviewCount > 0) "${data.dueReviewCount} soru tekrar vakti" else "Tekrar vakti yok",
                    textColor = if (data.dueReviewCount > 0) warn else muted,
                    navigateTo = "test_due",
                    fontSize = 13.sp
                )
                Spacer(modifier = GlanceModifier.height(8.dp))

                // 🎯 Sınav geri sayımı — tarih Ayarlar > Genel'den seçilir
                WidgetRow(
                    context = context,
                    emoji = "🎯",
                    text = when {
                        data.examDaysRemaining == -1L -> "Sınav tarihi ayarlanmadı"
                        data.examDaysRemaining < 0L -> "Sınav geçti — tarihi güncelle"
                        data.examDaysRemaining == 0L -> "Sınav günü! 🍀"
                        else -> "Sınava ${data.examDaysRemaining} gün kaldı"
                    },
                    textColor = accent,
                    navigateTo = "kutuphane",
                    fontSize = 13.sp
                )
            }
        }
    }

    @Composable
    private fun WidgetRow(
        context: Context,
        emoji: String,
        text: String,
        textColor: ColorProvider,
        navigateTo: String?,
        fontSize: TextUnit
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openAppIntent(context, navigateTo))),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(text = emoji, style = TextStyle(fontSize = 14.sp))
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = text,
                style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Medium)
            )
        }
    }
}
