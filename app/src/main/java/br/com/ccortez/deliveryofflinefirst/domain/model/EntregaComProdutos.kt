package br.com.ccortez.deliveryofflinefirst.domain.model

/**
 * Wraps a [Entrega] together with its associated item list.
 * Produced by the repository from the Room POJO [EntregaComProdutosEntity][br.com.ccortez.deliveryofflinefirst.data.local.EntregaComProdutosEntity];
 * consumed directly by the ViewModel and the UI without further transformations.
 */
data class EntregaComProdutos(
    val entrega: Entrega,
    val itens: List<ItemPedido>
)
