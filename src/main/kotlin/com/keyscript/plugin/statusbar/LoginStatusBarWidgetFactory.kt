package com.keyscript.plugin.statusbar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.services.AuthenticationService
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Status bar widget showing Keyscript login state.
 * - When not logged in: clicking opens the Login dialog.
 * - When logged in: clicking shows a popup menu to switch instance,
 *   open settings, or log out.
 */
class LoginStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "KeyscriptLoginStatus"
    override fun getDisplayName(): String = "Keyscript Login Status"
    override fun isAvailable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createWidget(project: Project): StatusBarWidget =
        LoginStatusBarWidget(project)
}

private class LoginStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

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

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { mouseEvent ->
        if (!session.isLoggedIn) {
            // Not logged in — delegate to the registered LoginAction
            val action = ActionManager.getInstance().getAction("Keyscript.Login") ?: return@Consumer
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build()
            val event = AnActionEvent.createFromAnAction(action, mouseEvent, "StatusBarWidget", dataContext)
            action.actionPerformed(event)
            return@Consumer
        }

        // Logged in — build the session management popup
        val instances = KeyscriptSettings.getInstance().supportedInstances

        val switchInstanceStep = object : BaseListPopupStep<String>("Switch Instance", instances) {
            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    switchInstance(selectedValue)
                }
                return FINAL_CHOICE
            }
        }

        val headerItem = "Logged in as ${session.username} (${session.instance})"
        val items = listOf(headerItem, "---separator---", "Switch Instance", "Open Settings...", "---separator2---", "Logout")

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>("Keyscript Session", items) {
                override fun isSelectable(value: String): Boolean =
                    value != headerItem && !value.startsWith("---separator")

                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    when (selectedValue) {
                        "Switch Instance" -> return switchInstanceStep
                        "Open Settings..." -> {
                            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keyscript IDE")
                        }
                        "Logout" -> {
                            session.clearSession()
                            ProxyServerService.getInstance(project).setSsoSession("")
                        }
                    }
                    return FINAL_CHOICE
                }

                override fun hasSubstep(selectedValue: String): Boolean =
                    selectedValue == "Switch Instance"

                override fun getSeparatorAbove(value: String): com.intellij.openapi.ui.popup.ListSeparator? =
                    if (value.startsWith("---separator")) com.intellij.openapi.ui.popup.ListSeparator() else null

                override fun getTextFor(value: String): String =
                    if (value.startsWith("---separator")) "" else value
            }
        )

        val relativePoint = com.intellij.ui.awt.RelativePoint(mouseEvent)
        popup.show(relativePoint)
    }

    private fun switchInstance(instance: String) {
        val creds = session.loadCredentials() ?: return
        val settings = KeyscriptSettings.getInstance()
        Thread({
            runBlocking {
                AuthenticationService.getInstance(project).login(
                    username = creds.first,
                    password = creds.second,
                    instance = instance,
                    deviceId = settings.deviceServiceUrl,
                    deviceName = settings.deviceName
                )
            }
        }, "keyscript-switch-instance").start()
    }

    override fun dispose() {
        session.removeListener(listener)
        statusBar = null
    }
}
