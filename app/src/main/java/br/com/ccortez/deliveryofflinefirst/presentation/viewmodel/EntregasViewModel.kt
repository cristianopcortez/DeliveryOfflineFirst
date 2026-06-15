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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class EntregasViewModel @Inject constructor(
    private val repository: EntregaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Estado da tela — fonte da verdade para loading, erro e lista completa (usada no dropdown)
    private val _uiState = MutableStateFlow(EntregasUiState())
    val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

    // 4.1 — SharedFlow: eventos one-shot que não devem re-emitir na rotação
    // Diferença chave vs StateFlow: não tem valor atual, não re-emite para novos coletores
    private val _eventos = MutableSharedFlow<EntregasEvent>()
    val eventos = _eventos.asSharedFlow()

    // 4.7 — Fontes dos filtros como StateFlow (mutáveis internamente, imutáveis externamente)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCliente = MutableStateFlow("Todos")
    val selectedCliente: StateFlow<String> = _selectedCliente.asStateFlow()

    // 4.7 combine | 4.7 debounce + distinctUntilChanged + flatMapLatest | 4.2 stateIn + WhileSubscribed
    // combine: merge dois flows de filtro em um único par antes de fazer a query
    // debounce: aguarda 300ms sem mudança antes de disparar (evita query a cada tecla)
    // flatMapLatest: cancela a query anterior quando um novo filtro chega
    // stateIn + WhileSubscribed(5000): converte cold → hot; mantém ativo 5s sem coletores (sobrevive à rotação)
    val entregasFiltradas: StateFlow<List<Entrega>> = combine(
        _searchQuery.debounce(300).distinctUntilChanged(),
        _selectedCliente
    ) { query, cliente -> Pair(query, cliente) }
        .flatMapLatest { (query, cliente) ->
            repository.observarTodas().map { list ->
                list
                    .filter { if (cliente == "Todos") true else it.cliente == cliente }
                    .filter {
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

    // Observa o estado do WorkManager de forma reativa — sem polling
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

    fun onClienteSelected(cliente: String) {
        _selectedCliente.value = cliente
    }

    fun concluirEntrega(id: String) {
        viewModelScope.launch {
            repository.concluirEntrega(id)
            // SharedFlow emite o evento; a tela coleta via LaunchedEffect e exibe o Snackbar
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

        // KEEP: se já há um sync enfileirado, não duplica — evita 50 syncs simultâneos
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
