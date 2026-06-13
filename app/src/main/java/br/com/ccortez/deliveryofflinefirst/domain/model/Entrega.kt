package br.com.ccortez.deliveryofflinefirst.domain.model

data class Entrega(
    val id: String,
    val cliente: String,
    val endereco: String,
    val status: String,
    val sincronizada: Boolean = true
)
