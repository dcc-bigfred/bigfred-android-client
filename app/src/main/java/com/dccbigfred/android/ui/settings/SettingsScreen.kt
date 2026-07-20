package com.dccbigfred.android.ui.settings

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.dccbigfred.android.R
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.network.ServerProbe
import kotlinx.coroutines.launch

private enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    POLISH("pl"),
    ENGLISH("en"),
    GERMAN("de"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUrl: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSearchAgain: () -> Unit,
    onLocaleChanged: (() -> Unit)? = null,
) {
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val probe = remember { ServerProbe() }

    val errorUrlRequired = stringResource(R.string.settings_error_url_required)
    val errorUnreachable = stringResource(R.string.settings_error_unreachable)

    var selectedLanguage by remember {
        mutableStateOf(currentAppLanguage())
    }

    fun save() {
        val normalized = ServerPreferences.normalizeBaseUrl(urlInput)
        if (normalized.removePrefix("http://").removePrefix("https://").isBlank()) {
            error = errorUrlRequired
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
                error = errorUnreachable
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_language_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption(
                    label = stringResource(R.string.settings_language_system),
                    selected = selectedLanguage == AppLanguage.SYSTEM,
                    onSelect = {
                        selectedLanguage = AppLanguage.SYSTEM
                        applyAppLanguage(AppLanguage.SYSTEM)
                        onLocaleChanged?.invoke()
                    },
                )
                LanguageOption(
                    label = stringResource(R.string.settings_language_polish),
                    selected = selectedLanguage == AppLanguage.POLISH,
                    onSelect = {
                        selectedLanguage = AppLanguage.POLISH
                        applyAppLanguage(AppLanguage.POLISH)
                        onLocaleChanged?.invoke()
                    },
                )
                LanguageOption(
                    label = stringResource(R.string.settings_language_english),
                    selected = selectedLanguage == AppLanguage.ENGLISH,
                    onSelect = {
                        selectedLanguage = AppLanguage.ENGLISH
                        applyAppLanguage(AppLanguage.ENGLISH)
                        onLocaleChanged?.invoke()
                    },
                )
                LanguageOption(
                    label = stringResource(R.string.settings_language_german),
                    selected = selectedLanguage == AppLanguage.GERMAN,
                    onSelect = {
                        selectedLanguage = AppLanguage.GERMAN
                        applyAppLanguage(AppLanguage.GERMAN)
                        onLocaleChanged?.invoke()
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_server_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_server_hint),
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
                label = { Text(stringResource(R.string.settings_server_url_label)) },
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
                Text(stringResource(R.string.settings_check_and_save))
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
                Text(stringResource(R.string.settings_save_without_check))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSearchAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_search_again))
            }
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun currentAppLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppLanguage.SYSTEM
    return when (locales[0]?.language) {
        "pl" -> AppLanguage.POLISH
        "en" -> AppLanguage.ENGLISH
        "de" -> AppLanguage.GERMAN
        else -> AppLanguage.SYSTEM
    }
}

private fun applyAppLanguage(language: AppLanguage) {
    val locales = if (language.tag == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(language.tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
