package com.gytxtx.openjbd.maintenance

class MaintenanceGate {
    var isUnlocked: Boolean = false
        private set

    fun requestUnlock(confirmed: Boolean) {
        if (confirmed) isUnlocked = true
    }

    fun relock() {
        isUnlocked = false
    }
}
