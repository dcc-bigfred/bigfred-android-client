package com.dccbigfred.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.R
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.network.ServerProbe
import com.dccbigfred.android.ui.connection.ConnectionStatusScreen
import com.dccbigfred.android.ui.discovery.DiscoveryScreen
import com.dccbigfred.android.ui.models.ModelsCatalogScreen
import com.dccbigfred.android.ui.settings.SettingsScreen
import com.dccbigfred.android.ui.webview.BigFredWebViewScreen
import com.dccbigfred.android.wifi.LowLatencyWifiLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Only this strip at the physical left edge can start an open gesture. */
private val DrawerOpenEdgeWidth = 28.dp
private const val DrawerOpenDragThresholdPx = 40f

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
    val selectedServerUrl = activeUrl ?: savedUrl

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

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // When closed: no full-screen open gesture (middle of screen stays free).
        // When open: swipe / scrim tap can close the drawer.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_settings)) },
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
                    label = { Text(stringResource(R.string.menu_models)) },
                    selected = currentRoute == Routes.MODELS,
                    icon = { Icon(Icons.Default.DirectionsRailway, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.MODELS) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
                if (selectedServerUrl != null) {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.menu_connection_status)) },
                        selected = currentRoute == Routes.CONNECTION,
                        icon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(Routes.CONNECTION) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_find_server)) },
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
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.BOOTSTRAP,
            ) {
                composable(Routes.BOOTSTRAP) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
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
                    // Hold the WiFi lock for the whole WebView visit (not keyed on
                    // url) so a DataStore refresh of the same address cannot
                    // briefly release/reacquire and stall the keepalive socket.
                    DisposableEffect(Unit) {
                        wifiLock.acquire()
                        onDispose { wifiLock.release() }
                    }
                    BigFredWebViewScreen(baseUrl = url)
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
                composable(Routes.CONNECTION) {
                    val url = selectedServerUrl
                    if (url == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.DISCOVERY) {
                                popUpTo(Routes.CONNECTION) { inclusive = true }
                            }
                        }
                        return@composable
                    }
                    var wifiLockHeld by remember(url) { mutableStateOf(false) }
                    DisposableEffect(url) {
                        wifiLock.acquire()
                        wifiLockHeld = wifiLock.isHeld
                        onDispose {
                            wifiLock.release()
                            wifiLockHeld = false
                        }
                    }
                    ConnectionStatusScreen(
                        serverUrl = url,
                        wifiLockHeld = wifiLockHeld,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.MODELS) {
                    ModelsCatalogScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            if (drawerState.isClosed) {
                LeftEdgeOpenHandle(
                    onOpen = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(DrawerOpenEdgeWidth),
                )
            }
        }
    }
}

@Composable
private fun LeftEdgeOpenHandle(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    // Dragging right from the left edge opens the drawer.
                    if (dragAmount > 0f) {
                        dragged += dragAmount
                    }
                },
                onDragEnd = {
                    if (dragged >= DrawerOpenDragThresholdPx) {
                        onOpen()
                    }
                    dragged = 0f
                },
                onDragCancel = { dragged = 0f },
            )
        },
    )
}
