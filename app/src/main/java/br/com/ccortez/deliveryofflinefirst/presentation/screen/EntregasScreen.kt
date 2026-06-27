package br.com.ccortez.deliveryofflinefirst.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasEvent
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntregasScreen(
    viewModel: EntregasViewModel,
    modifier: Modifier = Modifier,
    motoristaNome: String = "Motorista",
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // searchQuery lives in the ViewModel — debounce requires a persistent StateFlow
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Text-filtered list via debounce + flatMapLatest + stateIn in the ViewModel
    val entregasFiltradas by viewModel.entregasFiltradas.collectAsStateWithLifecycle()

    // selectedCliente uses remember — resets to "Todos" on rotation (intentional behaviour)
    var selectedCliente by remember { mutableStateOf("Todos") }

    // Client filtering done here in the composable; recalculates only when inputs change
    val entregasExibidas = remember(entregasFiltradas, selectedCliente) {
        if (selectedCliente == "Todos") entregasFiltradas
        else entregasFiltradas.filter { it.cliente == selectedCliente }
    }

    // WorkManager status observed reactively — no polling
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    // rememberSaveable: survives rotation without living in the ViewModel
    var notLivedInViewModel by rememberSaveable { mutableStateOf("") }

    // Ephemeral UI state — dropdown does not need to survive rotation
    var expanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // derivedStateOf: recomposes the FAB only when the boolean flips, not on every scroll pixel
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // derivedStateOf: recalculates only when sincronizada changes, not on every recomposition
    val pendentesSync by remember {
        derivedStateOf { state.entregas.count { !it.sincronizada } }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // LaunchedEffect collects one-shot events from SharedFlow
    // Key difference vs StateFlow: the snackbar does not re-appear on rotation
    LaunchedEffect(Unit) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                is EntregasEvent.ShowSnackbar -> snackbarHostState.showSnackbar(evento.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Entregas")
                        Text(
                            text = motoristaNome,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ClienteFilterDropdown(
                clientes = state.entregas.map { it.cliente },
                selectedCliente = selectedCliente,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                onClienteSelected = { cliente ->
                    selectedCliente = cliente
                    expanded = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Buscar ou descreva um comando de IA...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.processarComandoNLP(searchQuery) },
                        // Disabled while the AI is processing or if the field is empty
                        enabled = searchQuery.isNotBlank() && !state.isNlpLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar comando para IA"
                        )
                    }
                },
                // ImeAction.Search lets the soft keyboard show a Search/Go button
                // that also triggers processarComandoNLP without tapping the icon
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.processarComandoNLP(searchQuery) }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                    .testTag("campo_busca")
            )

            // Indeterminate bar that replaces the 8dp gap below the field while the
            // model is running — no extra vertical layout shift
            if (state.isNlpLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            OutlinedTextField(
                value = notLivedInViewModel,
                onValueChange = { notLivedInViewModel = it },
                label = { Text("Anotação rápida") },
                placeholder = { Text("Ex.: portão azul, campainha quebrada...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .testTag("campo_not_lived_in_viewmodel")
            )

            if (pendentesSync > 0) {
                PendenteSyncBadge(
                    count = pendentesSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            SyncStatusBanner(
                syncStatus = syncStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.erro != null -> {
                        Text(
                            text = "Erro: ${state.erro}",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    entregasExibidas.isEmpty() -> {
                        Text(
                            text = "Nenhuma entrega encontrada.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = entregasExibidas,
                                key = { it.id }
                            ) { entrega ->
                                EntregaCard(
                                    entrega = entrega,
                                    onConcluir = { viewModel.concluirEntrega(entrega.id) }
                                )
                            }
                        }

                        if (showScrollToTop) {
                            FloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Scroll to top"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBanner(
    syncStatus: WorkInfo.State?,
    modifier: Modifier = Modifier
) {
    val (texto, cor) = when (syncStatus) {
        WorkInfo.State.RUNNING ->
            "↻ Syncing..." to MaterialTheme.colorScheme.primaryContainer
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
            "⏳ Sync pending — waiting for network" to MaterialTheme.colorScheme.secondaryContainer
        WorkInfo.State.SUCCEEDED ->
            "✓ All synced" to MaterialTheme.colorScheme.tertiaryContainer
        WorkInfo.State.FAILED ->
            "✗ Sync failed" to MaterialTheme.colorScheme.errorContainer
        else -> return
    }

    Surface(
        modifier = modifier,
        color = cor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PendenteSyncBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "⚠ $count pending sync",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClienteFilterDropdown(
    clientes: List<String>,
    selectedCliente: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClienteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val opcoes = listOf("Todos") + clientes.distinct()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCliente,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filtrar") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag("dropdown_cliente")
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            opcoes.forEach { cliente ->
                DropdownMenuItem(
                    text = { Text(cliente) },
                    onClick = { onClienteSelected(cliente) },
                    modifier = Modifier.testTag("dropdown_item_$cliente")
                )
            }
        }
    }
}

@Composable
private fun EntregaCard(entrega: Entrega, onConcluir: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entrega.cliente,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entrega.endereco,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: ${entrega.status}",
                style = MaterialTheme.typography.bodySmall
            )
            if (entrega.horarioConclusao != null) {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(entrega.horarioConclusao))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Concluded at $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (entrega.status != "Concluída") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onConcluir,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Concluir")
                }
            }
        }
    }
}
