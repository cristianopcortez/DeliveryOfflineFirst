package br.com.ccortez.deliveryofflinefirst.domain.analytics

/** Analytics parameter values used by presentation. Wire names stay stable for existing dashboards. */
object AnalyticsEvents {
    const val TRIGGER_INIT = "init"
    const val TRIGGER_RESUME = "resume"
    const val TRIGGER_MANUAL_RELOAD = "manual_reload"

    const val ACTION_SET_SEARCH_QUERY = "set_search_query"
    const val ACTION_CONCLUDE_DELIVERY = "conclude_delivery"
    const val ACTION_CONFERIR_ITEM = "CONFERIR_ITEM"
    const val ACTION_UNKNOWN = "unknown"
}
