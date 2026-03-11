package com.keyscript.plugin.runconfig

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.keyscript.plugin.services.KeyscriptFileSupport
import com.keyscript.plugin.services.RunKeyscriptService

/**
 * Auto-creates Keyscript run configurations for .js files.
 * Provides the "Run as Keyscript" option in the gutter and context menus.
 */
class KeyscriptRunConfigurationProducer : LazyRunConfigurationProducer<KeyscriptRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return KeyscriptRunConfigurationType().configurationFactories[0]
    }

    override fun setupConfigurationFromContext(
        configuration: KeyscriptRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!KeyscriptFileSupport.isKeyscriptFile(file)) return false

        configuration.scriptPath = RunKeyscriptService.resolveScriptPath(configuration.project, file)
        configuration.name = "Keyscript: ${file.nameWithoutExtension}"
        return true
    }

    override fun isConfigurationFromContext(
        configuration: KeyscriptRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return KeyscriptFileSupport.isKeyscriptFile(file) &&
            configuration.scriptPath == RunKeyscriptService.resolveScriptPath(configuration.project, file)
    }
}
