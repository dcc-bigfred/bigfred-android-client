package com.dccbigfred.android.ui.myvehicles

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
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
import com.dccbigfred.android.ui.components.topAppBarEdgePadding

private val RowHeight = 56.dp
private val ThumbSize = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVehiclesScreen(
    onBack: () -> Unit,
    viewModel: MyVehiclesViewModel = viewModel(),
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val rowFlash by viewModel.rowFlash.collectAsStateWithLifecycle()
    val banners by viewModel.banners.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<LocalVehicleEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<LocalVehicleEntity?>(null) }
    var menuForUuid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { resId ->
            snackbar.showSnackbar(context.getString(resId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarEdgePadding(),
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
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { editing = viewModel.newEntity() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.my_vehicles_add),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(
                    onClick = viewModel::syncSelected,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.my_vehicles_send_selected, selected.size),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
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
            if (banners.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    banners.forEach { banner ->
                        ErrorBanner(
                            banner = banner,
                            onDismiss = { viewModel.dismissBanner(banner.id) },
                        )
                    }
                }
            }
            MyVehiclesTable(
                rows = vehicles,
                selected = selected,
                rowFlash = rowFlash,
                sortColumn = sort?.first,
                sortAsc = sort?.second ?: true,
                onSort = viewModel::toggleSort,
                menuForUuid = menuForUuid,
                onMenuChange = { menuForUuid = it },
                onToggleSelected = viewModel::toggleSelected,
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
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

    pendingDelete?.let { entity ->
        val label = entity.name.ifBlank { entity.number }.ifBlank { entity.uuid }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.my_vehicles_delete_title)) },
            text = { Text(stringResource(R.string.my_vehicles_delete_message, label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entity)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.my_vehicles_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.my_vehicles_cancel))
                }
            },
        )
    }
}

@Composable
private fun ErrorBanner(
    banner: BannerError,
    onDismiss: () -> Unit,
) {
    val reason = if (banner.reasonArg != null) {
        stringResource(banner.reasonResId, banner.reasonArg)
    } else {
        stringResource(banner.reasonResId)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.my_vehicles_banner_error, banner.vehicleName, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.my_vehicles_dismiss),
                )
            }
        }
    }
}

@Composable
private fun MyVehiclesTable(
    rows: List<LocalVehicleEntity>,
    selected: Set<String>,
    rowFlash: Map<String, RowFlash>,
    sortColumn: MyVehicleSortColumn?,
    sortAsc: Boolean,
    onSort: (MyVehicleSortColumn) -> Unit,
    menuForUuid: String?,
    onMenuChange: (String?) -> Unit,
    onToggleSelected: (String) -> Unit,
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
            HeaderCell(
                stringResource(R.string.my_vehicles_col_name),
                140.dp,
                MyVehicleSortColumn.NAME,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_kind),
                120.dp,
                MyVehicleSortColumn.KIND,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_number),
                140.dp,
                MyVehicleSortColumn.NUMBER,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_carrier),
                120.dp,
                MyVehicleSortColumn.CARRIER,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_assignment),
                120.dp,
                MyVehicleSortColumn.ASSIGNMENT,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_revision),
                100.dp,
                MyVehicleSortColumn.REVISION,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_epoch),
                80.dp,
                MyVehicleSortColumn.EPOCH,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.my_vehicles_col_dcc),
                80.dp,
                MyVehicleSortColumn.DCC_ADDRESS,
                sortColumn,
                sortAsc,
                onSort,
            )
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
                            selected = row.uuid in selected,
                            flash = rowFlash[row.uuid],
                            onClick = { onToggleSelected(row.uuid) },
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
    selected: Boolean,
    flash: RowFlash?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val flashColor = when (flash) {
        RowFlash.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        RowFlash.ERROR -> MaterialTheme.colorScheme.errorContainer
        null -> Color.Transparent
    }
    val selectedColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val target = if (flash != null) flashColor else selectedColor
    val bg by animateColorAsState(targetValue = target, label = "rowFlash")

    Row(
        modifier = Modifier
            .height(RowHeight)
            .background(bg)
            .combinedClickable(
                onClick = onClick,
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
private fun HeaderCell(
    text: String,
    width: Dp,
    sortColumn: MyVehicleSortColumn? = null,
    activeColumn: MyVehicleSortColumn? = null,
    sortAsc: Boolean = true,
    onSort: (MyVehicleSortColumn) -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(width)
            .then(
                if (sortColumn != null) {
                    Modifier.clickable { onSort(sortColumn) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (sortColumn != null && sortColumn == activeColumn) {
            Icon(
                imageVector = if (sortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
    }
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
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, icons) {
        if (query.isBlank()) {
            icons
        } else {
            icons.filter { it.vehicleNumber?.contains(query.trim(), ignoreCase = true) == true }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.my_vehicles_pick_icon)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.my_vehicles_icon_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.imagePath }) { icon ->
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.my_vehicles_cancel))
            }
        },
    )
}
