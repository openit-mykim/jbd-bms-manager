package com.gytxtx.openjbd.history

import com.gytxtx.openjbd.data.BmsRepository
import com.gytxtx.openjbd.data.BmsUiState
import com.gytxtx.openjbd.data.ConnectionState
import com.gytxtx.openjbd.protocol.JbdBasicInfo
import com.gytxtx.openjbd.protocol.JbdCellVoltages
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecorderTest {
    @Test
    fun disconnectedSnapshotIsNotStored() = runTest {
        val store = FakeHistoryStore()
        val repository = BmsRepository().apply {
            update(snapshot(connected = false, basicInfo = basicInfo()))
        }
        val recorder = recorder(repository, store)

        recorder.start(backgroundScope)
        advanceTimeBy(TEST_SAMPLE_INTERVAL)
        runCurrent()

        assertTrue(store.samples.isEmpty())
    }

    @Test
    fun connectedSnapshotIsStoredWithConvertedUnitsAndCellDelta() = runTest {
        val store = FakeHistoryStore()
        val repository = BmsRepository().apply {
            update(
                snapshot(
                    connected = true,
                    basicInfo = basicInfo(totalVoltage = 51.234f, current = -2.345f, soc = 67),
                    cells = JbdCellVoltages(listOf(3.301f, 3.315f, 3.299f))
                )
            )
        }
        val recorder = recorder(repository, store, clock = { 123_456L })

        recorder.start(backgroundScope)
        advanceTimeBy(TEST_SAMPLE_INTERVAL)
        runCurrent()

        assertEquals(
            HistorySample(
                timestampMillis = 123_456L,
                socPercent = 67,
                packMillivolts = 51_234,
                currentMilliamps = -2_345,
                cellDeltaMillivolts = 16
            ),
            store.samples.single()
        )
    }

    @Test
    fun missingCellVoltagesStoreNullDelta() = runTest {
        val store = FakeHistoryStore()
        val repository = BmsRepository().apply {
            update(snapshot(connected = true, basicInfo = basicInfo(), cells = null))
        }
        val recorder = recorder(repository, store)

        recorder.start(backgroundScope)
        advanceTimeBy(TEST_SAMPLE_INTERVAL)
        runCurrent()

        assertNull(store.samples.single().cellDeltaMillivolts)
    }

    @Test
    fun appendFailureIsLoggedAndSamplingContinues() = runTest {
        val store = FakeHistoryStore(appendFailure = IllegalStateException("disk full"))
        val errors = mutableListOf<String>()
        val repository = BmsRepository().apply {
            update(snapshot(connected = true, basicInfo = basicInfo()))
        }
        val recorder = recorder(repository, store, reportError = { message, _ -> errors += message })

        recorder.start(backgroundScope)
        advanceTimeBy(TEST_SAMPLE_INTERVAL * 2)
        runCurrent()

        assertEquals(2, store.appendAttempts)
        assertEquals(2, errors.size)
    }

    @Test
    fun startingTwiceDoesNotDuplicateSampling() = runTest {
        val store = FakeHistoryStore()
        val repository = BmsRepository().apply {
            update(snapshot(connected = true, basicInfo = basicInfo()))
        }
        val recorder = recorder(repository, store)

        recorder.start(backgroundScope)
        recorder.start(backgroundScope)
        advanceTimeBy(TEST_SAMPLE_INTERVAL)
        runCurrent()

        assertEquals(1, store.samples.size)
    }

    @Test
    fun startPrunesOnceImmediately() = runTest {
        val store = FakeHistoryStore()
        val now = 900_000_000L
        val recorder = recorder(BmsRepository(), store, clock = { now })

        recorder.start(backgroundScope)
        runCurrent()

        assertEquals(listOf(295_200_000L), store.pruneCutoffs)
    }

    private fun TestScope.recorder(
        repository: BmsRepository,
        store: HistoryStore,
        clock: () -> Long = { 1L },
        reportError: (String, Throwable) -> Unit = { _, _ -> }
    ) = HistoryRecorder(
        repository = repository,
        store = store,
        clock = clock,
        dispatcher = StandardTestDispatcher(testScheduler),
        sampleIntervalMillis = TEST_SAMPLE_INTERVAL,
        pruneIntervalMillis = TEST_PRUNE_INTERVAL,
        reportError = reportError
    )

    private fun snapshot(
        connected: Boolean,
        basicInfo: JbdBasicInfo?,
        cells: JbdCellVoltages? = null
    ): BmsUiState = BmsUiState.withConnectionState(
        connectionState = if (connected) ConnectionState.READY else ConnectionState.DISCONNECTED,
        connected = connected,
        deviceName = "Test BMS",
        deviceAddress = "00:11:22:33:44:55",
        status = null,
        basicInfo = basicInfo,
        cellVoltages = cells
    )

    private fun basicInfo(
        totalVoltage: Float = 52.0f,
        current: Float = 1.0f,
        soc: Int = 50
    ) = JbdBasicInfo(
        totalVoltage = totalVoltage,
        current = current,
        remainingAh = 50f,
        nominalAh = 100f,
        cycleCount = 10,
        productionDate = "2026-01-01",
        soc = soc,
        balanceState = 0,
        balanceStates = BooleanArray(4),
        protectionState = 0,
        protectionStates = BooleanArray(16),
        chargeEnabled = true,
        dischargeEnabled = true,
        cellCount = 4,
        ntcCount = 1,
        softwareVersion = "1.0",
        temperaturesC = listOf(25f),
        hasLearnCapacity = false,
        learnCapacityAh = 0f,
        hasExtendedInfo = false,
        extensionMarker = 0,
        alter = 0,
        hasBalanceCurrent = false,
        balanceCurrentA = 0f
    )

    private class FakeHistoryStore(
        private val appendFailure: Exception? = null
    ) : HistoryStore {
        val samples = mutableListOf<HistorySample>()
        val pruneCutoffs = mutableListOf<Long>()
        var appendAttempts = 0

        override suspend fun append(sample: HistorySample) {
            appendAttempts += 1
            appendFailure?.let { throw it }
            samples += sample
        }

        override suspend fun query(fromMillis: Long, toMillis: Long): List<HistorySample> =
            samples.filter { it.timestampMillis in fromMillis..toMillis }

        override suspend fun pruneBefore(cutoffMillis: Long): Int {
            pruneCutoffs += cutoffMillis
            val before = samples.size
            samples.removeAll { it.timestampMillis < cutoffMillis }
            return before - samples.size
        }

        override suspend fun count(): Long = samples.size.toLong()

        override suspend fun latestTimestamp(): Long? = samples.maxOfOrNull { it.timestampMillis }
    }

    private companion object {
        const val TEST_SAMPLE_INTERVAL = 10_000L
        const val TEST_PRUNE_INTERVAL = 3_600_000L
    }
}
