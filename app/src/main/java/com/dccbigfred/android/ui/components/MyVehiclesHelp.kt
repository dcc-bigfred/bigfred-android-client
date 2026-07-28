package com.dccbigfred.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dccbigfred.android.R
import kotlin.math.roundToInt

/** Default lift for the help FAB (~1.5 cm at 160 dpi). */
private val HelpFabDefaultLiftUp = 94.5.dp

@Composable
private fun DraggableHelpFab(
    @StringRes contentDescriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val defaultLiftPx = with(density) { HelpFabDefaultLiftUp.roundToPx().toFloat() }
    var offset by remember { mutableStateOf(Offset(0f, -defaultLiftPx)) }
    var didDrag by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = {
            if (!didDrag) onClick()
            didDrag = false
        },
        modifier = modifier
            .offset {
                IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                detectDragGestures(
                    onDragStart = { didDrag = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!didDrag && dragAmount.getDistance() > touchSlop) {
                            didDrag = true
                        }
                        if (didDrag) {
                            offset = Offset(
                                x = offset.x + dragAmount.x,
                                y = offset.y + dragAmount.y,
                            )
                        }
                    },
                )
            },
    ) {
        Icon(
            Icons.Default.Help,
            contentDescription = stringResource(contentDescriptionRes),
        )
    }
}

/** Bottom-right help FAB that opens the model catalog onboarding dialog. */
@Composable
fun ModelsCatalogHelpFab(modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    DraggableHelpFab(
        contentDescriptionRes = R.string.models_help,
        onClick = { showHelp = true },
        modifier = modifier,
    )
    if (showHelp) {
        ModelsCatalogHelpDialog(onDismiss = { showHelp = false })
    }
}

/** Bottom-right help FAB that opens the shared My vehicles onboarding dialog. */
@Composable
fun MyVehiclesHelpFab(modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    DraggableHelpFab(
        contentDescriptionRes = R.string.my_vehicles_help,
        onClick = { showHelp = true },
        modifier = modifier,
    )
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
fun ModelsCatalogHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.models_help_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HelpStep(
                    icon = Icons.Default.Search,
                    text = stringResource(R.string.models_help_step_find),
                )
                HelpStep(
                    icon = Icons.Default.Add,
                    text = stringResource(R.string.models_help_step_add),
                )
                HelpStep(
                    icon = Icons.Default.Edit,
                    text = stringResource(R.string.models_help_step_edit),
                )
                HelpStep(
                    icon = Icons.Default.Send,
                    text = stringResource(R.string.models_help_step_sync),
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
