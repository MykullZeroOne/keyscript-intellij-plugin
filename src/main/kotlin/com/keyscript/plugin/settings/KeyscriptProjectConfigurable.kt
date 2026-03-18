package com.keyscript.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.keyscript.plugin.services.KeyscriptProjectDetector
import java.io.File

/**
 * Project-level configurable under Settings > Language & Frameworks > Keyscript IDE.
 * Allows users to enable/disable Keyscript support for the current project.
 */
class KeyscriptProjectConfigurable(private val project: Project) : BoundConfigurable("Keyscript IDE") {

    private var enabledState = KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createPanel() = panel {
        row {
            checkBox("Enable Keyscript IDE support for this project")
                .bindSelected(
                    getter = { enabledState },
                    setter = { enabledState = it }
                )
        }
        row {
            comment(
                "When enabled, Keyscript tool windows, completions, status bar, and run " +
                        "configurations will be active.<br><br>" +
                        "This creates a <code>.keyscript</code> marker file in the project root.<br>" +
                        "Auto-detected if the project contains <code>keyscript.bundle.json</code> or " +
                        "<code>*.keyscript.js</code> files."
            )
        }
    }

    override fun isModified(): Boolean {
        val currentlyEnabled = KeyscriptProjectDetector.isKeyscriptProject(project)
        return enabledState != currentlyEnabled || super.isModified()
    }

    override fun apply() {
        super.apply()
        val basePath = project.basePath ?: return
        val markerFile = File(basePath, ".keyscript")

        if (enabledState && !markerFile.exists()) {
            markerFile.writeText("# Keyscript IDE project marker\n")
        } else if (!enabledState && markerFile.exists()) {
            markerFile.delete()
        }

        // Refresh the cached detection
        KeyscriptProjectDetector.getInstance(project).refresh()
    }

    override fun reset() {
        enabledState = KeyscriptProjectDetector.isKeyscriptProject(project)
        super.reset()
    }
}
