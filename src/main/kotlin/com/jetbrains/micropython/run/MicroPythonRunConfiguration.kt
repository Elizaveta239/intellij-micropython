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
      // Instead of defaulting to project path, let's use the actual path configured by the user
      val toUpload = StandardFileSystems.local().findFileByPath(path) ?: return null
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
    val m = module ?: throw RuntimeConfigurationError("Module for path is not found")
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
    facet.pythonPath ?: throw RuntimeConfigurationError("Python interpreter is not found")
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
      performUpload(project,listOf(toUpload.name to toUpload), toUpload)
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
      return performUpload(project, filesToUpload, null) // If null whole project is uploaded
    }

    private fun performUpload(project: Project, filesToUpload: List<Pair<String, VirtualFile>>, uploadRoot: VirtualFile?): Boolean {
      val flatListToUpload = filesToUpload.toMutableList()
      val ignorableFolders = collectExcluded(project)

      // Save test folders
      val testSourceFolders = project.modules.flatMap { module ->
        module.rootManager.contentEntries
          .flatMap { entry -> entry.sourceFolders.toList() }
          .filter { sourceFolder -> sourceFolder.isTestSource }
          .mapNotNull { it.file }
      }.toSet()

      // Determine if we are uploading a test folder specifically
      val isTestSourceUpload = uploadRoot?.let { root ->
        testSourceFolders.any { testSource ->
          VfsUtil.isAncestor(testSource, root, true) ||
                  testSourceFolders.any { it == root }
        }
      } ?: false

      // Save source folders
      val sourceRoots = project.modules.flatMap { module ->
        module.rootManager.contentEntries
          .flatMap { entry -> entry.sourceFolders.toList() }
          .filter { sourceFolder ->
            !sourceFolder.isTestSource &&
                    sourceFolder.file?.let { !it.leadingDot() } ?: false &&
                    (uploadRoot == null ||
                            sourceFolder.file?.let { VfsUtil.isAncestor(uploadRoot, it, true) } ?: false ||
                            sourceFolder.file?.let { VfsUtil.isAncestor(it, uploadRoot, true) } ?: false)
          }
          .mapNotNull { it.file }
      }.toSet()

      performReplAction(project, true, "Upload files") { fileSystemWidget ->
        withContext(Dispatchers.EDT) {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        val fileTypeRegistry = FileTypeRegistry.getInstance()
        var index = 0
        while (index < flatListToUpload.size) {
          val (currentPath, file) = flatListToUpload[index]

          // Check if file is inside of a test folder
          val isInTestSource = testSourceFolders.any { testSource ->
            VfsUtil.isAncestor(testSource, file, false)
          }

          // Check if the file is a source root itself
          val isSourceRoot = sourceRoots.any { it == file }

          if (!file.isValid ||
            file.leadingDot() ||
            fileTypeRegistry.isFileIgnored(file) ||
            (!isTestSourceUpload && isInTestSource) ||
            ignorableFolders.any { VfsUtil.isAncestor(it, file, true)} ||
            // For project-wide upload, exclude source roots but include their contents
            (uploadRoot == null && isSourceRoot)
          ) {
            flatListToUpload.removeAt(index)
          } else if (file.isDirectory) {
            val sourceRoot = sourceRoots.find { it == file } // Direct source root match

            if (sourceRoot != null) {
              // This is a source root - handle all descendants with paths relative to the source root
              file.children.forEach { child ->
                if (isTestSourceUpload || !testSourceFolders.any { VfsUtil.isAncestor(it, child, false) }) {
                  val relativePath = VfsUtil.getRelativePath(child, sourceRoot) ?: child.name
                  flatListToUpload.add(relativePath to child)
                }
              }
            } else {
              // Check if this directory is inside a source root
              val parentSourceRoot = sourceRoots.find { VfsUtil.isAncestor(it, file, false) }
              file.children.forEach { child ->
                if (isTestSourceUpload || !testSourceFolders.any { VfsUtil.isAncestor(it, child, false) }) {
                  val relativePath = when {
                    parentSourceRoot != null -> {
                      // If inside source root, path should be relative to source root
                      VfsUtil.getRelativePath(child, parentSourceRoot) ?: child.name
                    }
                    uploadRoot != null && VfsUtil.isAncestor(uploadRoot, child, true) -> {
                      VfsUtil.getRelativePath(child, uploadRoot) ?: child.name
                    }
                    currentPath.isEmpty() -> child.name
                    else -> "$currentPath/${child.name}"
                  }
                  flatListToUpload.add(relativePath to child)
                }
              }
            }
            flatListToUpload.removeAt(index)
          } else {
            val sourceRoot = sourceRoots.find { VfsUtil.isAncestor(it, file, false) }
            // Also consider test source roots when calculating relative paths
            val testSourceRoot = if (isTestSourceUpload) {
              testSourceFolders.find { VfsUtil.isAncestor(it, file, false) }
            } else null

            if ((sourceRoot != null || testSourceRoot != null) && uploadRoot == null) {
              val relativeToSource = when {
                testSourceRoot != null -> VfsUtil.getRelativePath(file, testSourceRoot)
                else -> VfsUtil.getRelativePath(file, sourceRoot!!)
              }
              if (relativeToSource != null) {
                flatListToUpload[index] = relativeToSource to file
              }
            }
            index++
          }
          checkCanceled()
        }

        reportSequentialProgress(flatListToUpload.size) { reporter ->
          flatListToUpload.forEach { (path, file) ->
            reporter.itemStep(path)
            fileSystemWidget.upload(path, file.contentsToByteArray())
          }
        }
        fileSystemWidget.refresh()
      }
      return true
    }
  }
}
