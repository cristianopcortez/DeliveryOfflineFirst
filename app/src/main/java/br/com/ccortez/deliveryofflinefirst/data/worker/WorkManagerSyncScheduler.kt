package br.com.ccortez.deliveryofflinefirst.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncEstado
import br.com.ccortez.deliveryofflinefirst.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class WorkManagerSyncScheduler(
    private val workManager: WorkManager
) : SyncScheduler {

    override fun agendar() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    override fun observarEstado(): Flow<SyncEstado?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK)
            .map { infos -> infos.firstOrNull()?.state?.toEstado() }

    private fun WorkInfo.State.toEstado(): SyncEstado? = when (this) {
        WorkInfo.State.RUNNING -> SyncEstado.EM_ANDAMENTO
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncEstado.AGUARDANDO_REDE
        WorkInfo.State.SUCCEEDED -> SyncEstado.CONCLUIDA
        WorkInfo.State.FAILED -> SyncEstado.FALHOU
        else -> null
    }

    companion object {
        const val UNIQUE_WORK = "sync_entregas"

        fun create(context: Context): WorkManagerSyncScheduler =
            WorkManagerSyncScheduler(WorkManager.getInstance(context))
    }
}
