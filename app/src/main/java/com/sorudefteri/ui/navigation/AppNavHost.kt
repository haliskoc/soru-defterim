package com.sorudefteri.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sorudefteri.ui.ayarlar.AyarlarScreen
import com.sorudefteri.ui.detay.SoruDetayScreen
import com.sorudefteri.ui.istatistik.IstatistikScreen
import com.sorudefteri.ui.kamera.KameraScreen
import com.sorudefteri.ui.kutuphane.KutuphaneScreen
import com.sorudefteri.ui.onboarding.OnboardingScreen
import com.sorudefteri.ui.soruekle.SoruEklemeScreen
import com.sorudefteri.ui.test.TestCozScreen
import com.sorudefteri.ui.test.TestOlusturScreen
import com.sorudefteri.ui.test.TestSonucScreen

/**
 * Root Navigation Host composable managing destinations, backstack, onboarding routing,
 * and adaptive responsive navigation (Bottom Navigation Bar on phones, NavigationRail on tablets/foldables).
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    initialOnboardingSeen: Boolean = true,
    deepLinkTarget: String? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isMainNavDestination = currentDestination?.let { dest ->
        dest.hasRoute<KutuphaneNav>() || dest.hasRoute<TestNav>() || dest.hasRoute<IstatistikNav>() || dest.hasRoute<AyarlarNav>()
    } ?: false

    val isMediumOrExpanded = windowWidthSizeClass == WindowWidthSizeClass.Medium || windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val startDest: Any = if (initialOnboardingSeen) KutuphaneNav else OnboardingNav

    // DeepLink handling (notification -> test)
    LaunchedEffect(deepLinkTarget, initialOnboardingSeen) {
        if (deepLinkTarget != null && initialOnboardingSeen) {
            when (deepLinkTarget) {
                "test_coz", "test" -> {
                    // Navigate to Test oluşturma (due reviews will be shown as filter hint)
                    navController.navigate(TestNav) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                "kutuphane" -> navController.navigate(KutuphaneNav) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
            onDeepLinkHandled()
        }
    }

    if (isMediumOrExpanded) {
        // TABLET / FOLDABLE / WIDE SCREEN LAYOUT: Side NavigationRail
        Row(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isMainNavDestination,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                NavigationSideRail(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppNavGraph(
                    navController = navController,
                    startDestination = startDest,
                    windowWidthSizeClass = windowWidthSizeClass
                )
            }
        }
    } else {
        // PHONE / COMPACT SCREEN LAYOUT: Standard Scaffold with Bottom Navigation Bar
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                AnimatedVisibility(
                    visible = isMainNavDestination,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    BottomNavBar(
                        navController = navController,
                        currentDestination = currentDestination
                    )
                }
            }
        ) { innerPadding ->
            AppNavGraph(
                navController = navController,
                startDestination = startDest,
                windowWidthSizeClass = windowWidthSizeClass,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any,
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 1. Onboarding & Zorunlu Alan Seçimi Ekranı
        composable<OnboardingNav> {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(KutuphaneNav) {
                        popUpTo<OnboardingNav> { inclusive = true }
                    }
                }
            )
        }

        // 2. Kütüphane (Soru Arşivi) Ekranı
        composable<KutuphaneNav> { backStackEntry ->
            val capturedPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("captured_image_path", null)
                .collectAsStateWithLifecycle()

            LaunchedEffect(capturedPath) {
                capturedPath?.let { path ->
                    backStackEntry.savedStateHandle.remove<String>("captured_image_path")
                    navController.navigate(SoruEkleNav(initialImageUri = path))
                }
            }

            KutuphaneScreen(
                isExpandedScreen = windowWidthSizeClass == WindowWidthSizeClass.Expanded,
                onSoruClick = { id ->
                    navController.navigate(SoruDetayNav(soruId = id))
                },
                onAddClick = {
                    navController.navigate(SoruEkleNav())
                },
                onNavigateToSoruEkle = { uri ->
                    navController.navigate(SoruEkleNav(initialImageUri = uri))
                },
                onCameraClick = {
                    navController.navigate(KameraNav(isSolution = false))
                }
            )
        }

        // 3. Soru Ekleme Ekranı
        composable<SoruEkleNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<SoruEkleNav>()
            val capturedPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("captured_image_path", null)
                .collectAsStateWithLifecycle()

            val effectiveSoruPath = capturedPath ?: nav.initialImageUri

            SoruEklemeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(KameraNav(isSolution = false))
                },
                capturedImageUri = effectiveSoruPath,
                editingSoruId = null
            )

            LaunchedEffect(capturedPath) {
                if (capturedPath != null) {
                    backStackEntry.savedStateHandle.remove<String>("captured_image_path")
                }
            }
        }

        // 3.1. Soru Düzenleme Ekranı
        composable<SoruDuzenleNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<SoruDuzenleNav>()
            val capturedPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("captured_image_path", null)
                .collectAsStateWithLifecycle()

            SoruEklemeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(KameraNav(isSolution = false))
                },
                capturedImageUri = capturedPath,
                editingSoruId = nav.soruId
            )

            LaunchedEffect(capturedPath) {
                if (capturedPath != null) {
                    backStackEntry.savedStateHandle.remove<String>("captured_image_path")
                }
            }
        }

        // 4. Soru Detay Ekranı
        composable<SoruDetayNav> {
            SoruDetayScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { soruId ->
                    navController.navigate(SoruDuzenleNav(soruId = soruId))
                },
                onNavigateToSettings = {
                    navController.navigate(AyarlarNav)
                }
            )
        }

        // 5. Kamera Ekranı (CameraX)
        composable<KameraNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<KameraNav>()
            KameraScreen(
                isSolution = nav.isSolution,
                onPhotoCaptured = { path ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("captured_image_path", path)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("is_solution_image", nav.isSolution)
                    navController.popBackStack()
                },
                onClose = {
                    navController.popBackStack()
                }
            )
        }

        // 6. Test Oluşturma Ekranı
        composable<TestNav> {
            TestOlusturScreen(
                onStartTest = {
                    navController.navigate(TestCozNav)
                }
            )
        }

        // 7. Test Çözme Ekranı
        composable<TestCozNav> {
            TestCozScreen(
                onFinishTest = {
                    navController.navigate(TestSonucNav) {
                        popUpTo<TestNav>()
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 8. Test Sonuç Ekranı
        composable<TestSonucNav> {
            TestSonucScreen(
                onNewTest = {
                    navController.navigate(TestNav) {
                        popUpTo<TestNav> { inclusive = true }
                    }
                },
                onNavigateToLibrary = {
                    navController.navigate(KutuphaneNav) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 9. İstatistik & Analitik Ekranı
        composable<IstatistikNav> {
            IstatistikScreen()
        }

        // 10. Ayarlar Ekranı
        composable<AyarlarNav> {
            AyarlarScreen()
        }
    }
}

/**
 * Yan Menü (NavigationRail) bileşeni - Tablet ve Geniş Ekranlar için.
 */
@Composable
fun NavigationSideRail(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(modifier = Modifier.height(8.dp))
        }
    ) {
        Spacer(modifier = Modifier.weight(1f))
        BottomNavItem.items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
            NavigationRailItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Alt Menü (NavigationBar) bileşeni - Telefon ve Kompakt Ekranlar için.
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        BottomNavItem.items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
