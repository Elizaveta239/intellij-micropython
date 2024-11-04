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

import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId

/**
 * @author vlan
 */
open class MicroBitDeviceProvider : MicroPythonDeviceProvider {
  override val persistentName: String
    get() = "Micro:bit"

  override val documentationURL: String
    get() = "https://github.com/JetBrains/intellij-micropython/wiki/BBC-Micro:bit"

  override fun checkUsbId(usbId: MicroPythonUsbId): Boolean = usbId == MicroPythonUsbId(0x0D28, 0x0204)

  override val typeHints: MicroPythonTypeHints by lazy {
    MicroPythonTypeHints(listOf("microbit"))
  }

  override val detectedModuleNames: Set<String>
    get() = linkedSetOf("microbit")

  override val isDefault: Boolean
    get() = true
}