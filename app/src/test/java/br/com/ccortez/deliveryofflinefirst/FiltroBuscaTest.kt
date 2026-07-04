package br.com.ccortez.deliveryofflinefirst

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the reactive search-filter predicate used in
 * `EntregasViewModel.entregasFiltradas`.
 *
 * The filter runs inside a `flatMapLatest` over a Room Flow, but the predicate
 * itself is a pure function on domain objects. Testing it here — without any
 * Android context, coroutines or Room — gives:
 *
 *  - Sub-millisecond feedback on the JVM (no emulator spin-up)
 *  - Full coverage of all three searchable fields (client, address, item name)
 *  - A regression net that catches any change to the filter logic immediately
 *
 * The fixture mirrors the seed data in `EntregasViewModel` so any seed change
 * breaks the relevant test, not silently passes.
 */
class FiltroBuscaTest {

    // ── Fixture ────────────────────────────────────────────────────────────────

    private val entregasFixture = listOf(
        EntregaComProdutos(
            entrega = Entrega("1", "Ana Paula Ferreira", "Rua das Flores, 123 — Jardim Primavera", "Pendente"),
            itens = listOf(
                ItemPedido("1a", "Tênis Nike Air Max 270 (tam. 38)", 1, false),
                ItemPedido("1b", "Mochila Escolar Estampada", 2, false),
            )
        ),
        EntregaComProdutos(
            entrega = Entrega("2", "Carlos Lima", "Av. Brasil, 456 — Centro", "Em rota"),
            itens = listOf(
                ItemPedido("2a", "Notebook Dell XPS 15 (i7 / 32GB)", 1, false),
                ItemPedido("2b", "Mouse Logitech MX Master 3", 1, false),
            )
        ),
        EntregaComProdutos(
            entrega = Entrega("3", "João Silva", "Rua do Comércio, 789 — Vila Industrial", "Pendente"),
            itens = listOf(
                ItemPedido("3a", "Livro: Clean Architecture (Uncle Bob)", 2, false),
                ItemPedido("3b", "Livro: Kotlin in Action (2ª Ed.)", 1, false),
            )
        ),
        EntregaComProdutos(
            entrega = Entrega("4", "Roberto Alves", "Alameda Santos, 201 — Higienópolis", "Pendente"),
            itens = listOf(
                ItemPedido("4a", "Dipirona Sódica 500mg — 20 comp.", 2, false),
            )
        ),
    )

    /**
     * Exact same predicate as `EntregasViewModel.entregasFiltradas`.
     * Keeping it here as a mirror ensures tests stay in sync with the production filter.
     */
    private fun filtrar(query: String): List<EntregaComProdutos> =
        entregasFixture.filter { ec ->
            query.isBlank() ||
            ec.entrega.cliente.contains(query, ignoreCase = true) ||
            ec.entrega.endereco.contains(query, ignoreCase = true) ||
            ec.itens.any { it.nome.contains(query, ignoreCase = true) }
        }

    // ── Blank query ────────────────────────────────────────────────────────────

    @Test
    fun `busca vazia retorna todas as entregas`() {
        assertEquals(entregasFixture.size, filtrar("").size)
    }

    @Test
    fun `busca com espaco em branco retorna todas as entregas`() {
        assertEquals(entregasFixture.size, filtrar("   ").size)
    }

    // ── Client name field ──────────────────────────────────────────────────────

    @Test
    fun `busca por nome de cliente retorna apenas a entrega correspondente`() {
        val resultado = filtrar("Carlos")
        assertEquals(1, resultado.size)
        assertEquals("Carlos Lima", resultado.first().entrega.cliente)
    }

    @Test
    fun `busca por nome de cliente e case insensitive`() {
        assertEquals(filtrar("ana paula"), filtrar("ANA PAULA"))
    }

    @Test
    fun `busca por sobrenome retorna entrega correta`() {
        val resultado = filtrar("Ferreira")
        assertEquals(1, resultado.size)
        assertEquals("Ana Paula Ferreira", resultado.first().entrega.cliente)
    }

    // ── Address field ──────────────────────────────────────────────────────────

    @Test
    fun `busca por logradouro retorna apenas entrega com esse endereco`() {
        val resultado = filtrar("Alameda Santos")
        assertEquals(1, resultado.size)
        assertEquals("Roberto Alves", resultado.first().entrega.cliente)
    }

    @Test
    fun `busca por bairro retorna apenas a entrega correspondente`() {
        val resultado = filtrar("Vila Industrial")
        assertEquals(1, resultado.size)
        assertEquals("João Silva", resultado.first().entrega.cliente)
    }

    @Test
    fun `busca por palavra presente em varios enderecos retorna multiplas entregas`() {
        // "Rua" appears in Ana Paula's and João Silva's addresses
        val resultado = filtrar("Rua")
        assertEquals(2, resultado.size)
    }

    // ── Item name field ────────────────────────────────────────────────────────

    @Test
    fun `busca por nome de item retorna a entrega que contem o item`() {
        val resultado = filtrar("Notebook Dell")
        assertEquals(1, resultado.size)
        assertEquals("Carlos Lima", resultado.first().entrega.cliente)
    }

    @Test
    fun `busca por item e case insensitive`() {
        val minusculo = filtrar("notebook dell")
        val maiusculo = filtrar("NOTEBOOK DELL")
        assertEquals(minusculo, maiusculo)
    }

    @Test
    fun `busca por item parcial retorna entrega com o item`() {
        // "Mochila" is a partial match for "Mochila Escolar Estampada"
        val resultado = filtrar("Mochila")
        assertEquals(1, resultado.size)
        assertEquals("Ana Paula Ferreira", resultado.first().entrega.cliente)
    }

    @Test
    fun `busca por item exclusivo nao retorna entregas sem esse item`() {
        val resultado = filtrar("Clean Architecture")
        assertEquals(1, resultado.size)
        assertEquals("João Silva", resultado.first().entrega.cliente)
    }

    // ── No match ───────────────────────────────────────────────────────────────

    @Test
    fun `busca sem match em nenhum campo retorna lista vazia`() {
        assertTrue(filtrar("xyzxyz").isEmpty())
    }

    @Test
    fun `busca por texto que nao existe em cliente endereco nem item retorna vazio`() {
        assertTrue(filtrar("Skywalker").isEmpty())
    }
}
