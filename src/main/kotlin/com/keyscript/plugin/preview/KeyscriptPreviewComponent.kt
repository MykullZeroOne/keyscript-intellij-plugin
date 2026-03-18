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
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.net.URI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class KeyscriptPreviewComponent(private val project: Project) {
    private val log = Logger.getInstance(KeyscriptPreviewComponent::class.java)
    private val cards = CardLayout()
    private val container = JPanel(cards)
    private val messageLabel = JBLabel("", SwingConstants.CENTER)
    private val browserPanel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var currentUrl: String? = null
    @Volatile
    private var browserReady = false
    private var pendingUrl: String? = null

    /** State captured from the preview before a preserving reload. */
    @Volatile
    private var pendingRestoreState: String? = null

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

        // Set JSESSIONID cookie in JCEF if available.
        // This is a best-effort optimization — the proxy also injects this cookie on responses,
        // so the preview still works even if JCEF cookie setting fails (e.g., CEF not yet initialized).
        tryCefCookie(url)

        cards.show(container, "browser")

        if (browserReady) {
            log.info("loadUrl: browser ready, loading immediately: $url")
            previewBrowser.loadURL(url)
        } else {
            // JCEF browser not ready yet — queue the URL for when onAfterCreated fires
            log.info("loadUrl: browser not ready, queuing URL: $url")
            pendingUrl = url
        }
    }

    private fun tryCefCookie(url: String) {
        val ssoSessionId = ProxyServerService.getInstance(project).ssoSessionId
        if (ssoSessionId.isNotEmpty()) {
            try {
                val cookieManager = org.cef.network.CefCookieManager.getGlobalManager()
                if (cookieManager != null) {
                    val parsed = URI.create(url).toURL()
                    val origin = "${parsed.protocol}://${parsed.host}:${parsed.port}"
                    val cookie = org.cef.network.CefCookie(
                        "JSESSIONID", ssoSessionId,
                        parsed.host, "/",
                        parsed.protocol == "https",
                        true, null, null, false, null
                    )
                    cookieManager.setCookie(origin, cookie)
                }
            } catch (e: Exception) {
                log.info("JCEF cookie not set (CEF may still be initializing) — proxy will inject cookie on responses")
            }
        }
    }

    fun showMessage(message: String) {
        messageLabel.text = "<html><body style='text-align:center;padding:16px;'>$message</body></html>"
        cards.show(container, "message")
    }

    fun reload() {
        browser?.cefBrowser?.reload()
    }

    /**
     * Reload the page while preserving user-entered form state (input values,
     * scroll positions, ExtJS component values). State is captured via
     * console.log bridge, stored Java-side, then injected after reload.
     */
    fun reloadPreservingState() {
        val cef = browser?.cefBrowser ?: return
        // Execute JS to capture state — the console handler intercepts the
        // __KS_STATE__ prefixed message and stores it in pendingRestoreState
        cef.executeJavaScript(CAPTURE_STATE_JS, cef.url, 0)
        // Give the JS time to execute and the console handler to capture,
        // then trigger the reload
        javax.swing.Timer(100) {
            cef.reload()
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun openInBrowser() {
        currentUrl?.let(BrowserUtil::browse)
    }

    fun hasLoadedUrl(): Boolean = currentUrl != null

    fun currentUrl(): String? = currentUrl

    fun dispose() {
        pendingUrl = null
        pendingRestoreState = null
        browserReady = false
        browser?.dispose()
        browser = null
    }

    private fun ensureBrowser(): JBCefBrowser? {
        if (!JBCefApp.isSupported()) {
            return null
        }

        browser?.let { return it }

        browserReady = false
        val createdBrowser = JBCefBrowser()

        // Detect when CEF browser is fully initialized — loadURL calls before this are silently dropped
        createdBrowser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(cefBrowser: CefBrowser?) {
                browserReady = true
                log.info("JCEF browser ready (onAfterCreated)")
                SwingUtilities.invokeLater {
                    val url = pendingUrl
                    if (url != null) {
                        pendingUrl = null
                        log.info("Loading pending URL: $url")
                        tryCefCookie(url)
                        createdBrowser.loadURL(url)
                    }
                }
            }
        }, createdBrowser.cefBrowser)

        // Console handler — captures state bridge messages and forwards others to network monitor
        createdBrowser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                if (message != null) {
                    // Intercept state capture messages from reloadPreservingState()
                    if (message.startsWith(STATE_BRIDGE_PREFIX)) {
                        pendingRestoreState = message.removePrefix(STATE_BRIDGE_PREFIX)
                        log.info("Captured preview state for restore (${pendingRestoreState?.length ?: 0} chars)")
                        return true // suppress from network monitor
                    }

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

        // Load handler — restores form state after a preserving reload completes
        createdBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame == null || !frame.isMain) return
                val stateJson = pendingRestoreState ?: return
                pendingRestoreState = null
                log.info("Page loaded after preserving reload, restoring state...")
                // Wait for the user's script to render ExtJS components before restoring
                val escaped = stateJson
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                val restoreJs = RESTORE_STATE_JS.replace("__STATE_JSON__", escaped)
                cefBrowser?.executeJavaScript(restoreJs, cefBrowser.url, 0)
            }
        }, createdBrowser.cefBrowser)

        browserPanel.removeAll()
        browserPanel.add(createdBrowser.component, BorderLayout.CENTER)
        browserPanel.revalidate()
        browserPanel.repaint()
        browser = createdBrowser
        return createdBrowser
    }

    companion object {
        private const val STATE_BRIDGE_PREFIX = "__KS_STATE__:"

        /**
         * JavaScript that captures form inputs, scroll position, and ExtJS
         * component values, then sends them to Java via console.log bridge.
         */
        private val CAPTURE_STATE_JS = """
            (function() {
                try {
                    var state = {
                        scrollTop: document.documentElement.scrollTop || document.body.scrollTop,
                        scrollLeft: document.documentElement.scrollLeft || document.body.scrollLeft,
                        inputs: {}
                    };
                    var els = document.querySelectorAll('input, textarea, select');
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var key = el.name || el.id || ('__ks_idx_' + el.tagName + '_' + i);
                        if (el.tagName === 'SELECT') {
                            state.inputs[key] = { tag: 'select', selectedIndex: el.selectedIndex };
                        } else if (el.type === 'checkbox' || el.type === 'radio') {
                            state.inputs[key] = { tag: el.type, checked: el.checked };
                        } else {
                            state.inputs[key] = { tag: 'text', value: el.value };
                        }
                    }
                    if (window.Ext && Ext.ComponentMgr) {
                        state.extValues = {};
                        Ext.ComponentMgr.all.each(function(key, cmp) {
                            if (cmp && cmp.getValue && typeof cmp.getValue === 'function') {
                                try {
                                    var v = cmp.getValue();
                                    if (v !== undefined && v !== null && v !== '') {
                                        state.extValues[cmp.id] = v;
                                    }
                                } catch(e) {}
                            }
                        });
                    }
                    console.log('__KS_STATE__:' + JSON.stringify(state));
                } catch(e) {
                    console.warn('[KeyScript IDE] Failed to capture preview state:', e);
                }
            })();
        """.trimIndent()

        /**
         * JavaScript that restores form state after reload.
         * __STATE_JSON__ is replaced with the escaped state string at runtime.
         */
        private val RESTORE_STATE_JS = """
            (function() {
                function doRestore() {
                    try {
                        var state = JSON.parse('__STATE_JSON__');
                        var els = document.querySelectorAll('input, textarea, select');
                        for (var i = 0; i < els.length; i++) {
                            var el = els[i];
                            var key = el.name || el.id || ('__ks_idx_' + el.tagName + '_' + i);
                            var d = state.inputs && state.inputs[key];
                            if (!d) continue;
                            if (d.tag === 'select') { el.selectedIndex = d.selectedIndex; }
                            else if (d.tag === 'checkbox' || d.tag === 'radio') { el.checked = d.checked; }
                            else { el.value = d.value; }
                        }
                        if (state.extValues && window.Ext && Ext.getCmp) {
                            for (var id in state.extValues) {
                                if (!state.extValues.hasOwnProperty(id)) continue;
                                var cmp = Ext.getCmp(id);
                                if (cmp && cmp.setValue) {
                                    try { cmp.setValue(state.extValues[id]); } catch(e) {}
                                }
                            }
                        }
                        if (state.scrollTop) {
                            document.documentElement.scrollTop = state.scrollTop;
                            document.body.scrollTop = state.scrollTop;
                        }
                        if (state.scrollLeft) {
                            document.documentElement.scrollLeft = state.scrollLeft;
                            document.body.scrollLeft = state.scrollLeft;
                        }
                        console.log('[KeyScript IDE] Preview state restored');
                    } catch(e) {
                        console.warn('[KeyScript IDE] Failed to restore preview state:', e);
                    }
                }
                // Wait for user script to load and ExtJS components to render
                if (window.CR && CR._scriptReady) {
                    setTimeout(doRestore, 800);
                } else {
                    // Poll until the user script has been loaded
                    var attempts = 0;
                    var check = setInterval(function() {
                        attempts++;
                        if ((window.CR && CR._scriptReady) || attempts > 30) {
                            clearInterval(check);
                            setTimeout(doRestore, 800);
                        }
                    }, 200);
                }
            })();
        """.trimIndent()
    }
}
