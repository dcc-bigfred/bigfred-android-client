package com.dccbigfred.android.ui.models

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.dccbigfred.android.models.ModelRow
import com.dccbigfred.android.models.ModelSortColumn
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.launch

private val RowHeight = 56.dp
private val ThumbSize = 48.dp

private fun openGoogleSearch(context: android.content.Context, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString())
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsCatalogScreen(
    onBack: () -> Unit,
    pickerMode: Boolean = false,
    onModelPicked: ((ModelRow) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onAddToMyVehicles: ((ModelRow) -> Unit)? = null,
    viewModel: ModelsCatalogViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filtersExpanded by remember { mutableStateOf(false) }
    var selectedRow by remember { mutableStateOf<ModelRow?>(null) }
    var menuForId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedMsg = stringResource(R.string.models_added_to_my_vehicles)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (pickerMode) R.string.models_pick_title else R.string.models_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (pickerMode) {
                                onCancel?.invoke() ?: onBack()
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            if (pickerMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.models_filters),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (pickerMode) {
                Button(
                    onClick = {
                        val row = selectedRow ?: return@Button
                        onModelPicked?.invoke(row)
                    },
                    enabled = selectedRow != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.models_select))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (filtersExpanded) {
                FiltersPanel(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                ActiveFilterChips(
                    state = state,
                    onOpenFilters = { filtersExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            when {
                state.error != null && state.page.rows.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    ModelsTable(
                        rows = state.page.rows,
                        loading = state.loading,
                        selectedId = if (pickerMode) selectedRow?.id else null,
                        sortColumn = state.filters.sortColumn,
                        sortAsc = state.filters.sortAsc,
                        onSort = viewModel::toggleSort,
                        onRowClick = if (pickerMode) {
                            { row -> selectedRow = row }
                        } else {
                            null
                        },
                        menuForId = if (!pickerMode) menuForId else null,
                        onMenuChange = { menuForId = it },
                        onAddToMyVehicles = if (!pickerMode) {
                            onAddToMyVehicles?.let { cb ->
                                { row ->
                                    cb(row)
                                    scope.launch { snackbar.showSnackbar(addedMsg) }
                                }
                            }
                        } else {
                            null
                        },
                        showContextMenu = !pickerMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onPageSizeChanged = viewModel::setPageSize,
                    )
                    PaginationBar(
                        page = state.page.page,
                        pageCount = state.page.pageCount,
                        totalCount = state.page.totalCount,
                        onPrev = viewModel::prevPage,
                        onNext = viewModel::nextPage,
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersPanel(
    state: ModelsCatalogUiState,
    viewModel: ModelsCatalogViewModel,
    modifier: Modifier = Modifier,
) {
    val filters = state.filters
    val options = state.filterOptions
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.models_filter_search)) },
        )
        MultiSelectFilter(
            label = stringResource(R.string.models_filter_manufacturer),
            options = options.manufacturers.map { it to it },
            selected = filters.manufacturers,
            onChange = viewModel::setManufacturers,
        )
        MultiSelectFilter(
            label = stringResource(R.string.models_filter_epoch),
            options = options.epochs.map { formatEpoch(it) to it },
            selected = filters.epochs,
            onChange = viewModel::setEpochs,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filters.revisionFrom.orEmpty(),
                onValueChange = viewModel::setRevisionFrom,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.models_filter_revision_from)) },
                placeholder = { Text("YYYY-MM-DD") },
            )
            OutlinedTextField(
                value = filters.revisionTo.orEmpty(),
                onValueChange = viewModel::setRevisionTo,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.models_filter_revision_to)) },
                placeholder = { Text("YYYY-MM-DD") },
            )
        }
        MultiSelectFilter(
            label = stringResource(R.string.models_filter_carrier),
            options = options.carriers.map { it to it },
            selected = filters.carriers,
            onChange = viewModel::setCarriers,
        )
        MultiSelectFilter(
            label = stringResource(R.string.models_filter_vehicle_kind),
            options = options.vehicleKinds.map { it to it },
            selected = filters.vehicleKinds,
            onChange = viewModel::setVehicleKinds,
        )
        ScaleDropdown(
            scales = options.scales,
            selected = filters.scale,
            onChange = viewModel::setScale,
        )
        TextButton(onClick = viewModel::clearFilters) {
            Text(stringResource(R.string.models_filters_clear))
        }
    }
}

@Composable
private fun ActiveFilterChips(
    state: ModelsCatalogUiState,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = state.filters
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = false,
            onClick = onOpenFilters,
            label = { Text(stringResource(R.string.models_filters)) },
        )
        if (f.query.isNotBlank()) {
            FilterChip(selected = true, onClick = onOpenFilters, label = { Text("\"${f.query}\"") })
        }
        if (f.manufacturers.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = onOpenFilters,
                label = {
                    Text("${stringResource(R.string.models_col_manufacturer)}: ${f.manufacturers.size}")
                },
            )
        }
        if (f.epochs.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = onOpenFilters,
                label = { Text("${stringResource(R.string.models_col_epoch)}: ${f.epochs.size}") },
            )
        }
        if (f.carriers.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = onOpenFilters,
                label = { Text("${stringResource(R.string.models_col_carrier)}: ${f.carriers.size}") },
            )
        }
        if (f.vehicleKinds.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = onOpenFilters,
                label = {
                    Text("${stringResource(R.string.models_col_vehicle_kind)}: ${f.vehicleKinds.size}")
                },
            )
        }
        f.scale?.let {
            FilterChip(selected = true, onClick = onOpenFilters, label = { Text(it) })
        }
    }
}

@Composable
private fun MultiSelectFilter(
    label: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            val count = if (selected.isEmpty()) "" else " (${selected.size})"
            Text("$label$count")
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(4.dp),
            ) {
                options.forEach { (display, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = { checked ->
                                onChange(if (checked) selected + value else selected - value)
                            },
                        )
                        Text(display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleDropdown(
    scales: List<String>,
    selected: String?,
    onChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.models_filter_scale_all)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: allLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.models_filter_scale)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    onChange(null)
                    expanded = false
                },
            )
            scales.forEach { scale ->
                DropdownMenuItem(
                    text = { Text(scale) },
                    onClick = {
                        onChange(scale)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelsTable(
    rows: List<ModelRow>,
    loading: Boolean,
    selectedId: Long?,
    onRowClick: ((ModelRow) -> Unit)?,
    onPageSizeChanged: (Int) -> Unit,
    sortColumn: ModelSortColumn? = null,
    sortAsc: Boolean = true,
    onSort: (ModelSortColumn) -> Unit = {},
    menuForId: Long? = null,
    onMenuChange: (Long?) -> Unit = {},
    onAddToMyVehicles: ((ModelRow) -> Unit)? = null,
    showContextMenu: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bodyHeightPx by remember { mutableIntStateOf(0) }
    val rowHeightPx = with(density) { RowHeight.roundToPx() }

    LaunchedEffect(bodyHeightPx, rowHeightPx) {
        if (bodyHeightPx > 0 && rowHeightPx > 0) {
            val size = max(1, floor(bodyHeightPx.toDouble() / rowHeightPx).toInt())
            onPageSizeChanged(size)
        }
    }

    val hScroll = rememberScrollState()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            HeaderCell(stringResource(R.string.models_col_image), 64.dp)
            HeaderCell(
                stringResource(R.string.models_col_vehicle_number),
                160.dp,
                ModelSortColumn.VEHICLE_NUMBER,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_assignment),
                120.dp,
                ModelSortColumn.ASSIGNMENT,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_revision),
                100.dp,
                ModelSortColumn.REVISION,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_epoch),
                100.dp,
                ModelSortColumn.EPOCH,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_manufacturer),
                110.dp,
                ModelSortColumn.MANUFACTURER,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_catalog),
                90.dp,
                ModelSortColumn.CATALOG,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_scale),
                56.dp,
                ModelSortColumn.SCALE,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_release),
                100.dp,
                ModelSortColumn.RELEASE,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_vehicle_kind),
                160.dp,
                ModelSortColumn.VEHICLE_KIND,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_type),
                100.dp,
                ModelSortColumn.TYPE,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_carrier),
                130.dp,
                ModelSortColumn.CARRIER,
                sortColumn,
                sortAsc,
                onSort,
            )
            HeaderCell(
                stringResource(R.string.models_col_livery),
                160.dp,
                ModelSortColumn.LIVERY,
                sortColumn,
                sortAsc,
                onSort,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { bodyHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll),
            ) {
                rows.forEach { row ->
                    Box {
                        ModelTableRow(
                            row = row,
                            selected = selectedId == row.id,
                            onClick = onRowClick?.let { cb -> { cb(row) } },
                            onLongClick = if (showContextMenu) {
                                { onMenuChange(row.id) }
                            } else {
                                null
                            },
                        )
                        if (showContextMenu) {
                            DropdownMenu(
                                expanded = menuForId == row.id,
                                onDismissRequest = { onMenuChange(null) },
                            ) {
                                if (onAddToMyVehicles != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.models_add_to_my_vehicles)) },
                                        onClick = {
                                            onAddToMyVehicles(row)
                                            onMenuChange(null)
                                        },
                                    )
                                }
                                val vehicleNumber = row.vehicleNumber?.trim().orEmpty()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.models_search_vehicle_number_google)) },
                                    enabled = vehicleNumber.isNotEmpty(),
                                    onClick = {
                                        openGoogleSearch(context, vehicleNumber)
                                        onMenuChange(null)
                                    },
                                )
                                val modelQuery = listOf(row.manufacturer, row.catalogNumber)
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" ")
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.models_search_model_google)) },
                                    enabled = modelQuery.isNotEmpty(),
                                    onClick = {
                                        openGoogleSearch(context, modelQuery)
                                        onMenuChange(null)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            if (!loading && rows.isEmpty()) {
                Text(
                    stringResource(R.string.models_empty),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp,
    sortColumn: ModelSortColumn? = null,
    activeColumn: ModelSortColumn? = null,
    sortAsc: Boolean = true,
    onSort: (ModelSortColumn) -> Unit = {},
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelTableRow(
    row: ModelRow,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .height(RowHeight)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    onClick != null || onLongClick != null -> Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                    else -> Modifier
                },
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (row.imagePath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/${row.imagePath}")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(ThumbSize),
                )
            }
        }
        BodyCell(row.vehicleNumber.orEmpty(), 160.dp)
        BodyCell(row.assignment.orEmpty(), 120.dp)
        BodyCell(formatDate(row.revisionDate, row.revisionDatePrecision), 100.dp)
        BodyCell(row.epochs.joinToString(", ") { formatEpoch(it) }, 100.dp)
        BodyCell(row.manufacturer, 110.dp)
        BodyCell(row.catalogNumber, 90.dp)
        BodyCell(row.scale, 56.dp)
        BodyCell(formatDate(row.releaseDate, row.releaseDatePrecision), 100.dp)
        BodyCell(row.vehicleKind, 160.dp)
        BodyCell(row.type.orEmpty(), 100.dp)
        BodyCell(row.carrier.orEmpty(), 130.dp)
        BodyCell(row.livery.orEmpty(), 160.dp)
    }
}

@Composable
private fun BodyCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .widthIn(max = width)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun PaginationBar(
    page: Int,
    pageCount: Int,
    totalCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev, enabled = page > 0) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = stringResource(R.string.models_page_prev),
            )
        }
        Text(
            stringResource(R.string.models_page_status, page + 1, pageCount, totalCount),
        )
        IconButton(onClick = onNext, enabled = page + 1 < pageCount) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = stringResource(R.string.models_page_next),
            )
        }
    }
}

private fun formatEpoch(code: String): String {
    val parts = code.split("_", limit = 2)
    return if (parts.size == 2) {
        "${parts[0]} ${parts[1].lowercase()}"
    } else {
        code
    }
}

private fun formatDate(iso: String?, precision: String?): String {
    if (iso.isNullOrBlank()) return ""
    return if (precision == "year" && iso.length >= 4) iso.take(4) else iso
}
