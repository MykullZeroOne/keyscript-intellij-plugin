package com.revfcu.keyscript.api

import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class JCEFBrowserManager(private val project: Project) {
    fun isJCEFSupported(): Boolean {
        return JBCefApp.isSupported()
    }
}
