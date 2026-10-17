package com.sorudefteri.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Ana ekran widget'ının receiver'ı. İçerik SoruDefteriWidget (Glance) tarafından sağlanır.
 * Manifest'te APPWIDGET_UPDATE action'ı ve @xml/soru_widget_info meta-data'sı ile tanımlıdır.
 */
class SoruDefteriWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SoruDefteriWidget()
}
