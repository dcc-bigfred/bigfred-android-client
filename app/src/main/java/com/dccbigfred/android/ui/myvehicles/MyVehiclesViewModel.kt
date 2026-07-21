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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

enum class MyVehicleSortColumn(val selector: (LocalVehicleEntity) -> String?) {
    NAME({ it.name }),
    KIND({ it.kind }),
    NUMBER({ it.number }),
    CARRIER({ it.carrier }),
    ASSIGNMENT({ it.assignment }),
    REVISION({ it.revisionDate }),
    EPOCH({ it.epoch }),
    DCC_ADDRESS({ it.dccAddress?.toString() }),
}

enum class RowFlash { SUCCESS, ERROR }

data class BannerError(
    val id: Long,
    val vehicleName: String,
    val reasonResId: Int,
    val reasonArg: String? = null,
)

class MyVehiclesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BigFredApplication
    private val repo = app.localVehicleRepository
    private val api = app.bigFredApiClient
    private val modelsRepo = ModelsRepository(application)

    private val _sort = MutableStateFlow<Pair<MyVehicleSortColumn, Boolean>?>(null)
    val sort: StateFlow<Pair<MyVehicleSortColumn, Boolean>?> = _sort.asStateFlow()

    val vehicles: StateFlow<List<LocalVehicleEntity>> =
        combine(repo.observeAll(), _sort) { list, sort ->
            if (sort == null) {
                list
            } else {
                val cmp = compareBy<LocalVehicleEntity, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) {
                    sort.first.selector(it)?.takeIf { s -> s.isNotBlank() }
                }
                list.sortedWith(if (sort.second) cmp else cmp.reversed())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _rowFlash = MutableStateFlow<Map<String, RowFlash>>(emptyMap())
    val rowFlash: StateFlow<Map<String, RowFlash>> = _rowFlash.asStateFlow()

    private val _banners = MutableStateFlow<List<BannerError>>(emptyList())
    val banners: StateFlow<List<BannerError>> = _banners.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val catalogIcons: List<CatalogIcon> by lazy { modelsRepo.listIcons() }

    fun toggleSort(column: MyVehicleSortColumn) {
        _sort.update { cur ->
            if (cur?.first == column) column to !cur.second else column to true
        }
    }

    fun toggleSelected(uuid: String) {
        _selected.update { if (uuid in it) it - uuid else it + uuid }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun dismissBanner(id: Long) {
        _banners.update { list -> list.filterNot { it.id == id } }
    }

    fun save(entity: LocalVehicleEntity) {
        viewModelScope.launch { repo.upsert(entity) }
    }

    fun delete(entity: LocalVehicleEntity) {
        viewModelScope.launch {
            _selected.update { it - entity.uuid }
            repo.delete(entity)
        }
    }

    fun sync(entity: LocalVehicleEntity) {
        viewModelScope.launch {
            when (val result = api.upsertVehicle(entity)) {
                is BigFredApiClient.SyncResult.Success -> {
                    flashRow(entity.uuid, RowFlash.SUCCESS)
                    _messages.emit(R.string.my_vehicles_sync_ok)
                }
                else -> {
                    flashRow(entity.uuid, RowFlash.ERROR)
                    enqueueBanner(entity, result)
                }
            }
        }
    }

    fun syncSelected() {
        viewModelScope.launch {
            val targets = vehicles.value.filter { it.uuid in _selected.value }
            clearSelection()
            for (v in targets) {
                val result = api.upsertVehicle(v)
                val ok = result is BigFredApiClient.SyncResult.Success
                flashRow(v.uuid, if (ok) RowFlash.SUCCESS else RowFlash.ERROR)
                if (!ok) enqueueBanner(v, result)
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

    private fun flashRow(uuid: String, state: RowFlash) {
        viewModelScope.launch {
            _rowFlash.update { it + (uuid to state) }
            delay(2_000)
            _rowFlash.update { it - uuid }
        }
    }

    private fun enqueueBanner(v: LocalVehicleEntity, res: BigFredApiClient.SyncResult) {
        viewModelScope.launch {
            val id = System.nanoTime()
            val banner = when (res) {
                is BigFredApiClient.SyncResult.Unauthorized -> BannerError(
                    id = id,
                    vehicleName = v.name.ifBlank { v.number.ifBlank { v.uuid } },
                    reasonResId = R.string.my_vehicles_sync_unauthorized,
                )
                is BigFredApiClient.SyncResult.Failure -> {
                    val mapped = syncErrorMessage(res.code)
                    BannerError(
                        id = id,
                        vehicleName = v.name.ifBlank { v.number.ifBlank { v.uuid } },
                        reasonResId = mapped.first,
                        reasonArg = mapped.second,
                    )
                }
                is BigFredApiClient.SyncResult.Success -> return@launch
            }
            _banners.update { it + banner }
            delay(5_000)
            dismissBanner(id)
        }
    }

    /** Returns string resource and optional format arg (used for generic error with code). */
    private fun syncErrorMessage(code: String): Pair<Int, String?> = when (code) {
        "dcc_address_taken" -> R.string.my_vehicles_sync_err_dcc_taken to null
        "dcc_address_outside_pool" -> R.string.my_vehicles_sync_err_dcc_pool to null
        "vehicle_name_required" -> R.string.my_vehicles_sync_err_name_required to null
        "vehicle_kind_invalid" -> R.string.my_vehicles_sync_err_kind_invalid to null
        "vehicle_epoch_invalid" -> R.string.my_vehicles_sync_err_epoch_invalid to null
        "vehicle_revision_date_invalid" -> R.string.my_vehicles_sync_err_revision_invalid to null
        "vehicle_not_owned" -> R.string.my_vehicles_sync_err_not_owned to null
        "no_server" -> R.string.my_vehicles_sync_err_no_server to null
        "network_error" -> R.string.my_vehicles_sync_err_network to null
        else -> R.string.my_vehicles_sync_error to code
    }

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
