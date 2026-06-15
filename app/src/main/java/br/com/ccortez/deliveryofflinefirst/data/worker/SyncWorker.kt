package br.com.ccortez.deliveryofflinefirst.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlin.random.Random

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: EntregaRepository
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val pendentes = repository.listarPendentes()

            if (pendentes.isEmpty()) {
                Log.d(TAG, "No pending deliveries — nothing to sync")
                return Result.success()
            }

            Log.d(TAG, "Syncing ${pendentes.size} pending deliveries...")
            delay(2_000) // simulates network latency

            if (Random.nextFloat() < 0.3f) {
                // 30% random failure — WorkManager retries with exponential backoff.
                // Deliveries stay in the outbox (sincronizada=0) and are retried intact.
                Log.w(TAG, "Simulated network failure — scheduling retry")
                Result.retry()
            } else {
                // Happy path: process each delivery individually.
                // In a real app: api.enviar(payload, idempotencyKey = entrega.uuid)
                // The server deduplicates by UUID — if this Worker runs twice due to a
                // crash after POST but before the next line, the second POST is a no-op.
                pendentes.forEach { entrega ->
                    Log.d(TAG, "POST /entregas idempotency-key=${entrega.uuid}")
                    // Mark synced only AFTER the simulated ACK — outbox contract:
                    // entry stays in queue until the server confirms receipt.
                    repository.marcarSincronizadaPorUuid(entrega.uuid)
                }

                Log.d(TAG, "Sync complete — ${pendentes.size} deliveries synced")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.retry()
        }
    }
}
