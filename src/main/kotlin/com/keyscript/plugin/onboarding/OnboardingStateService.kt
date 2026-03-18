package com.keyscript.plugin.onboarding

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks onboarding milestone completion per project.
 * Persists state so the welcome wizard only shows once,
 * and the Getting Started checklist updates as users complete actions.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "com.keyscript.plugin.onboarding.OnboardingState",
    storages = [Storage("KeyscriptOnboarding.xml")]
)
class OnboardingStateService(private val project: Project) : PersistentStateComponent<OnboardingStateService.State> {

    data class State(
        var shownWelcome: Boolean = false,
        var configuredServer: Boolean = false,
        var completedFirstLogin: Boolean = false,
        var completedFirstRun: Boolean = false,
        var completedFirstDeploy: Boolean = false,
        var dismissed: Boolean = false
    )

    private var myState = State()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    val isComplete: Boolean
        get() = myState.configuredServer &&
                myState.completedFirstLogin &&
                myState.completedFirstRun

    val shouldShowWelcome: Boolean
        get() = !myState.shownWelcome && !myState.dismissed

    var shownWelcome: Boolean
        get() = myState.shownWelcome
        set(value) { myState.shownWelcome = value }

    var configuredServer: Boolean
        get() = myState.configuredServer
        set(value) {
            if (myState.configuredServer != value) {
                myState.configuredServer = value
                notifyListeners()
            }
        }

    var completedFirstLogin: Boolean
        get() = myState.completedFirstLogin
        set(value) {
            if (myState.completedFirstLogin != value) {
                myState.completedFirstLogin = value
                notifyListeners()
            }
        }

    var completedFirstRun: Boolean
        get() = myState.completedFirstRun
        set(value) {
            if (myState.completedFirstRun != value) {
                myState.completedFirstRun = value
                notifyListeners()
            }
        }

    var completedFirstDeploy: Boolean
        get() = myState.completedFirstDeploy
        set(value) {
            if (myState.completedFirstDeploy != value) {
                myState.completedFirstDeploy = value
                notifyListeners()
            }
        }

    var dismissed: Boolean
        get() = myState.dismissed
        set(value) { myState.dismissed = value }

    fun resetOnboarding() {
        myState = State()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun notifyListeners() {
        javax.swing.SwingUtilities.invokeLater { listeners.forEach { it() } }
    }

    companion object {
        fun getInstance(project: Project): OnboardingStateService =
            project.getService(OnboardingStateService::class.java)
    }
}
