package com.keyscript.plugin.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons
import javax.swing.Icon

class KeyscriptRunConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "Keyscript"
    override fun getConfigurationTypeDescription(): String = "Run a Keyscript in the preview panel"
    override fun getIcon(): Icon = AllIcons.Actions.Execute
    override fun getId(): String = "KeyscriptRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(KeyscriptConfigurationFactory(this))
    }

    companion object {
        const val ID = "KeyscriptRunConfiguration"
    }
}
