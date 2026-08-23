package com.sorudefteri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.ui.navigation.AppNavHost
import com.sorudefteri.ui.theme.SoruDefteriTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity of Soru Defterim.
 * Sets up edge-to-edge display, Compose theme, and root navigation host.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var ayarlarDataStore: AyarlarDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by ayarlarDataStore.themeMode.collectAsStateWithLifecycle(initialValue = 0)
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
                    AppNavHost()
                }
            }
        }
    }
}
