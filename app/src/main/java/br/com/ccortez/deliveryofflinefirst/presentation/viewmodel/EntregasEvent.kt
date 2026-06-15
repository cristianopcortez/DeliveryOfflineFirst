package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

sealed class EntregasEvent {
    data class ShowSnackbar(val message: String) : EntregasEvent()
}
