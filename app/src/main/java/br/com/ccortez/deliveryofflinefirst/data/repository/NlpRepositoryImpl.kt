package br.com.ccortez.deliveryofflinefirst.data.repository

import android.util.Log
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpPrompts
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Two-stage NLP pipeline:
 *   Stage 1 — Orchestrator: ultra-light call that classifies the route (LOGISTICS / INVENTORY / UNKNOWN).
 *   Stage 2 — Worker:       specialist call that produces the final [NlpCommand] JSON for that route.
 *
 * Each stage gets its own GenerativeModel built with a hyper-focused system instruction,
 * keeping context small and reducing hallucination risk.
 */
class NlpRepositoryImpl : NlpRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Model factory ──────────────────────────────────────────────────────────

    private fun buildModel(systemPrompt: String) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig { responseMimeType = "application/json" },
            systemInstruction = content { text(systemPrompt) }
        )

    // ── Public API ─────────────────────────────────────────────────────────────

    override suspend fun interpretarComando(comando: String): NlpCommand {
        return try {
            // Stage 1: classify route
            val route = detectRoute(comando)

            // Stage 2: dispatch to the correct specialist
            when (route) {
                ROUTE_LOGISTICS -> callWorker(comando, NlpPrompts.LOGISTICS_WORKER_PROMPT)
                ROUTE_INVENTORY -> callWorker(comando, NlpPrompts.INVENTORY_WORKER_PROMPT)
                else            -> fallback("Rota '$route' não reconhecida — retornando UNKNOWN.")
            }
        } catch (e: Exception) {
            // Level-1 safety net: catches anything that escaped stage-level handlers
            fallback("Erro fatal na pipeline NLP [${e::class.simpleName}]: ${e.message}")
        }
    }

    // ── Stage 1: Orchestrator ──────────────────────────────────────────────────

    private suspend fun detectRoute(comando: String): String {
        return try {
            val response = buildModel(NlpPrompts.ORCHESTRATOR_ROUTER_PROMPT)
                .generateContent(comando)

            val rawJson = response.text?.trim()
                ?: run {
                    Log.w(TAG, "Orquestrador retornou resposta nula para: $comando")
                    return ROUTE_UNKNOWN
                }

            // Level-2 JSON parse: extract {"route":"…"}
            json.parseToJsonElement(rawJson)
                .jsonObject["route"]
                ?.jsonPrimitive
                ?.content
                ?: ROUTE_UNKNOWN

        } catch (e: kotlinx.serialization.SerializationException) {
            // Level-3: malformed JSON from the orchestrator
            Log.w(TAG, "Falha ao parsear rota do orquestrador: ${e.message}")
            ROUTE_UNKNOWN
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao chamar orquestrador [${e::class.simpleName}]: ${e.message}")
            ROUTE_UNKNOWN
        }
    }

    // ── Stage 2: Worker ────────────────────────────────────────────────────────

    private suspend fun callWorker(comando: String, systemPrompt: String): NlpCommand {
        return try {
            val response = buildModel(systemPrompt).generateContent(comando)

            val rawJson = response.text?.trim()
                ?: return fallback("Worker retornou resposta nula para: $comando")

            // Level-2 JSON parse: full NlpCommand deserialization
            json.decodeFromString<NlpCommand>(rawJson)

        } catch (e: kotlinx.serialization.SerializationException) {
            // Level-3: worker returned malformed / unexpected JSON structure
            fallback("Falha ao desserializar resposta do worker: ${e.message}")
        } catch (e: Exception) {
            fallback("Erro ao chamar worker [${e::class.simpleName}]: ${e.message}")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun fallback(reason: String): NlpCommand {
        Log.w(TAG, reason)
        return NlpCommand(action = NlpAction.UNKNOWN, isError = true)
    }

    companion object {
        private const val TAG = "NlpRepositoryImpl"
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val ROUTE_LOGISTICS = "LOGISTICS"
        private const val ROUTE_INVENTORY = "INVENTORY"
        private const val ROUTE_UNKNOWN   = "UNKNOWN"
    }
}
