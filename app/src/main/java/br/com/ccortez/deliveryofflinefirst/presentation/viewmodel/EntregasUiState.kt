package br.com.ccortez.deliveryofflinefirst.presentation.viewmodel

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega

data class EntregasUiState(
    val isLoading: Boolean = false,
    val entregas: List<Entrega> = emptyList(),
    val erro: String? = null,
    /** True while the NLP repository is awaiting a response from the Gemini model. */
    val isNlpLoading: Boolean = false
)
