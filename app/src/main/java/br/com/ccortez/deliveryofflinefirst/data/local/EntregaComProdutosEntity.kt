package br.com.ccortez.deliveryofflinefirst.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room POJO: not a directly persisted entity.
 * Room resolves the JOIN automatically via [Relation], ensuring transactional consistency
 * when the DAO method is annotated with [@Transaction].
 */
data class EntregaComProdutosEntity(
    @Embedded val entrega: EntregaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entregaId"
    )
    val itens: List<ItemPedidoEntity>
)
