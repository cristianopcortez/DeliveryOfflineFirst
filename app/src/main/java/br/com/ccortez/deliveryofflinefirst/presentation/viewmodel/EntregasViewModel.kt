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
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
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

    // WorkManager status observed reactively — no polling
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

            when (nlpCommand.action) {
                NlpAction.SET_SEARCH_QUERY -> {
                    nlpCommand.searchTerm?.let { onSearchQueryChange(it) }
                    analyticsRepository.logNlpCommandResult(
                        action = AnalyticsRepositoryImpl.ACTION_SET_SEARCH_QUERY,
                        success = true
                    )
                }
                NlpAction.CONCLUDE_DELIVERY -> {
                    val entregaComProdutos = _uiState.value.entregas.firstOrNull {
                        it.entrega.cliente.equals(nlpCommand.targetClient, ignoreCase = true)
                    }
                    if (entregaComProdutos != null) {
                        concluirEntrega(entregaComProdutos.entrega.id)
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
                NlpAction.CONFERIR_ITEM -> {
                    val targetItem = nlpCommand.targetItem
                    val estado = nlpCommand.itemConferidoState ?: true

                    if (targetItem.isNullOrBlank()) {
                        _eventos.emit(EntregasEvent.ShowSnackbar("Não consegui identificar o produto a conferir."))
                        return@launch
                    }

                    val todasEntregas = _uiState.value.entregas

                    if (nlpCommand.targetClient != null) {
                        // Scenario A: client specified — search only within that client's delivery
                        val entregaDoCliente = todasEntregas.firstOrNull {
                            it.entrega.cliente.contains(nlpCommand.targetClient, ignoreCase = true)
                        }
                        if (entregaDoCliente == null) {
                            _eventos.emit(
                                EntregasEvent.ShowSnackbar(
                                    "Cliente '${nlpCommand.targetClient}' não encontrado."
                                )
                            )
                            return@launch
                        }
                        val item = entregaDoCliente.itens.firstOrNull {
                            it.nome.contains(targetItem, ignoreCase = true)
                        }
                        if (item != null) {
                            repository.atualizarConferido(item.id, estado)
                            val acao = if (estado) "conferido" else "desmarcado"
                            _eventos.emit(EntregasEvent.ShowSnackbar("'${item.nome}' $acao."))
                        } else {
                            _eventos.emit(
                                EntregasEvent.ShowSnackbar(
                                    "Item '$targetItem' não encontrado para ${nlpCommand.targetClient}."
                                )
                            )
                        }
                    } else {
                        // Scenario B: no client — global search across ALL deliveries
                        val correspondentes = todasEntregas
                            .flatMap { it.itens }
                            .filter { it.nome.contains(targetItem, ignoreCase = true) }

                        when (correspondentes.size) {
                            0 -> _eventos.emit(
                                EntregasEvent.ShowSnackbar(
                                    "Nenhum item com '$targetItem' foi encontrado."
                                )
                            )
                            1 -> {
                                val item = correspondentes.first()
                                repository.atualizarConferido(item.id, estado)
                                val acao = if (estado) "conferido" else "desmarcado"
                                _eventos.emit(EntregasEvent.ShowSnackbar("'${item.nome}' $acao."))
                            }
                            else -> _eventos.emit(
                                EntregasEvent.ShowSnackbar(
                                    "Existe mais de um item correspondente a '$targetItem'. " +
                                    "Por favor, informe o nome do cliente."
                                )
                            )
                        }
                    }
                    analyticsRepository.logNlpCommandResult(
                        action = "CONFERIR_ITEM",
                        success = true
                    )
                }
                NlpAction.UNKNOWN -> {
                    val msg = if (nlpCommand.isError) {
                        "Erro de conexão com o serviço de IA. Verifique a rede."
                    } else {
                        "Não entendi o comando. Tente reformular."
                    }
                    _eventos.emit(EntregasEvent.ShowSnackbar(msg))
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

    /**
     * "Seed if empty" guard: only populates the database on the very first install.
     *
     * Checking [EntregaRepository.contarEntregas] == 0 before inserting prevents:
     *  - Re-seeding on every app relaunch
     *  - Triggering ON DELETE CASCADE on item_pedido FK (which would wipe checked items)
     *
     * Both inserts use IGNORE — if a race condition leaves existing rows, none are overwritten.
     */
    private fun popularBancoSeVazio() {
        viewModelScope.launch {
            if (repository.contarEntregas() > 0) return@launch

            repository.inserirTodas(entregasSeed)
            itensSeed.forEach { (entregaId, itens) ->
                repository.inserirItens(entregaId, itens)
            }
        }
    }

    companion object {
        private const val TAG = "DEBUG_OFFLINE_FIRST"

        // ── Deliveries seed ──────────────────────────────────────────────────
        private val entregasSeed = listOf(
            Entrega("seed-1", "Ana Paula Ferreira",   "Rua das Flores, 123 — Jardim Primavera",   "Pendente"),
            Entrega("seed-2", "Carlos Lima",           "Av. Brasil, 456 — Centro",                 "Em rota"),
            Entrega("seed-3", "João Silva",            "Rua do Comércio, 789 — Vila Industrial",   "Pendente"),
            Entrega("seed-4", "Maria Souza",           "Travessa Azul, 12 — Bairro Novo",          "Concluída",  sincronizada = false),
            Entrega("seed-5", "Roberto Alves",         "Alameda Santos, 201 — Higienópolis",        "Pendente"),
            Entrega("seed-6", "Fernanda Costa",        "Rua XV de Novembro, 88 — Centro",           "Em rota"),
            Entrega("seed-7", "Lucas Mendes",          "Estrada da Saudade, 50 — Zona Rural",       "Pendente"),
        )

        // ── Items seed per delivery ──────────────────────────────────────────
        // Covers varied scenarios: electronics, stationery, clothing, groceries, and pharmacy.
        // IDs prefixed with "seed-" to avoid collision with runtime-generated IDs.
        private val itensSeed: Map<String, List<ItemPedido>> = mapOf(

            // Ana Paula — mixed e-commerce order
            "seed-1" to listOf(
                ItemPedido("seed-1-a", "Tênis Nike Air Max 270 (tam. 38)",        1, false),
                ItemPedido("seed-1-b", "Mochila Escolar Estampada",               2, false),
                ItemPedido("seed-1-c", "Protetor Solar FPS 70 — 200ml",          3, false),
                ItemPedido("seed-1-d", "Caixa de Papelão 50×40×30 cm",           1, false),
            ),

            // Carlos Lima — IT equipment (in transit)
            "seed-2" to listOf(
                ItemPedido("seed-2-a", "Notebook Dell XPS 15 (i7 / 32GB)",       1, false),
                ItemPedido("seed-2-b", "Carregador Universal 65W USB-C",          1, false),
                ItemPedido("seed-2-c", "Mouse Logitech MX Master 3",             1, false),
                ItemPedido("seed-2-d", "Teclado Mecânico Keychron K6",           1, false),
                ItemPedido("seed-2-e", "Hub USB-C 7 portas",                     2, false),
                ItemPedido("seed-2-f", "Caixa de Papelão 50×40×30 cm",           4, false),
            ),

            // João Silva — books and stationery
            "seed-3" to listOf(
                ItemPedido("seed-3-a", "Livro: Clean Architecture (Uncle Bob)",   2, false),
                ItemPedido("seed-3-b", "Livro: Kotlin in Action (2ª Ed.)",        1, false),
                ItemPedido("seed-3-c", "Caderno Universitário 10 matérias",       3, false),
                ItemPedido("seed-3-d", "Caneta Pilot G2 Preta (cx. 12 un.)",     1, false),
                ItemPedido("seed-3-e", "Post-it 76×76mm — bloco colorido",       4, false),
                ItemPedido("seed-3-f", "Marca-texto Stabilo Ponto 68 (6 cores)", 2, false),
            ),

            // Maria Souza — electronics (concluded, pending sync)
            "seed-4" to listOf(
                ItemPedido("seed-4-a", "Fone Sony WH-1000XM5 (preto)",           1, true),
                ItemPedido("seed-4-b", "Cabo USB-C → 3.5mm Adaptador",           2, true),
                ItemPedido("seed-4-c", "Capinha Silicone Sony WH-1000XM5",        1, true),
            ),

            // Roberto Alves — pharmacy and personal care
            "seed-5" to listOf(
                ItemPedido("seed-5-a", "Dipirona Sódica 500mg — 20 comp.",       2, false),
                ItemPedido("seed-5-b", "Vitamina C 1000mg Efervescente (30 un.)",1, false),
                ItemPedido("seed-5-c", "Álcool Gel 70% — frasco 500ml",          3, false),
                ItemPedido("seed-5-d", "Termômetro Digital Axilar",              1, false),
            ),

            // Fernanda Costa — clothing (in transit)
            "seed-6" to listOf(
                ItemPedido("seed-6-a", "Jaqueta Corta-Vento Feminina (M)",        1, false),
                ItemPedido("seed-6-b", "Calça Legging Supplex (P)",               2, false),
                ItemPedido("seed-6-c", "Meias Esportivas Cano Médio (kit 3)",     2, false),
            ),

            // Lucas Mendes — grocery basket / rural route
            "seed-7" to listOf(
                ItemPedido("seed-7-a", "Arroz Branco Tipo 1 — 5kg",              2, false),
                ItemPedido("seed-7-b", "Feijão Carioca — 1kg",                   3, false),
                ItemPedido("seed-7-c", "Azeite Extravirgem — 500ml",             2, false),
                ItemPedido("seed-7-d", "Café Torrado e Moído — 500g",            4, false),
                ItemPedido("seed-7-e", "Açúcar Cristal — 1kg",                   2, false),
            ),
        )
    }
}
