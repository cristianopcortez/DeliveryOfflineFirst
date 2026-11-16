package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncEstado
import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** No-op scheduler for UI tests that uninstall [br.com.ccortez.deliveryofflinefirst.di.AppModule]. */
class FakeSyncScheduler : SyncScheduler {
    override fun agendar() = Unit
    override fun observarEstado(): Flow<SyncEstado?> = flowOf(null)
}
