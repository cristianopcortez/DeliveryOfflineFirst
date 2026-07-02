package br.com.ccortez.deliveryofflinefirst.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_pedido",
    foreignKeys = [
        ForeignKey(
            entity = EntregaEntity::class,
            parentColumns = ["id"],
            childColumns = ["entregaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entregaId")]
)
data class ItemPedidoEntity(
    @PrimaryKey val id: String,
    val entregaId: String,
    val nome: String,
    val quantidade: Int,
    val conferido: Boolean
)
