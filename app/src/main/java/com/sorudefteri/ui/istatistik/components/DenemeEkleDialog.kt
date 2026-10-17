package com.sorudefteri.ui.istatistik.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.domain.model.DenemeDersNeti
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

@Composable
fun DenemeEkleDialog(
    onDismiss: () -> Unit,
    onSave: (DenemeSinaviEntity) -> Unit
) {
    var baslik by remember { mutableStateOf("") }
    var selectedSinavTuru by remember { mutableStateOf("TYT") }
    var notlar by remember { mutableStateOf("") }

    val defaultSubjects = remember(selectedSinavTuru) {
        when (selectedSinavTuru) {
            "TYT" -> listOf("Türkçe", "Temel Matematik", "Fen Bilimleri", "Sosyal Bilimler")
            "AYT" -> listOf("Matematik", "Fizik", "Kimya", "Biyoloji", "Türk Dili ve Ed.", "Tarih-1/Coğ-1")
            "LGS" -> listOf("Türkçe", "Matematik", "Fen Bilimleri", "T.C. İnkılap", "Din Kültürü", "İngilizce")
            else -> listOf("Genel Yetenek", "Genel Kültür")
        }
    }

    // Her ders için doğru ve yanlış sayıları (Map: dersAdi -> Pair(dogruStr, yanlisStr))
    val dogruMap = remember(selectedSinavTuru) { mutableStateMapOf<String, String>() }
    val yanlisMap = remember(selectedSinavTuru) { mutableStateMapOf<String, String>() }

    // Net Katsayısı: LGS 3 yanlış 1 doğru (0.333), TYT/AYT/KPSS 4 yanlış 1 doğru (0.25)
    val yanlisKatsayi = if (selectedSinavTuru == "LGS") 1.0 / 3.0 else 0.25

    // Toplam Net Hesaplama
    val dersNetleri = defaultSubjects.map { ders ->
        val d = dogruMap[ders]?.toIntOrNull() ?: 0
        val y = yanlisMap[ders]?.toIntOrNull() ?: 0
        val rawNet = max(0.0, d - (y * yanlisKatsayi))
        val net = Math.round(rawNet * 100.0) / 100.0
        DenemeDersNeti(
            dersAdi = ders,
            dogruSayisi = d,
            yanlisSayisi = y,
            bosSayisi = 0,
            net = net
        )
    }

    val toplamNet = Math.round(dersNetleri.sumOf { it.net } * 100.0) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Yeni Deneme Sınavı",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Net: $toplamNet",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = baslik,
                    onValueChange = { baslik = it },
                    label = { Text("Deneme Adı / Yayın") },
                    placeholder = { Text("Örn: Özdebir TYT 1, 3D Simülasyon...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Sınav Türü Çipleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TYT", "AYT", "LGS", "Diğer").forEach { tur ->
                        FilterChip(
                            selected = selectedSinavTuru == tur,
                            onClick = { selectedSinavTuru = tur },
                            label = { Text(tur) }
                        )
                    }
                }

                Text(
                    text = "Ders Netleri:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Ders Girişleri
                defaultSubjects.forEach { ders ->
                    val dStr = dogruMap[ders] ?: ""
                    val yStr = yanlisMap[ders] ?: ""
                    val d = dStr.toIntOrNull() ?: 0
                    val y = yStr.toIntOrNull() ?: 0
                    val n = Math.round(max(0.0, d - (y * yanlisKatsayi)) * 100.0) / 100.0

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ders,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Net: $n",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = dStr,
                                    onValueChange = { dogruMap[ders] = it },
                                    label = { Text("D") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(62.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = yStr,
                                    onValueChange = { yanlisMap[ders] = it },
                                    label = { Text("Y") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(62.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notlar,
                    onValueChange = { notlar = it },
                    label = { Text("Sınav Notu & Değerlendirme (İsteğe Bağlı)") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (baslik.isNotBlank()) {
                        val json = Json { ignoreUnknownKeys = true }
                        val entity = DenemeSinaviEntity(
                            baslik = baslik.trim(),
                            sinavTuru = selectedSinavTuru,
                            tarih = System.currentTimeMillis(),
                            toplamNet = toplamNet,
                            dersNetleriJson = json.encodeToString(dersNetleri),
                            notlar = notlar.trim().ifBlank { null }
                        )
                        onSave(entity)
                    }
                },
                enabled = baslik.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
