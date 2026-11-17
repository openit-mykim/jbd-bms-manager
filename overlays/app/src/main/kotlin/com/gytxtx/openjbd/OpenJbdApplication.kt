package com.gytxtx.openjbd

import android.app.Application
import com.gytxtx.openjbd.history.HistoryRecorder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@HiltAndroidApp
class OpenJbdApplication : Application() {
    @Inject lateinit var historyRecorder: HistoryRecorder
    @Inject lateinit var deviceRegistry: com.gytxtx.openjbd.registry.BmsDeviceRegistry

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        deviceRegistry.start(applicationScope)
        historyRecorder.start(applicationScope)
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
