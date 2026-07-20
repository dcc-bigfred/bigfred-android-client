package com.dccbigfred.android.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dccbigfred.android.models.FilterOptions
import com.dccbigfred.android.models.ModelFilters
import com.dccbigfred.android.models.ModelPage
import com.dccbigfred.android.models.ModelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelsCatalogUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filters: ModelFilters = ModelFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val page: ModelPage = ModelPage(emptyList(), 0, 0, 1),
    val pageSize: Int = 10,
)

class ModelsCatalogViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ModelsRepository(application)

    private val _state = MutableStateFlow(ModelsCatalogUiState())
    val state: StateFlow<ModelsCatalogUiState> = _state.asStateFlow()

    private var queryJob: Job? = null
    private var reloadJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val options = withContext(Dispatchers.IO) { repository.loadFilterOptions() }
                _state.update { it.copy(filterOptions = options) }
                reload()
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: e.toString())
                }
            }
        }
    }

    fun setPageSize(pageSize: Int) {
        val size = pageSize.coerceAtLeast(1)
        if (size == _state.value.pageSize) return
        _state.update { it.copy(pageSize = size) }
        reload(resetPage = true)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(filters = it.filters.copy(query = query)) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(250)
            reload(resetPage = true)
        }
    }

    fun setManufacturers(values: Set<String>) {
        updateFilters { copy(manufacturers = values) }
    }

    fun setEpochs(values: Set<String>) {
        updateFilters { copy(epochs = values) }
    }

    fun setCarriers(values: Set<String>) {
        updateFilters { copy(carriers = values) }
    }

    fun setVehicleKinds(values: Set<String>) {
        updateFilters { copy(vehicleKinds = values) }
    }

    fun setScale(scale: String?) {
        updateFilters { copy(scale = scale) }
    }

    fun setRevisionFrom(value: String?) {
        updateFilters { copy(revisionFrom = value?.takeIf { it.isNotBlank() }) }
    }

    fun setRevisionTo(value: String?) {
        updateFilters { copy(revisionTo = value?.takeIf { it.isNotBlank() }) }
    }

    fun clearFilters() {
        _state.update { it.copy(filters = ModelFilters(query = it.filters.query)) }
        reload(resetPage = true)
    }

    fun nextPage() {
        val s = _state.value
        if (s.page.page + 1 < s.page.pageCount) {
            reload(page = s.page.page + 1)
        }
    }

    fun prevPage() {
        val s = _state.value
        if (s.page.page > 0) {
            reload(page = s.page.page - 1)
        }
    }

    private fun updateFilters(block: ModelFilters.() -> ModelFilters) {
        _state.update { it.copy(filters = it.filters.block()) }
        reload(resetPage = true)
    }

    private fun reload(resetPage: Boolean = false, page: Int? = null) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val current = _state.value
            val targetPage = when {
                page != null -> page
                resetPage -> 0
                else -> current.page.page
            }
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.queryPage(current.filters, targetPage, current.pageSize)
                }
                _state.update { it.copy(loading = false, page = result) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: e.toString())
                }
            }
        }
    }
}
