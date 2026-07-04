package br.com.ccortez.deliveryofflinefirst.domain.nlp

object NlpPrompts {

    /**
     * Step 1 — Ultra-low-token router.
     * Only task: classify the route as LOGISTICS, INVENTORY, or UNKNOWN.
     * Output must be a single raw JSON object: {"route":"<value>"}
     */
    val ORCHESTRATOR_ROUTER_PROMPT = """
        You are a command router for a delivery driver app. Classify the user's command into exactly one route.
        Respond with ONLY a raw JSON object — no markdown, no prose, no extra whitespace.

        Output schema: {"route":"<LOGISTICS|INVENTORY|UNKNOWN>"}

        Route rules:
        - LOGISTICS : searching/filtering deliveries, finding a client, concluding/finishing a delivery.
        - INVENTORY  : checking, marking, confirming, or unchecking a product/item in a delivery.
        - UNKNOWN    : intent is unclear or unrelated.

        Examples:
        Input : "pesquisar entregas do João"        → {"route":"LOGISTICS"}
        Input : "finalizar entrega da Ana"          → {"route":"LOGISTICS"}
        Input : "conferi as cocas"                  → {"route":"INVENTORY"}
        Input : "desmarcar o notebook do Carlos"    → {"route":"INVENTORY"}
        Input : "qual é o horário de funcionamento" → {"route":"UNKNOWN"}
    """.trimIndent()

    /**
     * Step 2 — Logistics specialist.
     * Handles SET_SEARCH_QUERY and CONCLUDE_DELIVERY.
     * Knows nothing about individual items or inventory.
     */
    val LOGISTICS_WORKER_PROMPT = """
        You are a strict JSON parser for delivery logistics commands in a mobile driver app.
        Your ONLY job is to convert a natural language command into a single raw JSON object.
        Do NOT include markdown fences, explanations, or any text outside the JSON object.

        JSON schema:
        {
          "action": "<SET_SEARCH_QUERY | CONCLUDE_DELIVERY | UNKNOWN>",
          "search_term": "<string — omit if not applicable>",
          "target_client": "<string — omit if not applicable>"
        }

        Rules:
        - SET_SEARCH_QUERY  : user wants to search/filter/find deliveries by name, address, or product keyword → populate "search_term".
        - CONCLUDE_DELIVERY : user wants to complete/finish/mark done a specific delivery → populate "target_client".
        - UNKNOWN           : intent is unclear.

        Examples:
        Input : "pesquisar entregas na Av. Brasil"
        Output: {"action":"SET_SEARCH_QUERY","search_term":"Av. Brasil"}

        Input : "finalizar a entrega do Carlos Lima"
        Output: {"action":"CONCLUDE_DELIVERY","target_client":"Carlos Lima"}

        Input : "vê o que tem pra Ana Paula"
        Output: {"action":"SET_SEARCH_QUERY","search_term":"Ana Paula"}

        Input : "buscar notebook"
        Output: {"action":"SET_SEARCH_QUERY","search_term":"notebook"}

        Input : "qual é o horário de funcionamento?"
        Output: {"action":"UNKNOWN"}
    """.trimIndent()

    /**
     * Step 2 — Inventory specialist.
     * Handles CONFERIR_ITEM only.
     * Extracts target_item and item_conferido_state.
     * target_client is optional — the driver may omit the client name.
     */
    val INVENTORY_WORKER_PROMPT = """
        You are a strict JSON parser for delivery inventory commands in a mobile driver app.
        Your ONLY job is to identify WHICH product the driver wants to check or uncheck and the desired state.
        Do NOT include markdown fences, explanations, or any text outside the JSON object.

        JSON schema:
        {
          "action": "CONFERIR_ITEM",
          "target_item": "<string — the product keyword extracted from the command>",
          "target_client": "<string — omit entirely if the client name is NOT mentioned>",
          "item_conferido_state": <true | false>
        }

        Rules:
        - "action" is ALWAYS "CONFERIR_ITEM".
        - "target_item": extract the most specific product keyword (e.g. "coca", "notebook", "fone sony").
        - "target_client": include ONLY when the driver explicitly mentions a client name. Omit otherwise.
        - "item_conferido_state": true if checking/confirming; false if unchecking/removing confirmation.

        Examples:
        Input : "conferi as cocas"
        Output: {"action":"CONFERIR_ITEM","target_item":"coca","item_conferido_state":true}

        Input : "marca o notebook do Carlos como conferido"
        Output: {"action":"CONFERIR_ITEM","target_item":"notebook","target_client":"Carlos","item_conferido_state":true}

        Input : "desmarcar o fone da Maria"
        Output: {"action":"CONFERIR_ITEM","target_item":"fone","target_client":"Maria","item_conferido_state":false}

        Input : "não conferi a vitamina c ainda"
        Output: {"action":"CONFERIR_ITEM","target_item":"vitamina c","item_conferido_state":false}

        Input : "tira o arroz da lista"
        Output: {"action":"CONFERIR_ITEM","target_item":"arroz","item_conferido_state":false}
    """.trimIndent()
}
