package com.dccbigfred.android.ui.myvehicles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.R
import com.dccbigfred.android.data.localvehicles.LocalVehicleEntity
import com.dccbigfred.android.models.CatalogIcon
import com.dccbigfred.android.models.EpochMapping
import com.dccbigfred.android.models.ModelRow
import com.dccbigfred.android.models.ModelsRepository
import com.dccbigfred.android.network.BigFredApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val VEHICLE_KINDS = listOf("loco", "emu", "driving_wagon", "trolley", "wagon")

val VEHICLE_EPOCHS = listOf(
    "",
    "I", "Ia", "Ib",
    "II", "IIa", "IIb", "IIc",
    "III", "IIIa", "IIIb", "IIIc",
    "IV", "IVa", "IVb", "IVc",
    "V", "Va", "Vb", "Vc",
    "VI", "VIa", "VIb",
)

class MyVehiclesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BigFredApplication
    private val repo = app.localVehicleRepository
    private val api = app.bigFredApiClient
    private val modelsRepo = ModelsRepository(application)

    val vehicles: StateFlow<List<LocalVehicleEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val catalogIcons: List<CatalogIcon> by lazy { modelsRepo.listIcons() }

    fun save(entity: LocalVehicleEntity) {
        viewModelScope.launch { repo.upsert(entity) }
    }

    fun delete(entity: LocalVehicleEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    fun sync(entity: LocalVehicleEntity) {
        viewModelScope.launch {
            when (val result = api.upsertVehicle(entity)) {
                is BigFredApiClient.SyncResult.Success ->
                    _messages.emit(R.string.my_vehicles_sync_ok)
                is BigFredApiClient.SyncResult.Unauthorized ->
                    _messages.emit(R.string.my_vehicles_sync_unauthorized)
                is BigFredApiClient.SyncResult.Conflict ->
                    _messages.emit(R.string.my_vehicles_sync_conflict)
                is BigFredApiClient.SyncResult.Error ->
                    _messages.emit(R.string.my_vehicles_sync_error)
            }
        }
    }

    fun newEntity(): LocalVehicleEntity = LocalVehicleEntity(
        uuid = repo.newUuid(),
        name = "",
        kind = "loco",
        number = "",
        dccAddress = null,
        carrier = "",
        assignment = "",
        revisionDate = null,
        epoch = "",
        iconPath = null,
    )

    companion object {
        fun fromModelRow(row: ModelRow, uuid: String): LocalVehicleEntity {
            val epoch = row.epochs.firstOrNull()?.let { EpochMapping.toPolishEpoch(it) }.orEmpty()
            return LocalVehicleEntity(
                uuid = uuid,
                name = listOf(row.manufacturer, row.catalogNumber)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
                kind = "loco",
                number = row.vehicleNumber.orEmpty(),
                dccAddress = null,
                carrier = row.carrier.orEmpty(),
                assignment = row.assignment.orEmpty(),
                revisionDate = row.revisionDate?.takeIf { it.isNotBlank() },
                epoch = epoch,
                iconPath = row.imagePath,
            )
        }
    }
}
