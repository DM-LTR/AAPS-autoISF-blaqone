package app.aaps.plugins.source

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** AutoISF fork: the fixed CGM correction is only value x slope + offset, nothing else. */
class BgSlopeOffsetTest {

    @Test fun `default settings leave the value exactly as it is`() {
        assertThat(BgSlopeOffset.apply(123.0, slope = 1.0, offset = 0.0)).isEqualTo(123.0)
        assertThat(BgSlopeOffset.isActive(1.0, 0.0)).isFalse()
    }

    @Test fun `offset and slope are applied`() {
        // sensor reads 30 mg/dL too low
        assertThat(BgSlopeOffset.apply(90.0, slope = 1.0, offset = 30.0)).isEqualTo(120.0)
        assertThat(BgSlopeOffset.apply(100.0, slope = 1.1, offset = -5.0)).isWithin(1e-9).of(105.0)
    }

    @Test fun `limits are kept`() {
        // slope and offset outside the allowed range are clamped, the result is never below 40
        assertThat(BgSlopeOffset.apply(100.0, slope = 3.0, offset = 0.0)).isEqualTo(150.0)
        assertThat(BgSlopeOffset.apply(100.0, slope = 1.0, offset = -90.0)).isEqualTo(50.0)
        assertThat(BgSlopeOffset.apply(60.0, slope = 0.5, offset = -50.0)).isEqualTo(40.0)
    }

    @Test fun `no value stays no value`() {
        assertThat(BgSlopeOffset.apply(0.0, slope = 1.0, offset = 30.0)).isEqualTo(0.0)
    }
}
