package com.keyscript.plugin.preview

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.keyscript.plugin.services.NetworkMonitorService
import com.keyscript.plugin.services.ProxyServerService
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.net.URI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class KeyscriptPreviewComponent(private val project: Project) {
    private val log = Logger.getInstance(KeyscriptPreviewComponent::class.java)
    private val cards = CardLayout()
    private val container = JPanel(cards)
    private val messageLabel = JBLabel("", SwingConstants.CENTER)
    private val browserPanel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var currentUrl: String? = null

    val component: JComponent = container

    init {
        val messagePanel = JPanel(BorderLayout()).apply {
            add(messageLabel, BorderLayout.CENTER)
        }
        container.add(messagePanel, "message")
        container.add(browserPanel, "browser")

        if (JBCefApp.isSupported()) {
            showMessage("Preview is ready. Toggle split preview or run the script to load it.")
        } else {
            showMessage("JCEF is not available in this IDE. Use browser preview instead.")
        }
    }

    fun loadUrl(url: String) {
        currentUrl = url
        val previewBrowser = ensureBrowser()
        if (previewBrowser == null) {
            showMessage("JCEF is not available in this IDE. Use browser preview instead.")
            return
        }

        val ssoSessionId = ProxyServerService.getInstance(project).ssoSessionId
        if (ssoSessionId.isNotEmpty()) {
            try {
                val cookieManager = org.cef.network.CefCookieManager.getGlobalManager()
                val parsed = URI.create(url).toURL()
                val origin = "${parsed.protocol}://${parsed.host}:${parsed.port}"
                val cookie = org.cef.network.CefCookie(
                    "JSESSIONID", ssoSessionId,
                    parsed.host, "/",
                    parsed.protocol == "https",
                    true, null, null, false, null
                )
                cookieManager?.setCookie(origin, cookie)
            } catch (e: Exception) {
                log.warn("Failed to set preview cookie", e)
            }
        }

        cards.show(container, "browser")
        previewBrowser.loadURL(url)
    }

    fun showMessage(message: String) {
        messageLabel.text = "<html><body style='text-align:center;padding:16px;'>$message</body></html>"
        cards.show(container, "message")
    }

    fun reload() {
        browser?.cefBrowser?.reload()
    }

    fun openInBrowser() {
        currentUrl?.let(BrowserUtil::browse)
    }

    fun hasLoadedUrl(): Boolean = currentUrl != null

    fun currentUrl(): String? = currentUrl

    fun dispose() {
        browser?.dispose()
        browser = null
    }

    private fun ensureBrowser(): JBCefBrowser? {
        if (!JBCefApp.isSupported()) {
            return null
        }

        browser?.let { return it }

        val createdBrowser = JBCefBrowser()
        createdBrowser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                if (message != null) {
                    NetworkMonitorService.getInstance(project).addEvent(
                        NetworkMonitorService.NetworkEvent(
                            id = "console-${System.currentTimeMillis()}",
                            type = "console",
                            method = level?.name ?: "LOG",
                            url = source ?: "",
                            body = message,
                            status = line
                        )
                    )
                }
                return false
            }
        }, createdBrowser.cefBrowser)

        browserPanel.removeAll()
        browserPanel.add(createdBrowser.component, BorderLayout.CENTER)
        browserPanel.revalidate()
        browserPanel.repaint()
        browser = createdBrowser
        return createdBrowser
    }
}
