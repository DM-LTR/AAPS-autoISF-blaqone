package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder for the fork's AutomationState feature. The implementation is
 * `:plugins:automation-state`, a plain Android library (it stores state in SharedPreferences and
 * has no Apple variant), so the iOS graph has nothing to bind.
 *
 * Every call throws rather than answering: an action or trigger that reads state and gets an empty
 * answer would run down a branch the user never configured. Worth saying plainly - this is a port
 * candidate, not an iOS limitation: the state store is a string map, and it should move to
 * commonMain rather than be reimplemented here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAutomationState @Inject constructor(
    private val aapsLogger: AAPSLogger
) : AutomationStateInterface {

    override fun inState(stateName: String, state: String): Boolean =
        aapsLogger.failNotOnIosYet("AutomationState.inState")

    override fun setState(stateName: String, state: String) =
        aapsLogger.failNotOnIosYet("AutomationState.setState")

    override fun getState(stateName: String): String =
        aapsLogger.failNotOnIosYet("AutomationState.getState")

    override fun getAllStates(): List<Pair<String, String>> =
        aapsLogger.failNotOnIosYet("AutomationState.getAllStates")

    override fun getStateValues(stateName: String): List<String> =
        aapsLogger.failNotOnIosYet("AutomationState.getStateValues")

    override fun setStateValues(stateName: String, values: List<String>) =
        aapsLogger.failNotOnIosYet("AutomationState.setStateValues")

    override fun hasStateValues(stateName: String): Boolean =
        aapsLogger.failNotOnIosYet("AutomationState.hasStateValues")

    override fun deleteState(stateName: String) =
        aapsLogger.failNotOnIosYet("AutomationState.deleteState")
}
