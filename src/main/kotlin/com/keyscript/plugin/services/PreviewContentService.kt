package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PreviewContentService(private val project: Project) {
    private val scriptOverrides = ConcurrentHashMap<String, String>()

    fun setScriptOverride(relativePath: String, content: String) {
        scriptOverrides[relativePath] = content
    }

    fun getScriptOverride(relativePath: String): String? = scriptOverrides[relativePath]

    fun removeScriptOverride(relativePath: String) {
        scriptOverrides.remove(relativePath)
    }

    companion object {
        fun getInstance(project: Project): PreviewContentService =
            project.getService(PreviewContentService::class.java)
    }
}
