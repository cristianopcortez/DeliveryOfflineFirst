package br.com.ccortez.deliveryofflinefirst.domain.sync

import kotlinx.coroutines.flow.Flow

/** Screen-facing sync state. WorkManager stays behind [SyncScheduler]. */
enum class SyncEstado {
    EM_ANDAMENTO,
    AGUARDANDO_REDE,
    CONCLUIDA,
    FALHOU
}

/** Schedules the outbox drain and exposes its status. Implementation lives in data/. */
interface SyncScheduler {
    fun agendar()
    fun observarEstado(): Flow<SyncEstado?>
}
