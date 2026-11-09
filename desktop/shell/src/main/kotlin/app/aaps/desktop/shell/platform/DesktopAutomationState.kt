package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder for the AutomationState feature. The implementation is
 * `:plugins:automation-state`, a plain Android library (it stores state in SharedPreferences and
 * has no desktop variant), so the desktop graph has nothing to bind.
 *
 * Every call throws rather than answering: an action or trigger that reads state and gets an empty
 * answer would run down a branch the user never configured.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopAutomationState @Inject constructor(
    private val aapsLogger: AAPSLogger
) : AutomationStateInterface {

    override fun inState(stateName: String, state: String): Boolean =
        aapsLogger.failNotOnDesktopYet("AutomationState.inState")

    override fun setState(stateName: String, state: String) =
        aapsLogger.failNotOnDesktopYet("AutomationState.setState")

    override fun getState(stateName: String): String =
        aapsLogger.failNotOnDesktopYet("AutomationState.getState")

    override fun getAllStates(): List<Pair<String, String>> =
        aapsLogger.failNotOnDesktopYet("AutomationState.getAllStates")

    override fun getStateValues(stateName: String): List<String> =
        aapsLogger.failNotOnDesktopYet("AutomationState.getStateValues")

    override fun setStateValues(stateName: String, values: List<String>) =
        aapsLogger.failNotOnDesktopYet("AutomationState.setStateValues")

    override fun hasStateValues(stateName: String): Boolean =
        aapsLogger.failNotOnDesktopYet("AutomationState.hasStateValues")

    override fun deleteState(stateName: String) =
        aapsLogger.failNotOnDesktopYet("AutomationState.deleteState")
}
