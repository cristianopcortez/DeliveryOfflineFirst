package br.com.ccortez.deliveryofflinefirst.domain.nlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NlpAction {
    @SerialName("SET_SEARCH_QUERY")
    SET_SEARCH_QUERY,

    @SerialName("CONCLUDE_DELIVERY")
    CONCLUDE_DELIVERY,

    @SerialName("UNKNOWN")
    UNKNOWN
}

@Serializable
data class NlpCommand(
    val action: NlpAction,

    /** Populated only when action == SET_SEARCH_QUERY.
     *  Injected directly into EntregasViewModel.onSearchQueryChange(). */
    @SerialName("search_term")
    val searchTerm: String? = null,

    /** Populated only when action == CONCLUDE_DELIVERY.
     *  The app must resolve this name against the live list to obtain the entrega ID
     *  before calling EntregasViewModel.concluirEntrega(id). */
    @SerialName("target_client")
    val targetClient: String? = null
)
