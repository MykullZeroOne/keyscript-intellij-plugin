package com.keyscript.plugin.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class KeyscriptRunConfigurationOptions : RunConfigurationOptions() {
    var scriptPath by string("")
}

class KeyscriptRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<KeyscriptRunConfigurationOptions>(project, factory, name) {

    var scriptPath: String
        get() = (options as KeyscriptRunConfigurationOptions).scriptPath ?: ""
        set(value) { (options as KeyscriptRunConfigurationOptions).scriptPath = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        KeyscriptRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return KeyscriptRunProfileState(environment, this)
    }
}

class KeyscriptRunConfigurationEditor : SettingsEditor<KeyscriptRunConfiguration>() {
    private val scriptPathField = JBTextField()

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Script path:", scriptPathField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun resetEditorFrom(config: KeyscriptRunConfiguration) {
        scriptPathField.text = config.scriptPath
    }

    override fun applyEditorTo(config: KeyscriptRunConfiguration) {
        config.scriptPath = scriptPathField.text
    }
}
