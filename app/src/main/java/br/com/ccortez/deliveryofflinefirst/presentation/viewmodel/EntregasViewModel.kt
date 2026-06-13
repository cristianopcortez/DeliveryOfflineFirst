package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntregasViewModel @Inject constructor(
    private val repository: EntregaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntregasUiState())
    val uiState: StateFlow<EntregasUiState> = _uiState.asStateFlow()

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

    fun concluirEntrega(id: String) {
        viewModelScope.launch {
            repository.concluirEntrega(id)
            // Room Flow emits automatically — StateFlow updates without manual setState
        }
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
