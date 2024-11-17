package com.jetbrains.micropython.nova

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.PathUtilRt.Platform
import com.intellij.util.asSafely
import com.jetbrains.micropython.run.MicroPythonRunConfiguration
import com.jetbrains.micropython.settings.MicroPythonProjectConfigurable
import com.jetbrains.micropython.settings.microPythonFacet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

fun fileSystemWidget(project: Project?): FileSystemWidget? {
    return ToolWindowManager.getInstance(project ?: return null)
        .getToolWindow(TOOL_WINDOW_ID)
        ?.contentManager
        ?.contents
        ?.firstNotNullOfOrNull { it.component.asSafely<FileSystemWidget>() }
}

abstract class ReplAction(text: String, private val connectionRequired: Boolean) : DumbAwareAction(text) {

    abstract val actionDescription: @NlsContexts.DialogMessage String

    @Throws(IOException::class, TimeoutCancellationException::class, CancellationException::class)
    abstract suspend fun performAction(fileSystemWidget: FileSystemWidget)

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        performReplAction(project, connectionRequired, actionDescription, this::performAction)
    }

    protected fun fileSystemWidget(e: AnActionEvent): FileSystemWidget? = fileSystemWidget(e.project)

    fun enableIfConnected(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabled = false
        }
    }

}

fun <T> performReplAction(
    project: Project,
    connectionRequired: Boolean,
    @NlsContexts.DialogMessage description: String,
    action: suspend (FileSystemWidget) -> T
): T? {
    val fileSystemWidget = fileSystemWidget(project) ?: return null
    if (connectionRequired && fileSystemWidget.state != State.CONNECTED) {
        if (!MessageDialogBuilder.yesNo("Device is not connected", "Connect now?").ask(project)) {
            return null
        }
    }
    var result: T? = null
    runWithModalProgressBlocking(project, "Board data exchange...") {
        var error: String? = null
        try {
            if (connectionRequired) {
                doConnect(fileSystemWidget)
            }
            result = action(fileSystemWidget)
        } catch (e: TimeoutCancellationException) {
            error = "$description timed out"
            thisLogger().info(error, e)
        } catch (e: CancellationException) {
            error = "$description cancelled"
            thisLogger().info(error, e)
        } catch (e: IOException) {
            error = "$description I/O error - ${e.localizedMessage ?: e.message ?: "No message"}"
            thisLogger().info(error, e)
        } catch (e: Exception) {
            error = e.localizedMessage ?: e.message
            error = if (error.isNullOrBlank()) "$description error - ${e::class.simpleName}"
            else "$description error - ${e::class.simpleName}: $error"
            thisLogger().error(error, e)
        }
        if (!error.isNullOrBlank()) {
            Notifications.Bus.notify(Notification(NOTIFICATION_GROUP, error, NotificationType.ERROR), project)
        }
    }
    return result
}

class Refresh : ReplAction("Refresh", false) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Refresh"

    override fun update(e: AnActionEvent) = enableIfConnected(e)

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) = fileSystemWidget.refresh()
}

class Disconnect(text: String = "Disconnect") : ReplAction(text, false), Toggleable {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Disconnect"

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) = fileSystemWidget.disconnect()

    override fun update(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabledAndVisible = false
        } else {
            Toggleable.setSelected(e.presentation, true)
        }
    }
}

class DeleteFiles : ReplAction("Delete Item(s)", true) {
    override val actionDescription: String = "Delete"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        try {
            fileSystemWidget.deleteCurrent()
        } finally {
            fileSystemWidget.refresh()
        }
    }

    override fun update(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabled = false
            return
        }
        val selectedFiles = fileSystemWidget(e)?.selectedFiles()
        e.presentation.isEnabled = selectedFiles?.any { !it.isRoot } == true
    }
}

class InstantRun : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.VIRTUAL_FILE) != null

    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        FileDocumentManager.getInstance().saveAllDocuments()
        val code = e.getData(CommonDataKeys.VIRTUAL_FILE)?.readText() ?: return
        performReplAction(project,true,"Run code") {
            it.instantRun(code, false)
        }
    }
}

class InstantFragmentRun : ReplAction("Instant Run", true) {
    override val actionDescription: String = "Run code"
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = editor(e.project)
        if (editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val emptySelection = editor.selectionModel.getSelectedText(true).isNullOrBlank()
        e.presentation.text =
            if (emptySelection) "Execute Line in Micropython REPL" else "Execute Selection in Micropython REPL"
    }

    private fun editor(project: Project?): Editor? =
        project?.let { FileEditorManager.getInstance(it).selectedTextEditor }

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        val code = withContext(Dispatchers.EDT) {
            val editor = editor(fileSystemWidget.project) ?: return@withContext null
            var text = editor.selectionModel.getSelectedText(true)
            if (text.isNullOrBlank()) {
                try {
                    val range = EditorUtil.calcCaretLineTextRange(editor)
                    if (!range.isEmpty) {
                        text = editor.document.getText(range).trim()
                    }
                } catch (_: Throwable) {
                }
            }
            text
        }
        if (!code.isNullOrBlank()) {
            fileSystemWidget.instantRun(code, true)
        }
    }
}

class OpenMpyFile : ReplAction("Open file", true) {

    override val actionDescription: String = "Open file"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val fileSystemWidget = fileSystemWidget(e)
        e.presentation.isEnabledAndVisible = fileSystemWidget != null &&
                fileSystemWidget.state == State.CONNECTED &&
                fileSystemWidget.selectedFiles().any { it is FileNode }
    }

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {

        val selectedFiles = withContext(Dispatchers.EDT) {
            fileSystemWidget.selectedFiles().mapNotNull { it as? FileNode }
        }
        for (file in selectedFiles) {
            var text = fileSystemWidget.download(file.fullName).toString(StandardCharsets.UTF_8)
            withContext(Dispatchers.EDT) {
                var fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(file.name)
                if (fileType.isBinary) {
                    fileType = PlainTextFileType.INSTANCE
                } else {
                    //hack for LightVirtualFile and line endings
                    text = StringUtilRt.convertLineSeparators(text)
                }
                val file = LightVirtualFile("micropython: ${file.fullName}", fileType, text)
                file.isWritable = false
                FileEditorManager.getInstance(fileSystemWidget.project).openFile(file, true, true)
            }
        }
    }
}

open class UploadFile() : DumbAwareAction("Upload File(s) to Micropython device") {
    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project != null
            && file?.isInLocalFileSystem == true
            && ModuleUtil.findModuleForFile(file, project)?.microPythonFacet != null
        ) {
            e.presentation.text =
                if (file.isDirectory) "Upload Directory to Micropython device" else "Upload File to Micropython device"
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
            FileDocumentManager.getInstance().saveAllDocuments()
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file != null) {
            MicroPythonRunConfiguration.uploadFileOrFolder(e.project ?: return, file)
        }
    }
}

class OpenSettingsAction : DumbAwareAction("Settings") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MicroPythonProjectConfigurable::class.java)
    }
}

class InterruptAction: ReplAction("Interrupt", true) {
    override fun getActionUpdateThread(): ActionUpdateThread = BGT
    override fun update(e: AnActionEvent)  = enableIfConnected(e)
    override val actionDescription: @NlsContexts.DialogMessage String = "Interrupt..."

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        fileSystemWidget.interrupt()
    }
}

class SoftResetAction: ReplAction("Reset", true) {
    override fun getActionUpdateThread(): ActionUpdateThread = BGT
    override fun update(e: AnActionEvent)  = enableIfConnected(e)
    override val actionDescription: @NlsContexts.DialogMessage String = "Reset..."

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        fileSystemWidget.reset()
        fileSystemWidget.clearTerminalIfNeeded()
    }
}

class CreateDeviceFolderAction : ReplAction("New Folder", true) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun selectedFolder(fileSystemWidget: FileSystemWidget): DirNode? =
        fileSystemWidget.selectedFiles().firstOrNull().asSafely<DirNode>()

    override fun update(e: AnActionEvent) {
        val fileSystemWidget = fileSystemWidget(e)
        if (fileSystemWidget?.state != State.CONNECTED || selectedFolder(fileSystemWidget) == null) {
            e.presentation.isEnabled = false
        }
    }

    override val actionDescription: @NlsContexts.DialogMessage String = "New folder is created..."

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {

        val parent = selectedFolder(fileSystemWidget) ?: return

        val validator = object : InputValidator {
            override fun checkInput(inputString: String): Boolean {
                if (!PathUtilRt.isValidFileName(inputString, Platform.UNIX, true, Charsets.US_ASCII)) return false
                return parent.children().asSequence().none { it.asSafely<FileSystemNode>()?.name == inputString }
            }

            override fun canClose(inputString: String): Boolean = checkInput(inputString)
        }

        val newName = withContext(Dispatchers.EDT) {
            Messages.showInputDialog(
                fileSystemWidget.project,
                "Name:", "Create New Folder", AllIcons.Actions.AddDirectory,
                "new_folder", validator
            )
        }

        if (!newName.isNullOrBlank()) {
            fileSystemWidget.blindExecute(TIMEOUT, "import os; os.mkdir('${parent.fullName}/$newName')")
                .extractSingleResponse()
            fileSystemWidget.refresh()
        }

    }
}