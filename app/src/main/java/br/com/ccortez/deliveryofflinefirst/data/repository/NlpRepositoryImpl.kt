package br.com.ccortez.deliveryofflinefirst.data.repository

import android.util.Log
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import com.google.firebase.ai.GenerativeModel
import kotlinx.serialization.json.Json

class NlpRepositoryImpl(
    private val model: GenerativeModel
) : NlpRepository {

    // ignoreUnknownKeys: tolerates extra fields the model may add in future schema versions
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun interpretarComando(comando: String): NlpCommand {
        return try {
            val response = model.generateContent(comando)

            val rawJson = response.text
                ?: return fallback("Resposta nula do modelo para o comando: $comando")

            json.decodeFromString<NlpCommand>(rawJson.trim())
        } catch (e: kotlinx.serialization.SerializationException) {
            // Model returned text that is not valid JSON (e.g. a markdown-wrapped response)
            fallback("Falha ao desserializar resposta JSON: ${e.message}")
        } catch (e: Exception) {
            // Covers: network timeout, FirebaseException, API quota exceeded, etc.
            fallback("Erro ao chamar o modelo de IA: ${e.message}")
        }
    }

    private fun fallback(reason: String): NlpCommand {
        Log.w(TAG, reason)
        return NlpCommand(action = NlpAction.UNKNOWN)
    }

    companion object {
        private const val TAG = "NlpRepositoryImpl"
    }
}
