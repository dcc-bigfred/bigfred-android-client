package com.dccbigfred.android.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.BuildConfig
import com.dccbigfred.android.R
import com.dccbigfred.android.network.BigFredApiClient
import com.dccbigfred.android.ui.components.topAppBarEdgePadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val commitLabel = if (BuildConfig.GIT_DIRTY) {
        stringResource(R.string.about_commit_dirty, BuildConfig.GIT_COMMIT)
    } else {
        BuildConfig.GIT_COMMIT
    }

    val app = LocalContext.current.applicationContext as BigFredApplication
    var serverState by remember {
        mutableStateOf<ServerVersionUiState>(ServerVersionUiState.Loading)
    }
    LaunchedEffect(Unit) {
        serverState = when (val result = app.bigFredApiClient.getVersion()) {
            is BigFredApiClient.VersionResult.Success ->
                ServerVersionUiState.Ready(result.info)
            BigFredApiClient.VersionResult.NoServer ->
                ServerVersionUiState.Unavailable
            is BigFredApiClient.VersionResult.Failure ->
                ServerVersionUiState.Unavailable
        }
    }

    val loadingPlaceholder = stringResource(R.string.about_server_loading)
    val unavailablePlaceholder = stringResource(R.string.about_server_unavailable)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarEdgePadding(),
                title = { Text(stringResource(R.string.about_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            AboutRow(
                label = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME,
            )
            AboutRow(
                label = stringResource(R.string.about_version_code),
                value = BuildConfig.VERSION_CODE.toString(),
            )
            AboutRow(
                label = stringResource(R.string.about_commit),
                value = commitLabel,
                mono = true,
            )
            AboutRow(
                label = stringResource(R.string.about_commit_full),
                value = BuildConfig.GIT_COMMIT_FULL,
                mono = true,
            )
            AboutRow(
                label = stringResource(R.string.about_build_type),
                value = BuildConfig.BUILD_TYPE,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_server_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val serverVersion = when (val s = serverState) {
                ServerVersionUiState.Loading -> loadingPlaceholder
                ServerVersionUiState.Unavailable -> unavailablePlaceholder
                is ServerVersionUiState.Ready -> s.info.version.ifBlank { unavailablePlaceholder }
            }
            val tagCommit = when (val s = serverState) {
                ServerVersionUiState.Loading -> loadingPlaceholder
                ServerVersionUiState.Unavailable -> unavailablePlaceholder
                is ServerVersionUiState.Ready -> s.info.tagCommit.ifBlank { "—" }
            }
            val buildCommit = when (val s = serverState) {
                ServerVersionUiState.Loading -> loadingPlaceholder
                ServerVersionUiState.Unavailable -> unavailablePlaceholder
                is ServerVersionUiState.Ready -> s.info.buildCommit.ifBlank { "—" }
            }
            val buildTime = when (val s = serverState) {
                ServerVersionUiState.Loading -> loadingPlaceholder
                ServerVersionUiState.Unavailable -> unavailablePlaceholder
                is ServerVersionUiState.Ready -> s.info.buildTime.ifBlank { "—" }
            }

            AboutRow(
                label = stringResource(R.string.about_server_version),
                value = serverVersion,
            )
            AboutRow(
                label = stringResource(R.string.about_server_tag_commit),
                value = tagCommit,
                mono = true,
            )
            AboutRow(
                label = stringResource(R.string.about_server_build_commit),
                value = buildCommit,
                mono = true,
            )
            AboutRow(
                label = stringResource(R.string.about_server_build_time),
                value = buildTime,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.about_acknowledgements),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface ServerVersionUiState {
    data object Loading : ServerVersionUiState
    data object Unavailable : ServerVersionUiState
    data class Ready(val info: BigFredApiClient.ServerVersion) : ServerVersionUiState
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
