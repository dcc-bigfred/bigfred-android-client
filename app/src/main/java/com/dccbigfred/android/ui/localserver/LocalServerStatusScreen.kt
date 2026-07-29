package com.dccbigfred.android.ui.localserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dccbigfred.android.R
import com.dccbigfred.android.server.LocalServerPaths
import com.dccbigfred.android.server.LocalServerState
import com.dccbigfred.android.server.LocoServerService
import com.dccbigfred.android.ui.components.topAppBarEdgePadding
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalServerStatusScreen(
    onOpenApp: () -> Unit,
    onStopped: () -> Unit,
) {
    val context = LocalContext.current
    val state by LocoServerService.state.collectAsStateWithLifecycle()
    var logTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        if (state is LocalServerState.Starting) {
            while (LocoServerService.state.value is LocalServerState.Starting) {
                delay(1_000)
                logTick++
            }
        }
        logTick++
    }
    val logs = remember(state, logTick) {
        val dir = LocalServerPaths.from(context).logsDir
        listOf("loco-server.log", "valkey.log", "supervisord.log")
            .map { File(dir, it) }
            .filter { it.isFile }
            .joinToString("\n\n") { f ->
                "--- ${f.name} ---\n" + f.readLines().takeLast(40).joinToString("\n")
            }
            .ifBlank { "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarEdgePadding(),
                title = { Text(stringResource(R.string.local_server_status_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state is LocalServerState.Starting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.discovery_on_phone_starting),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                Text(
                    text = when (val s = state) {
                        LocalServerState.Stopped -> stringResource(R.string.local_server_status_stopped)
                        LocalServerState.Starting -> stringResource(R.string.local_server_status_starting)
                        is LocalServerState.Running ->
                            stringResource(R.string.local_server_status_running, s.baseUrl)
                        is LocalServerState.Failed ->
                            stringResource(R.string.local_server_status_failed, s.message)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state is LocalServerState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            if (state is LocalServerState.Running) {
                Button(
                    onClick = onOpenApp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.local_server_open_app))
                }
            }

            OutlinedButton(
                onClick = {
                    LocoServerService.stop(context)
                    LocoServerService.start(context)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is LocalServerState.Starting,
            ) {
                Text(stringResource(R.string.local_server_restart))
            }

            Button(
                onClick = {
                    LocoServerService.stop(context)
                    onStopped()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.local_server_stop))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.local_server_logs),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = logs.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
