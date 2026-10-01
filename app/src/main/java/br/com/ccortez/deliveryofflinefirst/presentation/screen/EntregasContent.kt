package br.com.ccortez.deliveryofflinefirst.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.ui.theme.DeliveryOfflineFirstTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateless version of the "EntregasScreen" Figma frame (node 1:2, 360dp wide).
 *
 * Figma hierarchy -> Compose:
 * - TopAppBar (64dp, padding 0/4/0/16)         -> [TopAppBar] + [IconButton] (48dp)
 * - ClienteFilterDropdown (padding 8/16)       -> [ClienteFilterDropdownContent]
 * - campo_busca (padding 0/16/4)               -> [OutlinedTextField]
 * - Spacer (h=4)                               -> [Spacer]
 * - campo_not_lived_in_viewmodel (0/16/8)      -> [OutlinedTextField]
 * - PendenteSyncBadge/inset (0/16/4)           -> [PendenteSyncBadgeContent]
 * - SyncStatusBanner/inset (0/16/8)            -> [SyncBannerContent]
 * - ListaBox > lista_entregas (padding 16, gap 8) -> [LazyColumn]
 * - FloatingActionButton (56dp, radius 16)     -> [FloatingActionButton]
 *
 * All state is hoisted: nothing here depends on a ViewModel, so it is previewable and testable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntregasContent(
    entregas: List<EntregaComProdutos>,
    motoristaNome: String,
    clientes: List<String>,
    selectedCliente: String,
    searchQuery: String,
    anotacaoRapida: String,
    pendentesSync: Int,
    syncBanner: SyncBannerUi?,
    showScrollToTop: Boolean,
    onClienteSelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAnotacaoChange: (String) -> Unit,
    onEnviarComando: () -> Unit,
    onConcluir: (entregaId: String) -> Unit,
    onConferirItem: (itemId: String, conferido: Boolean) -> Unit,
    onScrollToTop: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nlpEnabled: Boolean = true,
    isNlpLoading: Boolean = false,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            // Material 3 TopAppBar is 64dp tall with 4dp end / 16dp start insets,
            // matching the Figma "TopAppBar" frame.
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Entregas", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = motoristaNome,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Open settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (showScrollToTop) {
                FloatingActionButton(onClick = onScrollToTop) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            ClienteFilterDropdownContent(
                clientes = clientes,
                selectedCliente = selectedCliente,
                onClienteSelected = onClienteSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        if (nlpEnabled) "Buscar ou descreva um comando de IA..."
                        else "Buscar entrega ou item..."
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (nlpEnabled) {
                    {
                        IconButton(
                            onClick = onEnviarComando,
                            enabled = searchQuery.isNotBlank() && !isNlpLoading,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar comando para IA",
                            )
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (nlpEnabled) onEnviarComando() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                    .testTag("campo_busca"),
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = anotacaoRapida,
                onValueChange = onAnotacaoChange,
                label = { Text("Anotação rápida") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .testTag("campo_not_lived_in_viewmodel"),
            )

            if (pendentesSync > 0) {
                PendenteSyncBadgeContent(
                    count = pendentesSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }

            if (syncBanner != null) {
                SyncBannerContent(
                    banner = syncBanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            // ListaBox > lista_entregas: padding 16, gap 8
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lista_entregas"),
            ) {
                items(items = entregas, key = { it.entrega.id }) { entregaComProdutos ->
                    EntregaCardContent(
                        entregaComProdutos = entregaComProdutos,
                        searchQuery = searchQuery,
                        onConcluir = { onConcluir(entregaComProdutos.entrega.id) },
                        onConferirItem = onConferirItem,
                    )
                }
            }
        }
    }
}

/** UI model for the sync banner (text + container colour role), decoupled from WorkManager. */
enum class SyncBannerUi(val texto: String) {
    Syncing("↻ Syncing..."),
    Pending("⏳ Sync pending — waiting for network"),
    Synced("✓ All synced"),
    Failed("✗ Sync failed"),
}

@Composable
private fun SyncBannerContent(banner: SyncBannerUi, modifier: Modifier = Modifier) {
    val container = when (banner) {
        SyncBannerUi.Syncing -> MaterialTheme.colorScheme.primaryContainer
        SyncBannerUi.Pending -> MaterialTheme.colorScheme.secondaryContainer
        SyncBannerUi.Synced -> MaterialTheme.colorScheme.tertiaryContainer
        SyncBannerUi.Failed -> MaterialTheme.colorScheme.errorContainer
    }
    // Figma: radius 8 (shapes.small), padding 6/12, labelMedium (12/16, Medium)
    Surface(modifier = modifier, color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = banner.texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PendenteSyncBadgeContent(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer, // Figma #F9DEDC
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "⚠ $count pending sync",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer, // Figma #410E0B
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClienteFilterDropdownContent(
    clientes: List<String>,
    selectedCliente: String,
    onClienteSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Menu open/closed is ephemeral UI state; it is fine to keep it local.
    // Single assignment site: several `expanded = ...` writes make the compiler
    // report the first one as "Assigned value is never read".
    var expanded by remember { mutableStateOf(false) }
    val onExpandedChange = { next: Boolean -> expanded = next }
    val opcoes = remember(clientes) { listOf("Todos") + clientes.distinct() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        // Figma: outlined, 56dp tall, padding 0/12/0/16, label "Filtrar" floating on the border
        OutlinedTextField(
            value = selectedCliente,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filtrar") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag("dropdown_cliente"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            opcoes.forEach { cliente ->
                DropdownMenuItem(
                    text = { Text(cliente) },
                    onClick = {
                        onClienteSelected(cliente)
                        onExpandedChange(false)
                    },
                    modifier = Modifier.testTag("dropdown_item_$cliente"),
                )
            }
        }
    }
}

/**
 * Figma "EntregaCard" (one per client): column, padding 16, radius 12, 1dp stroke #CAC4D0, elevation ~1dp,
 * fill #F7F2FA (surfaceContainerLow). [androidx.compose.material3.ElevatedCard] has no `border`
 * parameter, so a [Card] is configured with the same container colour and level-1 elevation.
 */
@Composable
private fun EntregaCardContent(
    entregaComProdutos: EntregaComProdutos,
    searchQuery: String,
    onConcluir: () -> Unit,
    onConferirItem: (itemId: String, conferido: Boolean) -> Unit,
) {
    val entrega = entregaComProdutos.entrega
    val itens = entregaComProdutos.itens

    var expandido by remember(searchQuery, itens) {
        mutableStateOf(
            searchQuery.isNotBlank() && itens.any { it.nome.contains(searchQuery, ignoreCase = true) }
        )
    }
    val todosItensConferidos = remember(itens) { itens.all { it.conferido } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // 12dp
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow, // Figma #F7F2FA
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), // Figma #CAC4D0
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: row, items centred; "Recolher/Expandir" is a 48dp icon button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entrega.cliente,
                    style = MaterialTheme.typography.titleMedium, // Inter Medium 16/24
                    modifier = Modifier.weight(1f),
                )
                if (itens.isNotEmpty()) {
                    IconButton(onClick = { expandido = !expandido }) {
                        Icon(
                            imageVector = if (expandido) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expandido) "Recolher itens" else "Ver itens",
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(text = entrega.endereco, style = MaterialTheme.typography.bodyMedium) // 14/20
            Spacer(Modifier.height(4.dp))
            Text(text = "Status: ${entrega.status}", style = MaterialTheme.typography.bodySmall) // 12/16

            if (entrega.horarioConclusao != null) {
                val hora = remember(entrega.horarioConclusao) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entrega.horarioConclusao))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Concluded at $hora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline, // Figma #79747E
                )
            }

            // "Itens": column, padding-top 8, divider + 4dp spacer + rows
            AnimatedVisibility(visible = expandido) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    itens.forEach { item ->
                        ItemPedidoRowContent(
                            item = item,
                            onConferido = { onConferirItem(item.id, it) },
                        )
                    }
                }
            }

            if (entrega.status != "Concluída") {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConcluir,
                    enabled = todosItensConferidos,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary, // Figma #6750A4
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, // #E7E0EC
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant, // #49454F
                    ),
                ) {
                    Text(if (todosItensConferidos) "Concluir Entrega" else "Confira todos os itens")
                }
            }
        }
    }
}

/** Figma "ItemPedidoRow": row, padding 2/0, gap 4, checkbox + "ItemInfo" column. */
@Composable
private fun ItemPedidoRowContent(item: ItemPedido, onConferido: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.conferido, onCheckedChange = onConferido)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.nome,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.conferido) TextDecoration.LineThrough else TextDecoration.None,
            )
            Text(
                text = "Qtd: ${item.quantidade}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Figma #49454F
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Preview with the exact data shown in the Figma frame
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 1280)
@Composable
private fun EntregasContentPreview() {
    val entregas = listOf(
        EntregaComProdutos(
            entrega = Entrega("1", "Ana Paula Ferreira", "Rua das Flores, 123 — Jardim Primavera", "Pendente", sincronizada = false),
            itens = listOf(
                ItemPedido("1", "Tênis Nike Air Max 270 (tam. 38)", 1, true),
                ItemPedido("2", "Mochila Escolar Estampada", 2, false),
                ItemPedido("3", "Protetor Solar FPS 70 — 200ml", 3, false),
                ItemPedido("4", "Caixa de Papelão 50×40×30 cm", 1, false),
            ),
        ),
        EntregaComProdutos(
            entrega = Entrega("2", "Carlos Lima", "Av. Brasil, 456 — Centro", "Em rota"),
            itens = emptyList(),
        ),
        EntregaComProdutos(
            entrega = Entrega("3", "Roberto Alves", "Alameda Santos, 201 — Higienópolis", "Pendente"),
            itens = listOf(
                ItemPedido("5", "Dipirona Sódica 500mg — 20 comp.", 2, true),
                ItemPedido("6", "Vitamina C 1000mg Efervescente (30 un.)", 1, true),
            ),
        ),
        EntregaComProdutos(
            entrega = Entrega("4", "Maria Souza", "Travessa Azul, 12 — Bairro Novo", "Concluída", horarioConclusao = 1_700_000_000_000),
            itens = listOf(ItemPedido("7", "Fone Sony WH-1000XM5 (preto)", 1, true)),
        ),
    )
    DeliveryOfflineFirstTheme(dynamicColor = false) {
        EntregasContent(
            entregas = entregas,
            motoristaNome = "Motorista",
            clientes = entregas.map { it.entrega.cliente },
            selectedCliente = "Todos",
            searchQuery = "",
            anotacaoRapida = "",
            pendentesSync = 1,
            syncBanner = SyncBannerUi.Pending,
            showScrollToTop = true,
            onClienteSelected = {},
            onSearchQueryChange = {},
            onAnotacaoChange = {},
            onEnviarComando = {},
            onConcluir = {},
            onConferirItem = { _, _ -> },
            onScrollToTop = {},
            onNavigateToSettings = {},
        )
    }
}
