package com.sorudefteri.ui.ayarlar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.ui.ortak.drawing.StylusDoubleTapAction

@Composable
fun GenelAyarlarTab(
    currentAlan: Alan,
    themeMode: Int,
    onAlanSelected: (Alan) -> Unit,
    onThemeModeSelected: (Int) -> Unit,
    onResetAllDataClick: () -> Unit,
    examTargetDateMillis: Long = 0L,
    onExamDateSelected: (Long) -> Unit = {},
    stylusDoubleTapAction: StylusDoubleTapAction = StylusDoubleTapAction.PEN_ERASER_TOGGLE,
    isStylusPalmRejectionEnabled: Boolean = true,
    isStylusHapticFeedbackEnabled: Boolean = true,
    onStylusDoubleTapActionSelected: (StylusDoubleTapAction) -> Unit = {},
    onStylusPalmRejectionToggle: (Boolean) -> Unit = {},
    onStylusHapticFeedbackToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sınav & Hazırlık Alanı
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sınav & Hazırlık Alanı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Ders listesi, konu kataloğu ve AI soru üretim şablonları seçilen alana göre dinamik olarak yapılandırılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val alanlar = Alan.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alanlar.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { alan ->
                                val isSelected = currentAlan == alan
                                if (isSelected) {
                                    Button(
                                        onClick = { onAlanSelected(alan) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(alan.displayName, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onAlanSelected(alan) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(alan.displayName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Sınav Geri Sayımı (Ana ekran widget'ı ile senkron)
        var showExamDatePicker by rememberSaveable { mutableStateOf(false) }
                val examDateFormatted = if (examTargetDateMillis > 0L) {
                    val fmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr"))
                    // DatePicker UTC tabanlı millis döndürür; yerel saat kaymasını önlemek için GMT
                    fmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
                    fmt.format(java.util.Date(examTargetDateMillis))
                } else null

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sınav Geri Sayımı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Sınav tarihinizi seçin; ana ekran widget'ında ve burada kaç gün kaldığını gösterir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val daysLeft = if (examTargetDateMillis > 0L) {
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val examCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = examTargetDateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    (examCal.timeInMillis - cal.timeInMillis) / (24L * 60L * 60L * 1000L)
                } else null

                Text(
                    text = when (val d = if (examTargetDateMillis > 0L) daysLeft else null) {
                        null -> "Henüz sınav tarihi seçilmedi"
                        0L -> "Sınav bugün! Bol şans! 🍀"
                        else ->
                            if (d > 0L) "$examDateFormatted · Sınava $d gün kaldı 🎯"
                            else "$examDateFormatted · Sınav geçti, tarihi güncelleyin"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (examTargetDateMillis > 0L) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showExamDatePicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tarih Seç")
                    }
                    if (examTargetDateMillis > 0L) {
                        OutlinedButton(
                            onClick = { onExamDateSelected(0L) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sıfırla")
                        }
                    }
                }
            }
        }

        if (showExamDatePicker) {
            val datePickerState = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = if (examTargetDateMillis > 0L) examTargetDateMillis
                else System.currentTimeMillis()
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { showExamDatePicker = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                // DatePicker UTC geceyarısı döndürür; DataStore'a olduğu gibi yazılır.
                                // Gün hesapları her iki uçta da gün başına normalizasyonla yapılır.
                                onExamDateSelected(millis)
                            }
                            showExamDatePicker = false
                        }
                    ) { Text("Tamam") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showExamDatePicker = false }
                    ) { Text("İptal") }
                }
            ) {
                androidx.compose.material3.DatePicker(state = datePickerState)
            }
        }

        // 3. Tema Tercihi
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Görünüm ve Tema",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val themes = listOf("Sistem", "Açık", "Koyu")
                val themeIcons = listOf(Icons.Default.BrightnessAuto, Icons.Default.LightMode, Icons.Default.DarkMode)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themes.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = themeMode == index,
                            onClick = { onThemeModeSelected(index) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                            icon = { Icon(themeIcons[index], contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        // 3. Lenovo Stylus & Yoga Tab Ayarları
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Lenovo Stylus & Yoga Tab Ayarları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lenovo Yoga Tab (TB-710FU) & Lenovo Tab Pen Pro donanım entegrasyonu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Çift Tıklama Eylemi
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kalem Çift Tıklama Eylemi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val actions = StylusDoubleTapAction.entries
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        actions.chunked(2).forEach { rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowActions.forEach { action ->
                                    val isSelected = stylusDoubleTapAction == action
                                    if (isSelected) {
                                        Button(
                                            onClick = { onStylusDoubleTapActionSelected(action) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(action.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onStylusDoubleTapActionSelected(action) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(action.title, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Donanımsal Avuç İçi Engelleme (Palm Rejection) Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Donanımsal Avuç İçi Engelleme (Palm Rejection)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Yalnızca kalemi algılar, elinizin ekrana temasından kaynaklanan yanlış çizimleri engeller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isStylusPalmRejectionEnabled,
                        onCheckedChange = onStylusPalmRejectionToggle
                    )
                }

                // Kalem Haptik Titreşimi Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Kalem Haptik Titreşimi (Haptic Tick)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Çift tıklama ve araç geçişlerinde anlık tıklama hissi (Haptic Tick) verir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isStylusHapticFeedbackEnabled,
                        onCheckedChange = onStylusHapticFeedbackToggle
                    )
                }
            }
        }

        // 4. Verileri Sıfırla (Tehlikeli Bölge)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tüm Verileri Sıfırla",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "Kütüphanedeki tüm soruları, fotoğrafları, test geçmişini ve ayarları kalıcı olarak siler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onResetAllDataClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tüm Verileri ve Ayarları Sıfırla", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
