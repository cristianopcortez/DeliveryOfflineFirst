package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import br.com.ccortez.deliveryofflinefirst.domain.analytics.AnalyticsEvents
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ResultadoConclusao
import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncEstado
import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncScheduler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.ConcludeDeliveryHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.ConferirItemHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.NlpCommandHost
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.NlpCommandRouter
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.SetSearchQueryHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.UnknownCommandHandler
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntregasViewModel @Inject constructor(
    private val repository: EntregaRepository,
    private val nlpRepository: NlpRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel(), NlpCommandHost {

    // Screen state — single source of truth for loading, error, and the full list (used by the dropdown)
    private val _uiState = MutableStateFlow(EntregasUiState())
    val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

    // SharedFlow: one-shot events that do not re-emit on rotation
    private val _eventos = MutableSharedFlow<EntregasEvent>()
    val eventos = _eventos.asSharedFlow()

    // Optimistic default: NLP stays on while Remote Config is being fetched
    private val _nlpEnabled = MutableStateFlow(true)
    val nlpEnabled: StateFlow<Boolean> = _nlpEnabled.asStateFlow()

    // Reactive source for the search filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // debounce + distinctUntilChanged + flatMapLatest | stateIn + WhileSubscribed
    // The filter now covers: client name, address, and any item name within the delivery.
    val entregasFiltradas: StateFlow<List<EntregaComProdutos>> = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            repository.observarTodas().map { list ->
                list.filter { ec ->
                    query.isBlank() ||
                    ec.entrega.cliente.contains(query, ignoreCase = true) ||
                    ec.entrega.endereco.contains(query, ignoreCase = true) ||
                    ec.itens.any { it.nome.contains(query, ignoreCase = true) }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val nlpRouter = NlpCommandRouter(
        listOf(
            SetSearchQueryHandler(),
            ConcludeDeliveryHandler(),
            ConferirItemHandler(),
            UnknownCommandHandler()
        )
    )

    val syncStatus: StateFlow<SyncEstado?> = syncScheduler.observarEstado()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        popularBancoSeVazio()
        observarEntregas()
        fetchRemoteConfig()
    }

    private fun fetchRemoteConfig() {
        viewModelScope.launch {
            Log.d(TAG, "fetchRemoteConfig → calling remoteConfigRepository.isNlpEnabled() [trigger=init]")
            val enabled = remoteConfigRepository.isNlpEnabled()
            Log.d(TAG, "fetchRemoteConfig → isNlpEnabled() returned: $enabled — proceeding to Analytics")
            _nlpEnabled.value = enabled
            analyticsRepository.setNlpFeatureUserProperty(enabled)
            analyticsRepository.logNlpConfigFetched(
                nlpEnabled = enabled,
                trigger = AnalyticsEvents.TRIGGER_INIT
            )
            Log.d(TAG, "fetchRemoteConfig → Analytics tagged successfully [trigger=init]")
        }
    }

    /**
     * Called by EntregasScreen whenever the lifecycle enters RESUMED.
     * In debug builds (minimumFetchIntervalInSeconds = 0) always hits the server.
     * In release it reads from the 1-hour cache — no extra quota consumed on every resume.
     */
    fun reloadRemoteConfig() {
        viewModelScope.launch {
            Log.d(TAG, "reloadRemoteConfig → calling remoteConfigRepository.isNlpEnabled() [trigger=resume]")
            val enabled = remoteConfigRepository.isNlpEnabled()
            Log.d(TAG, "reloadRemoteConfig → isNlpEnabled() returned: $enabled — proceeding to Analytics")
            _nlpEnabled.value = enabled
            analyticsRepository.setNlpFeatureUserProperty(enabled)
            analyticsRepository.logNlpConfigFetched(
                nlpEnabled = enabled,
                trigger = AnalyticsEvents.TRIGGER_RESUME
            )
            Log.d(TAG, "reloadRemoteConfig → Analytics tagged successfully [trigger=resume]")
        }
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
        viewModelScope.launch { tentarConcluirEntrega(id) }
    }

    /**
     * Single entry point for concluding a delivery (UI button AND NLP CONCLUDE_DELIVERY).
     * The "all items checked" invariant is enforced by the repository; here we only react to it.
     * Returns true only when the delivery was actually concluded.
     */
    private suspend fun tentarConcluirEntrega(id: String): Boolean {
        when (repository.concluirEntrega(id)) {
            ResultadoConclusao.CONCLUIDA -> Unit
            ResultadoConclusao.ITENS_PENDENTES -> {
                _eventos.emit(EntregasEvent.ShowSnackbar("Confira todos os itens antes de concluir a entrega."))
                return false
            }
            ResultadoConclusao.NAO_ENCONTRADA -> {
                _eventos.emit(EntregasEvent.ShowSnackbar("Entrega não encontrada."))
                return false
            }
        }
        // SharedFlow emits the event; the screen collects it via LaunchedEffect and shows the Snackbar
        _eventos.emit(EntregasEvent.ShowSnackbar("Entrega concluída. Sincronizará quando houver rede."))
        syncScheduler.agendar()
        return true
    }

    /**
     * Updates the [conferido] flag of an item via a clean UDF event.
     * Room emits a new Flow snapshot → [entregasFiltradas] and [uiState] react automatically.
     */
    fun conferirItem(itemId: String, conferido: Boolean) {
        viewModelScope.launch {
            repository.atualizarConferido(itemId, conferido)
        }
    }

    /**
     * SET_SEARCH_QUERY  → injects the extracted term into the debounce + flatMapLatest pipeline.
     * CONCLUDE_DELIVERY → resolves the client from [_uiState].entregas and delegates to [concluirEntrega].
     * CONFERIR_ITEM     → performs a scoped (Scenario A) or global (Scenario B) item search,
     *                     then handles the result: update, ambiguity warning, or "not found" notice.
     * UNKNOWN           → emits a one-shot snackbar event; the UI never crashes.
     */
    fun processarComandoNLP(comando: String) {
        if (comando.isBlank()) return
        viewModelScope.launch {
            _searchQuery.value = ""
            _uiState.update { it.copy(isNlpLoading = true) }
            analyticsRepository.logNlpCommandSubmitted()

            val nlpCommand = nlpRepository.interpretarComando(comando)
            _uiState.update { it.copy(isNlpLoading = false) }
            nlpRouter.despachar(nlpCommand, host = this@EntregasViewModel)
        }
    }

    override fun entregas(): List<EntregaComProdutos> = _uiState.value.entregas
    override fun aplicarBusca(termo: String) = onSearchQueryChange(termo)
    override suspend fun concluir(id: String): Boolean = tentarConcluirEntrega(id)
    override suspend fun conferir(itemId: String, conferido: Boolean) {
        repository.atualizarConferido(itemId, conferido)
    }
    override suspend fun snackbar(mensagem: String) {
        _eventos.emit(EntregasEvent.ShowSnackbar(mensagem))
    }
    override fun logResultado(action: String, success: Boolean) {
        analyticsRepository.logNlpCommandResult(action, success)
    }

    private fun popularBancoSeVazio() {
        viewModelScope.launch { repository.semearDemonstracaoSeVazio() }
    }

    companion object {
        private const val TAG = "DEBUG_OFFLINE_FIRST"
    }
}
