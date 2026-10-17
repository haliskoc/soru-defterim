package com.sorudefteri

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.domain.repository.TestSessionManager
import com.sorudefteri.ui.navigation.AppNavHost
import com.sorudefteri.ui.theme.SoruDefteriTheme
import com.sorudefteri.ui.widget.SoruWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity of Soru Defterim.
 * Sets up edge-to-edge display, Compose theme, window size class detection for responsive tablet layouts,
 * and passes the initial navigation state.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ayarlarDataStore: AyarlarDataStore

    @Inject
    lateinit var testSessionManager: TestSessionManager

    @Inject
    lateinit var soruRepository: SoruRepository

    private var deepLinkTarget by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkTarget = extractDeepLinkTarget(intent)
        enableEdgeToEdge()
        // Widget'ı (streak/tekrar/sınav geri sayımı) her açılışta tazele
        SoruWidgetUpdater.updateAllNowAsync(this)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by ayarlarDataStore.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val isOnboardingSeen by ayarlarDataStore.isOnboardingSeenFlow.collectAsStateWithLifecycle(initialValue = null)

            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            SoruDefteriTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isOnboardingSeen != null) {
                        AppNavHost(
                            windowWidthSizeClass = windowSizeClass.widthSizeClass,
                            initialOnboardingSeen = isOnboardingSeen == true,
                            deepLinkTarget = deepLinkTarget,
                            onDeepLinkHandled = { deepLinkTarget = null },
                            testSessionManager = testSessionManager,
                            soruRepository = soruRepository
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = extractDeepLinkTarget(intent)
        if (target != null) {
            deepLinkTarget = target
        }
    }

    private fun extractDeepLinkTarget(intent: Intent?): String? {
        if (intent == null) return null
        val navigateTo = intent.getStringExtra("navigate_to")
        if (!navigateTo.isNullOrBlank()) return navigateTo
        val deepLink = intent.getStringExtra("deepLink")
        if (!deepLink.isNullOrBlank()) return deepLink
        // Paylaş (ACTION_SEND / SEND_MULTIPLE) ile gelen .sdefteri / ZIP paketini
        // içe aktarma akışına yönlendir (Ayarlar > Yedek onay dialogu).
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val stream = getSendStreamUri(intent) ?: return null
            return "import::$stream"
        }
        val dataUri = intent.data
        if (dataUri != null) {
            if (dataUri.scheme == "sorudefteri" && dataUri.host == "test" && dataUri.path == "/due") {
                return "test_due"
            }
            // SD Defteri yedek paketi (.sdefteri) ile açılış: içe aktarma akışına yönlendir.
            // Uzantıya değil, açıldıktan sonra backup_manifest.json varlığına bakılarak
            // doğrulanır (BackupManager), yani yanlış dosya seçilirse kullanıcı uyarılır.
            val path = dataUri.path.orEmpty()
            val name = dataUri.lastPathSegment.orEmpty()
            if (path.endsWith(".sdefteri", ignoreCase = true) ||
                name.endsWith(".sdefteri", ignoreCase = true)
            ) {
                return "import::${dataUri}"
            }
            return dataUri.toString()
        }
        return null
    }

    /** ACTION_SEND / SEND_MULTIPLE payload'ından ilk Uri'yi cihaz sürümü uyumlu biçimde çıkarır. */
    private fun getSendStreamUri(intent: Intent): Uri? {
        val single = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (single != null) return single
        val list = IntentCompat.getParcelableArrayListExtra(
            intent,
            Intent.EXTRA_STREAM,
            Uri::class.java
        )
        return list?.firstOrNull()
    }
}
