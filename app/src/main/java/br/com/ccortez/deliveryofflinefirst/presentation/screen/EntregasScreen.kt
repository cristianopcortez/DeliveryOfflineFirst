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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntregasScreen(
    viewModel: EntregasViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Estado efêmero de UI: sobrevive à recomposição, não precisa sobreviver à rotação
    var expanded by remember { mutableStateOf(false) }
    var selectedCliente by remember { mutableStateOf("Todos") }

    val entregasFiltradas = remember(state.entregas, selectedCliente) {
        if (selectedCliente == "Todos") state.entregas
        else state.entregas.filter { it.cliente == selectedCliente }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Entregas") }) }
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
                    entregasFiltradas.isEmpty() -> {
                        Text(
                            text = "Nenhuma entrega encontrada.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = entregasFiltradas,
                                key = { it.id }
                            ) { entrega ->
                                EntregaCard(entrega)
                            }
                        }
                    }
                }
            }
        }
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
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            opcoes.forEach { cliente ->
                DropdownMenuItem(
                    text = { Text(cliente) },
                    onClick = { onClienteSelected(cliente) }
                )
            }
        }
    }
}

@Composable
private fun EntregaCard(entrega: Entrega) {
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
        }
    }
}
