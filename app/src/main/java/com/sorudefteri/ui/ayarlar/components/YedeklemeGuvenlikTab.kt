package com.sorudefteri.ui.ayarlar.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.Sync

@Composable
fun YedeklemeGuvenlikTab(
    isBackupLoading: Boolean,
    backupMessage: String?,
    isBackupError: Boolean,
    isAutoBackupEnabled: Boolean = false,
    isWifiOnly: Boolean = true,
    driveLastSyncTime: Long = 0L,
    isDriveSyncing: Boolean = false,
    driveSyncMessage: String? = null,
    isDriveSyncError: Boolean = false,
    onExportBackup: (Uri, String?) -> Unit,
    onImportBackup: (Uri, String?) -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit = {},
    onWifiOnlyToggle: (Boolean) -> Unit = {},
    // PERF: Hatırlatıcı bildirim anahtarları (koşulsuz 2 alarm yerine).
    isDailyReminderEnabled: Boolean = true,
    isStreakReminderEnabled: Boolean = true,
    onDailyReminderToggle: (Boolean) -> Unit = {},
    onStreakReminderToggle: (Boolean) -> Unit = {},
    onSyncDrive: () -> Unit = {},
    // Dosya yöneticisinden .sdefteri ile açılışta gelen bekleyen dosya
    pendingImportUri: String? = null,
    onPendingImportHandled: () -> Unit = {},
    onNavigateToEslesme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var showPendingImportDialog by rememberSaveable { mutableStateOf(false) }

    // Dosyadan açma: onay dialogunu göster (şifre + içe aktar / vazgeç)
    androidx.compose.runtime.LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) showPendingImportDialog = true
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let { onExportBackup(it, exportPassword.ifBlank { null }) }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { onImportBackup(it, importPassword.ifBlank { null }) }
        }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Şifreli .sdefteri Yedek Paketi Dışa/İçe Aktar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Şifreli Yedek (.sdefteri Paketi)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Tüm sorularınız, fotoğraflarınız ve test geçmişiniz AES-256-GCM + PBKDF2 standardında tek bir dosyada güvenle paketlenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // İsteğe bağlı Parola Alanı
                OutlinedTextField(
                    value = exportPassword,
                    onValueChange = { exportPassword = it },
                    label = { Text("Yedek Şifreleme Parolası (Opsiyonel)") },
                    placeholder = { Text("Boş bırakılırsa standart şifresiz paketlenir") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val filename = "soru_defterim_yedek_${System.currentTimeMillis()}.sdefteri"
                            exportLauncher.launch(filename)
                        },
                        enabled = !isBackupLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dışa Aktar")
                    }

                    OutlinedButton(
                        onClick = {
                            // ÖNEMLİ (.sdefteri seçici sorunu): DocumentsUI uzantıları
                            // anlayamaz; sadece MIME ile filtreler ve bilinmeyen uzantılı
                            // dosyaların provider raporlu tipi (octet-stream, x-trash vb.)
                            // cihaza göre değişir (bkz. tickmate #136, SO 64791028).
                            // Bu yüzden "Tüm dosyalar" (*/*) istenir; geçerli yedek olup
                            // olmadığı BackupManager.anlasVeriPaketi... içinde manifest
                            // kontrolüyle doğrulanır, geçersiz dosya uyarıyla reddedilir.
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !isBackupLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("İçe Aktar")
                    }
                }

                if (isBackupLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İşlem yapılıyor...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                backupMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBackupError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. Otomatik Yedekleme (Yerel) & Wi-Fi Otomasyonu
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, contentDescription = "Otomatik Yedekleme", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Otomatik Yedekleme (Yerel)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Otomatik Yedekleme Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Günlük Arka Plan Yedeklemesi", fontWeight = FontWeight.SemiBold)
                        Text(
                            "WorkManager ile her 24 saatte bir otomatik yedek alınır (Yerel klasöre).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoBackupEnabled,
                        onCheckedChange = onAutoBackupToggle
                    )
                }

                // Wi-Fi Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yalnızca Wi-Fi Bağlıyken", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Mobil veri kotanız korunur, sadece Wi-Fi ağındayken yedeklenir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isWifiOnly,
                        onCheckedChange = onWifiOnlyToggle
                    )
                }

                // Günlük Tekrar Hatırlatıcısı (19:00)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Günlük Tekrar Hatırlatıcısı (19:00)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Kapalıysa her gün süreç uyandırılmaz (pil).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDailyReminderEnabled,
                        onCheckedChange = onDailyReminderToggle
                    )
                }

                // Seri Koruma Hatırlatıcısı (21:00)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seri Koruma Hatırlatıcısı (21:00)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Kapalıysa her gün süreç uyandırılmaz (pil).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isStreakReminderEnabled,
                        onCheckedChange = onStreakReminderToggle
                    )
                }
            }
        }

        // 3. Sessiz Google Drive AppData Senkronizasyonu (Faz 5.5)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = "Google Drive", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Drive Bulut Yedeği",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Soru arşiviniz, çözümleriniz ve görselleriniz Google Drive hesabınızın gizli uygulama alanına (AppData) sessizce senkronize edilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Son Senkronizasyon Zaman Damgası
                val lastSyncText = if (driveLastSyncTime > 0L) {
                    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr"))
                    "Son Senkronizasyon: ${sdf.format(Date(driveLastSyncTime))}"
                } else {
                    "Henüz bulut senkronizasyonu yapılmadı."
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☁️ $lastSyncText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Şimdi Senkronize Et Butonu
                Button(
                    onClick = onSyncDrive,
                    enabled = !isDriveSyncing && !isBackupLoading,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDriveSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Senkronize Ediliyor...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Şimdi Senkronize Et")
                    }
                }

                driveSyncMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDriveSyncError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. Yakındaki Cihazla Eşle (telefon ↔ tablet, internetsiz)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = "Cihaz Eşleşme", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yakındaki Cihazla Eşle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Telefon ve tabletinizi yan yana getirin; olmayan sorular karşılıklı aktarılır. İnternet gerekmez, Bluetooth + Wi-Fi ile şifreli aktarım.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onNavigateToEslesme,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eşleşmeyi Başlat")
                }
            }
        }
    }

    // Dosyadan açılan .sdefteri için içe aktarma onay dialogu.
    // Doğruluk uzantıya göre değil, paketteki backup_manifest.json'a göre
    // BackupManager içinde yapılır; yanlış dosya seçilirse hata mesajı çıkar.
    if (showPendingImportDialog && pendingImportUri != null) {
        val pendingUri = Uri.parse(pendingImportUri)
        val fileName = pendingUri.lastPathSegment?.substringAfterLast('/') ?: "yedek.sdefteri"
        AlertDialog(
            onDismissRequest = {
                showPendingImportDialog = false
                onPendingImportHandled()
            },
            title = { Text("Yedeği İçe Aktar", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("“$fileName” dosyasındaki yedek bu cihaza aktarılacak. Mevcut verileriniz korunur, aynı sorular akıllıca birleştirilir.")
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Yedek Parolası (varsa)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPendingImportDialog = false
                        onImportBackup(pendingUri, importPassword.ifBlank { null })
                        onPendingImportHandled()
                    }
                ) {
                    Text("İçe Aktar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPendingImportDialog = false
                        onPendingImportHandled()
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
