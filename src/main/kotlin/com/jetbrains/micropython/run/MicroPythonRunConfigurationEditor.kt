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

package com.jetbrains.micropython.run

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.CheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class MicroPythonRunConfigurationEditor(config: MicroPythonRunConfiguration) : SettingsEditor<MicroPythonRunConfiguration>() {
  private val pathField = TextFieldWithBrowseButton()
    private val resetOnSuccess = CheckBox("Reset on success", selected = true)
    private val runReplOnSuccess = CheckBox("Open MicroPython REPL on success", selected = true)

    init {
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false).withTitle("Select Path")
        val listener = ComponentWithBrowseButton.BrowseFolderActionListener(
            pathField,
            config.project,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )
        pathField.addActionListener(listener)
    }

  override fun createEditor(): JComponent =
      FormBuilder.createFormBuilder()
          .addLabeledComponent("Path:", pathField)
          .addComponent(resetOnSuccess)
          .addComponent(runReplOnSuccess)
          .panel

  override fun applyEditorTo(s: MicroPythonRunConfiguration) {
    s.path = pathField.text
    s.runReplOnSuccess = runReplOnSuccess.isSelected
    s.resetOnSuccess = resetOnSuccess.isSelected
  }

  override fun resetEditorFrom(s: MicroPythonRunConfiguration) {
    pathField.text = s.path
    runReplOnSuccess.isSelected = s.runReplOnSuccess
    resetOnSuccess.isSelected = s.resetOnSuccess
  }
}
