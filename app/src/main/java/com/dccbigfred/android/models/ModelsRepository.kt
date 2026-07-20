package com.dccbigfred.android.models

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class ModelsRepository(
    context: Context,
) {
    private val database = ModelsDatabase.get(context)

    fun loadFilterOptions(): FilterOptions {
        val db = database.readable()
        return FilterOptions(
            manufacturers = distinct(db, "SELECT DISTINCT manufacturer FROM models WHERE manufacturer != '' ORDER BY manufacturer COLLATE NOCASE"),
            epochs = distinct(db, "SELECT DISTINCT epoch FROM model_epochs ORDER BY epoch"),
            carriers = distinct(db, "SELECT DISTINCT carrier FROM models WHERE carrier IS NOT NULL AND carrier != '' ORDER BY carrier COLLATE NOCASE"),
            vehicleKinds = distinct(db, "SELECT DISTINCT vehicle_kind FROM models WHERE vehicle_kind != '' ORDER BY vehicle_kind COLLATE NOCASE"),
            scales = distinct(db, "SELECT DISTINCT scale FROM models WHERE scale != '' ORDER BY scale COLLATE NOCASE"),
        )
    }

    fun queryPage(filters: ModelFilters, page: Int, pageSize: Int): ModelPage {
        val db = database.readable()
        val (where, args) = buildWhere(filters)
        val total = db.rawQuery(
            "SELECT COUNT(*) FROM models m WHERE $where",
            args.toTypedArray(),
        ).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

        val safePageSize = pageSize.coerceAtLeast(1)
        val pageCount = if (total == 0) 1 else ((total + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val offset = safePage * safePageSize

        val sql = """
            SELECT m.id, m.manufacturer, m.catalog_number, m.image_path, m.scale,
                   m.release_date, m.release_date_precision, m.vehicle_kind, m.type,
                   m.vehicle_number, m.carrier, m.assignment, m.revision_date,
                   m.revision_date_precision, m.livery
            FROM models m
            WHERE $where
            ORDER BY m.manufacturer COLLATE NOCASE, m.catalog_number COLLATE NOCASE
            LIMIT ? OFFSET ?
        """.trimIndent()

        val pageArgs = args + listOf(safePageSize.toString(), offset.toString())
        val rows = db.rawQuery(sql, pageArgs.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    add(cursor.toModelRow(id, loadEpochs(db, id)))
                }
            }
        }

        return ModelPage(
            rows = rows,
            totalCount = total,
            page = safePage,
            pageSize = safePageSize,
        )
    }

    private fun loadEpochs(db: SQLiteDatabase, modelId: Long): List<String> {
        return db.rawQuery(
            "SELECT epoch FROM model_epochs WHERE model_id = ? ORDER BY epoch",
            arrayOf(modelId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0))
            }
        }
    }

    private fun buildWhere(filters: ModelFilters): Pair<String, List<String>> {
        val clauses = mutableListOf("1=1")
        val args = mutableListOf<String>()

        val q = filters.query.trim()
        if (q.isNotEmpty()) {
            clauses += "m.search_blob LIKE ?"
            args += "%${q.lowercase()}%"
        }

        fun addIn(column: String, values: Set<String>) {
            if (values.isEmpty()) return
            val placeholders = values.joinToString(",") { "?" }
            clauses += "m.$column IN ($placeholders)"
            args += values
        }

        addIn("manufacturer", filters.manufacturers)
        addIn("carrier", filters.carriers)
        addIn("vehicle_kind", filters.vehicleKinds)

        if (filters.epochs.isNotEmpty()) {
            val placeholders = filters.epochs.joinToString(",") { "?" }
            clauses += """
                EXISTS (
                  SELECT 1 FROM model_epochs e
                  WHERE e.model_id = m.id AND e.epoch IN ($placeholders)
                )
            """.trimIndent()
            args += filters.epochs
        }

        filters.revisionFrom?.takeIf { it.isNotBlank() }?.let {
            clauses += "m.revision_date IS NOT NULL AND m.revision_date >= ?"
            args += it
        }
        filters.revisionTo?.takeIf { it.isNotBlank() }?.let {
            clauses += "m.revision_date IS NOT NULL AND m.revision_date <= ?"
            args += it
        }

        filters.scale?.takeIf { it.isNotBlank() }?.let {
            clauses += "m.scale = ?"
            args += it
        }

        return clauses.joinToString(" AND ") to args
    }

    private fun distinct(db: SQLiteDatabase, sql: String): List<String> {
        return db.rawQuery(sql, emptyArray()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val v = c.getString(0)
                    if (!v.isNullOrBlank()) add(v)
                }
            }
        }
    }

    private fun Cursor.toModelRow(id: Long, epochs: List<String>): ModelRow {
        return ModelRow(
            id = id,
            manufacturer = getString(1).orEmpty(),
            catalogNumber = getString(2).orEmpty(),
            imagePath = getStringOrNull(3),
            scale = getString(4).orEmpty(),
            releaseDate = getStringOrNull(5),
            releaseDatePrecision = getStringOrNull(6),
            vehicleKind = getString(7).orEmpty(),
            type = getStringOrNull(8),
            vehicleNumber = getStringOrNull(9),
            carrier = getStringOrNull(10),
            assignment = getStringOrNull(11),
            revisionDate = getStringOrNull(12),
            revisionDatePrecision = getStringOrNull(13),
            livery = getStringOrNull(14),
            epochs = epochs,
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        return if (isNull(index)) null else getString(index)
    }
}
