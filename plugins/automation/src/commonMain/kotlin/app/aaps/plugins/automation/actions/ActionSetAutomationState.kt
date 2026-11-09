package app.aaps.plugins.automation.actions

import androidx.compose.runtime.mutableStateOf
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSetAutomationState(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val automationState: AutomationStateInterface
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var inputStateName = mutableStateOf("")
    var inputStateValue = mutableStateOf("")

    val stateNames: List<String>
        get() = automationState.getAllStates().map { it.first }.distinct()

    fun getStateValues(stateName: String): List<String> =
        if (stateName.isNotEmpty()) automationState.getStateValues(stateName) else emptyList()

    override fun friendlyName(): TextRef = AutomationStrings.set_state
    override fun shortDescription(): String = rh.gs(AutomationStrings.set_state_description, inputStateName.value, inputStateValue.value)
    override fun isValid(): Boolean = inputStateName.value.isNotEmpty() && inputStateValue.value.isNotEmpty()
    override fun hasDialog(): Boolean = true

    override suspend fun doAction(): PumpEnactResult {
        automationState.setState(inputStateName.value, inputStateValue.value)
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("inputStateName", inputStateName.value)
            put("inputState", inputStateValue.value)
        }
        return buildJsonObject {
            put("type", this@ActionSetAutomationState::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        inputStateName.value = o.lenientString("inputStateName")
        inputStateValue.value = o.lenientString("inputState")
        return this
    }
}
