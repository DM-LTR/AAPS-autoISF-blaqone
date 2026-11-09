package app.aaps.plugins.aps.openAPSAutoISF

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActivitySensitivityTest {

    @Test
    fun `activity never gives more insulin than autosens`() {
        // autosens says sensitive (0.8), activity monitor says neutral (1.0): keep 0.8
        assertThat(activitySensitivityRatio(autosensRatio = 0.8, activityRatio = 1.0, activityDetected = true)).isEqualTo(0.8)
        // autosens says resistant (1.2), activity says 0.7: use 0.7
        assertThat(activitySensitivityRatio(autosensRatio = 1.2, activityRatio = 0.7, activityDetected = true)).isEqualTo(0.7)
        // both below 1: the smaller one wins
        assertThat(activitySensitivityRatio(autosensRatio = 0.6, activityRatio = 0.85, activityDetected = true)).isEqualTo(0.6)
    }

    @Test
    fun `inactivity uses the activity ratio as in AutoISF 3_2_1`() {
        assertThat(activitySensitivityRatio(autosensRatio = 0.9, activityRatio = 1.2, activityDetected = false)).isEqualTo(1.2)
    }

    @Test
    fun `default activity ratio follows the scale factor`() {
        assertThat(defaultActivityRatio(1.0)).isWithin(1e-9).of(0.7)
        assertThat(defaultActivityRatio(0.5)).isWithin(1e-9).of(0.85)
        assertThat(defaultActivityRatio(0.0)).isWithin(1e-9).of(1.0)
    }
}
