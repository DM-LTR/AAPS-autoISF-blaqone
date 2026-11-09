package app.aaps.plugins.automation.triggers

import androidx.compose.runtime.mutableStateOf
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerAutomationState(deps: TriggerDeps, private val automationState: AutomationStateInterface) : Trigger(deps) {

    var inputStateName = mutableStateOf("")
    var inputStateValue = mutableStateOf("")

    val stateNames: List<String>
        get() = automationState.getAllStates().map { it.first }.distinct()

    fun getStateValues(stateName: String): List<String> =
        if (stateName.isNotEmpty()) automationState.getStateValues(stateName) else emptyList()

    override suspend fun shouldRun(): Boolean {
        val result = automationState.inState(inputStateName.value, inputStateValue.value)
        if (result) aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: ${friendlyDescription()}")
        else aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: ${friendlyDescription()}")
        return result
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("stateName", inputStateName.value)
            put("stateValue", inputStateValue.value)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        inputStateName.value = d.lenientString("stateName")
        inputStateValue.value = d.lenientString("stateValue")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.check_state_name

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.check_state_description, inputStateName.value, inputStateValue.value)

    override fun duplicate(): Trigger {
        val copy = TriggerAutomationState(deps, automationState)
        copy.inputStateName.value = inputStateName.value
        copy.inputStateValue.value = inputStateValue.value
        return copy
    }
}
