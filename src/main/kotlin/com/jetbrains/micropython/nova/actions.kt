package com.jetbrains.micropython.nova

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LightVirtualFile
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

class Connect(text: String = "Connect") : ReplAction(text, false) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Connect"

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        doConnect(fileSystemWidget)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = when (fileSystemWidget(e)?.state) {
            State.DISCONNECTING, State.DISCONNECTED, null -> true
            State.CONNECTING, State.CONNECTED, State.TTY_DETACHED -> false
        }
    }
}

suspend fun doConnect(fileSystemWidget: FileSystemWidget) {
    if (fileSystemWidget.state == State.CONNECTED) return
    val facet = fileSystemWidget.project.modules.firstNotNullOfOrNull { it.microPythonFacet } ?: return
    var msg: String? = null
    val connectionParameters: ConnectionParameters?
    if (facet.configuration.uart) {
        val portName = facet.configuration.portName
        if (portName.isBlank()) {
            msg = "Port is not selected"
            connectionParameters = null
        } else {
            connectionParameters = ConnectionParameters(portName)
        }

    } else {
        val url = facet.configuration.webReplUrl
        val password = fileSystemWidget.project.service<ConnectCredentials>().retrievePassword(url)
        msg = messageForBrokenUrl(url)
        if (password.isBlank()) {
            msg = "Empty password"
            connectionParameters = null
        } else {
            connectionParameters = ConnectionParameters(url, password)
        }

    }
    if (msg != null) {
        withContext(Dispatchers.EDT) {
            val result = Messages.showIdeaMessageDialog(
                fileSystemWidget.project,
                msg,
                "Cannot Connect",
                arrayOf("OK", "Settings..."),
                1,
                AllIcons.General.ErrorDialog,
                null
            )
            if (result == 1) {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(fileSystemWidget.project, MicroPythonProjectConfigurable::class.java)
            }
        }
    } else {
        if (connectionParameters != null) {
            fileSystemWidget.setConnectionParams(connectionParameters)
            fileSystemWidget.connect()
            fileSystemWidget.refresh()
            ActivityTracker.getInstance().inc()
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
        e.presentation.isEnabled = selectedFiles?.any { it.fullName != "/" } == true
    }
}

class InstantRun : ReplAction("Instant Run", true) {
    override val actionDescription: String = "Run code"
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { FileEditorManager.getInstance(it).selectedEditor } != null
    }

    override suspend fun performAction(fileSystemWidget: FileSystemWidget) {
        val code = withContext(Dispatchers.EDT) {
            FileEditorManager.getInstance(fileSystemWidget.project).selectedEditor.asSafely<TextEditor>()?.editor?.document?.text
        }
        if (code != null) {
            fileSystemWidget.instantRun(code)
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
        fun fileReadCommand(name: String) = """
with open('$name','rb') as f:
    while 1:
          b=f.read(50)
          if not b:break
          print(b.hex())
"""

        val selectedFiles = withContext(Dispatchers.EDT) {
            fileSystemWidget.selectedFiles().mapNotNull { it as? FileNode }
        }
        for (file in selectedFiles) {
            val result = fileSystemWidget.blindExecute(fileReadCommand(file.fullName)).extractSingleResponse()
            var text =
                result.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.chunked(2).map { it.toInt(16).toByte() }
                    .toByteArray().toString(StandardCharsets.UTF_8)
            withContext(Dispatchers.EDT) {
                val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(file.name)
                if(!fileType.isBinary) {
                    //hack for LightVirtualFile and \
                    text = StringUtilRt.convertLineSeparators(text)
                }
                val file = LightVirtualFile("micropython: ${file.fullName}", fileType, text)
                file.isWritable = false
                FileEditorManager.getInstance(fileSystemWidget.project).openFile(file, true)
            }
        }
    }
}

open class UploadFile() : DumbAwareAction("Upload File(s)") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        var enabled = false
        if (project != null && file?.isInLocalFileSystem == true) {
            enabled = ModuleUtil.findModuleForFile(file, project)?.microPythonFacet != null
        }
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
            FileDocumentManager.getInstance().saveAllDocuments()
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) return
        MicroPythonRunConfiguration.uploadMultipleFiles(e.project ?: return, null, files.toList())
    }
}

class OpenSettingsAction : DumbAwareAction("Settings") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MicroPythonProjectConfigurable::class.java)
    }
}
