package com.gytxtx.openjbd.history

import android.util.Log
import com.gytxtx.openjbd.data.BmsRepository
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class HistoryRecorder internal constructor(
    private val repository: BmsRepository,
    private val store: HistoryStore,
    private val clock: () -> Long,
    private val dispatcher: CoroutineDispatcher,
    private val sampleIntervalMillis: Long,
    private val pruneIntervalMillis: Long,
    private val reportError: (String, Throwable) -> Unit
) {
    @Inject
    constructor(repository: BmsRepository, store: SqliteHistoryStore) : this(
        repository = repository,
        store = store,
        clock = System::currentTimeMillis,
        dispatcher = Dispatchers.Default,
        sampleIntervalMillis = HistoryPolicy.SAMPLE_INTERVAL_MILLIS,
        pruneIntervalMillis = HistoryPolicy.PRUNE_INTERVAL_MILLIS,
        reportError = { message, error -> Log.w(TAG, message, error) }
    )

    private val started = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return

        scope.launch(dispatcher) {
            while (currentCoroutineContext().isActive) {
                delay(sampleIntervalMillis)
                recordCurrentSnapshot()
            }
        }
        scope.launch(dispatcher) {
            pruneExpiredSamples()
            while (currentCoroutineContext().isActive) {
                delay(pruneIntervalMillis)
                pruneExpiredSamples()
            }
        }
    }

    private suspend fun recordCurrentSnapshot() {
        try {
            val snapshot = repository.getSnapshot()
            val basicInfo = snapshot.basicInfo
            if (!HistoryPolicy.shouldRecord(snapshot.connected, basicInfo != null)) return

            store.append(
                HistorySample(
                    timestampMillis = clock(),
                    socPercent = checkNotNull(basicInfo).soc,
                    packMillivolts = (basicInfo.totalVoltage * MILLIVOLTS_PER_VOLT).roundToInt(),
                    currentMilliamps = (basicInfo.current * MILLIAMPS_PER_AMP).roundToInt(),
                    cellDeltaMillivolts = snapshot.cellVoltages
                        ?.cells
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { cells ->
                            ((cells.maxOrNull()!! - cells.minOrNull()!!) * MILLIVOLTS_PER_VOLT)
                                .roundToInt()
                        }
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportError("Failed to append history sample", error)
        }
    }

    private suspend fun pruneExpiredSamples() {
        try {
            store.pruneBefore(HistoryPolicy.retentionCutoffMillis(clock()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportError("Failed to prune history samples", error)
        }
    }

    private companion object {
        const val TAG = "HistoryRecorder"
        const val MILLIVOLTS_PER_VOLT = 1_000f
        const val MILLIAMPS_PER_AMP = 1_000f
    }
}
