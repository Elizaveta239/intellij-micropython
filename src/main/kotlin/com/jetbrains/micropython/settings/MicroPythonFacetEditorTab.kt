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

import com.intellij.facet.ui.FacetEditorTab
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * @author vlan
 */
class MicroPythonFacetEditorTab(val configuration: MicroPythonFacetConfiguration,
                                private val facet: MicroPythonFacet) : FacetEditorTab() {
  private val disposable = Disposer.newDisposable()
  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  private val panel: MicroPythonSettingsPanel by lazy {
    MicroPythonSettingsPanel(facet.module, disposable)
  }

  override fun isModified(): Boolean = panel.isModified(configuration, facet)

  override fun getDisplayName(): String = panel.getDisplayName()

  override fun createComponent(): JComponent = panel

  override fun apply() {
    panel.apply(configuration, facet)
    facet.updateLibrary()
  }

  override fun reset() {
    panel.reset(configuration, facet)
  }
}