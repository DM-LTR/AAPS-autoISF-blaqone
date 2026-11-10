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
 * every platform.
 *
 * The preferences are the only place the states live: every call reads them fresh. A settings import
 * (applied in v4 without restarting the app) writes these preferences, and the imported states must count
 * at once. A copy held in memory would keep the states from before the import, and the next change would
 * write that old copy back over the import.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutomationStateStore @Inject constructor(
    private val preferences: Preferences
) : AutomationStateInterface {

    private fun states(): HashMap<String, String> = readMap(StringNonKey.AutomationStateCurrent)
    private fun stateValues(): HashMap<String, List<String>> = readMap(StringNonKey.AutomationStateValues)

    private inline fun <reified T> readMap(key: StringNonKey): HashMap<String, T> =
        try {
            Json.decodeFromString<HashMap<String, T>>(preferences.get(key))
        } catch (_: Exception) {
            HashMap()
        }

    private fun saveStates(states: HashMap<String, String>) = preferences.put(StringNonKey.AutomationStateCurrent, Json.encodeToString(states))
    private fun saveValues(values: HashMap<String, List<String>>) = preferences.put(StringNonKey.AutomationStateValues, Json.encodeToString(values))

    override fun inState(stateName: String, state: String): Boolean =
        states()[stateName.trim()]?.let { it == state.trim() } ?: false

    /** Sets [stateName] to [state]. Both must have been defined with [setStateValues] before. */
    override fun setState(stateName: String, state: String) {
        val trimmedName = stateName.trim()
        val trimmedState = state.trim()
        val allowed = stateValues()[trimmedName]
        require(allowed != null) { "Invalid state name: $trimmedName" }
        require(allowed.contains(trimmedState)) { "Invalid state value: $trimmedState" }
        val states = states()
        states[trimmedName] = trimmedState
        saveStates(states)
    }

    /** The current value of [stateName], empty when it has none. */
    override fun getState(stateName: String): String = states()[stateName.trim()] ?: ""

    override fun getAllStates(): List<Pair<String, String>> = states().toList()

    override fun getStateValues(stateName: String): List<String> = stateValues()[stateName.trim()] ?: emptyList()

    /** Defines the values [stateName] may take. A current value that is no longer allowed is cleared. */
    override fun setStateValues(stateName: String, values: List<String>) {
        val trimmedName = stateName.trim()
        val trimmedValues = values.map { it.trim() }
        val states = states()
        val currentState = states[trimmedName]
        if (currentState != null && !trimmedValues.contains(currentState)) {
            states.remove(trimmedName)
            saveStates(states)
        }
        val stateValues = stateValues()
        stateValues[trimmedName] = trimmedValues
        saveValues(stateValues)
    }

    override fun hasStateValues(stateName: String): Boolean = stateValues().containsKey(stateName.trim())

    override fun deleteState(stateName: String) {
        val trimmedName = stateName.trim()
        val states = states()
        val stateValues = stateValues()
        states.remove(trimmedName)
        stateValues.remove(trimmedName)
        saveStates(states)
        saveValues(stateValues)
    }
}
