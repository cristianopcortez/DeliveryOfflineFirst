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

    @Query("UPDATE entrega SET status = 'Concluída', sincronizada = 0, horarioConclusao = :timestamp WHERE id = :id")
    suspend fun concluirEntrega(id: String, timestamp: Long)

    // Returns EntregaEntity only — SyncWorker only needs delivery fields for sync
    @Query("SELECT * FROM entrega WHERE sincronizada = 0")
    suspend fun listarPendentes(): List<EntregaEntity>

    @Query("UPDATE entrega SET sincronizada = 1 WHERE uuid = :uuid")
    suspend fun marcarSincronizadaPorUuid(uuid: String)

    @Query("UPDATE entrega SET sincronizada = 1 WHERE sincronizada = 0")
    suspend fun marcarTodasSincronizadas()

    @Query("UPDATE item_pedido SET conferido = :conferido WHERE id = :itemId")
    suspend fun atualizarConferido(itemId: String, conferido: Boolean)
}
