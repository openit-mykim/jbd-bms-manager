package com.gytxtx.openjbd.maintenance

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceTransactionCoordinatorTest {
    @Test
    fun acknowledgedWriteIsSuccessfulOnlyAfterMatchingReadBack() = runTest {
        val gateway = FakeGateway(readBackResult = GatewayReadResult.ValueRead("new"))

        val result = coordinator(gateway).execute("new")

        assertTrue(result is MaintenanceTransactionResult.SuccessVerified<*>)
        val success = result as MaintenanceTransactionResult.SuccessVerified<String>
        assertTrue(success.writeApplied)
        assertEquals("old", success.previousValue)
        assertEquals("new", success.verifiedValue)
        assertEquals(listOf("readCurrent", "apply:old->new", "readBack"), gateway.calls)
    }

    @Test
    fun rejectedWriteReturnsWriteRejectedWithoutReadBack() = runTest {
        val gateway = FakeGateway(writeResult = GatewayWriteResult.Rejected("status=0x80"))

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.WriteRejected("status=0x80"),
            result
        )
        assertEquals(listOf("readCurrent", "apply:old->new"), gateway.calls)
    }

    @Test
    fun disconnectDuringInitialReadIsModeled() = runTest {
        val gateway = FakeGateway(
            currentResult = GatewayReadResult.TransportDisconnected("link lost")
        )

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.TransportDisconnected(
                TransactionPhase.READ_CURRENT,
                "link lost"
            ),
            result
        )
        assertEquals(listOf("readCurrent"), gateway.calls)
    }

    @Test
    fun timeoutDuringApplyIsModeled() = runTest {
        val gateway = FakeGateway(writeResult = GatewayWriteResult.Timeout("write deadline"))

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.Timeout(TransactionPhase.APPLY, "write deadline"),
            result
        )
    }

    @Test
    fun malformedReadBackIsModeled() = runTest {
        val gateway = FakeGateway(
            readBackResult = GatewayReadResult.MalformedResponse("checksum mismatch")
        )

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.MalformedResponse(
                TransactionPhase.READ_BACK,
                "checksum mismatch"
            ),
            result
        )
    }

    @Test
    fun unsupportedInitialReadIsModeled() = runTest {
        val gateway = FakeGateway(
            currentResult = GatewayReadResult.Unsupported("unknown firmware")
        )

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.Unsupported(
                TransactionPhase.READ_CURRENT,
                "unknown firmware"
            ),
            result
        )
    }

    @Test
    fun differingReadBackReturnsMismatchEvenAfterAck() = runTest {
        val gateway = FakeGateway(readBackResult = GatewayReadResult.ValueRead("device-value"))

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.ReadBackMismatch("old", "new", "device-value"),
            result
        )
        assertTrue(gateway.calls.contains("readBack"))
    }

    @Test
    fun partialGatewayApplyIsModeledWithoutClaimingSuccess() = runTest {
        val gateway = FakeGateway(
            writeResult = GatewayWriteResult.PartiallyApplied(1, 3, "field two rejected")
        )

        val result = coordinator(gateway).execute("new")

        assertEquals(
            MaintenanceTransactionResult.PartialApply(1, 3, "field two rejected"),
            result
        )
        assertFalse(gateway.calls.contains("readBack"))
    }

    @Test
    fun noDiffIsExplicitVerifiedNoOpAndSkipsWrite() = runTest {
        val gateway = FakeGateway()

        val result = coordinator(gateway).execute("old")

        assertTrue(result is MaintenanceTransactionResult.SuccessVerified<*>)
        val success = result as MaintenanceTransactionResult.SuccessVerified<String>
        assertFalse(success.writeApplied)
        assertEquals("old", success.verifiedValue)
        assertEquals(listOf("readCurrent"), gateway.calls)
    }

    @Test
    fun invalidCandidateIsLocallyRejectedBeforeDiffOrWrite() = runTest {
        val gateway = FakeGateway()

        val result = coordinator(gateway).execute("invalid")

        assertEquals(
            MaintenanceTransactionResult.WriteRejected(
                "invalid candidate",
                WriteRejectionSource.LOCAL_VALIDATION
            ),
            result
        )
        assertEquals(listOf("readCurrent"), gateway.calls)
    }

    @Test
    fun explicitNormalizerCanVerifyEquivalentRepresentations() = runTest {
        val gateway = FakeGateway(readBackResult = GatewayReadResult.ValueRead("4.2000"))
        val comparer = ReadBackComparer.normalized<String, Double> { it.toDouble() }
        val coordinator = MaintenanceTransactionCoordinator(gateway, StringOperation, comparer)

        val result = coordinator.execute("4.2")

        assertTrue(result is MaintenanceTransactionResult.SuccessVerified<*>)
    }

    private fun coordinator(gateway: FakeGateway) =
        MaintenanceTransactionCoordinator(gateway, StringOperation)

    private object StringOperation : MaintenanceOperation<String, String> {
        override fun validate(candidate: String): ValidationResult =
            if (candidate == "invalid") {
                ValidationResult.Invalid("invalid candidate")
            } else {
                ValidationResult.Valid
            }

        override fun diff(current: String, candidate: String): String? =
            if (current == candidate) null else "$current->$candidate"
    }

    private class FakeGateway(
        private val currentResult: GatewayReadResult<String> = GatewayReadResult.ValueRead("old"),
        private val writeResult: GatewayWriteResult = GatewayWriteResult.Acknowledged,
        private val readBackResult: GatewayReadResult<String> = GatewayReadResult.ValueRead("new")
    ) : MaintenanceTransactionGateway<String, String> {
        val calls = mutableListOf<String>()

        override suspend fun readCurrent(): GatewayReadResult<String> {
            calls += "readCurrent"
            return currentResult
        }

        override suspend fun apply(diff: String): GatewayWriteResult {
            calls += "apply:$diff"
            return writeResult
        }

        override suspend fun readBack(): GatewayReadResult<String> {
            calls += "readBack"
            return readBackResult
        }
    }
}
