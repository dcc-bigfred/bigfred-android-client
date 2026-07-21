package com.dccbigfred.android.models

data class ModelRow(
    val id: Long,
    val manufacturer: String,
    val catalogNumber: String,
    val imagePath: String?,
    val scale: String,
    val releaseDate: String?,
    val releaseDatePrecision: String?,
    val vehicleKind: String,
    val type: String?,
    val vehicleNumber: String?,
    val carrier: String?,
    val assignment: String?,
    val revisionDate: String?,
    val revisionDatePrecision: String?,
    val livery: String?,
    val epochs: List<String>,
)

enum class ModelSortColumn(val sql: String) {
    VEHICLE_NUMBER("m.vehicle_number COLLATE NOCASE"),
    ASSIGNMENT("m.assignment COLLATE NOCASE"),
    REVISION("m.revision_date"),
    EPOCH("(SELECT MIN(e.epoch) FROM model_epochs e WHERE e.model_id = m.id)"),
    MANUFACTURER("m.manufacturer COLLATE NOCASE"),
    CATALOG("m.catalog_number COLLATE NOCASE"),
    SCALE("m.scale COLLATE NOCASE"),
    RELEASE("m.release_date"),
    VEHICLE_KIND("m.vehicle_kind COLLATE NOCASE"),
    TYPE("m.type COLLATE NOCASE"),
    CARRIER("m.carrier COLLATE NOCASE"),
    LIVERY("m.livery COLLATE NOCASE"),
}

data class ModelFilters(
    val query: String = "",
    val manufacturers: Set<String> = emptySet(),
    val epochs: Set<String> = emptySet(),
    val revisionFrom: String? = null,
    val revisionTo: String? = null,
    val carriers: Set<String> = emptySet(),
    val vehicleKinds: Set<String> = emptySet(),
    val scale: String? = null,
    val sortColumn: ModelSortColumn? = null,
    val sortAsc: Boolean = true,
)

data class ModelPage(
    val rows: List<ModelRow>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
) {
    val pageCount: Int
        get() = if (totalCount == 0 || pageSize <= 0) 1 else ((totalCount + pageSize - 1) / pageSize)
}

data class FilterOptions(
    val manufacturers: List<String> = emptyList(),
    val epochs: List<String> = emptyList(),
    val carriers: List<String> = emptyList(),
    val vehicleKinds: List<String> = emptyList(),
    val scales: List<String> = emptyList(),
)

/** Icon entry for the local-vehicle icon picker (asset path + vehicle number label). */
data class CatalogIcon(
    val imagePath: String,
    val vehicleNumber: String?,
)
