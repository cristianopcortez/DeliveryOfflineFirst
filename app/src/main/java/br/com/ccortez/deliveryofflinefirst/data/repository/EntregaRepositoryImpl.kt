package br.com.ccortez.deliveryofflinefirst.data.repository

import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EntregaRepositoryImpl(private val dao: EntregaDao) : EntregaRepository {

    override fun observarTodas(): Flow<List<Entrega>> =
        dao.observarTodas().map { list -> list.map { it.toEntrega() } }

    override suspend fun inserirTodas(entregas: List<Entrega>) {
        dao.inserirTodas(entregas.map { it.toEntity() })
    }

    override suspend fun concluirEntrega(id: String) {
        dao.concluirEntrega(id, timestamp = System.currentTimeMillis())
    }

    override suspend fun marcarTodasSincronizadas() {
        dao.marcarTodasSincronizadas()
    }

    private fun EntregaEntity.toEntrega() = Entrega(
        id = id,
        cliente = cliente,
        endereco = endereco,
        status = status,
        sincronizada = sincronizada,
        horarioConclusao = horarioConclusao
    )

    private fun Entrega.toEntity() = EntregaEntity(
        id = id,
        cliente = cliente,
        endereco = endereco,
        status = status,
        sincronizada = sincronizada,
        horarioConclusao = horarioConclusao
    )
}
