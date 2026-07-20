package com.dccbigfred.android.models

import org.json.JSONArray
import org.json.JSONObject

data class ModelPickPayload(
    val vehicleNumber: String?,
    val carrier: String?,
    val assignment: String?,
    val revisionDate: String?,
    val epochs: List<String>,
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("vehicleNumber", vehicleNumber ?: JSONObject.NULL)
        obj.put("carrier", carrier ?: JSONObject.NULL)
        obj.put("assignment", assignment ?: JSONObject.NULL)
        obj.put("revisionDate", revisionDate ?: JSONObject.NULL)
        obj.put("epochs", JSONArray(epochs))
        return obj.toString()
    }

    companion object {
        fun fromRow(row: ModelRow): ModelPickPayload {
            return ModelPickPayload(
                vehicleNumber = row.vehicleNumber?.takeIf { it.isNotBlank() },
                carrier = row.carrier?.takeIf { it.isNotBlank() },
                assignment = row.assignment?.takeIf { it.isNotBlank() },
                revisionDate = row.revisionDate?.takeIf { it.isNotBlank() },
                epochs = EpochMapping.mapAll(row.epochs),
            )
        }
    }
}
