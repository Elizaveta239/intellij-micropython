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
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.micropython.run.MicroPythonRunConfiguration
import com.jetbrains.micropython.settings.MicroPythonTypeHints
import com.jetbrains.micropython.settings.MicroPythonUsbId
import com.jetbrains.python.packaging.PyRequirement

/**
 * @author vlan
 */
interface MicroPythonDeviceProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<MicroPythonDeviceProvider> =
        ExtensionPointName.create("com.jetbrains.micropython.deviceProvider")

    val providers: List<MicroPythonDeviceProvider>
      get() = EP_NAME.extensionList

    val default: MicroPythonDeviceProvider
      get() = providers.first { it.isDefault }
  }

  val persistentName: String

  val documentationURL: String
  fun checkUsbId(usbId: MicroPythonUsbId): Boolean

  val presentableName: String
    get() = persistentName

  fun getPackageRequirements(sdk: Sdk): List<PyRequirement> = emptyList()

  val typeHints: MicroPythonTypeHints?
    get() = null

  val detectedModuleNames: Set<String>
    get() = emptySet()

  val isDefault: Boolean
    get() = false
}