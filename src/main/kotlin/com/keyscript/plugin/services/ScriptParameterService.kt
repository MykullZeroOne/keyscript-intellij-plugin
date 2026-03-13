package com.keyscript.plugin.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Manages script execution parameters: person serial, account serial, instance.
 * State is persisted per-project so values are remembered across IDE restarts.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.keyscript.plugin.services.ScriptParameterService",
    storages = [Storage("KeyscriptParameters.xml")]
)
class ScriptParameterService(private val project: Project) : PersistentStateComponent<ScriptParameterService.State> {

    class State {
        var personSerial: String = ""
        var accountSerial: String = ""
        var workTaskSerial: String = ""
        var instance: String = ""
        var debugMode: Boolean = false
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    var personSerial: String
        get() = myState.personSerial
        set(value) { myState.personSerial = value }

    var accountSerial: String
        get() = myState.accountSerial
        set(value) { myState.accountSerial = value }

    var workTaskSerial: String
        get() = myState.workTaskSerial
        set(value) { myState.workTaskSerial = value }

    var instance: String
        get() = myState.instance
        set(value) { myState.instance = value }

    var debugMode: Boolean
        get() = myState.debugMode
        set(value) { myState.debugMode = value }

    /**
     * Build the script parameters JSON structure matching the Electron IDE format.
     * This is what gets posted to SessionStore and injected into iframe-target.html.
     */
    fun getScriptParameters(): Map<String, Any> {
        val session = SessionService.getInstance(project)

        val crlogin = mutableMapOf<String, String>()
        // Spread full login data (postingDate, locationName, databaseName, userSerial, etc.)
        crlogin.putAll(session.loginData)
        crlogin["instance"] = instance.ifEmpty {
            com.keyscript.plugin.settings.KeyscriptSettings.getInstance().getDefaultInstance()
        }
        crlogin["userName"] = session.username
        crlogin["JSESSIONID"] = session.jsessionId
        crlogin["isLoggedIn"] = session.isLoggedIn.toString()

        val crscript = mutableMapOf<String, String>()
        crscript["personSerial"] = personSerial
        crscript["accountSerial"] = accountSerial
        crscript["workTaskSerial"] = workTaskSerial
        crscript["scriptDefaultPanelId"] = "ks-script-panel"
        crscript["scriptPanelId"] = "ks-script-panel"
        crscript["hostPanelId"] = "ks-host-panel"

        return mapOf("crlogin" to crlogin, "crscript" to crscript)
    }

    companion object {
        fun getInstance(project: Project): ScriptParameterService =
            project.getService(ScriptParameterService::class.java)
    }
}
