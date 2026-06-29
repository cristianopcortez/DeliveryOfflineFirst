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
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.data.repository.AnalyticsRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.domain.repository.AnalyticsRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.RemoteConfigRepository
import android.util.Log
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
    private val nlpRepository: NlpRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val analyticsRepository: AnalyticsRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Screen state — single source of truth for loading, error, and full list (used by the dropdown)
    private val _uiState = MutableStateFlow(EntregasUiState())
    val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

    // SharedFlow: one-shot events that must not re-emit on rotation
    // Key difference vs StateFlow: no current value, does not replay to new collectors
    private val _eventos = MutableSharedFlow<EntregasEvent>()
    val eventos = _eventos.asSharedFlow()

    // Optimistic default: NLP stays on while Remote Config is being fetched.
    // Updated once fetchRemoteConfig() completes; the UI reacts reactively.
    private val _nlpEnabled = MutableStateFlow(true)
    val nlpEnabled: StateFlow<Boolean> = _nlpEnabled.asStateFlow()

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
                trigger = AnalyticsRepositoryImpl.TRIGGER_INIT
            )
            Log.d(TAG, "fetchRemoteConfig → Analytics tagged successfully [trigger=init]")
        }
    }

    /**
     * Option C: called by EntregasScreen whenever the lifecycle enters RESUMED
     * (i.e. app comes back to foreground or user returns from Settings).
     *
     * In debug builds minimumFetchIntervalInSeconds = 0 (Option A), so this
     * always goes to the server. In release it reads from the 1-hour cache,
     * meaning no extra quota is consumed on every resume.
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
                trigger = AnalyticsRepositoryImpl.TRIGGER_RESUME
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
        viewModelScope.launch {
            repository.concluirEntrega(id)
            // SharedFlow emits the event; the screen collects it via LaunchedEffect and shows the Snackbar
            _eventos.emit(EntregasEvent.ShowSnackbar("Entrega concluída. Sincronizará quando houver rede."))
            agendarSync()
        }
    }

    /**
     * Sends [comando] to the Gemini model and dispatches the result back into the
     * existing ViewModel state/event channels so the UI reacts through normal UDF flow.
     *
     * SET_SEARCH_QUERY → injects the extracted term into [_searchQuery], which
     *   immediately triggers the debounce + flatMapLatest reactive pipeline.
     *
     * CONCLUDE_DELIVERY → resolves the client name from [_uiState].entregas (the
     *   source of truth already in memory) and delegates to [concluirEntrega].
     *
     * UNKNOWN → emits a one-shot snackbar event; the screen never crashes.
     */
    fun processarComandoNLP(comando: String) {
        if (comando.isBlank()) return
        viewModelScope.launch {
            // Clear the reactive search immediately so the list shows all deliveries
            // while the NLP request is in flight — the command text must not filter the list
            _searchQuery.value = ""
            _uiState.update { it.copy(isNlpLoading = true) }
            analyticsRepository.logNlpCommandSubmitted()

            val nlpCommand = nlpRepository.interpretarComando(comando)

            // Loading ends as soon as the model responds — regardless of the action taken next
            _uiState.update { it.copy(isNlpLoading = false) }

            when (nlpCommand.action) {
                NlpAction.SET_SEARCH_QUERY -> {
                    nlpCommand.searchTerm?.let { onSearchQueryChange(it) }
                    analyticsRepository.logNlpCommandResult(
                        action = AnalyticsRepositoryImpl.ACTION_SET_SEARCH_QUERY,
                        success = true
                    )
                }
                NlpAction.CONCLUDE_DELIVERY -> {
                    val entrega = _uiState.value.entregas.firstOrNull {
                        it.cliente.equals(nlpCommand.targetClient, ignoreCase = true)
                    }
                    if (entrega != null) {
                        concluirEntrega(entrega.id)
                        analyticsRepository.logNlpCommandResult(
                            action = AnalyticsRepositoryImpl.ACTION_CONCLUDE_DELIVERY,
                            success = true
                        )
                    } else {
                        _eventos.emit(
                            EntregasEvent.ShowSnackbar(
                                "Cliente '${nlpCommand.targetClient}' não encontrado na lista."
                            )
                        )
                        analyticsRepository.logNlpCommandResult(
                            action = AnalyticsRepositoryImpl.ACTION_CONCLUDE_DELIVERY,
                            success = false
                        )
                    }
                }
                NlpAction.UNKNOWN -> {
                    _eventos.emit(
                        EntregasEvent.ShowSnackbar("Não entendi o comando ou houve um erro.")
                    )
                    analyticsRepository.logNlpCommandResult(
                        action = AnalyticsRepositoryImpl.ACTION_UNKNOWN,
                        success = false
                    )
                }
            }
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
        private const val TAG = "DEBUG_OFFLINE_FIRST"

        private val entregasSeed = listOf(
            Entrega("1", "Ana Paula", "Rua das Flores, 123", "Pendente"),
            Entrega("2", "Carlos Lima", "Av. Brasil, 456", "Em rota"),
            Entrega("3", "João Silva", "Rua do Comércio, 789", "Pendente"),
            Entrega("4", "Maria Souza", "Travessa A, 12", "Concluída", sincronizada = false),
        )
    }
}
