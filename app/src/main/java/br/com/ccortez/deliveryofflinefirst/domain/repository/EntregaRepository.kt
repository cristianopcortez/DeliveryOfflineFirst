package br.com.ccortez.deliveryofflinefirst.domain.repository

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import kotlinx.coroutines.flow.Flow

interface EntregaRepository {
    /** Single Source of Truth: emits the full delivery list with items on every Room change. */
    fun observarTodas(): Flow<List<EntregaComProdutos>>

    suspend fun inserirTodas(entregas: List<Entrega>)

    /** Inserts items for a specific delivery. Uses IGNORE for idempotency on seed and re-inserts. */
    suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>)

    /** Returns the total number of deliveries in the database. Used as a guard for the initial seed. */
    suspend fun contarEntregas(): Int

    suspend fun concluirEntrega(id: String)
    suspend fun listarPendentes(): List<Entrega>
    suspend fun marcarSincronizadaPorUuid(uuid: String)
    suspend fun marcarTodasSincronizadas()

    /** Updates the check flag of an individual item. */
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)
}
