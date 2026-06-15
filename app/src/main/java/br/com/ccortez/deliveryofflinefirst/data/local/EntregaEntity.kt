package br.com.ccortez.deliveryofflinefirst.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entrega")
data class EntregaEntity(
    @PrimaryKey val id: String,
    val cliente: String,
    val endereco: String,
    val status: String,
    val sincronizada: Boolean = true,
    val horarioConclusao: Long? = null
)
