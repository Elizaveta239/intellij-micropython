/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.micropython.devices

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.micropython.run.MicroPythonRunConfiguration
import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId
import com.jetbrains.micropython.settings.microPythonFacet
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyRequirement

/**
 * @author vlan
 */
open class MicroBitDeviceProvider : MicroPythonDeviceProvider {
  override val persistentName: String
    get() = "Micro:bit"

  override val documentationURL: String
    get() = "https://github.com/JetBrains/intellij-micropython/wiki/BBC-Micro:bit"

  override fun checkUsbId(usbId: MicroPythonUsbId): Boolean = usbId == MicroPythonUsbId(0x0D28, 0x0204)

  override fun getPackageRequirements(sdk: Sdk): List<PyRequirement> {
    val manager = PyPackageManager.getInstance(sdk)
    return manager.parseRequirements("""|uflash>=1.2.4,<1.3
                                        |docopt>=0.6.2,<0.7
                                        |pyserial>=3.5,<4.0""".trimMargin())
  }

  override val typeHints: MicroPythonTypeHints by lazy {
    MicroPythonTypeHints(listOf("microbit"))
  }

  override val detectedModuleNames: Set<String>
    get() = linkedSetOf("microbit")

  override val isDefault: Boolean
    get() = true
}