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

import com.intellij.execution.Executor
import com.intellij.execution.configuration.AbstractRunConfiguration
import com.intellij.execution.configuration.EmptyRunProfileState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.micropython.nova.fileSystemWidget
import com.jetbrains.micropython.nova.performReplAction
import com.jetbrains.micropython.settings.MicroPythonProjectConfigurable
import com.jetbrains.micropython.settings.microPythonFacet
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import java.nio.file.Path


/**
 * @author Mikhail Golubev
 */

class MicroPythonRunConfiguration(project: Project, factory: ConfigurationFactory) : AbstractRunConfiguration(project, factory), RunConfigurationWithSuppressedDefaultDebugAction {

  var path: String = ""
  var runReplOnSuccess: Boolean = false
  var resetOnSuccess: Boolean = true

  override fun getValidModules() =
          allModules.filter { it.microPythonFacet != null }.toMutableList()

  override fun getConfigurationEditor() = MicroPythonRunConfigurationEditor(this)


  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    val success: Boolean
    if (path.isBlank()) {
      success = uploadProject(project)
    } else {
      val toUpload = VfsUtil.findFile(Path.of(project.basePath ?: return null), true) ?: return null
      success = uploadFileOrFolder(project, toUpload)
    }
    if (success) {
      val fileSystemWidget = fileSystemWidget(project)
      if(resetOnSuccess)fileSystemWidget?.reset()
      if(runReplOnSuccess) fileSystemWidget?.activateRepl()
      return EmptyRunProfileState.INSTANCE
    } else {
      return null
    }
  }


  override fun checkConfiguration() {
    super.checkConfiguration()
    val m = module ?: throw RuntimeConfigurationError("Module for path was not found")
    val showSettings = Runnable {
      when {
        PlatformUtils.isPyCharm() ->
          ShowSettingsUtil.getInstance().showSettingsDialog(project, MicroPythonProjectConfigurable::class.java)
        PlatformUtils.isIntelliJ() ->
          ProjectSettingsService.getInstance(project).openModuleSettings(module)
        else ->
          ShowSettingsUtil.getInstance().showSettingsDialog(project)
      }
    }
    val facet = m.microPythonFacet ?: throw RuntimeConfigurationError(
            "MicroPython support is not enabled for selected module in IDE settings",
            showSettings
    )
    val validationResult = facet.checkValid()
    if (validationResult != ValidationResult.OK) {
      val runQuickFix = Runnable {
        validationResult.quickFix.run(null)
      }
      throw RuntimeConfigurationError(validationResult.errorMessage, runQuickFix)
    }
    facet.pythonPath ?: throw RuntimeConfigurationError("Python interpreter was not found")
  }

  override fun suggestedName() = "Flash ${PathUtil.getFileName(path)}"

  override fun writeExternal(element: Element) {
    super.writeExternal(element)
    element.setAttribute("path", path)
    element.setAttribute("run-repl-on-success", if (runReplOnSuccess) "yes" else "no")
    element.setAttribute("reset-on-success", if (resetOnSuccess) "yes" else "no")
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)
    configurationModule.readExternal(element)
    element.getAttributeValue("path")?.let {
      path = it
    }
    element.getAttributeValue("run-repl-on-success")?.let {
      runReplOnSuccess = it == "yes"
    }
    element.getAttributeValue("reset-on-success")?.let {
      resetOnSuccess = it == "yes"
    }
  }

  val module: Module?
    get() {
      if (path.isEmpty()) {
        val projectDir = project.guessProjectDir()
        if (projectDir != null) return ModuleUtil.findModuleForFile(projectDir, project)
      }
      val file = StandardFileSystems.local().findFileByPath(path) ?: return null
      return ModuleUtil.findModuleForFile(file, project)
    }

  companion object {

    private fun VirtualFile.leadingDot() = this.name.startsWith(".")

    fun uploadFileOrFolder(project: Project, toUpload: VirtualFile): Boolean {
      FileDocumentManager.getInstance().saveAllDocuments()
      performUpload(project,listOf(toUpload.name to toUpload))
      return false
    }

    private fun collectUploadables(project: Project): Set<VirtualFile> {
      return project.modules.flatMap { module ->
        val moduleRoots = module.rootManager
          .contentEntries
          .flatMap { it.sourceFolders.asSequence() }
          .mapNotNull { if (!it.isTestSource) it.file else null }
          .filter { !it.leadingDot() }
          .toMutableList()

        if (moduleRoots.isEmpty()) {
          module.rootManager.contentRoots.filterTo(moduleRoots) { it.isDirectory && !it.leadingDot() }
        }
        moduleRoots
      }.toSet()
    }

    private fun collectExcluded(project: Project): Set<VirtualFile> {
      val ideaDir = project.stateStore.directoryStorePath?.let { VfsUtil.findFile(it, false) }
      val excludes = if (ideaDir == null) mutableSetOf<VirtualFile>() else mutableSetOf(ideaDir)
      project.modules.forEach { module ->
        PythonSdkUtil.findPythonSdk(module)?.homeDirectory?.apply { excludes.add(this) }
        module.rootManager.contentEntries.forEach { entry ->
          excludes.addAll(entry.excludeFolderFiles)
        }
      }
      return excludes
    }

    fun uploadProject(project: Project): Boolean {
      val filesToUpload = collectUploadables(project).map { file -> "" to file }.toMutableList()
      return performUpload(project, filesToUpload)
    }

    private fun performUpload(project: Project, filesToUpload: List<Pair<String, VirtualFile>>): Boolean {
      val flatListToUpload = filesToUpload.toMutableList()
      val ignorableFolders = collectExcluded(project)
      try {
        performReplAction(project, true, "Upload files") { fileSystemWidget ->
          withContext(Dispatchers.EDT) {
            FileDocumentManager.getInstance().saveAllDocuments()
          }
          val fileTypeRegistry = FileTypeRegistry.getInstance()
          var index = 0
          while (index < flatListToUpload.size) {
            val file = flatListToUpload[index].second
            if (!file.isValid || file.leadingDot() || fileTypeRegistry.isFileIgnored(file)) {
              flatListToUpload.removeAt(index)
            } else if (ignorableFolders.any { VfsUtil.isAncestor(it, file, true) }) {
              flatListToUpload.removeAt(index)
            } else if (file.isDirectory) {
              file.children.forEach { flatListToUpload.add("${flatListToUpload[index].first}/${it.name}" to it) }
              flatListToUpload.removeAt(index)
            } else {
              index++
            }
            checkCanceled()
          }
          //todo low priority create empty folders
          reportSequentialProgress(flatListToUpload.size) { reporter ->
            flatListToUpload.forEach { (path, file) ->
              reporter.itemStep(path)
              fileSystemWidget.upload(path, file.contentsToByteArray())
            }
          }
        }
      } finally {
        runWithModalProgressBlocking(project, "Updating file system view...") {
          fileSystemWidget(project)?.refresh()
        }
      }
      return true
    }

  }
}
