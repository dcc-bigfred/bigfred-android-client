package com.dccbigfred.android.ui.navigation

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.MainActivity
import com.dccbigfred.android.R
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.locale.LocalePrefs
import com.dccbigfred.android.network.ServerProbe
import com.dccbigfred.android.ui.about.AboutScreen
import com.dccbigfred.android.ui.connection.ConnectionStatusScreen
import com.dccbigfred.android.ui.discovery.DiscoveryScreen
import com.dccbigfred.android.ui.models.ModelsCatalogScreen
import com.dccbigfred.android.ui.myvehicles.MyVehiclesScreen
import com.dccbigfred.android.ui.myvehicles.MyVehiclesViewModel
import com.dccbigfred.android.ui.settings.SettingsScreen
import com.dccbigfred.android.ui.webview.BigFredWebViewScreen
import com.dccbigfred.android.ui.webview.applyLocaleToWebView
import com.dccbigfred.android.ui.webview.deliverThrottleHardwareKeys
import com.dccbigfred.android.ui.webview.handleVolumeKeyEvent
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
    val volumeKeysThrottleEnabled by prefs.volumeKeysThrottleEnabled
        .collectAsStateWithLifecycle(initialValue = ServerPreferences.DEFAULT_VOLUME_KEYS_THROTTLE_ENABLED)
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val probe = remember { ServerProbe() }
    val wifiLock = remember { LowLatencyWifiLock(context) }

    var bootstrapped by remember { mutableStateOf(false) }
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var spaWebView by remember { mutableStateOf<WebView?>(null) }
    var throttleHardwareKeysActive by remember { mutableStateOf(false) }
    val activity = context as? MainActivity

    DisposableEffect(spaWebView, volumeKeysThrottleEnabled, throttleHardwareKeysActive, activity) {
        if (activity == null ||
            spaWebView == null ||
            !volumeKeysThrottleEnabled ||
            !throttleHardwareKeysActive
        ) {
            activity?.volumeKeyInterceptor = null
            return@DisposableEffect onDispose {
                activity?.volumeKeyInterceptor = null
            }
        }
        activity.volumeKeyInterceptor = { event ->
            handleVolumeKeyEvent(event) { direction ->
                deliverThrottleHardwareKeys(spaWebView, direction)
            }
        }
        onDispose {
            activity.volumeKeyInterceptor = null
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedServerUrl = activeUrl ?: savedUrl

    fun pushLocaleToSpa() {
        applyLocaleToWebView(spaWebView, LocalePrefs.resolvedWebLocale())
    }
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

    fun openBigFredApp() {
        scope.launch {
            drawerState.close()
            if (selectedServerUrl != null) {
                navController.navigate(Routes.WEBVIEW) {
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Routes.DISCOVERY) {
                    launchSingleTop = true
                }
            }
        }
    }

    val webViewVisible = currentRoute == Routes.WEBVIEW
    val webSessionUrl = selectedServerUrl

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
                    label = { Text(stringResource(R.string.menu_app)) },
                    selected = currentRoute == Routes.WEBVIEW,
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    onClick = { openBigFredApp() },
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
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_my_vehicles)) },
                    selected = currentRoute == Routes.MY_VEHICLES,
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.MY_VEHICLES) {
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
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_about)) },
                    selected = currentRoute == Routes.ABOUT,
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.ABOUT) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep the WebView alive while browsing other drawer screens so SPA
            // route, history and the WS keepalive are preserved on return.
            if (webSessionUrl != null) {
                key(webSessionUrl) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (webViewVisible) 1f else 0f),
                    ) {
                        DisposableEffect(Unit) {
                            wifiLock.acquire()
                            onDispose { wifiLock.release() }
                        }
                        BigFredWebViewScreen(
                            baseUrl = webSessionUrl,
                            onWebViewReady = { spaWebView = it },
                            onThrottleHardwareKeysActive = { active ->
                                throttleHardwareKeysActive = active
                            },
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = Routes.BOOTSTRAP,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (webViewVisible) 0f else 1f),
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
                    if (webSessionUrl == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.DISCOVERY) {
                                popUpTo(Routes.WEBVIEW) { inclusive = true }
                            }
                        }
                    }
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
                        onLocaleChanged = { pushLocaleToSpa() },
                        volumeKeysThrottleEnabled = volumeKeysThrottleEnabled,
                        onVolumeKeysThrottleEnabledChange = { enabled ->
                            scope.launch { prefs.setVolumeKeysThrottleEnabled(enabled) }
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
                        onAddToMyVehicles = { row ->
                            scope.launch {
                                val repo = app.localVehicleRepository
                                repo.upsert(
                                    MyVehiclesViewModel.fromModelRow(row, repo.newUuid()),
                                )
                            }
                        },
                    )
                }
                composable(Routes.MY_VEHICLES) {
                    MyVehiclesScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen(
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
                        .width(DrawerOpenEdgeWidth)
                        .zIndex(2f),
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
