package br.com.ccortez.deliveryofflinefirst.data.repository

import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.data.local.ItemPedidoEntity
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EntregaRepositoryImpl(private val dao: EntregaDao) : EntregaRepository {

    override fun observarTodas(): Flow<List<EntregaComProdutos>> =
        dao.observarTodas().map { list ->
            list.map { entity ->
                EntregaComProdutos(
                    entrega = entity.entrega.toEntrega(),
                    itens = entity.itens.map { it.toItemPedido() }
                )
            }
        }

    override suspend fun inserirTodas(entregas: List<Entrega>) {
        dao.inserirTodas(entregas.map { it.toEntity() })
    }

    override suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>) {
        dao.inserirItens(itens.map { it.toEntity(entregaId) })
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

    override suspend fun atualizarConferido(itemId: String, conferido: Boolean) =
        dao.atualizarConferido(itemId, conferido)

    override suspend fun contarEntregas(): Int =
        dao.contarEntregas()

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun EntregaEntity.toEntrega() = Entrega(
        id = id, cliente = cliente, endereco = endereco,
        status = status, sincronizada = sincronizada,
        horarioConclusao = horarioConclusao, uuid = uuid
    )

    private fun Entrega.toEntity() = EntregaEntity(
        id = id, cliente = cliente, endereco = endereco,
        status = status, sincronizada = sincronizada,
        horarioConclusao = horarioConclusao,
        uuid = uuid.ifBlank { UUID.randomUUID().toString() }
    )

    private fun ItemPedidoEntity.toItemPedido() = ItemPedido(
        id = id, nome = nome, quantidade = quantidade, conferido = conferido
    )

    private fun ItemPedido.toEntity(entregaId: String) = ItemPedidoEntity(
        id = id, entregaId = entregaId,
        nome = nome, quantidade = quantidade, conferido = conferido
    )
}
