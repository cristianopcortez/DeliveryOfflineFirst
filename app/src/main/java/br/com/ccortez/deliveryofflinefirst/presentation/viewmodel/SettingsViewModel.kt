package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.ccortez.deliveryofflinefirst.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _eventos = MutableSharedFlow<SettingsEvent>()
    val eventos = _eventos.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { config ->
                _uiState.update {
                    it.copy(
                        darkTheme = config.darkTheme,
                        motoristaNome = config.motoristaNome,
                        nomeEditado = config.motoristaNome
                    )
                }
            }
        }
    }

    fun onDarkThemeChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDarkTheme(enabled)
        }
    }

    fun onNomeEditadoChange(nome: String) {
        _uiState.update { it.copy(nomeEditado = nome) }
    }

    fun onSaveNome() {
        viewModelScope.launch {
            settingsRepository.updateMotoristaNome(_uiState.value.nomeEditado)
            _eventos.emit(SettingsEvent.ShowSnackbar("Settings saved"))
        }
    }
}
