package com.keyscript.plugin.completion

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/**
 * Live template context for Keyscript — active in JavaScript files.
 */
class KeyscriptTemplateContext : TemplateContextType("Keyscript") {
    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file
        if (!file.name.endsWith(".js") && !file.name.endsWith(".jsx")) return false
        val project = context.file.project
        return com.keyscript.plugin.services.KeyscriptProjectDetector.isKeyscriptProject(project)
    }
}
