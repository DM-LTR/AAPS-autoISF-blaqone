package app.aaps.plugins.sync.garmin

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The steps since the last day total that a Garmin watch face sent. */
class GarminDayTotalStepsTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `the difference to the last total is the steps since then`() {
        assertThat(stepsSinceLastTotal(total = 5400, lastTotal = 5200, lastTimeMs = now - 3 * minute, nowMs = now)).isEqualTo(200)
        assertThat(stepsSinceLastTotal(total = 5200, lastTotal = 5200, lastTimeMs = now - minute, nowMs = now)).isEqualTo(0)
    }

    @Test
    fun `the first total is only the starting point`() {
        assertThat(stepsSinceLastTotal(total = 5400, lastTotal = -1, lastTimeMs = 0L, nowMs = now)).isNull()
    }

    @Test
    fun `a lower total is a new day, not steps`() {
        assertThat(stepsSinceLastTotal(total = 165, lastTotal = 17506, lastTimeMs = now - 5 * minute, nowMs = now)).isNull()
    }

    @Test
    fun `after a long gap the steps cannot be placed in time`() {
        // 3000 steps over two hours must not look like activity just now
        assertThat(stepsSinceLastTotal(total = 8000, lastTotal = 5000, lastTimeMs = now - 120 * minute, nowMs = now)).isNull()
        assertThat(stepsSinceLastTotal(total = 8000, lastTotal = 5000, lastTimeMs = now - DAY_TOTAL_MAX_GAP_MS, nowMs = now)).isEqualTo(3000)
    }

    @Test
    fun `a time in the future gives nothing`() {
        assertThat(stepsSinceLastTotal(total = 8000, lastTotal = 5000, lastTimeMs = now + minute, nowMs = now)).isNull()
    }
}
