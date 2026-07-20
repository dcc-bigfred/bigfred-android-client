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

data class ModelFilters(
    val query: String = "",
    val manufacturers: Set<String> = emptySet(),
    val epochs: Set<String> = emptySet(),
    val revisionFrom: String? = null,
    val revisionTo: String? = null,
    val carriers: Set<String> = emptySet(),
    val vehicleKinds: Set<String> = emptySet(),
    val scale: String? = null,
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
