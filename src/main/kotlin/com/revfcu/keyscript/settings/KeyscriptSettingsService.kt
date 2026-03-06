package com.revfcu.keyscript.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "com.revfcu.keyscript.settings.KeyscriptSettingsService",
    storages = [Storage("KeyscriptSettings.xml")]
)
class KeyscriptSettingsService : PersistentStateComponent<KeyscriptSettingsService.State> {

    data class State(
        var keybridgeUrl: String = "http://keystonedev.revfcu.com:52310/Development",
        var keystoneExecutionUrl: String = "http://keystonedev.revfcu.com:52310/Development/Execution"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): KeyscriptSettingsService = ApplicationManager.getApplication().getService(KeyscriptSettingsService::class.java)
    }

    var keybridgeUrl: String
        get() = myState.keybridgeUrl
        set(value) { myState.keybridgeUrl = value }

    var keystoneExecutionUrl: String
        get() = myState.keystoneExecutionUrl
        set(value) { myState.keystoneExecutionUrl = value }
}
