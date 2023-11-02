package com.ucal.tpms.blescanner.model

data class BleDevice(
    val name: String,
    val address: String,
    val pressure: String,
    val temperature: String
) {
    companion object {
        fun createBleDevicesList(): MutableList<BleDevice> {
            return mutableListOf()
        }
    }
}
