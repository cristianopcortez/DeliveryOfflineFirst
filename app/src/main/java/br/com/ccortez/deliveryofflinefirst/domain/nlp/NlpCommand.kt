package br.com.ccortez.deliveryofflinefirst.domain.nlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class NlpAction {
    @SerialName("SET_SEARCH_QUERY")
    SET_SEARCH_QUERY,

    @SerialName("CONCLUDE_DELIVERY")
    CONCLUDE_DELIVERY,

    @SerialName("CONFERIR_ITEM")
    CONFERIR_ITEM,

    @SerialName("UNKNOWN")
    UNKNOWN
}

@Serializable
data class NlpCommand(
    val action: NlpAction,

    /** Populated only when action == SET_SEARCH_QUERY. */
    @SerialName("search_term")
    val searchTerm: String? = null,

    /** Populated when action == CONCLUDE_DELIVERY or CONFERIR_ITEM (optional for CONFERIR_ITEM). */
    @SerialName("target_client")
    val targetClient: String? = null,

    /** Populated only when action == CONFERIR_ITEM. The product keyword to match against item names. */
    @SerialName("target_item")
    val targetItem: String? = null,

    /** Populated only when action == CONFERIR_ITEM. true = check, false = uncheck. */
    @SerialName("item_conferido_state")
    val itemConferidoState: Boolean? = null,

    /**
     * Set to true when [action] is UNKNOWN due to a technical failure rather than genuine
     * intent ambiguity. Not serialized — populated only by `NlpRepositoryImpl.fallback()`.
     */
    @Transient
    val isError: Boolean = false
)
