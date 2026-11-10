package app.aaps.plugins.automation

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

/**
 * AutoISF fork: the automation states (for example "Sleeping" = "yes") and the values each state may take.
 *
 * Both are plain string maps, kept as JSON in two preferences, so this lives in shared code and works on
 * every platform. It used to be in the Android-only `:plugins:automation-state` module, and iOS and
 * desktop had placeholders that threw on every call.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutomationStateStore @Inject constructor(
    private val preferences: Preferences
) : AutomationStateInterface {

    private val automationStates: HashMap<String, String> = readMap(StringNonKey.AutomationStateCurrent)
    private val stateValues: HashMap<String, List<String>> = readMap(StringNonKey.AutomationStateValues)

    private inline fun <reified T> readMap(key: StringNonKey): HashMap<String, T> =
        try {
            Json.decodeFromString<HashMap<String, T>>(preferences.get(key))
        } catch (_: Exception) {
            HashMap()
        }

    private fun saveStates() = preferences.put(StringNonKey.AutomationStateCurrent, Json.encodeToString(automationStates))
    private fun saveValues() = preferences.put(StringNonKey.AutomationStateValues, Json.encodeToString(stateValues))

    override fun inState(stateName: String, state: String): Boolean =
        automationStates[stateName.trim()]?.let { it == state.trim() } ?: false

    /** Sets [stateName] to [state]. Both must have been defined with [setStateValues] before. */
    override fun setState(stateName: String, state: String) {
        val trimmedName = stateName.trim()
        val trimmedState = state.trim()
        val allowed = stateValues[trimmedName]
        require(allowed != null) { "Invalid state name: $trimmedName" }
        require(allowed.contains(trimmedState)) { "Invalid state value: $trimmedState" }
        automationStates[trimmedName] = trimmedState
        saveStates()
    }

    /** The current value of [stateName], empty when it has none. */
    override fun getState(stateName: String): String = automationStates[stateName.trim()] ?: ""

    override fun getAllStates(): List<Pair<String, String>> = automationStates.toList()

    override fun getStateValues(stateName: String): List<String> = stateValues[stateName.trim()] ?: emptyList()

    /** Defines the values [stateName] may take. A current value that is no longer allowed is cleared. */
    override fun setStateValues(stateName: String, values: List<String>) {
        val trimmedName = stateName.trim()
        val trimmedValues = values.map { it.trim() }
        val currentState = automationStates[trimmedName]
        if (currentState != null && !trimmedValues.contains(currentState)) {
            automationStates.remove(trimmedName)
            saveStates()
        }
        stateValues[trimmedName] = trimmedValues
        saveValues()
    }

    override fun hasStateValues(stateName: String): Boolean = stateValues.containsKey(stateName.trim())

    override fun deleteState(stateName: String) {
        val trimmedName = stateName.trim()
        automationStates.remove(trimmedName)
        stateValues.remove(trimmedName)
        saveStates()
        saveValues()
    }
}
