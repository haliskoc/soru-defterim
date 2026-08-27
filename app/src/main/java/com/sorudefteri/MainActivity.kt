package com.sorudefteri

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.ui.navigation.AppNavHost
import com.sorudefteri.ui.theme.SoruDefteriTheme
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

    private var deepLinkTarget by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkTarget = intent?.getStringExtra("navigate_to")
        enableEdgeToEdge()
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
                            onDeepLinkHandled = { deepLinkTarget = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra("navigate_to")
        if (target != null) {
            deepLinkTarget = target
        }
    }
}
