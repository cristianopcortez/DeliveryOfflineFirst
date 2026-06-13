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

    @Query("UPDATE entrega SET status = 'Concluída', sincronizada = 0 WHERE id = :id")
    suspend fun concluirEntrega(id: String)
}
