package com.keyscript.plugin.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

class KeyscriptConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = KeyscriptRunConfigurationType.ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return KeyscriptRunConfiguration(project, this, "Keyscript")
    }

    override fun getOptionsClass(): Class<out BaseState> = KeyscriptRunConfigurationOptions::class.java
}
