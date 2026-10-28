package com.sorudefteri.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destination for Onboarding & Track Selection screen.
 */
@Serializable
object OnboardingNav

/**
 * Type-safe navigation destination for Kütüphane (Question Archive) screen.
 */
@Serializable
object KutuphaneNav

/**
 * Type-safe navigation destination for Soru Ekleme (Add Question) screen.
 * Supports optional initial image URI, isSolution flag, and trial exam (deneme) linkage.
 */
@Serializable
data class SoruEkleNav(
    val initialImageUri: String? = null,
    val isSolution: Boolean = false,
    val denemeId: Long? = null,
    val denemeBaslik: String? = null,
    val denemeYayin: String? = null,
    val denemeSoruNo: Int? = null,
    val initialExamType: String? = null,
    val initialSubjectId: String? = null,
    val initialTopic: String? = null,
    val initialCorrectAnswer: String? = null,
    val initialStudentAnswer: String? = null
)

/**
 * Type-safe navigation destination for Soru Detay (Question Detail) screen.
 */
@Serializable
data class SoruDetayNav(val soruId: Long)

/**
 * Type-safe navigation destination for Soru Düzenleme (Edit Question) screen.
 */
@Serializable
data class SoruDuzenleNav(val soruId: Long)

/**
 * Type-safe navigation destination for Kamera (CameraX Capture) screen.
 * @param isSolution Whether this camera capture is for a solution image (true) or a question image (false).
 */
@Serializable
data class KameraNav(val isSolution: Boolean = false)

/**
 * Type-safe navigation destination for Test Oluşturma screen.
 */
@Serializable
object TestNav

/**
 * Type-safe navigation destination for Test Çözme Ekranı.
 */
@Serializable
object TestCozNav

/**
 * Type-safe navigation destination for Test Sonuç Ekranı.
 */
@Serializable
object TestSonucNav

/**
 * Type-safe navigation destination for Soru Reels (Vertical Feed) screen.
 */
@Serializable
object ReelsNav

/**
 * Convenience helper namespace for Screen destinations (e.g. Screen.TestCoz.route)
 */
object Screen {
    object Onboarding {
        val route: Any = OnboardingNav
    }
    object Kutuphane {
        val route: Any = KutuphaneNav
    }
    object Kamera {
        val route: Any = KameraNav::class.qualifiedName ?: "KameraNav"
    }
    object TestCoz {
        val route: Any = TestCozNav
    }
    object Test {
        val route: Any = TestNav
    }
    object TestSonuc {
        val route: Any = TestSonucNav
    }
    object Reels {
        val route: Any = ReelsNav
    }
    object Istatistik {
        val route: Any = IstatistikNav
    }
    object Ayarlar {
        val route: Any = AyarlarNav
    }
}


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
 * Type-safe navigation destination for Yakın-Cihaz Eşleşme screen.
 */
@Serializable
object CihazEslemeNav

/**
 * Type-safe navigation destination for Deneme Detay (Trial Exam Detail & Wrong Questions) screen.
 */
@Serializable
data class DenemeDetayNav(val denemeId: Long)

/**
 * Type-safe navigation destination for Kamera Tabanlı Optik Okuyucu (OMR Scanner) screen.
 */
@Serializable
data class OmrScannerNav(
    val denemeId: Long? = null,
    val testId: String? = null
)

/**
 * Type-safe navigation destination for Optik Okuma Sonuç & Karne (OMR Result) screen.
 */
@Serializable
data class OmrSonucNav(val denemeId: Long)

/**
 * Bottom navigation bar item definition with type-safe route, localized title, and icons.
 */
sealed class BottomNavItem(
    val route: Any,
    val title: String,
    val shortTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Kutuphane : BottomNavItem(
        route = KutuphaneNav,
        title = "Kütüphane",
        shortTitle = "Kütüphane",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook
    )

    data object Reels : BottomNavItem(
        route = ReelsNav,
        title = "Reels",
        shortTitle = "Reels",
        selectedIcon = Icons.Filled.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle
    )

    data object Test : BottomNavItem(
        route = TestNav,
        title = "Test Çöz",
        shortTitle = "Test",
        selectedIcon = Icons.Filled.Quiz,
        unselectedIcon = Icons.Outlined.Quiz
    )

    data object Istatistik : BottomNavItem(
        route = IstatistikNav,
        title = "İstatistik",
        shortTitle = "Analiz",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )

    data object Ayarlar : BottomNavItem(
        route = AyarlarNav,
        title = "Ayarlar",
        shortTitle = "Ayarlar",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val items = listOf(Kutuphane, Reels, Test, Istatistik, Ayarlar)
    }
}
