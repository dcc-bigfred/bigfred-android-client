package com.dccbigfred.android.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dccbigfred.android.discovery.DiscoveredServer
import com.dccbigfred.android.discovery.DiscoverySource
import com.dccbigfred.android.discovery.ServerDiscovery
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onServerSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val discovery = remember { ServerDiscovery(context) }
    val servers by discovery.servers.collectAsStateWithLifecycle()
    val scanning by discovery.scanning.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("8080") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualBusy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    fun submitManual() {
        val host = hostInput.trim()
        if (host.isEmpty()) {
            manualError = "Podaj host lub adres IP"
            return
        }
        val port = portInput.toIntOrNull() ?: 8080
        scope.launch {
            manualBusy = true
            manualError = null
            val found = discovery.probeManual(host, port)
            manualBusy = false
            if (found != null) {
                onServerSelected(found.baseUrl)
            } else {
                manualError = "Serwer nie odpowiada na /"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wybierz serwer BigFred") },
                actions = {
                    IconButton(
                        onClick = {
                            discovery.stop()
                            discovery.start()
                        },
                        enabled = !scanning,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Szukanie: bigfred.local:8080 (mDNS), potem {podsieć}.120:8080, albo wpisz ręcznie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Skanowanie sieci…")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (servers.isEmpty() && !scanning) {
                    item {
                        Text(
                            text = "Nie znaleziono serwera. Sprawdź Wi‑Fi lub wpisz adres ręcznie.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(servers, key = { it.baseUrl }) { server ->
                    ServerRow(server = server, onClick = { onServerSelected(server.baseUrl) })
                    HorizontalDivider()
                }
            }

            Text("Ręcznie", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hostInput,
                onValueChange = {
                    hostInput = it
                    manualError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Host / IP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it.filter { c -> c.isDigit() }.take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submitManual() }),
            )
            if (manualError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(manualError!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { submitManual() },
                enabled = !manualBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (manualBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Sprawdź i połącz")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServerRow(server: DiscoveredServer, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(server.label) },
        supportingContent = {
            Text("${server.baseUrl} · ${sourceLabel(server.source)}")
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun sourceLabel(source: DiscoverySource): String = when (source) {
    DiscoverySource.MDNS -> "mDNS"
    DiscoverySource.SUBNET -> "podsieć .120"
    DiscoverySource.MANUAL -> "ręczny"
}
