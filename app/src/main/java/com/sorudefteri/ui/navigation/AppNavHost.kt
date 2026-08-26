package com.sorudefteri.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.ui.ayarlar.AyarlarScreen
import com.sorudefteri.ui.detay.SoruDetayScreen
import com.sorudefteri.ui.istatistik.IstatistikScreen
import com.sorudefteri.ui.kamera.KameraScreen
import com.sorudefteri.ui.kutuphane.KutuphaneScreen
import com.sorudefteri.ui.soruekle.SoruEklemeScreen
import com.sorudefteri.ui.test.TestCozScreen
import com.sorudefteri.ui.test.TestOlusturScreen
import com.sorudefteri.ui.test.TestSonucScreen

/**
 * Root Navigation Host composable managing destinations, backstack, and dynamic bottom navigation bar.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isBottomBarVisible = currentDestination?.let { dest ->
        dest.hasRoute<KutuphaneNav>() || dest.hasRoute<TestNav>() || dest.hasRoute<IstatistikNav>() || dest.hasRoute<AyarlarNav>()
    } ?: false


    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
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
        NavHost(
            navController = navController,
            startDestination = KutuphaneNav,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
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

            composable<SoruEkleNav> { backStackEntry ->
                val nav = backStackEntry.toRoute<SoruEkleNav>()
                val capturedPath by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("captured_image_path", null)
                    .collectAsStateWithLifecycle()
                val isSolutionImage by backStackEntry.savedStateHandle
                    .getStateFlow<Boolean>("is_solution_image", false)
                    .collectAsStateWithLifecycle()

                val effectiveSoruPath = if (!isSolutionImage && capturedPath != null) capturedPath else nav.initialImageUri
                val effectiveSolutionPath = if (isSolutionImage && capturedPath != null) capturedPath else null

                SoruEklemeScreen(
                    initialSoruImagePath = effectiveSoruPath,
                    initialSolutionImagePath = effectiveSolutionPath,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onCameraLaunch = { isSol ->
                        navController.navigate(KameraNav(isSolution = isSol))
                    }
                )

                LaunchedEffect(capturedPath) {
                    if (capturedPath != null) {
                        backStackEntry.savedStateHandle.remove<String>("captured_image_path")
                        backStackEntry.savedStateHandle.remove<Boolean>("is_solution_image")
                    }
                }
            }

            composable<SoruDetayNav> {
                SoruDetayScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onCameraLaunch = { isSol ->
                        navController.navigate(KameraNav(isSolution = isSol))
                    },
                    onNavigateToAyarlar = {
                        navController.navigate(AyarlarNav)
                    }
                )
            }

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

            composable<TestNav> {
                TestOlusturScreen(
                    onStartTest = {
                        navController.navigate(TestCozNav)
                    }
                )
            }

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

            composable<IstatistikNav> {
                IstatistikScreen()
            }

            composable<AyarlarNav> {
                AyarlarScreen()
            }
        }
    }
}

/**
 * Bottom Navigation Bar component displaying items for main tabs.
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
