package com.jetbrains.micropython.nova

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jediterm.terminal.TerminalMode
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import javax.swing.JComponent

internal const val NOTIFICATION_GROUP = "Micropython"
internal const val TOOL_WINDOW_ID = "com.jetbrains.micropython.nova.MicroPythonToolWindow"

class MicroPythonToolWindow : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val newDisposable = Disposer.newDisposable(toolWindow.disposable, "Webrepl")

        val fileSystemWidget = FileSystemWidget(project, newDisposable)
        val fileSystemContent = ContentFactory.getInstance().createContent(fileSystemWidget, "File System", true)
        fileSystemContent.setDisposer(newDisposable)
        toolWindow.contentManager.addContent(fileSystemContent)

        val jediTermWidget = jediTermWidget(project, newDisposable, fileSystemWidget.ttyConnector)
        val terminalContent = ContentFactory.getInstance().createContent(jediTermWidget, "REPL", true)
        terminalContent.setDisposer(newDisposable)
        toolWindow.contentManager.addContent(terminalContent)
        fileSystemWidget.terminalContent = terminalContent
    }

    private fun jediTermWidget(project: Project, disposable: Disposable, connector: TtyConnector): JComponent {
        val mySettingsProvider = JBTerminalSystemSettingsProvider()
        val terminal = JBTerminalWidget(project, mySettingsProvider, disposable)
        terminal.isEnabled = false
        with(terminal.terminal) {
            setModeEnabled(TerminalMode.ANSI, true)
            setModeEnabled(TerminalMode.AutoNewLine, true)
            setModeEnabled(TerminalMode.WideColumn, true)
        }
        terminal.ttyConnector = connector
        terminal.start()

        val widget = BorderLayoutPanel()
        widget.addToCenter(terminal)
        val actions = ActionManager.getInstance().getAction("micropython.repl.ReplToolbar") as ActionGroup
        val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actions, true)
        actionToolbar.targetComponent = terminal
        widget.addToTop(actionToolbar.component)
        return widget

    }

}

