package com.keyscript.plugin.statusbar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.ui.UIUtil
import com.intellij.util.Consumer
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.SessionService
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Status bar widget showing Keyscript login state.
 * Clicking it opens the Login dialog (or shows logout option if already logged in).
 */
class LoginStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "KeyscriptLoginStatus"
    override fun getDisplayName(): String = "Keyscript Login Status"
    override fun isAvailable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createWidget(project: Project): StatusBarWidget =
        LoginStatusBarWidget(project)
}

private class LoginStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private val session = SessionService.getInstance(project)
    private val listener = {
        UIUtil.invokeLaterIfNeeded {
            statusBar?.updateWidget(ID())
        }
    }

    override fun ID(): String = "KeyscriptLoginStatus"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        session.addListener(listener)
        statusBar.updateWidget(ID())
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        return if (session.isLoggedIn) {
            "KS: ${session.username} | ${session.instance}"
        } else {
            "KS: Not Logged In"
        }
    }

    override fun getTooltipText(): String {
        return if (session.isLoggedIn) {
            "Keyscript: Logged in as ${session.username} on ${session.instance}\nClick to manage session"
        } else {
            "Keyscript: Click to log in to Keystone"
        }
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val action = ActionManager.getInstance().getAction("Keyscript.Login") ?: return@Consumer
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .build()
        val event = AnActionEvent.createFromAnAction(action, it, "StatusBarWidget", dataContext)
        action.actionPerformed(event)
    }

    override fun dispose() {
        session.removeListener(listener)
        statusBar = null
    }
}
