package com.keyscript.plugin.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.GenericProgramRunner

/**
 * Program runner for Keyscript run configurations.
 */
class KeyscriptProgramRunner : GenericProgramRunner<Nothing>() {
    override fun getRunnerId(): String = "KeyscriptProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return profile is KeyscriptRunConfiguration
    }
}
