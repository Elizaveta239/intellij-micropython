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

package com.jetbrains.micropython.settings

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.ui.FacetConfigurationQuickFix
import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.micropython.devices.MicroPythonDeviceProvider
import com.jetbrains.python.facet.FacetLibraryConfigurator
import com.jetbrains.python.facet.LibraryContributingFacet
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import javax.swing.JComponent

/**
 * @author vlan
 */
class MicroPythonFacet(facetType: FacetType<out Facet<*>, *>, module: Module, name: String,
                       configuration: MicroPythonFacetConfiguration, underlyingFacet: Facet<*>?)
  : LibraryContributingFacet<MicroPythonFacetConfiguration>(facetType, module, name, configuration, underlyingFacet) {

  companion object {
    private const val PLUGIN_ID = "intellij-micropython"

    val scriptsPath: String
      get() = "${pluginDescriptor.pluginPath}/scripts"

    private val pluginDescriptor: IdeaPluginDescriptor
      get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?:
          throw RuntimeException("The $PLUGIN_ID plugin cannot find itself")
  }

  override fun initFacet() {
    updateLibrary()
  }

  override fun updateLibrary() {
    val plugin = pluginDescriptor
    val boardHintsPaths = configuration.deviceProvider.typeHints?.paths?.map {
      "${plugin.pluginPath}/typehints/$it"
    } ?: emptyList()
    ApplicationManager.getApplication().invokeLater {
      FacetLibraryConfigurator.attachPythonLibrary(module, null, "MicroPython", boardHintsPaths)
      removeLegacyLibraries()
    }
  }

  override fun removeLibrary() {
    FacetLibraryConfigurator.detachPythonLibrary(module, "MicroPython")
  }

  fun checkValid(): ValidationResult {
    val provider = configuration.deviceProvider
    val sdk = PythonSdkUtil.findPythonSdk(module)
    if (sdk == null || PythonSdkUtil.isInvalid(sdk) || PySdkUtil.getLanguageLevelForSdk(sdk).isOlderThan(LanguageLevel.PYTHON35)) {
      return ValidationResult("${provider.presentableName} support requires valid Python 3.5+ SDK")
    }
    val packageManager = PyPackageManager.getInstance(sdk)
    val packages = packageManager.packages ?: return ValidationResult.OK
    val requirements = provider.getPackageRequirements(sdk).filter { it.match(packages) == null }.toList()
    if (requirements.isNotEmpty()) {
      val requirementsText = requirements.joinToString(", ") {
        it.presentableText
      }
      return ValidationResult("Packages required for ${provider.presentableName} support not found: $requirementsText",
                              object : FacetConfigurationQuickFix("Install Requirements") {
        override fun run(place: JComponent?) {
          PyPackageManagerUI(module.project, sdk, null).install(requirements, emptyList())
        }
      })
    }
    return ValidationResult.OK
  }

  private fun findSerialPorts(deviceProvider: MicroPythonDeviceProvider, indicator: ProgressIndicator): List<String> {
    val timeout = 1_000
    val pythonPath = pythonPath ?: return emptyList()
    val command = listOf(pythonPath, "$scriptsPath/findusb.py")
    val process = CapturingProcessHandler(GeneralCommandLine(command))
    val output = process.runProcessWithProgressIndicator(indicator, timeout)
    return when {
      output.isCancelled -> emptyList()
      output.isTimeout -> emptyList()
      output.exitCode != 0 -> emptyList()
      else -> {
        output.stdoutLines.associate {
          Pair(MicroPythonUsbId.parse(it.substringBefore(' ')), it.substringAfter(' '))
        }.filterKeys { deviceProvider.checkUsbId(it) }.values.toList()
      }
    }
  }

  val pythonPath: String?
    get() = PythonSdkUtil.findPythonSdk(module)?.homePath

  private fun removeLegacyLibraries() {
    FacetLibraryConfigurator.detachPythonLibrary(module, "Micro:bit")
  }
}

val Module.microPythonFacet: MicroPythonFacet?
  get() = FacetManager.getInstance(this).getFacetByType(MicroPythonFacetType.ID)

val Project.firstMicroPythonFacet: MicroPythonFacet?
  get() = ModuleManager.getInstance(this).modules
      .asSequence()
      .map { it.microPythonFacet }
      .firstOrNull()
