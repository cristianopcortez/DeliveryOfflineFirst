package br.com.ccortez.deliveryofflinefirst.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntregaDao {
    @Query("SELECT * FROM entrega ORDER BY cliente ASC")
    fun observarTodas(): Flow<List<EntregaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(entregas: List<EntregaEntity>)

    @Query("UPDATE entrega SET status = 'Concluída', sincronizada = 0, horarioConclusao = :timestamp WHERE id = :id")
    suspend fun concluirEntrega(id: String, timestamp: Long)

    // Returns a snapshot (not a Flow) — the Worker reads once, processes, then finishes
    @Query("SELECT * FROM entrega WHERE sincronizada = 0")
    suspend fun listarPendentes(): List<EntregaEntity>

    // Marks a single delivery as synced by its client-generated UUID.
    // Called only after the server ACKs — so a mid-flight network drop
    // leaves the entry in the outbox and WorkManager retries it.
    @Query("UPDATE entrega SET sincronizada = 1 WHERE uuid = :uuid")
    suspend fun marcarSincronizadaPorUuid(uuid: String)

    @Query("UPDATE entrega SET sincronizada = 1 WHERE sincronizada = 0")
    suspend fun marcarTodasSincronizadas()
}
