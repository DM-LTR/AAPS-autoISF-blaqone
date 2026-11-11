package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.defs.fillFor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Sport fork: the keto protection temp basal is never 0 on the pump and never more than the profile basal. */
class KetoProtectionTest {

    private val danaI = PumpDescription().fillFor(PumpType.DANA_I)
    private val medtrum = PumpDescription().fillFor(PumpType.MEDTRUM_NANO)

    @Test
    fun `a Dana gets at least 0,10 U per hour, its smallest temp basal above 0`() {
        // 5 % of 0.8 U/h would be 0.04, which the Dana driver turns into 0 %
        assertThat(ketoProtectionRate(0.8, 5, danaI)).isEqualTo(0.10)
        assertThat(ketoProtectionRate(2.4, 5, danaI)).isEqualTo(0.12)
    }

    @Test
    fun `a Medtrum gets at least one rate step of 0,05 U per hour`() {
        assertThat(ketoProtectionRate(0.6, 5, medtrum)).isEqualTo(0.05)
        assertThat(ketoProtectionRate(1.0, 20, medtrum)).isEqualTo(0.20)
    }

    @Test
    fun `the protection never gives more than the profile basal`() {
        assertThat(ketoProtectionRate(0.05, 5, danaI)).isEqualTo(0.05)
        assertThat(ketoProtectionRate(0.04, 5, medtrum)).isEqualTo(0.04)
    }
}
