package com.gytxtx.openjbd.history

interface HistoryStore {
    suspend fun append(sample: HistorySample)
    suspend fun query(fromMillis: Long, toMillis: Long): List<HistorySample>
    suspend fun pruneBefore(cutoffMillis: Long): Int
    suspend fun count(): Long
    suspend fun latestTimestamp(): Long?
}
