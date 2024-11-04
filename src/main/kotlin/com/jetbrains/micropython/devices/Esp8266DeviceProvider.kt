package com.jetbrains.micropython.devices

import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId

/**
 * @author vlan
 */
class Esp8266DeviceProvider : MicroPythonDeviceProvider {
  override val persistentName: String
    get() = "ESP8266"

  override val documentationURL: String
    get() = "https://github.com/JetBrains/intellij-micropython/wiki/ESP8266"

  override fun checkUsbId(usbId: MicroPythonUsbId): Boolean = usbIds.contains(usbId)

  val usbIds: List<MicroPythonUsbId>
    get() = listOf(
      MicroPythonUsbId(0x1A86, 0x7523),
      MicroPythonUsbId(0x10C4, 0xEA60),
      MicroPythonUsbId(0x0403, 0x6001),
      MicroPythonUsbId(0x239A, 0x8038),  // Metro M4 Airlift Lite
    )

  override val typeHints: MicroPythonTypeHints by lazy {
    MicroPythonTypeHints(listOf("stdlib", "micropython", "esp8266"))
  }

}
