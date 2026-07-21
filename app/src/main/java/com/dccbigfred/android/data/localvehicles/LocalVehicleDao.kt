package com.dccbigfred.android.data.localvehicles

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalVehicleDao {
    @Query("SELECT * FROM local_vehicles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalVehicleEntity>>

    @Query("SELECT * FROM local_vehicles WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): LocalVehicleEntity?

    @Upsert
    suspend fun upsert(entity: LocalVehicleEntity)

    @Delete
    suspend fun delete(entity: LocalVehicleEntity)
}
