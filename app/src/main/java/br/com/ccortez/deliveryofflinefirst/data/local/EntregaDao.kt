package br.com.ccortez.deliveryofflinefirst.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EntregaDao {

    // @Transaction ensures Room reads entrega + items in a single atomic operation,
    // preventing inconsistencies if a concurrent write occurs during Flow collection.
    @Transaction
    @Query("SELECT * FROM entrega ORDER BY cliente ASC")
    fun observarTodas(): Flow<List<EntregaComProdutosEntity>>

    // IGNORE: does not delete existing rows — prevents triggering ON DELETE CASCADE on item_pedido
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirTodas(entregas: List<EntregaEntity>)

    // IGNORE: idempotent on seed; items already checked by the driver are never overwritten
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirItens(itens: List<ItemPedidoEntity>)

    /** Seed guard: returns 0 only on the very first install. */
    @Query("SELECT COUNT(*) FROM entrega")
    suspend fun contarEntregas(): Int

    /**
     * Single atomic statement: concludes the delivery ONLY if it has no unchecked item.
     * Returns the number of updated rows (1 = concluded, 0 = not found OR items pending).
     * Business invariant lives in SQL so no caller (UI, NLP, tests) can bypass it.
     */
    @Query(
        "UPDATE entrega SET status = 'Concluída', sincronizada = 0, horarioConclusao = :timestamp " +
            "WHERE id = :id AND NOT EXISTS " +
            "(SELECT 1 FROM item_pedido WHERE entregaId = :id AND conferido = 0)"
    )
    suspend fun concluirEntregaSeConferida(id: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM item_pedido WHERE entregaId = :id AND conferido = 0")
    suspend fun contarItensPendentes(id: String): Int

    /** Outbox payload: pending deliveries together with their items (full state for the server). */
    @Transaction
    @Query("SELECT * FROM entrega WHERE sincronizada = 0")
    suspend fun listarPendentes(): List<EntregaComProdutosEntity>

    /**
     * Seed in ONE transaction (all-or-nothing). A crash between the two inserts can no longer
     * leave deliveries without items behind the `contarEntregas() > 0` guard.
     */
    @Transaction
    suspend fun semearSeVazio(entregas: List<EntregaEntity>, itens: List<ItemPedidoEntity>): Boolean {
        if (contarEntregas() > 0) return false
        inserirTodas(entregas)
        inserirItens(itens)
        return true
    }

    @Query("UPDATE entrega SET sincronizada = 1 WHERE uuid = :uuid")
    suspend fun marcarSincronizadaPorUuid(uuid: String)

    @Query("UPDATE entrega SET sincronizada = 1 WHERE sincronizada = 0")
    suspend fun marcarTodasSincronizadas()

    @Query("UPDATE item_pedido SET conferido = :conferido WHERE id = :itemId")
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)

    /** Outbox: flags the parent delivery as pending sync (sincronizada = 0) given one of its items. */
    @Query(
        "UPDATE entrega SET sincronizada = 0 " +
            "WHERE id = (SELECT entregaId FROM item_pedido WHERE id = :itemId)"
    )
    suspend fun marcarEntregaPaiComoPendente(itemId: String)

    /**
     * Atomically updates the item check flag AND flags the parent delivery as pending sync.
     * Without the transaction a crash between the two writes would leave the change outside the outbox.
     */
    @Transaction
    suspend fun atualizarConferidoMarcandoPendente(itemId: String, conferido: Boolean) {
        atualizarConferido(itemId, conferido)
        marcarEntregaPaiComoPendente(itemId)
    }
}
