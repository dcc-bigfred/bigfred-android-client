package com.dccbigfred.android.ui.myvehicles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dccbigfred.android.R
import com.dccbigfred.android.data.localvehicles.LocalVehicleEntity
import com.dccbigfred.android.models.CatalogIcon

private val RowHeight = 56.dp
private val ThumbSize = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVehiclesScreen(
    onBack: () -> Unit,
    viewModel: MyVehiclesViewModel = viewModel(),
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<LocalVehicleEntity?>(null) }
    var menuForUuid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { resId ->
            snackbar.showSnackbar(context.getString(resId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_vehicles_title)) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = viewModel.newEntity() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.my_vehicles_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.my_vehicles_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            MyVehiclesTable(
                rows = vehicles,
                menuForUuid = menuForUuid,
                onMenuChange = { menuForUuid = it },
                onEdit = { editing = it },
                onDelete = { viewModel.delete(it) },
                onSync = { viewModel.sync(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    editing?.let { entity ->
        VehicleEditDialog(
            initial = entity,
            icons = viewModel.catalogIcons,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }
}

@Composable
private fun MyVehiclesTable(
    rows: List<LocalVehicleEntity>,
    menuForUuid: String?,
    onMenuChange: (String?) -> Unit,
    onEdit: (LocalVehicleEntity) -> Unit,
    onDelete: (LocalVehicleEntity) -> Unit,
    onSync: (LocalVehicleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            HeaderCell(stringResource(R.string.my_vehicles_col_icon), 64.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_name), 140.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_kind), 120.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_number), 140.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_carrier), 120.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_assignment), 120.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_revision), 100.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_epoch), 80.dp)
            HeaderCell(stringResource(R.string.my_vehicles_col_dcc), 80.dp)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEach { row ->
                    Box {
                        VehicleTableRow(
                            row = row,
                            onLongClick = { onMenuChange(row.uuid) },
                        )
                        DropdownMenu(
                            expanded = menuForUuid == row.uuid,
                            onDismissRequest = { onMenuChange(null) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.my_vehicles_action_edit)) },
                                onClick = {
                                    onEdit(row)
                                    onMenuChange(null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.my_vehicles_action_delete)) },
                                onClick = {
                                    onDelete(row)
                                    onMenuChange(null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.my_vehicles_action_sync)) },
                                onClick = {
                                    onSync(row)
                                    onMenuChange(null)
                                },
                            )
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.my_vehicles_empty),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleTableRow(
    row: LocalVehicleEntity,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .height(RowHeight)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(64.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (row.iconPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/${row.iconPath}")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(ThumbSize),
                )
            } else {
                Icon(
                    Icons.Default.DirectionsRailway,
                    contentDescription = null,
                    modifier = Modifier.size(ThumbSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BodyCell(row.name, 140.dp)
        BodyCell(row.kind, 120.dp)
        BodyCell(row.number, 140.dp)
        BodyCell(row.carrier, 120.dp)
        BodyCell(row.assignment, 120.dp)
        BodyCell(row.revisionDate.orEmpty(), 100.dp)
        BodyCell(row.epoch, 80.dp)
        BodyCell(row.dccAddress?.toString().orEmpty(), 80.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@Composable
private fun BodyCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleEditDialog(
    initial: LocalVehicleEntity,
    icons: List<CatalogIcon>,
    onDismiss: () -> Unit,
    onSave: (LocalVehicleEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var number by remember { mutableStateOf(initial.number) }
    var carrier by remember { mutableStateOf(initial.carrier) }
    var assignment by remember { mutableStateOf(initial.assignment) }
    var revisionDate by remember { mutableStateOf(initial.revisionDate.orEmpty()) }
    var epoch by remember { mutableStateOf(initial.epoch) }
    var dccText by remember { mutableStateOf(initial.dccAddress?.toString().orEmpty()) }
    var iconPath by remember { mutableStateOf(initial.iconPath) }
    var pickIcon by remember { mutableStateOf(false) }
    var kindExpanded by remember { mutableStateOf(false) }
    var epochExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial.name.isEmpty() && initial.number.isEmpty()) {
                        R.string.my_vehicles_dialog_add
                    } else {
                        R.string.my_vehicles_dialog_edit
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.my_vehicles_col_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = kindExpanded,
                    onExpandedChange = { kindExpanded = it },
                ) {
                    OutlinedTextField(
                        value = kind,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.my_vehicles_col_kind)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = kindExpanded,
                        onDismissRequest = { kindExpanded = false },
                    ) {
                        VEHICLE_KINDS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = {
                                    kind = k
                                    kindExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.my_vehicles_col_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text(stringResource(R.string.my_vehicles_col_carrier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = assignment,
                    onValueChange = { assignment = it },
                    label = { Text(stringResource(R.string.my_vehicles_col_assignment)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = revisionDate,
                    onValueChange = { revisionDate = it },
                    label = { Text(stringResource(R.string.my_vehicles_col_revision)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = epochExpanded,
                    onExpandedChange = { epochExpanded = it },
                ) {
                    OutlinedTextField(
                        value = epoch.ifEmpty { "—" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.my_vehicles_col_epoch)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(epochExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = epochExpanded,
                        onDismissRequest = { epochExpanded = false },
                    ) {
                        VEHICLE_EPOCHS.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(e.ifEmpty { "—" }) },
                                onClick = {
                                    epoch = e
                                    epochExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dccText,
                    onValueChange = { dccText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.my_vehicles_col_dcc)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconPreview(iconPath)
                    TextButton(onClick = { pickIcon = true }) {
                        Text(stringResource(R.string.my_vehicles_pick_icon))
                    }
                    if (iconPath != null) {
                        TextButton(onClick = { iconPath = null }) {
                            Text(stringResource(R.string.my_vehicles_clear_icon))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            kind = kind,
                            number = number.trim(),
                            carrier = carrier.trim(),
                            assignment = assignment.trim(),
                            revisionDate = revisionDate.trim().ifEmpty { null },
                            epoch = epoch,
                            dccAddress = dccText.toIntOrNull(),
                            iconPath = iconPath,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.my_vehicles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.my_vehicles_cancel))
            }
        },
    )

    if (pickIcon) {
        IconPickerDialog(
            icons = icons,
            onDismiss = { pickIcon = false },
            onPick = {
                iconPath = it.imagePath
                pickIcon = false
            },
        )
    }
}

@Composable
private fun IconPreview(iconPath: String?) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(ThumbSize),
        contentAlignment = Alignment.Center,
    ) {
        if (iconPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/$iconPath")
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(ThumbSize),
            )
        } else {
            Icon(Icons.Default.DirectionsRailway, contentDescription = null)
        }
    }
}

@Composable
private fun IconPickerDialog(
    icons: List<CatalogIcon>,
    onDismiss: () -> Unit,
    onPick: (CatalogIcon) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.my_vehicles_pick_icon)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(icons, key = { it.imagePath }) { icon ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onPick(icon) }
                            .padding(4.dp),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/${icon.imagePath}")
                                .build(),
                            contentDescription = icon.vehicleNumber,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            text = icon.vehicleNumber?.takeIf { it.isNotBlank() } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.my_vehicles_cancel))
            }
        },
    )
}
