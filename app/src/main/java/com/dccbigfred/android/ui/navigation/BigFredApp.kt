package com.dccbigfred.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.network.ServerProbe
import com.dccbigfred.android.ui.discovery.DiscoveryScreen
import com.dccbigfred.android.ui.settings.SettingsScreen
import com.dccbigfred.android.ui.webview.BigFredWebViewScreen
import com.dccbigfred.android.wifi.LowLatencyWifiLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun BigFredApp() {
    val context = LocalContext.current
    val app = context.applicationContext as BigFredApplication
    val prefs = app.serverPreferences
    val savedUrl by prefs.serverBaseUrl.collectAsStateWithLifecycle(initialValue = null)
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val probe = remember { ServerProbe() }
    val wifiLock = remember { LowLatencyWifiLock(context) }

    var bootstrapped by remember { mutableStateOf(false) }
    var activeUrl by remember { mutableStateOf<String?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        if (bootstrapped) return@LaunchedEffect
        val url = prefs.serverBaseUrl.first()
        if (url != null && probe.isReachable(url)) {
            activeUrl = url
            navController.navigate(Routes.WEBVIEW) {
                popUpTo(Routes.BOOTSTRAP) { inclusive = true }
            }
        } else {
            navController.navigate(Routes.DISCOVERY) {
                popUpTo(Routes.BOOTSTRAP) { inclusive = true }
            }
        }
        bootstrapped = true
    }

    fun goToWebView(url: String) {
        scope.launch {
            val normalized = ServerPreferences.normalizeBaseUrl(url)
            prefs.setServerBaseUrl(normalized)
            activeUrl = normalized
            navController.navigate(Routes.WEBVIEW) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == Routes.WEBVIEW || currentRoute == Routes.SETTINGS,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "BigFred",
                    modifier = Modifier.padding(16.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Ustawienia serwera") },
                    selected = currentRoute == Routes.SETTINGS,
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Wyszukaj serwer") },
                    selected = currentRoute == Routes.DISCOVERY,
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.DISCOVERY) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.BOOTSTRAP,
        ) {
            composable(Routes.BOOTSTRAP) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            composable(Routes.DISCOVERY) {
                DiscoveryScreen(onServerSelected = { url -> goToWebView(url) })
            }
            composable(Routes.WEBVIEW) {
                val url = activeUrl ?: savedUrl
                if (url == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.DISCOVERY) {
                            popUpTo(Routes.WEBVIEW) { inclusive = true }
                        }
                    }
                    return@composable
                }
                DisposableEffect(url) {
                    wifiLock.acquire()
                    onDispose { wifiLock.release() }
                }
                BigFredWebViewScreen(
                    baseUrl = url,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    currentUrl = activeUrl ?: savedUrl,
                    onBack = { navController.popBackStack() },
                    onSaved = { url -> goToWebView(url) },
                    onSearchAgain = {
                        navController.navigate(Routes.DISCOVERY) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}
