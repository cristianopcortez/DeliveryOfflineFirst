package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unit-test fake for [EntregaRepository].
 *
 * Pre-loads the same seed data used by `EntregasViewModel.popularBancoSeVazio` so that
 * `_uiState.value.entregas` is already populated when `processarComandoNLP()` reads it.
 *
 * [conferidosRegistrados] captures every [atualizarConferido] call in order, allowing
 * `CONFERIR_ITEM` tests to assert:
 *  - that the correct item was updated (Scenario A and C)
 *  - that no write was attempted on an ambiguous match (Scenario B)
 */
class FakeEntregaRepositoryForTest(
    initialData: List<EntregaComProdutos> = SEED_DATA
) : EntregaRepository {

    private val _data = MutableStateFlow(initialData)

    /** Ordered list of (itemId, conferido) pairs written by the ViewModel. */
    val conferidosRegistrados = mutableListOf<Pair<String, Boolean>>()

    override fun observarTodas(): Flow<List<EntregaComProdutos>> = _data.asStateFlow()

    override suspend fun atualizarConferido(itemId: String, conferido: Boolean) {
        conferidosRegistrados.add(itemId to conferido)
    }

    override suspend fun inserirTodas(entregas: List<Entrega>) = Unit
    override suspend fun inserirItens(entregaId: String, itens: List<ItemPedido>) = Unit
    override suspend fun contarEntregas(): Int = _data.value.size
    override suspend fun concluirEntrega(id: String) = Unit
    override suspend fun listarPendentes(): List<Entrega> = emptyList()
    override suspend fun marcarSincronizadaPorUuid(uuid: String) = Unit
    override suspend fun marcarTodasSincronizadas() = Unit

    companion object {

        /**
         * Mirrors the seed defined in `EntregasViewModel.itensSeed` for the two deliveries
         * relevant to `ConferirItemViewModelTest`:
         *
         *  - "Mochila Escolar Estampada" (seed-1-b, Ana Paula) — unique across all deliveries
         *  - "Caixa de Papelão 50×40×30 cm" appears in BOTH seed-1-d (Ana Paula)
         *    AND seed-2-f (Carlos Lima) — intentional ambiguity for Scenario B and C.
         *
         * All 7 seed deliveries and their items are included so the filter pipeline
         * reflects real production behaviour.
         */
        val SEED_DATA: List<EntregaComProdutos> = listOf(
            EntregaComProdutos(
                entrega = Entrega("seed-1", "Ana Paula Ferreira", "Rua das Flores, 123 — Jardim Primavera", "Pendente"),
                itens = listOf(
                    ItemPedido("seed-1-a", "Tênis Nike Air Max 270 (tam. 38)", 1, false),
                    ItemPedido("seed-1-b", "Mochila Escolar Estampada", 2, false),
                    ItemPedido("seed-1-c", "Protetor Solar FPS 70 — 200ml", 3, false),
                    ItemPedido("seed-1-d", "Caixa de Papelão 50×40×30 cm", 1, false),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-2", "Carlos Lima", "Av. Brasil, 456 — Centro", "Em rota"),
                itens = listOf(
                    ItemPedido("seed-2-a", "Notebook Dell XPS 15 (i7 / 32GB)", 1, false),
                    ItemPedido("seed-2-b", "Carregador Universal 65W USB-C", 1, false),
                    ItemPedido("seed-2-c", "Mouse Logitech MX Master 3", 1, false),
                    ItemPedido("seed-2-d", "Teclado Mecânico Keychron K6", 1, false),
                    ItemPedido("seed-2-e", "Hub USB-C 7 portas", 2, false),
                    ItemPedido("seed-2-f", "Caixa de Papelão 50×40×30 cm", 4, false),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-3", "João Silva", "Rua do Comércio, 789 — Vila Industrial", "Pendente"),
                itens = listOf(
                    ItemPedido("seed-3-a", "Livro: Clean Architecture (Uncle Bob)", 2, false),
                    ItemPedido("seed-3-b", "Livro: Kotlin in Action (2ª Ed.)", 1, false),
                    ItemPedido("seed-3-c", "Caderno Universitário 10 matérias", 3, false),
                    ItemPedido("seed-3-d", "Caneta Pilot G2 Preta (cx. 12 un.)", 1, false),
                    ItemPedido("seed-3-e", "Post-it 76×76mm — bloco colorido", 4, false),
                    ItemPedido("seed-3-f", "Marca-texto Stabilo Ponto 68 (6 cores)", 2, false),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-4", "Maria Souza", "Travessa Azul, 12 — Bairro Novo", "Concluída", sincronizada = false),
                itens = listOf(
                    ItemPedido("seed-4-a", "Fone Sony WH-1000XM5 (preto)", 1, true),
                    ItemPedido("seed-4-b", "Cabo USB-C → 3.5mm Adaptador", 2, true),
                    ItemPedido("seed-4-c", "Capinha Silicone Sony WH-1000XM5", 1, true),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-5", "Roberto Alves", "Alameda Santos, 201 — Higienópolis", "Pendente"),
                itens = listOf(
                    ItemPedido("seed-5-a", "Dipirona Sódica 500mg — 20 comp.", 2, false),
                    ItemPedido("seed-5-b", "Vitamina C 1000mg Efervescente (30 un.)", 1, false),
                    ItemPedido("seed-5-c", "Álcool Gel 70% — frasco 500ml", 3, false),
                    ItemPedido("seed-5-d", "Termômetro Digital Axilar", 1, false),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-6", "Fernanda Costa", "Rua XV de Novembro, 88 — Centro", "Em rota"),
                itens = listOf(
                    ItemPedido("seed-6-a", "Jaqueta Corta-Vento Feminina (M)", 1, false),
                    ItemPedido("seed-6-b", "Calça Legging Supplex (P)", 2, false),
                    ItemPedido("seed-6-c", "Meias Esportivas Cano Médio (kit 3)", 2, false),
                )
            ),
            EntregaComProdutos(
                entrega = Entrega("seed-7", "Lucas Mendes", "Estrada da Saudade, 50 — Zona Rural", "Pendente"),
                itens = listOf(
                    ItemPedido("seed-7-a", "Arroz Branco Tipo 1 — 5kg", 2, false),
                    ItemPedido("seed-7-b", "Feijão Carioca — 1kg", 3, false),
                    ItemPedido("seed-7-c", "Azeite Extravirgem — 500ml", 2, false),
                    ItemPedido("seed-7-d", "Café Torrado e Moído — 500g", 4, false),
                    ItemPedido("seed-7-e", "Açúcar Cristal — 1kg", 2, false),
                )
            ),
        )
    }
}
