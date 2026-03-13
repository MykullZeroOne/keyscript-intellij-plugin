package com.keyscript.plugin.runconfig

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.keyscript.plugin.services.KeyscriptFileSupport

/**
 * Shows a green "Run" gutter icon on line 1 of Keyscript files,
 * similar to how Java shows a play button on the `main` method.
 */
class KeyscriptRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Only act on the very first leaf element in the file to avoid duplicate icons.
        if (element.parent !is PsiFile) return null
        val file = element.containingFile ?: return null
        if (file.firstChild !== element) return null

        val virtualFile = file.virtualFile ?: return null
        if (!KeyscriptFileSupport.isKeyscriptFile(virtualFile, element.project)) return null

        val actions = ExecutorAction.getActions(0)
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            actions,
            { "Run Keyscript" }
        )
    }
}
