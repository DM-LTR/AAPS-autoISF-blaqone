package app.aaps.plugins.automationstate.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class AutomationStateUiState(
    val states: List<Pair<String, String>> = emptyList(),
    val stateValues: Map<String, List<String>> = emptyMap()
)

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class AutomationStateViewModel @Inject constructor(
    private val automationStateService: AutomationStateInterface,
    preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationStateUiState())
    val uiState: StateFlow<AutomationStateUiState> = _uiState.asStateFlow()

    init {
        // AutoISF fork: the screen follows every change of the states at once - from an automation, a
        // scene, a settings import or this screen - like the States tab of AutoISF 3.2.1 did.
        combine(
            preferences.observe(StringNonKey.AutomationStateCurrent),
            preferences.observe(StringNonKey.AutomationStateValues)
        ) { _, _ -> }
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun reload() {
        val states = automationStateService.getAllStates()
        val values = states.associate { (name, _) -> name to automationStateService.getStateValues(name) }
        _uiState.update { it.copy(states = states, stateValues = values) }
    }

    fun setState(name: String, value: String) {
        try {
            automationStateService.setState(name, value)
            reload()
        } catch (_: Exception) {}
    }

    fun setStateValues(name: String, values: List<String>) {
        if (values.isEmpty()) return
        automationStateService.setStateValues(name, values)
        // Set first value as current if not yet initialized
        if (automationStateService.getState(name).isEmpty()) {
            try { automationStateService.setState(name, values.first()) } catch (_: Exception) {}
        }
        reload()
    }

    fun deleteState(name: String) {
        automationStateService.deleteState(name)
        reload()
    }
}
