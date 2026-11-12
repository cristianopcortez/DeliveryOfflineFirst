package br.com.ccortez.deliveryofflinefirst.data.repository

import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaComProdutosEntity
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.data.local.ItemPedidoEntity
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.model.ResultadoConclusao
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EntregaRepositoryImpl(private val dao: EntregaDao) : EntregaRepository {

    override fun observarTodas(): Flow<List<EntregaComProdutos>> =
        dao.observarTodas().map { list -> list.map { it.toDomain() } }

    override suspend fun inserirTodas(entregas: List<Entrega>) {
        dao.inserirTodas(entregas.map { it.toEntity() })
    }

    override suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>) {
        dao.inserirItens(itens.map { it.toEntity(entregaId) })
    }

    override suspend fun concluirEntrega(id: String): ResultadoConclusao {
        val atualizadas = dao.concluirEntregaSeConferida(id, timestamp = System.currentTimeMillis())
        return when {
            atualizadas > 0 -> ResultadoConclusao.CONCLUIDA
            dao.contarItensPendentes(id) > 0 -> ResultadoConclusao.ITENS_PENDENTES
            else -> ResultadoConclusao.NAO_ENCONTRADA
        }
    }

    override suspend fun listarPendentes(): List<EntregaComProdutos> =
        dao.listarPendentes().map { it.toDomain() }

    override suspend fun semearSeVazio(
        entregas: List<Entrega>,
        itens: Map<String, List<ItemPedido>>
    ): Boolean = dao.semearSeVazio(
        entregas = entregas.map { it.toEntity() },
        itens = itens.flatMap { (entregaId, lista) -> lista.map { it.toEntity(entregaId) } }
    )

    override suspend fun marcarSincronizadaPorUuid(uuid: String) =
        dao.marcarSincronizadaPorUuid(uuid)

    override suspend fun marcarTodasSincronizadas() =
        dao.marcarTodasSincronizadas()

    override suspend fun atualizarConferido(itemId: String, conferido: Boolean) =
        dao.atualizarConferidoMarcandoPendente(itemId, conferido)

    override suspend fun contarEntregas(): Int =
        dao.contarEntregas()

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun EntregaComProdutosEntity.toDomain() = EntregaComProdutos(
        entrega = entrega.toEntrega(),
        itens = itens.map { it.toItemPedido() }
    )

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
