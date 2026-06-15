package br.com.ccortez.deliveryofflinefirst.domain.repository

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import kotlinx.coroutines.flow.Flow

interface EntregaRepository {
    fun observarTodas(): Flow<List<Entrega>>
    suspend fun inserirTodas(entregas: List<Entrega>)
    suspend fun concluirEntrega(id: String)
    suspend fun listarPendentes(): List<Entrega>
    suspend fun marcarSincronizadaPorUuid(uuid: String)
    suspend fun marcarTodasSincronizadas()
}
