package br.com.ccortez.deliveryofflinefirst.domain.repository

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.model.ResultadoConclusao
import kotlinx.coroutines.flow.Flow

interface EntregaRepository {
    /** Single Source of Truth: emits the full delivery list with items on every Room change. */
    fun observarTodas(): Flow<List<EntregaComProdutos>>

    suspend fun inserirTodas(entregas: List<Entrega>)

    /** Inserts items for a specific delivery. Uses IGNORE for idempotency on seed and re-inserts. */
    suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>)

    /** Returns the total number of deliveries in the database. Used as a guard for the initial seed. */
    suspend fun contarEntregas(): Int

    /**
     * Concludes a delivery atomically. NEVER concludes when any item is unchecked
     * ([ResultadoConclusao.ITENS_PENDENTES]); the rule lives here so UI and NLP share it.
     */
    suspend fun concluirEntrega(id: String): ResultadoConclusao

    /** Outbox payload: pending deliveries WITH their items, so the server receives the full state. */
    suspend fun listarPendentes(): List<EntregaComProdutos>

    /**
     * First-install seed in a single transaction: either all deliveries and items are written,
     * or none. Returns false (and writes nothing) if the database already has deliveries.
     */
    suspend fun semearSeVazio(entregas: List<Entrega>, itens: Map<String, List<ItemPedido>>): Boolean
    suspend fun marcarSincronizadaPorUuid(uuid: String)
    suspend fun marcarTodasSincronizadas()

    /** Updates the check flag of an individual item. */
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)
}
