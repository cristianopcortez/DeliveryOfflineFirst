package br.com.ccortez.deliveryofflinefirst.domain.model

data class ItemPedido(
    val id: String,
    val nome: String,
    val quantidade: Int,
    val conferido: Boolean
)
