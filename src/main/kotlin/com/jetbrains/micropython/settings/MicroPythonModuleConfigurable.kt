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

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author vlan
 */
class MicroPythonModuleConfigurable(private val module: Module) : Configurable {

  private val disposable = Disposer.newDisposable()

  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  private val panel: MicroPythonSettingsPanel by lazy {
    MicroPythonSettingsPanel(module, disposable)
  }

  private val enabledCheckbox by lazy {
    val checkBox = JCheckBox("Enable MicroPython Support")
    checkBox.addActionListener {
      update()
    }
    checkBox
  }

  override fun isModified(): Boolean {
    val facet = module.microPythonFacet
    val enabled = facet != null

    if (enabledCheckbox.isSelected != enabled) return true
    val c = facet?.configuration ?: return false
    return panel.isModified(c)
  }

  override fun getDisplayName() = "MicroPython"

  override fun apply() {
    val facet = module.microPythonFacet
    val application = ApplicationManager.getApplication()
    val facetManager = FacetManager.getInstance(module)

    if (enabledCheckbox.isSelected) {
      if (facet != null) {
        panel.apply(facet.configuration, facet)
        FacetManager.getInstance(module).facetConfigurationChanged(facet)
        facet.updateLibrary()
      }
      else {
        val facetType = MicroPythonFacetType.getInstance()
        val newFacet = facetManager.createFacet(facetType, facetType.defaultFacetName, null)
        panel.apply(newFacet.configuration, newFacet)

        val facetModel = facetManager.createModifiableModel()
        facetModel.addFacet(newFacet)
        application.runWriteAction { facetModel.commit() }
      }
    }
    else if (facet != null) {
      val facetModel = facetManager.createModifiableModel()
      facetModel.removeFacet(facet)
      application.runWriteAction { facetModel.commit() }
    }
  }

  override fun createComponent(): JComponent {
    val mainPanel = JPanel()
    with(mainPanel) {
      layout = BorderLayout()
      add(enabledCheckbox, BorderLayout.NORTH)
      add(panel, BorderLayout.CENTER)
    }
    update()
    return mainPanel
  }

  override fun reset() {
    val facet = module.microPythonFacet
    val enabled = facet != null

    enabledCheckbox.isSelected = enabled
    panel.isEnabled = enabled
    update()

    if (facet != null) {
      panel.reset(facet.configuration)
    }
  }

  private fun update() {
    UIUtil.setEnabled(panel, enabledCheckbox.isSelected, true)
  }
}