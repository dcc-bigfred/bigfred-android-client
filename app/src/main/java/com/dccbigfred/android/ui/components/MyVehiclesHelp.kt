package com.dccbigfred.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dccbigfred.android.R

/** Bottom-right help FAB that opens the shared My vehicles onboarding dialog. */
@Composable
fun MyVehiclesHelpFab(modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    FloatingActionButton(
        onClick = { showHelp = true },
        modifier = modifier,
    ) {
        Icon(
            Icons.Default.Help,
            contentDescription = stringResource(R.string.my_vehicles_help),
        )
    }
    if (showHelp) {
        MyVehiclesHelpDialog(onDismiss = { showHelp = false })
    }
}

@Composable
fun MyVehiclesHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.my_vehicles_help_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HelpStep(
                    icon = Icons.Default.Add,
                    text = stringResource(R.string.my_vehicles_help_step_prepare),
                )
                HelpStep(
                    icon = Icons.Default.Edit,
                    text = stringResource(R.string.my_vehicles_help_step_edit),
                )
                HelpStep(
                    icon = Icons.AutoMirrored.Filled.Login,
                    text = stringResource(R.string.my_vehicles_help_step_login),
                )
                HelpStep(
                    icon = Icons.Default.Send,
                    text = stringResource(R.string.my_vehicles_help_step_send),
                )
                HelpStep(
                    icon = Icons.Default.AddCircle,
                    text = stringResource(R.string.my_vehicles_help_step_layout),
                )
                HelpStep(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.my_vehicles_help_step_done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.my_vehicles_dismiss))
            }
        },
    )
}

@Composable
private fun HelpStep(
    icon: ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
