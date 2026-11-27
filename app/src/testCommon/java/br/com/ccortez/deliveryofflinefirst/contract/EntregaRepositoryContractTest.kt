package br.com.ccortez.deliveryofflinefirst.contract

import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.model.ResultadoConclusao
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LSP contract for [EntregaRepository.concluirEntrega]: every implementation — production
 * (`EntregaRepositoryImpl`, Room-backed) and every `Fake` in `test/` and `androidTest/` — MUST
 * honor the same invariant documented on the interface: a delivery is NEVER concluded while any
 * of its items is unchecked ([ItemPedido.conferido] == false).
 *
 * Lives in `src/testCommon` so the exact same test bodies run unmodified in both the JVM unit
 * test tree (against the Robolectric-backed Room implementation and `FakeEntregaRepositoryForTest`)
 * and the instrumented tree (against the real on-device Room implementation and
 * `FakeEntregaRepository`). A fake that regresses this invariant fails here before it ever
 * reaches a ViewModel test that assumes the contract holds.
 *
 * Found violating this exact contract: `FakeEntregaRepositoryForTest.concluirEntrega` used to
 * unconditionally return [ResultadoConclusao.CONCLUIDA] — see PR "code-review-solid-lsp".
 */
abstract class EntregaRepositoryContractTest {

    /** Builds a fresh, isolated implementation seeded with exactly one delivery [entregaId] holding [itens]. */
    protected abstract suspend fun repositorioCom(entregaId: String, itens: List<ItemPedido>): EntregaRepository

    /** Builds a fresh, isolated implementation with no deliveries at all. */
    protected abstract suspend fun repositorioVazio(): EntregaRepository

    @Test
    fun concluirEntrega_recusaQuandoHaItemPendente() = runBlocking {
        val repo = repositorioCom(
            entregaId = "contrato-1",
            itens = listOf(
                ItemPedido("contrato-1-a", "Item A", 1, conferido = true),
                ItemPedido("contrato-1-b", "Item B", 1, conferido = false)
            )
        )

        assertEquals(
            "NEVER conclude with a pending item (LSP postcondition shared by every implementation).",
            ResultadoConclusao.ITENS_PENDENTES,
            repo.concluirEntrega("contrato-1")
        )
    }

    @Test
    fun concluirEntrega_concluiQuandoTodosItensConferidos() = runBlocking {
        val repo = repositorioCom(
            entregaId = "contrato-2",
            itens = listOf(ItemPedido("contrato-2-a", "Item A", 1, conferido = true))
        )

        assertEquals(
            ResultadoConclusao.CONCLUIDA,
            repo.concluirEntrega("contrato-2")
        )
    }

    @Test
    fun concluirEntrega_naoEncontradaQuandoIdInexistente() = runBlocking {
        val repo = repositorioVazio()

        assertEquals(
            ResultadoConclusao.NAO_ENCONTRADA,
            repo.concluirEntrega("id-que-nao-existe")
        )
    }
}
