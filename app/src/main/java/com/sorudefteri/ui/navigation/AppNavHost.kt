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
import com.sorudefteri.ui.ayarlar.AyarlarScreen
import com.sorudefteri.ui.detay.SoruDetayScreen
import com.sorudefteri.ui.istatistik.IstatistikScreen
import com.sorudefteri.ui.kamera.KameraScreen
import com.sorudefteri.ui.kutuphane.KutuphaneScreen
import com.sorudefteri.ui.soruekle.SoruEklemeScreen

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
        dest.hasRoute<KutuphaneNav>() || dest.hasRoute<IstatistikNav>() || dest.hasRoute<AyarlarNav>()
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
                .padding(innerPadding)
        ) {
            composable<KutuphaneNav> {
                KutuphaneScreen(
                    onSoruClick = { id ->
                        navController.navigate(SoruDetayNav(soruId = id))
                    },
                    onAddClick = {
                        navController.navigate(SoruEkleNav)
                    },
                    onCameraClick = {
                        navController.navigate(KameraNav(isSolution = false))
                    }
                )
            }

            composable<SoruEkleNav> {
                SoruEklemeScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onCameraLaunch = { isSol ->
                        navController.navigate(KameraNav(isSolution = isSol))
                    }
                )
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
                        navController.popBackStack()
                    },
                    onClose = {
                        navController.popBackStack()
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
