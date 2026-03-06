package com.revfcu.keyscript.options

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "com.revfcu.keyscript.options.ScriptOptionsService",
    storages = [Storage("KeyscriptOptions.xml")]
)
class ScriptOptionsService(private val project: Project) : PersistentStateComponent<ScriptOptionsService.State> {

    class State {
        var personSerial: String = ""
        var accountSerial: String = ""
        var noteSerial: String = ""
        var commentSerial: String = ""
        var transactionSerial: String = ""
        var checkSerial: String = ""
        var cardSerial: String = ""
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): ScriptOptionsService = project.getService(ScriptOptionsService::class.java)
    }

    var personSerial: String
        get() = myState.personSerial
        set(value) { myState.personSerial = value }

    var accountSerial: String
        get() = myState.accountSerial
        set(value) { myState.accountSerial = value }

    var noteSerial: String
        get() = myState.noteSerial
        set(value) { myState.noteSerial = value }

    var commentSerial: String
        get() = myState.commentSerial
        set(value) { myState.commentSerial = value }

    var transactionSerial: String
        get() = myState.transactionSerial
        set(value) { myState.transactionSerial = value }

    var checkSerial: String
        get() = myState.checkSerial
        set(value) { myState.checkSerial = value }

    var cardSerial: String
        get() = myState.cardSerial
        set(value) { myState.cardSerial = value }
}
