package com.jetbrains.micropython.devices

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyRequirement

/**
 * @author timsavage
 */
class RPiPicoDeviceProvider : MicroPythonDeviceProvider {
  override val persistentName: String
    get() = "Raspberry Pi Pico"

  override val documentationURL: String
    get() = "https://www.raspberrypi.com/documentation/microcontrollers/raspberry-pi-pico.html"

  /**
   * From https://github.com/raspberrypi/usb-pid :
   *
   * "The USB-IF have given Raspberry Pi permission to sub-license the USB product ID values for its vendor ID (0x2E8A)
   * since they are to be used on a common silicon component which will be used within a customer's product (the RP2040
   * silicon)."
   *
   * Consequently, most usb devices with the vendor id 0x2E8A are likely to be an RP2040, and therefore capable of
   * running MicroPython.
   */
  override fun checkUsbId(usbId: MicroPythonUsbId): Boolean =
          usbId.vendorId == 0x2E8A && ProductId.likelyToRunMicroPython(usbId.productId)

  object ProductId {
    private const val PICO_WITH_STANDARD_MICROPYTHON_FIRMWARE = 0x05

    /**
     * Raspberry Pi have allocated the productId range 0x1000 - 0x1fff for Commercial RP2040 devices.
     * See https://github.com/raspberrypi/usb-pid#assignment
     */
    private val COMMERCIAL_RANGE = 0x1000..0x1fff

    fun likelyToRunMicroPython(productId: Int): Boolean =
            productId == PICO_WITH_STANDARD_MICROPYTHON_FIRMWARE || productId in COMMERCIAL_RANGE
  }

  override val typeHints: MicroPythonTypeHints by lazy {
    MicroPythonTypeHints(listOf("stdlib", "micropython", "rpi_pico"))
  }

  override fun getPackageRequirements(sdk: Sdk): List<PyRequirement> {
    val manager = PyPackageManager.getInstance(sdk)
    return manager.parseRequirements("""|pyserial>=3.5,<4.0
                                        |docopt>=0.6.2,<0.7
                                        |adafruit-ampy>=1.0.5,<1.1""".trimMargin())
  }

}
