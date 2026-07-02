package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos

data class EntregasUiState(
    val isLoading: Boolean = false,
    val entregas: List<EntregaComProdutos> = emptyList(),
    val erro: String? = null,
    val isNlpLoading: Boolean = false
)
