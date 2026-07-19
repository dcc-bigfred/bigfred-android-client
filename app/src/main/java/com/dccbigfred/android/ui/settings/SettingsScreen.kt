package com.dccbigfred.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.network.ServerProbe
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUrl: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSearchAgain: () -> Unit,
) {
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val probe = remember { ServerProbe() }

    fun save() {
        val normalized = ServerPreferences.normalizeBaseUrl(urlInput)
        if (normalized.removePrefix("http://").removePrefix("https://").isBlank()) {
            error = "Podaj adres serwera"
            return
        }
        scope.launch {
            busy = true
            error = null
            val ok = probe.isReachable(normalized)
            busy = false
            if (ok) {
                onSaved(normalized)
            } else {
                error = "Serwer nie odpowiada na / — zapisano mimo to? Użyj „Zapisz bez sprawdzania” lub popraw adres."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia serwera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = "Adres BigFred (np. http://bigfred.local:8080 lub http://192.168.0.120:8080)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL serwera") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { save() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Sprawdź i zapisz")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val normalized = ServerPreferences.normalizeBaseUrl(urlInput)
                    onSaved(normalized)
                },
                enabled = !busy && urlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Zapisz bez sprawdzania")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSearchAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Wyszukaj serwer ponownie")
            }
        }
    }
}
