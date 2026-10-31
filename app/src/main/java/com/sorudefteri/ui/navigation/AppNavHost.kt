package com.sorudefteri.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.domain.repository.TestSessionManager
import com.sorudefteri.ui.ayarlar.AyarlarScreen
import com.sorudefteri.ui.deneme.DenemeDetayScreen
import com.sorudefteri.ui.detay.SoruDetayScreen
import com.sorudefteri.ui.eslesme.CihazEslemeScreen
import com.sorudefteri.ui.istatistik.IstatistikScreen
import com.sorudefteri.ui.kamera.KameraScreen
import com.sorudefteri.ui.kutuphane.KutuphaneScreen
import com.sorudefteri.ui.omr.OmrScannerScreen
import com.sorudefteri.ui.omr.OmrSonucScreen
import com.sorudefteri.ui.onboarding.OnboardingScreen
import com.sorudefteri.ui.reels.SoruReelsScreen
import com.sorudefteri.ui.soruekle.SoruEklemeScreen
import com.sorudefteri.ui.test.TestCozScreen
import com.sorudefteri.ui.test.TestOlusturScreen
import com.sorudefteri.ui.test.TestSonucScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavHostEntryPoint {
    fun testSessionManager(): TestSessionManager
    fun soruRepository(): SoruRepository
}

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
    onDeepLinkHandled: () -> Unit = {},
    testSessionManager: TestSessionManager? = null,
    soruRepository: SoruRepository? = null
) {
    val context = LocalContext.current
    val actualSessionManager = androidx.compose.runtime.remember(context, testSessionManager) {
        testSessionManager ?: try {
            EntryPointAccessors.fromApplication(context.applicationContext, NavHostEntryPoint::class.java).testSessionManager()
        } catch (e: Exception) {
            null
        }
    }
    val actualSoruRepository = androidx.compose.runtime.remember(context, soruRepository) {
        soruRepository ?: try {
            EntryPointAccessors.fromApplication(context.applicationContext, NavHostEntryPoint::class.java).soruRepository()
        } catch (e: Exception) {
            null
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Dosyadan açılan .sdefteri paketinin içe aktarma onayı için bekleyen Uri'si
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }

    val isMainNavDestination = currentDestination?.let { dest ->
        dest.hasRoute<KutuphaneNav>() || dest.hasRoute<ReelsNav>() || dest.hasRoute<TestNav>() || dest.hasRoute<IstatistikNav>() || dest.hasRoute<AyarlarNav>()
    } ?: false

    val showNavigationRail = currentDestination?.let { dest ->
        !dest.hasRoute<KameraNav>() &&
            !dest.hasRoute<TestCozNav>() &&
            !dest.hasRoute<OnboardingNav>()
    } ?: false

    val isMediumOrExpanded = windowWidthSizeClass == WindowWidthSizeClass.Medium || windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val startDest: Any = if (initialOnboardingSeen) KutuphaneNav else OnboardingNav

    // DeepLink handling (notification -> test / test_due, dosya -> yedek içe aktarma)
    LaunchedEffect(deepLinkTarget, initialOnboardingSeen) {
        if (deepLinkTarget != null && initialOnboardingSeen) {
            if (deepLinkTarget.startsWith("soru::")) {
                // AI çözüm bildirimi -> ilgili soru detayı
                val id = deepLinkTarget.removePrefix("soru::").toLongOrNull()
                if (id != null && id > 0) {
                    navController.navigate(SoruDetayNav(soruId = id)) { launchSingleTop = true }
                }
            } else if (deepLinkTarget.startsWith("import::")) {
                // SD Defteri yedek dosyasıyla açılış: Ayarlar > Yedek sekmesine git,
                // onay dialogu için Uri'yi beklemeye al.
                pendingImportUri = deepLinkTarget.removePrefix("import::")
                navController.navigate(AyarlarNav) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } else when (deepLinkTarget) {
                "test_due", "sorudefteri://test/due" -> {
                    // Günün Kurtarma Paketi Deep Link: Due soruları yükle ve doğrudan Test Çöz ekranına geç
                    if (actualSoruRepository != null && actualSessionManager != null) {
                        val dueQuestions = actualSoruRepository.getDueReviewSorularList()
                        if (dueQuestions.isNotEmpty()) {
                            val testQuestions = dueQuestions.map { TestQuestion(soru = it) }
                            val filter = TestFiltre(
                                examType = null,
                                selectedSubjects = emptySet(),
                                selectedTopics = emptySet(),
                                selectedMistakeTypes = emptySet(),
                                onlyUnsolved = false,
                                questionCount = testQuestions.size
                            )
                            actualSessionManager.startSession(testQuestions, filter)
                            navController.navigate(TestCozNav) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                                restoreState = false
                            }
                        } else {
                            navController.navigate(TestNav) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    } else {
                        navController.navigate(TestNav) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                }
                "test_coz", "test" -> {
                    // Navigate to Test oluşturma (due reviews will be shown as filter hint)
                    navController.navigate(TestNav) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
                "reels" -> navController.navigate(ReelsNav) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                "kutuphane" -> navController.navigate(KutuphaneNav) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onDeepLinkHandled()
        }
    }

    if (isMediumOrExpanded) {
        // TABLET / FOLDABLE / WIDE SCREEN LAYOUT: Side NavigationRail
        Row(modifier = modifier.fillMaxSize()) {
            if (isMediumOrExpanded && showNavigationRail) {
                NavigationSideRail(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppNavGraph(
                    navController = navController,
                    startDestination = startDest,
                    windowWidthSizeClass = windowWidthSizeClass,
                    pendingImportUri = pendingImportUri,
                    onPendingImportHandled = { pendingImportUri = null }
                )
            }
        }
    } else {
        // PHONE / COMPACT SCREEN LAYOUT: Standard Scaffold with Bottom Navigation Bar
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isMainNavDestination) {
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
                pendingImportUri = pendingImportUri,
                onPendingImportHandled = { pendingImportUri = null },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
            )
        }
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any,
    windowWidthSizeClass: WindowWidthSizeClass,
    pendingImportUri: String? = null,
    onPendingImportHandled: () -> Unit = {},
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
                },
                onStartDueTest = {
                    navController.navigate(TestCozNav) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // 2.1. Soru Reels Ekranı (Dikey Feed, Hızlı Çözüm, Beğeni ve Hap Bilgi)
        composable<ReelsNav> {
            SoruReelsScreen(
                onNavigateToDetail = { soruId ->
                    navController.navigate(SoruDetayNav(soruId = soruId))
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
                editingSoruId = null,
                initialDenemeId = nav.denemeId,
                initialDenemeBaslik = nav.denemeBaslik,
                initialDenemeYayin = nav.denemeYayin,
                initialDenemeSoruNo = nav.denemeSoruNo,
                initialExamType = nav.initialExamType,
                initialSubjectId = nav.initialSubjectId,
                initialTopic = nav.initialTopic,
                initialCorrectAnswer = nav.initialCorrectAnswer,
                initialStudentAnswer = nav.initialStudentAnswer
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
                    navController.navigate(TestCozNav) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // 7. Test Çözme Ekranı
        composable<TestCozNav> {
            TestCozScreen(
                onFinishTest = {
                    navController.navigate(TestSonucNav) {
                        popUpTo<TestCozNav>() { inclusive = true }
                        launchSingleTop = true
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
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onNavigateToLibrary = {
                    navController.navigate(KutuphaneNav) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToStats = {
                    navController.navigate(IstatistikNav) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 9. İstatistik & Analitik Ekranı
        composable<IstatistikNav> {
            IstatistikScreen(
                onNavigateToAdd = {
                    navController.navigate(SoruEkleNav())
                },
                onStartTargetedTest = {
                    navController.navigate(TestCozNav) {
                        launchSingleTop = true
                    }
                },
                onStartDueTest = {
                    navController.navigate(TestCozNav) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDenemeDetay = { denemeId ->
                    navController.navigate(DenemeDetayNav(denemeId = denemeId))
                },
                onNavigateToOmrScan = {
                    navController.navigate(OmrScannerNav())
                },
                onNavigateToSoruEkleForDeneme = { dId, dBaslik, dYayin, sinavTuru ->
                    navController.navigate(
                        SoruEkleNav(
                            denemeId = dId,
                            denemeBaslik = dBaslik,
                            denemeYayin = dYayin,
                            initialExamType = sinavTuru
                        )
                    )
                }
            )
        }

        // 10. Ayarlar Ekranı
        composable<AyarlarNav> {
            AyarlarScreen(
                pendingImportUri = pendingImportUri,
                onPendingImportHandled = onPendingImportHandled,
                onNavigateToEslesme = { navController.navigate(CihazEslemeNav) }
            )
        }

        // 11. Yakın-Cihaz Eşleşme Ekranı
        composable<CihazEslemeNav> {
            CihazEslemeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 12. Deneme Sınavı Detay & Yanlış Soru Karnesi
        composable<DenemeDetayNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<DenemeDetayNav>()
            DenemeDetayScreen(
                denemeId = nav.denemeId,
                onNavigateBack = { navController.popBackStack() },
                onSoruClick = { soruId -> navController.navigate(SoruDetayNav(soruId)) },
                onAddQuestionToDeneme = { dId, dBaslik, dYayin, soruNo, sinavTuru ->
                    navController.navigate(
                        SoruEkleNav(
                            denemeId = dId,
                            denemeBaslik = dBaslik,
                            denemeYayin = dYayin,
                            denemeSoruNo = soruNo,
                            initialExamType = sinavTuru
                        )
                    )
                },
                onScanOpticalForm = { dId, testId ->
                    navController.navigate(OmrScannerNav(denemeId = dId, testId = testId))
                },
                onStartTestSession = {
                    navController.navigate(TestCozNav) { launchSingleTop = true }
                }
            )
        }

        // 13. Kamera Tabanlı Optik Okuyucu (OMR Scanner)
        composable<OmrScannerNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<OmrScannerNav>()
            OmrScannerScreen(
                initialDenemeId = nav.denemeId,
                initialTestId = nav.testId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSonuc = { dId -> navController.navigate(OmrSonucNav(dId)) }
            )
        }

        // 14. Optik Okuma Sonuç & Karne
        composable<OmrSonucNav> { backStackEntry ->
            val nav = backStackEntry.toRoute<OmrSonucNav>()
            OmrSonucScreen(
                denemeId = nav.denemeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDenemeDetay = { dId ->
                    navController.navigate(DenemeDetayNav(dId)) {
                        popUpTo<OmrScannerNav> { inclusive = true }
                    }
                },
                onAddWrongQuestion = { dId, dBaslik, dYayin, soruNo, sinavTuru, subId, cAns, sAns ->
                    navController.navigate(
                        SoruEkleNav(
                            denemeId = dId,
                            denemeBaslik = dBaslik,
                            denemeYayin = dYayin,
                            denemeSoruNo = soruNo,
                            initialExamType = sinavTuru,
                            initialSubjectId = subId,
                            initialCorrectAnswer = cAns,
                            initialStudentAnswer = sAns
                        )
                    )
                }
            )
        }
    }
}

/**
 * Yan Menü (NavigationRail) bileşeni - Tablet ve Geniş Ekranlar için.
 * Küçük düzeltme: üstte uygulama rozeti, altta sürüm ipucu.
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
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        text = "SD",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Soru Defterim",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterVertically)
        ) {
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
                        Text(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            if (item.route == TestNav) {
                                navController.navigate(TestNav) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Alt Menü (NavigationBar) bileşeni - Telefon ve Kompakt Ekranlar için.
 * TELEFON YENİ TASARIM: dikey kullanıma uygun, büyük dokunma hedefli,
 * seçili hap göstergeli, tek satırlık kısa etiketli 5'li bar.
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        BottomNavItem.items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.shortTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.Medium
                    )
                },
                selected = isSelected,
                alwaysShowLabel = true,
                onClick = {
                    if (!isSelected) {
                        if (item.route == TestNav) {
                            navController.navigate(TestNav) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        } else {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            )
        }
    }
}
