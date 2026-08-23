package com.sorudefteri.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destination for Kütüphane (Question Archive) screen.
 */
@Serializable
object KutuphaneNav

/**
 * Type-safe navigation destination for Soru Ekleme (Add Question) screen.
 */
@Serializable
object SoruEkleNav

/**
 * Type-safe navigation destination for Soru Detay (Question Detail) screen.
 */
@Serializable
data class SoruDetayNav(val soruId: Long)

/**
 * Type-safe navigation destination for Kamera (CameraX Capture) screen.
 * @param isSolution Whether this camera capture is for a solution image (true) or a question image (false).
 */
@Serializable
data class KameraNav(val isSolution: Boolean = false)

/**
 * Type-safe navigation destination for İstatistik (Statistics & Analytics) screen.
 */
@Serializable
object IstatistikNav

/**
 * Type-safe navigation destination for Ayarlar (Settings) screen.
 */
@Serializable
object AyarlarNav

/**
 * Bottom navigation bar item definition with type-safe route, localized title, and icons.
 */
sealed class BottomNavItem(
    val route: Any,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Kutuphane : BottomNavItem(
        route = KutuphaneNav,
        title = "Kütüphane",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook
    )

    data object Istatistik : BottomNavItem(
        route = IstatistikNav,
        title = "İstatistik",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )

    data object Ayarlar : BottomNavItem(
        route = AyarlarNav,
        title = "Ayarlar",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val items = listOf(Kutuphane, Istatistik, Ayarlar)
    }
}
