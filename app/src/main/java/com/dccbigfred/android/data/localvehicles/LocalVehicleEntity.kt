package com.dccbigfred.android.data.localvehicles

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_vehicles")
data class LocalVehicleEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    /** loco | emu | driving_wagon | trolley | wagon */
    val kind: String,
    val number: String,
    val dccAddress: Int?,
    val carrier: String,
    val assignment: String,
    /** YYYY-MM-DD */
    val revisionDate: String?,
    /** e.g. "IIIa" */
    val epoch: String,
    /** Asset path (like ModelRow.imagePath); local-only, never sent to BigFred. */
    val iconPath: String?,
)
