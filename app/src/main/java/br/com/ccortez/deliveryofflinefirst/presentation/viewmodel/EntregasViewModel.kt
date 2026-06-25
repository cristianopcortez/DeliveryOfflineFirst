package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.ccortez.deliveryofflinefirst.data.worker.SyncWorker
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntregasViewModel @Inject constructor(
    private val repository: EntregaRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Screen state — single source of truth for loading, error, and full list (used by the dropdown)
    private val _uiState = MutableStateFlow(EntregasUiState())
    val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

    // SharedFlow: one-shot events that must not re-emit on rotation
    // Key difference vs StateFlow: no current value, does not replay to new collectors
    private val _eventos = MutableSharedFlow<EntregasEvent>()
    val eventos = _eventos.asSharedFlow()

    // Search filter source as StateFlow (mutable internally, immutable externally)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // debounce + distinctUntilChanged + flatMapLatest | stateIn + WhileSubscribed
    // debounce: waits 300ms with no changes before firing (avoids a query on every keystroke)
    // flatMapLatest: cancels the previous query when a new filter arrives
    // stateIn + WhileSubscribed(5000): converts cold → hot; stays active 5s without collectors (survives rotation)
    // Client filter stays in the composable with remember — resets on rotation intentionally
    val entregasFiltradas: StateFlow<List<Entrega>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.observarTodas().map { list ->
                list.filter {
                    query.isBlank() ||
                    it.cliente.contains(query, ignoreCase = true) ||
                    it.endereco.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Observes WorkManager state reactively — no polling
    val syncStatus: StateFlow<WorkInfo.State?> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("sync_entregas")
        .map { infos -> infos.firstOrNull()?.state }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        popularBancoSeVazio()
        observarEntregas()
    }

    private fun observarEntregas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.observarTodas()
                .catch { e ->
                    _uiState.update { it.copy(erro = e.message, isLoading = false) }
                }
                .collect { lista ->
                    _uiState.update { it.copy(isLoading = false, entregas = lista) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun concluirEntrega(id: String) {
        viewModelScope.launch {
            repository.concluirEntrega(id)
            // SharedFlow emits the event; the screen collects it via LaunchedEffect and shows the Snackbar
            _eventos.emit(EntregasEvent.ShowSnackbar("Entrega concluída. Sincronizará quando houver rede."))
            agendarSync()
        }
    }

    private fun agendarSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // KEEP: if a sync is already enqueued, do not duplicate it — prevents concurrent sync storms
        WorkManager.getInstance(context)
            .enqueueUniqueWork("sync_entregas", ExistingWorkPolicy.KEEP, request)
    }

    private fun popularBancoSeVazio() {
        viewModelScope.launch {
            repository.inserirTodas(entregasSeed)
        }
    }

    companion object {
        private val entregasSeed = listOf(
            Entrega("1", "Ana Paula", "Rua das Flores, 123", "Pendente"),
            Entrega("2", "Carlos Lima", "Av. Brasil, 456", "Em rota"),
            Entrega("3", "João Silva", "Rua do Comércio, 789", "Pendente"),
            Entrega("4", "Maria Souza", "Travessa A, 12", "Concluída", sincronizada = false),
        )
    }
}
