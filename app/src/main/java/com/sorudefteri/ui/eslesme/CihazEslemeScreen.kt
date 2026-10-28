package com.sorudefteri.ui.eslesme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.sync.CihazEslestirme
import com.sorudefteri.data.sync.SyncDeposu

internal fun gerekliIzinler(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    Build.VERSION.SDK_INT >= 29 -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    else -> arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun konumServisiAcikMi(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33) return true
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
    return try {
        LocationManagerCompat.isLocationEnabled(lm)
    } catch (_: Exception) {
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CihazEslemeScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CihazEslemeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val durum by viewModel.durum.collectAsStateWithLifecycle()
    val bulunanlar by viewModel.bulunanlar.collectAsStateWithLifecycle()
    val faz by viewModel.faz.collectAsStateWithLifecycle()
    val ilerleme by viewModel.ilerleme.collectAsStateWithLifecycle()
    val trustedDevices by viewModel.trustedDevices.collectAsStateWithLifecycle()
    val autoProximitySyncEnabled by viewModel.autoProximitySyncEnabled.collectAsStateWithLifecycle()

    var izinlerTamam by remember { mutableStateOf(false) }
    var konumAcik by remember { mutableStateOf(konumServisiAcikMi(context)) }

    val izinLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sonuc ->
        izinlerTamam = sonuc.isNotEmpty() && sonuc.values.all { it }
        konumAcik = konumServisiAcikMi(context)
        if (izinlerTamam && konumAcik) viewModel.baslat()
    }

    fun izinKontrol(launchIfMissing: Boolean = true): Boolean {
        val eksik = gerekliIzinler().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        konumAcik = konumServisiAcikMi(context)
        if (eksik.isEmpty()) {
            izinlerTamam = true
            return true
        }
        izinlerTamam = false
        if (launchIfMissing) {
            izinLauncher.launch(eksik.toTypedArray())
        }
        return false
    }

    LaunchedEffect(Unit) {
        if (izinKontrol(launchIfMissing = true) && konumAcik) {
            viewModel.baslat()
        }
    }

    // Kullanıcı sistem ayarlarından izin veya konum açıp ekrana döndüğünde otomatik başlat
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (izinKontrol(launchIfMissing = false) && konumAcik) {
                    viewModel.baslat()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pinDurumu = durum as? CihazEslestirme.BaglantiDurumu.PinBekliyor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yakındaki Cihazla Eşle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (izinKontrol(launchIfMissing = true) && konumAcik) {
                            viewModel.yenidenTara()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yeniden tara")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!izinlerTamam) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Bluetooth ve Yakın Cihaz izinleri gerekli", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Yakındaki cihazları bulmak için izin verin. İnternet gerekmez.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                if (izinKontrol(launchIfMissing = true) && konumAcik) {
                                    viewModel.baslat()
                                }
                            }) { Text("İzinleri Ver") }
                        }
                    }
                }
            }

            if (izinlerTamam && !konumAcik) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Konum servisi kapalı", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Android 12 ve altı sürümlerde yakındaki cihazların Bluetooth/Wi-Fi ile keşfedilebilmesi için Konum servisinin açık olması gerekir.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (_: Exception) {}
                            }) { Text("Konumu Aç") }
                        }
                    }
                }
            }

            val baglantiHata = durum as? CihazEslestirme.BaglantiDurumu.Hata
            if (baglantiHata != null) {
                item {
                    EslesmeBilgiKarti("Bağlantı Uyarısı", baglantiHata.mesaj, hata = true)
                }
            }

            // Faz kartı (eşleşme ilerlemesi)
            when (val f = faz) {
                is SyncDeposu.Faz.OzetBekleniyor -> item {
                    val detay = if (f.karsiAd.isNotBlank()) "${f.karsiAd} ile soru listesi karşılaştırılıyor..."
                    else "Karşı cihazdan soru özeti bekleniyor..."
                    EslesmeBilgiKarti("Bağlandı — Özet bekleniyor", detay, yukleniyor = true)
                }
                is SyncDeposu.Faz.Hesaplandi -> item {
                    EslesmeBilgiKarti(
                        "Fark bulundu",
                        "${f.alinacak} soru alınacak, ${f.gonderilecek} soru gönderilecek",
                        yukleniyor = true
                    )
                }
                is SyncDeposu.Faz.Aliyor -> item {
                    EslesmeBilgiKarti("Alınıyor", "${f.alinan} / ${f.toplam} soru", true)
                }
                is SyncDeposu.Faz.Gonderiyor -> item {
                    EslesmeBilgiKarti("Gönderiliyor", "${f.gonderilen} / ${f.toplam} soru", true)
                }
                is SyncDeposu.Faz.Bitti -> item {
                    val s = f.sonuc
                    EslesmeBilgiKarti(
                        "Eşleşme tamamlandı",
                        "${s.eklenen} eklendi • ${s.guncellenen} güncellendi • ${s.guncel} zaten güncel" +
                            (if (s.cakisan > 0) " • ${s.cakisan} çakışan (yenisi kazandı)" else "") +
                            (if (s.gorselsiz > 0) " • ${s.gorselsiz} görselsiz" else "") +
                            (f.karsiSonuc?.let { " • karşı: ${it.eklenen} ekledi" } ?: "")
                    )
                }
                is SyncDeposu.Faz.Hata -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Hata", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(f.mesaj, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.yenidenTara() }) {
                                Text("Yeniden Dene")
                            }
                        }
                    }
                }
                is SyncDeposu.Faz.Bos -> Unit
            }

            ilerleme?.let { (alinan, toplam) ->
                if (toplam > 0) item {
                    LinearProgressIndicator(
                        progress = { alinan.toFloat() / toplam.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Yakındaki cihazlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (durum is CihazEslestirme.BaglantiDurumu.Yayinda && bulunanlar.isEmpty()) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (bulunanlar.isEmpty()) {
                item {
                    Text(
                        "Henüz cihaz bulunamadı. Diğer cihazda da bu ekranı açın ve iki cihazı yan yana getirin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(bulunanlar, key = { it.endpointId }) { cihaz ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cihaz.ad, fontWeight = FontWeight.SemiBold)
                            }
                            Button(onClick = { viewModel.baglan(cihaz.endpointId, cihaz.ad) }) {
                                Icon(Icons.Default.Sync, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Eşleş")
                            }
                        }
                    }
                }
            }

            val bagli = durum as? CihazEslestirme.BaglantiDurumu.Bagli
            if (bagli != null) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.baglantiyiKes() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("${bagli.endpointAdi} ile bağlantıyı kes") }
                }
            }
        }
    }

    // PIN dialogu (iki tarafta da aynı kod görünür)
    pinDurumu?.let { pin ->
        AlertDialog(
            onDismissRequest = { viewModel.pinReddet(pin.endpointId) },
            title = { Text("Eşleşme onayı") },
            text = {
                Column {
                    Text("${pin.endpointAdi} bağlanmak istiyor.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kod: ${pin.pin}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "İki cihazda da aynı kod görünmelidir. Aynıysa Onayla'ya basın.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.pinOnayla(pin.endpointId) }) { Text("Onayla") } },
            dismissButton = { TextButton(onClick = { viewModel.pinReddet(pin.endpointId) }) { Text("Reddet") } }
        )
    }
}

@Composable
private fun EslesmeBilgiKarti(baslik: String, detay: String, yukleniyor: Boolean = false, hata: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hata) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (yukleniyor) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(baslik, fontWeight = FontWeight.Bold)
                Text(detay, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
