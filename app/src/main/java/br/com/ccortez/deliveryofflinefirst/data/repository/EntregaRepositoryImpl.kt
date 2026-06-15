package br.com.ccortez.deliveryofflinefirst.data.repository

import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EntregaRepositoryImpl(private val dao: EntregaDao) : EntregaRepository {

    override fun observarTodas(): Flow<List<Entrega>> =
        dao.observarTodas().map { list -> list.map { it.toEntrega() } }

    override suspend fun inserirTodas(entregas: List<Entrega>) {
        dao.inserirTodas(entregas.map { it.toEntity() })
    }

    override suspend fun concluirEntrega(id: String) {
        dao.concluirEntrega(id, timestamp = System.currentTimeMillis())
    }

    override suspend fun listarPendentes(): List<Entrega> =
        dao.listarPendentes().map { it.toEntrega() }

    override suspend fun marcarSincronizadaPorUuid(uuid: String) =
        dao.marcarSincronizadaPorUuid(uuid)

    override suspend fun marcarTodasSincronizadas() =
        dao.marcarTodasSincronizadas()

    private fun EntregaEntity.toEntrega() = Entrega(
        id = id,
        cliente = cliente,
        endereco = endereco,
        status = status,
        sincronizada = sincronizada,
        horarioConclusao = horarioConclusao,
        uuid = uuid
    )

    private fun Entrega.toEntity() = EntregaEntity(
        id = id,
        cliente = cliente,
        endereco = endereco,
        status = status,
        sincronizada = sincronizada,
        horarioConclusao = horarioConclusao,
        // UUID is generated here — repository is the "client" in the outbox pattern.
        // The ViewModel and domain layer never need to know about UUID generation.
        uuid = uuid.ifBlank { UUID.randomUUID().toString() }
    )
}
