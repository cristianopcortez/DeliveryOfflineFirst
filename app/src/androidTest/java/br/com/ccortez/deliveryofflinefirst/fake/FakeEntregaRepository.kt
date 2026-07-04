package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for [EntregaRepository].
 *
 * Pre-seeded with one delivery that has **no items** — `EntregasViewModel.popularBancoSeVazio`
 * sees [contarEntregas] > 0 and skips inserting the production seed, so the fake data is
 * the only data in the test. Because the item list is empty, `todosItensConferidos` evaluates
 * to `true` via vacuous truth and the "Concluir Entrega" button is visible immediately —
 * no checkbox interaction needed.
 *
 * [concluirEntrega] updates the in-memory [MutableStateFlow] reactively, mirroring how the
 * real Room-backed implementation triggers a new Flow emission.
 */
class FakeEntregaRepository : EntregaRepository {

    private val _entregas = MutableStateFlow(
        listOf(
            EntregaComProdutos(
                entrega = Entrega(
                    id = "fake-1",
                    cliente = "Cliente Fake",
                    endereco = "Rua dos Testes, 1",
                    status = "Pendente"
                ),
                itens = emptyList()
            )
        )
    )

    override fun observarTodas(): Flow<List<EntregaComProdutos>> = _entregas.asStateFlow()

    override suspend fun inserirTodas(entregas: List<Entrega>) = Unit

    override suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>) = Unit

    override suspend fun contarEntregas(): Int = _entregas.value.size

    override suspend fun concluirEntrega(id: String) {
        _entregas.value = _entregas.value.map { ec ->
            if (ec.entrega.id == id)
                ec.copy(entrega = ec.entrega.copy(status = "Concluída"))
            else ec
        }
    }

    override suspend fun listarPendentes(): List<Entrega> =
        _entregas.value.map { it.entrega }.filter { it.status != "Concluída" }

    override suspend fun marcarSincronizadaPorUuid(uuid: String) = Unit

    override suspend fun marcarTodasSincronizadas() = Unit

    override suspend fun atualizarConferido(itemId: String, conferido: Boolean) = Unit
}
