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
import com.intellij.openapi.ui.Messages
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
import javax.swing.Icon
import kotlin.coroutines.cancellation.CancellationException


fun fileSystemWidget(project: Project?): FileSystemWidget? {
    return ToolWindowManager.getInstance(project ?: return null)
        .getToolWindow(TOOL_WINDOW_ID)
        ?.contentManager
        ?.contents
        ?.firstNotNullOfOrNull { it.component.asSafely<FileSystemWidget>() }
}

abstract class ReplAction(text: String, icon: Icon? = null) : DumbAwareAction(text, "", icon) {

    abstract val actionDescription: String

    @Throws(IOException::class, TimeoutCancellationException::class, CancellationException::class)
    abstract suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget)

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fileSystemWidget = fileSystemWidget(project) ?: return
        runWithModalProgressBlocking(project, "Board data exchange...") {
            var error: String? = null
            try {
                performAction(e, fileSystemWidget)
            } catch (e: TimeoutCancellationException) {
                error = "$actionDescription timed out"
                thisLogger().info(error, e)
            } catch (e: CancellationException) {
                error = "$actionDescription cancelled"
                thisLogger().info(error, e)
            } catch (e: IOException) {
                error = "$actionDescription I/O error - ${e.localizedMessage ?: e.message ?: "No message"}"
                thisLogger().info(error, e)
            } catch (e: Exception) {
                error = e.localizedMessage ?: e.message
                error = if (error.isNullOrBlank()) "$actionDescription error - ${e::class.simpleName}"
                else "$actionDescription error - ${e::class.simpleName}: $error"
                thisLogger().error(error, e)
            }
            if (!error.isNullOrBlank()) {
                Notifications.Bus.notify(Notification(NOTIFICATION_GROUP, error, NotificationType.ERROR), project)
            }
        }
    }


    protected fun fileSystemWidget(e: AnActionEvent): FileSystemWidget? = fileSystemWidget(e.project)
    fun enableIfConnected(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabled = false
        }
    }

}

class Refresh : ReplAction("Refresh", AllIcons.Actions.Refresh) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Refresh"

    override fun update(e: AnActionEvent) = enableIfConnected(e)

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) = fileSystemWidget.refresh()
}

class Disconnect(text: String = "Disconnect") : ReplAction(text), Toggleable {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Disconnect"

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) = fileSystemWidget.disconnect()

    override fun update(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabledAndVisible = false
        } else {
            Toggleable.setSelected(e.presentation, true)
        }
    }
}

class Connect(text: String = "Connect") : ReplAction(text) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override val actionDescription: String = "Connect"

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) {
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

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = when (fileSystemWidget(e)?.state) {
            State.DISCONNECTING, State.DISCONNECTED, null -> true
            State.CONNECTING, State.CONNECTED, State.TTY_DETACHED -> false
        }
    }
}

class DeleteFiles : ReplAction("Delete Item(s)", AllIcons.Actions.GC) {
    override val actionDescription: String = "Delete"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) {
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
        val selectedFile = fileSystemWidget(e)?.selectedFile()
        e.presentation.isEnabled = selectedFile?.fullName !in listOf("/", null)
    }
}

class InstantRun : ReplAction("Instant Run", AllIcons.Actions.Rerun) {
    override val actionDescription: String = "Run code"
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabled = false
            return
        }
        e.presentation.isEnabled = e.project?.let { FileEditorManager.getInstance(it).selectedEditor } != null
    }

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) {
        val code = withContext(Dispatchers.EDT) {
            FileEditorManager.getInstance(fileSystemWidget.project).selectedEditor.asSafely<TextEditor>()?.editor?.document?.text
        }
        if (code != null) {
            fileSystemWidget.instantRun(code)
        }
    }
}

class OpenMpyFile : ReplAction("Open file", AllIcons.Actions.MenuOpen) {

    override val actionDescription: String = "Open file"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        if (fileSystemWidget(e)?.state != State.CONNECTED) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = fileSystemWidget(e)?.selectedFile() is FileNode
    }

    override suspend fun performAction(e: AnActionEvent, fileSystemWidget: FileSystemWidget) {
        fun fileReadCommand(name: String) = """
with open('$name','rb') as f:
    while 1:
          b=f.read(50)
          if not b:break
          print(b.hex())
"""

        val selectedFile = withContext(Dispatchers.EDT) {
            fileSystemWidget.selectedFile()
        }
        if (selectedFile !is FileNode) return
        val result = fileSystemWidget.blindExecute(fileReadCommand(selectedFile.fullName)).extractSingleResponse()
        val text =
            result.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.chunked(2).map { it.toInt(16).toByte() }
                .toByteArray().toString(StandardCharsets.UTF_8)
        withContext(Dispatchers.EDT) {
            val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(selectedFile.name)
            val infoFile = LightVirtualFile("micropython: ${selectedFile.fullName}", fileType, text)
            infoFile.isWritable = false
            FileEditorManager.getInstance(fileSystemWidget.project).openFile(infoFile, false)
        }
    }
}

open class UploadFile(text: String = "Upload File(s)", icon: Icon = AllIcons.Actions.Upload) :
    DumbAwareAction(text, "", icon) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        var visible = false
        var enabled = false
        if (project != null && file != null) {
            val module = ModuleUtil.findModuleForFile(file, project)
            if (module?.microPythonFacet != null && file.isInLocalFileSystem) {
                visible = true
                enabled = fileSystemWidget(project)?.state == State.CONNECTED
            }
        }
        e.presentation.isVisible = visible
        e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
            FileDocumentManager.getInstance().saveAllDocuments()
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (files.isNullOrEmpty()) return
        MicroPythonRunConfiguration.uploadMultipleFiles(e.project ?: return, null, files.toList())
    }
}

class OpenSettingsAction : DumbAwareAction("Settings", null, AllIcons.General.Gear) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MicroPythonProjectConfigurable::class.java)
    }
}
