package com.dccbigfred.android.data.localvehicles

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class LocalVehicleRepository(
    private val dao: LocalVehicleDao,
) {
    fun observeAll(): Flow<List<LocalVehicleEntity>> = dao.observeAll()

    suspend fun findByUuid(uuid: String): LocalVehicleEntity? = dao.findByUuid(uuid)

    suspend fun upsert(entity: LocalVehicleEntity) = dao.upsert(entity)

    suspend fun delete(entity: LocalVehicleEntity) = dao.delete(entity)

    fun newUuid(): String = UUID.randomUUID().toString()
}
