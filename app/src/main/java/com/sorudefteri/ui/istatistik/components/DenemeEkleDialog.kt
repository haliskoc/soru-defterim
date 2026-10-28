package com.sorudefteri.ui.istatistik.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import com.sorudefteri.ui.deneme.DenemeViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max

private data class DersSoruKotasi(
    val dersAdi: String,
    val varsayilanSoru: Int
)

private val POPULER_YAYINEVLERI = listOf(
    "3D Yayınları",
    "Özdebir",
    "Bilgi Sarmal",
    "Apotemi",
    "Orijinal",
    "Toprak",
    "Töder",
    "Limit",
    "Krallar Karması"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DenemeEkleDialog(
    onDismiss: () -> Unit,
    onSave: (DenemeSinaviEntity) -> Unit
) {
    var baslik by remember { mutableStateOf("") }
    var yayinEvi by remember { mutableStateOf("") }
    var selectedSinavTuru by remember { mutableStateOf("TYT") }
    var sureDakikaStr by remember(selectedSinavTuru) {
        mutableStateOf(
            when (selectedSinavTuru) {
                "TYT" -> "165"
                "AYT" -> "180"
                "LGS" -> "155"
                else -> "130"
            }
        )
    }
    var optikTestId by remember {
        mutableStateOf("SD-EXAM-${UUID.randomUUID().toString().take(8).uppercase()}")
    }
    var hizliCevapAnahtari by remember { mutableStateOf("") }
    var notlar by remember { mutableStateOf("") }

    val defaultSubjects = remember(selectedSinavTuru) {
        when (selectedSinavTuru) {
            "TYT" -> listOf(
                DersSoruKotasi("Türkçe", 40),
                DersSoruKotasi("Temel Matematik", 40),
                DersSoruKotasi("Fen Bilimleri", 20),
                DersSoruKotasi("Sosyal Bilimler", 20)
            )
            "AYT" -> listOf(
                DersSoruKotasi("Matematik", 40),
                DersSoruKotasi("Fizik", 14),
                DersSoruKotasi("Kimya", 13),
                DersSoruKotasi("Biyoloji", 13),
                DersSoruKotasi("Türk Dili ve Ed.", 24),
                DersSoruKotasi("Tarih-1/Coğ-1", 16)
            )
            "LGS" -> listOf(
                DersSoruKotasi("Türkçe", 20),
                DersSoruKotasi("Matematik", 20),
                DersSoruKotasi("Fen Bilimleri", 20),
                DersSoruKotasi("T.C. İnkılap", 10),
                DersSoruKotasi("Din Kültürü", 10),
                DersSoruKotasi("İngilizce", 10)
            )
            else -> listOf(
                DersSoruKotasi("Genel Yetenek", 60),
                DersSoruKotasi("Genel Kültür", 60)
            )
        }
    }

    val dogruMap = remember(selectedSinavTuru) { mutableStateMapOf<String, String>() }
    val yanlisMap = remember(selectedSinavTuru) { mutableStateMapOf<String, String>() }

    // Net Katsayısı: LGS 3 yanlış 1 doğru (0.333), TYT/AYT/KPSS 4 yanlış 1 doğru (0.25)
    val yanlisKatsayi = if (selectedSinavTuru == "LGS") 1.0 / 3.0 else 0.25

    val dersNetleri = defaultSubjects.map { kota ->
        val d = dogruMap[kota.dersAdi]?.toIntOrNull() ?: 0
        val y = yanlisMap[kota.dersAdi]?.toIntOrNull() ?: 0
        val bos = if (d > 0 || y > 0) (kota.varsayilanSoru - d - y).coerceAtLeast(0) else 0
        val rawNet = max(0.0, d - (y * yanlisKatsayi))
        val net = Math.round(rawNet * 100.0) / 100.0
        DenemeDersNeti(
            dersAdi = kota.dersAdi,
            dogruSayisi = d,
            yanlisSayisi = y,
            bosSayisi = bos,
            net = net
        )
    }

    val toplamDogru = dersNetleri.sumOf { it.dogruSayisi }
    val toplamYanlis = dersNetleri.sumOf { it.yanlisSayisi }
    val toplamBos = dersNetleri.sumOf { it.bosSayisi }
    val toplamSoru = (toplamDogru + toplamYanlis + toplamBos).takeIf { it > 0 }
        ?: defaultSubjects.sumOf { it.varsayilanSoru }
    val toplamNet = Math.round(dersNetleri.sumOf { it.net } * 100.0) / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Yeni Deneme Sınavı",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$toplamDogru D • $toplamYanlis Y • $toplamBos B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Net: $toplamNet",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                    label = { Text("Deneme Adı *") },
                    placeholder = { Text("Örn: Türkiye Geneli TYT-1, Simülasyon 3...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Yayınevi Girişi + Hızlı Çipler
                OutlinedTextField(
                    value = yayinEvi,
                    onValueChange = { yayinEvi = it },
                    label = { Text("Yayınevi / Kurum") },
                    placeholder = { Text("Örn: 3D Yayınları, Özdebir...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    POPULER_YAYINEVLERI.forEach { populer ->
                        SuggestionChip(
                            onClick = {
                                yayinEvi = populer
                                if (baslik.isBlank()) {
                                    baslik = "$populer $selectedSinavTuru Denemesi"
                                }
                            },
                            label = { Text(populer, fontSize = 11.sp) }
                        )
                    }
                }

                // Sınav Türü Çipleri & Süre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TYT", "AYT", "LGS", "KPSS").forEach { tur ->
                            FilterChip(
                                selected = selectedSinavTuru == tur,
                                onClick = { selectedSinavTuru = tur },
                                label = { Text(tur, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = sureDakikaStr,
                        onValueChange = { sureDakikaStr = it },
                        label = { Text("Süre(dk)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.width(86.dp)
                    )
                }

                Text(
                    text = "Ders Bazlı Doğru / Yanlış Girişi:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Ders Girişleri
                defaultSubjects.forEach { kota ->
                    val ders = kota.dersAdi
                    val dStr = dogruMap[ders] ?: ""
                    val yStr = yanlisMap[ders] ?: ""
                    val d = dStr.toIntOrNull() ?: 0
                    val y = yStr.toIntOrNull() ?: 0
                    val bos = if (d > 0 || y > 0) (kota.varsayilanSoru - d - y).coerceAtLeast(0) else 0
                    val n = Math.round(max(0.0, d - (y * yanlisKatsayi)) * 100.0) / 100.0

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
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
                                    text = "$ders (${kota.varsayilanSoru})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Net: $n" + if (bos > 0) " • Boş: $bos" else "",
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

                // Optik Kod & Hızlı Cevap Anahtarı (İsteğe Bağlı)
                OutlinedTextField(
                    value = hizliCevapAnahtari,
                    onValueChange = { hizliCevapAnahtari = it },
                    label = { Text("Hızlı Cevap Anahtarı (Optik İçin İsteğe Bağlı)") },
                    placeholder = { Text("Örn: 1:A, 2:C, 3:E veya ACDEBA...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                        val parsedKey = DenemeViewModel.parseFlexibleAnswerKeyInput(hizliCevapAnahtari)
                        val answerKeyJson = if (parsedKey.isNotEmpty()) json.encodeToString(parsedKey) else null
                        val entity = DenemeSinaviEntity(
                            testId = optikTestId.trim().ifBlank {
                                "SD-EXAM-${UUID.randomUUID().toString().take(8).uppercase()}"
                            },
                            baslik = baslik.trim(),
                            yayinEvi = yayinEvi.trim(),
                            sinavTuru = selectedSinavTuru,
                            tarih = System.currentTimeMillis(),
                            sureDakika = sureDakikaStr.toIntOrNull() ?: 165,
                            toplamSoru = toplamSoru,
                            toplamDogru = toplamDogru,
                            toplamYanlis = toplamYanlis,
                            toplamBos = toplamBos,
                            toplamNet = toplamNet,
                            dersNetleriJson = json.encodeToString(dersNetleri),
                            cevapAnahtariJson = answerKeyJson,
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
