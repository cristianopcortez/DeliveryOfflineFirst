package br.com.ccortez.deliveryofflinefirst.domain.model

data class Entrega(
    val id: String,
    val cliente: String,
    val endereco: String,
    val status: String,
    val sincronizada: Boolean = true,
    val horarioConclusao: Long? = null,
    // Client-generated UUID — server uses it as idempotency key to deduplicate
    // retransmissions (network dropped after POST but before ACK)
    val uuid: String = ""
)
