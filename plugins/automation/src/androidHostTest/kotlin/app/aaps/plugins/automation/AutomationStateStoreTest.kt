package app.aaps.plugins.automation

import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * AutoISF fork: the automation states live only in the preferences. A settings import writes the
 * preferences without restarting the app; the imported states must count at once and must not be
 * overwritten by what was there before the import.
 */
class AutomationStateStoreTest {

    private val stored = mutableMapOf<String, String>()
    private val preferences: Preferences = mock()
    private lateinit var sut: AutomationStateStore

    @BeforeEach fun setUp() {
        whenever(preferences.get(any<StringNonKey>())).thenAnswer { stored[(it.arguments[0] as StringNonKey).key] ?: "{}" }
        doAnswer { stored[(it.arguments[0] as StringNonKey).key] = it.arguments[1] as String; null }
            .whenever(preferences).put(any<StringNonKey>(), any<String>())
        sut = AutomationStateStore(preferences)
    }

    /** What a settings import does: it writes the two preferences directly. */
    private fun import(states: String, values: String) {
        stored[StringNonKey.AutomationStateCurrent.key] = states
        stored[StringNonKey.AutomationStateValues.key] = values
    }

    @Test fun `states set through the store are kept`() {
        sut.setStateValues("Sleeping", listOf("yes", "no"))
        sut.setState("Sleeping", "yes")
        assertThat(sut.inState("Sleeping", "yes")).isTrue()
        assertThat(sut.getStateValues("Sleeping")).containsExactly("yes", "no")
    }

    @Test fun `an import counts at once`() {
        sut.setStateValues("Sleeping", listOf("yes", "no"))
        sut.setState("Sleeping", "no")

        import(states = """{"Sleeping":"yes","Sport":"on"}""", values = """{"Sleeping":["yes","no"],"Sport":["on","off"]}""")

        assertThat(sut.inState("Sleeping", "yes")).isTrue()
        assertThat(sut.getState("Sport")).isEqualTo("on")
        assertThat(sut.hasStateValues("Sport")).isTrue()
    }

    @Test fun `a change after an import does not bring back the states from before it`() {
        sut.setStateValues("Old", listOf("a", "b"))
        sut.setState("Old", "a")

        import(states = """{"Sport":"off"}""", values = """{"Sport":["on","off"]}""")
        sut.setState("Sport", "on")

        assertThat(sut.getState("Sport")).isEqualTo("on")
        assertThat(sut.hasStateValues("Old")).isFalse()
        assertThat(sut.getAllStates()).containsExactly("Sport" to "on")
    }
}
