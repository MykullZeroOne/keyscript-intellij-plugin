package com.keyscript.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.KeyscriptProjectDetector
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Project-level configurable under Settings > Language & Frameworks > Keyscript IDE.
 * Allows users to enable/disable Keyscript support for the current project.
 */
class KeyscriptProjectConfigurable(private val project: Project) : Configurable {
    private var enabledCheckBox: JBCheckBox? = null

    override fun getDisplayName(): String = "Keyscript IDE"

    override fun createComponent(): JComponent {
        enabledCheckBox = JBCheckBox("Enable Keyscript IDE support for this project")
        enabledCheckBox!!.isSelected = KeyscriptProjectDetector.isKeyscriptProject(project)

        val hint = JLabel("<html>When enabled, Keyscript tool windows, completions, status bar, and run " +
                "configurations will be active.<br><br>" +
                "This creates a <code>.keyscript</code> marker file in the project root.<br>" +
                "Auto-detected if the project contains <code>keyscript.bundle.json</code> or " +
                "<code>*.keyscript.js</code> files.</html>")
        hint.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()

        return FormBuilder.createFormBuilder()
            .addComponent(enabledCheckBox!!)
            .addVerticalGap(8)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply { border = JBUI.Borders.empty(8) }
    }

    override fun isModified(): Boolean {
        val currentlyEnabled = KeyscriptProjectDetector.isKeyscriptProject(project)
        return enabledCheckBox?.isSelected != currentlyEnabled
    }

    override fun apply() {
        val basePath = project.basePath ?: return
        val markerFile = File(basePath, ".keyscript")
        val wantEnabled = enabledCheckBox?.isSelected == true

        if (wantEnabled && !markerFile.exists()) {
            markerFile.writeText("# Keyscript IDE project marker\n")
        } else if (!wantEnabled && markerFile.exists()) {
            markerFile.delete()
        }

        // Refresh the cached detection
        KeyscriptProjectDetector.getInstance(project).refresh()
    }

    override fun reset() {
        enabledCheckBox?.isSelected = KeyscriptProjectDetector.isKeyscriptProject(project)
    }
}
