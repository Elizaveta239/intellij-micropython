package com.jetbrains.micropython.devices

class MicroBitV2DeviceProvider : MicroBitDeviceProvider() {
    override val persistentName: String
        get() = "Micro:bit V2"

    override val isDefault: Boolean
        get() = false
}