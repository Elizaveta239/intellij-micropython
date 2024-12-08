package com.jetbrains.micropython.nova

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadFromDeviceAction : ReplAction("Download File or Folder...", true, false) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val fileSystemWidget = fileSystemWidget(e)
        if (fileSystemWidget?.state != State.CONNECTED) {
            e.presentation.isEnabled = false
        } else {
            val selectedFile = fileSystemWidget.selectedFiles().firstOrNull()
            when {
                selectedFile == null -> with(e.presentation) { text = "Download File or Folder"; isEnabled = false }
                selectedFile.isRoot -> e.presentation.text = "Download Device Content"
                selectedFile is DirNode -> e.presentation.text = "Download Folder '${selectedFile.name}'"
                else -> e.presentation.text = "Download File '${selectedFile.name}'..."
            }
        }
    }

    override val actionDescription: @NlsContexts.DialogMessage String = "Downloading..."

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        val selectedFiles = fileSystemWidget.selectedFiles()
        if (selectedFiles.isEmpty()) return
        var destination: VirtualFile? = null
        withContext(Dispatchers.EDT) {
            FileChooserFactory.getInstance().createPathChooser(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(), fileSystemWidget.project, null
            ).choose(null) { folders ->
                destination = folders.firstOrNull()
                if (destination?.children?.isNotEmpty() == true) {
                    if (Messages.showOkCancelDialog(
                            fileSystemWidget.project,
                            "The destination folder is not empty.\nIts contents may be damaged.",
                            "Warning",
                            "Continue",
                            "Cancel",
                            Messages.getWarningIcon()
                        ) != Messages.OK
                    ) {
                        destination = null
                    }
                }
            }
        }
        if (destination == null) return
        val parentNameToFile = selectedFiles.map { node -> "" to node }.toMutableList()
        var listIndex = 0
        while (listIndex < parentNameToFile.size) {
            var (nodeParentName, node) = parentNameToFile[listIndex]
            node.children().asSequence().forEach { child ->
                child as FileSystemNode
                val parentName = when {
                    nodeParentName.isEmpty() && node.isRoot -> ""
                    nodeParentName.isEmpty() -> node.name
                    nodeParentName == "/" -> node.name
                    else -> "$nodeParentName/${node.name}"
                }
                parentNameToFile.add(parentName to child)
            }
            if (node.isRoot) {
                parentNameToFile.removeAt(listIndex)
            } else {
                listIndex++
            }
        }
        reportSequentialProgress(parentNameToFile.size) { reporter ->
            parentNameToFile.forEach { (parentName, node) ->
                val name = if (parentName.isEmpty()) node.name else "$parentName/${node.name}"
                reporter.itemStep(name) {
                    writeDown(node, fileSystemWidget, name, destination)
                }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    private suspend fun writeDown(
        node: FileSystemNode,
        fileSystemWidget: FileSystemWidget,
        name: String,
        destination: VirtualFile?
    ) {
        if (node is FileNode) {
            val content = fileSystemWidget.download(node.fullName)
            writeAction {
                destination!!.findOrCreateFile(name).setBinaryContent(content)
            }
        } else {
            writeAction {
                destination!!.findOrCreateDirectory(name)
            }
        }
    }
}