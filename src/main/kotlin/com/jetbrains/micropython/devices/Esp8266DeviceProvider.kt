package com.jetbrains.micropython.devices

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyRequirement

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

  override fun getPackageRequirements(sdk: Sdk): List<PyRequirement> {
    val manager = PyPackageManager.getInstance(sdk)
    return manager.parseRequirements("""|pyserial>=3.5,<4.0
                                        |docopt>=0.6.2,<0.7
                                        |adafruit-ampy>=1.0.5,<1.1""".trimMargin())
  }

}
