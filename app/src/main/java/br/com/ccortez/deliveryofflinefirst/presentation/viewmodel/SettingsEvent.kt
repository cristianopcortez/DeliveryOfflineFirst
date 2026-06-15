package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

sealed class SettingsEvent {
    data class ShowSnackbar(val message: String) : SettingsEvent()
}
