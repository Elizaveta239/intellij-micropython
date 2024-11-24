package com.jetbrains.micropython.nova

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.Messages
import com.jetbrains.micropython.settings.MicroPythonProjectConfigurable
import com.jetbrains.micropython.settings.microPythonFacet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectAction(text: String = "Connect") : ReplAction(text, false) {

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
            msg = "No port is selected"
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
            try {
                fileSystemWidget.refresh()
            } finally {
                ActivityTracker.getInstance().inc()
            }
        }
    }
}

