package br.com.ccortez.deliveryofflinefirst.domain.model

/**
 * Outcome of trying to conclude a delivery.
 *
 * Business invariant (enforced in the data layer, so UI and NLP paths cannot bypass it):
 * a delivery can only be concluded when ALL of its items are checked ([ItemPedido.conferido]).
 */
enum class ResultadoConclusao {
    CONCLUIDA,
    ITENS_PENDENTES,
    NAO_ENCONTRADA
}
