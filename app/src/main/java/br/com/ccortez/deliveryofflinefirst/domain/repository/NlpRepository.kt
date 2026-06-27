package br.com.ccortez.deliveryofflinefirst.domain.repository

import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand

interface NlpRepository {
    /**
     * Sends a free-text delivery command to the AI model and returns a structured
     * [NlpCommand] representing the parsed intent.
     *
     * This function never throws: network failures, API errors, or JSON parse
     * problems all result in [NlpCommand] with action = UNKNOWN, keeping the
     * caller free of try/catch logic.
     */
    suspend fun interpretarComando(comando: String): NlpCommand
}
