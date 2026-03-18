package com.keyscript.plugin.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/**
 * Action for File > New > Keyscript File.
 * Creates a .keyscript.js file from the built-in template with the // @keyscript annotation.
 */
class NewKeyscriptFileAction : CreateFileFromTemplateAction(
    "Keyscript File",
    "Create a new Keyscript file",
    AllIcons.General.Web
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New Keyscript File")
            .addKind("Keyscript File", AllIcons.General.Web, "Keyscript File")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String {
        return "Create Keyscript File $newName"
    }
}
