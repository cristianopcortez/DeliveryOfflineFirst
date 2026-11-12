package br.com.ccortez.deliveryofflinefirst.data.seed

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido

/** Demo catalog for the first install. Lives in data so the ViewModel only asks the repository to seed. */
object EntregaSeed {
    val entregas = listOf(
        Entrega("seed-1", "Ana Paula Ferreira", "Rua das Flores, 123 — Jardim Primavera", "Pendente"),
        Entrega("seed-2", "Carlos Lima", "Av. Brasil, 456 — Centro", "Em rota"),
        Entrega("seed-3", "João Silva", "Rua do Comércio, 789 — Vila Industrial", "Pendente"),
        Entrega("seed-4", "Maria Souza", "Travessa Azul, 12 — Bairro Novo", "Concluída", sincronizada = false),
        Entrega("seed-5", "Roberto Alves", "Alameda Santos, 201 — Higienópolis", "Pendente"),
        Entrega("seed-6", "Fernanda Costa", "Rua XV de Novembro, 88 — Centro", "Em rota"),
        Entrega("seed-7", "Lucas Mendes", "Estrada da Saudade, 50 — Zona Rural", "Pendente"),
    )

    val itens: Map<String, List<ItemPedido>> = mapOf(
        "seed-1" to listOf(
            ItemPedido("seed-1-a", "Tênis Nike Air Max 270 (tam. 38)", 1, false),
            ItemPedido("seed-1-b", "Mochila Escolar Estampada", 2, false),
            ItemPedido("seed-1-c", "Protetor Solar FPS 70 — 200ml", 3, false),
            ItemPedido("seed-1-d", "Caixa de Papelão 50×40×30 cm", 1, false),
        ),
        "seed-2" to listOf(
            ItemPedido("seed-2-a", "Notebook Dell XPS 15 (i7 / 32GB)", 1, false),
            ItemPedido("seed-2-b", "Carregador Universal 65W USB-C", 1, false),
            ItemPedido("seed-2-c", "Mouse Logitech MX Master 3", 1, false),
            ItemPedido("seed-2-d", "Teclado Mecânico Keychron K6", 1, false),
            ItemPedido("seed-2-e", "Hub USB-C 7 portas", 2, false),
            ItemPedido("seed-2-f", "Caixa de Papelão 50×40×30 cm", 4, false),
        ),
        "seed-3" to listOf(
            ItemPedido("seed-3-a", "Livro: Clean Architecture (Uncle Bob)", 2, false),
            ItemPedido("seed-3-b", "Livro: Kotlin in Action (2ª Ed.)", 1, false),
            ItemPedido("seed-3-c", "Caderno Universitário 10 matérias", 3, false),
            ItemPedido("seed-3-d", "Caneta Pilot G2 Preta (cx. 12 un.)", 1, false),
            ItemPedido("seed-3-e", "Post-it 76×76mm — bloco colorido", 4, false),
            ItemPedido("seed-3-f", "Marca-texto Stabilo Ponto 68 (6 cores)", 2, false),
        ),
        "seed-4" to listOf(
            ItemPedido("seed-4-a", "Fone Sony WH-1000XM5 (preto)", 1, true),
            ItemPedido("seed-4-b", "Cabo USB-C → 3.5mm Adaptador", 2, true),
            ItemPedido("seed-4-c", "Capinha Silicone Sony WH-1000XM5", 1, true),
        ),
        "seed-5" to listOf(
            ItemPedido("seed-5-a", "Dipirona Sódica 500mg — 20 comp.", 2, false),
            ItemPedido("seed-5-b", "Vitamina C 1000mg Efervescente (30 un.)", 1, false),
            ItemPedido("seed-5-c", "Álcool Gel 70% — frasco 500ml", 3, false),
            ItemPedido("seed-5-d", "Termômetro Digital Axilar", 1, false),
        ),
        "seed-6" to listOf(
            ItemPedido("seed-6-a", "Jaqueta Corta-Vento Feminina (M)", 1, false),
            ItemPedido("seed-6-b", "Calça Legging Supplex (P)", 2, false),
            ItemPedido("seed-6-c", "Meias Esportivas Cano Médio (kit 3)", 2, false),
        ),
        "seed-7" to listOf(
            ItemPedido("seed-7-a", "Arroz Branco Tipo 1 — 5kg", 2, false),
            ItemPedido("seed-7-b", "Feijão Carioca — 1kg", 3, false),
            ItemPedido("seed-7-c", "Azeite Extravirgem — 500ml", 2, false),
            ItemPedido("seed-7-d", "Café Torrado e Moído — 500g", 4, false),
            ItemPedido("seed-7-e", "Açúcar Cristal — 1kg", 2, false),
        ),
    )
}
