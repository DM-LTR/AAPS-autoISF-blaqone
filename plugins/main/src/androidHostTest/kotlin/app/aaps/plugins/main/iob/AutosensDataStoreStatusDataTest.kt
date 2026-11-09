package app.aaps.plugins.main.iob

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.objects.extensions.fromGv
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataStoreObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.measureTime

/**
 * Status data on synthetic BG streams: the fixed grid stays for autosens, and the glucose status gets
 * the newest reading.
 */
class AutosensDataStoreStatusDataTest : TestBaseWithProfile() {

    private val start = T.hours(100).msecs()
    private val window = T.hours(34).msecs()

    /** Smooth, slowly changing BG with a little noise, the same for every call with the same time. */
    private fun bgAt(time: Long): Double {
        val minutes = time / 60_000.0
        val noise = Random(time).nextDouble(-2.0, 2.0)
        return (140.0 + 50.0 * sin(2 * PI * minutes / 180.0) + noise).roundToLong().toDouble()
    }

    /** Readings every [stepSec] seconds for the 34 hour load window ending at [newest], newest first. */
    private fun readings(newest: Long, stepSec: Long, jitterSec: Long = 0): List<GV> {
        val step = T.secs(stepSec).msecs()
        val count = (window / step).toInt()
        return (0 until count).map { i ->
            val nominal = newest - i * step
            val jitter = if (jitterSec == 0L || i == 0) 0L else Random(nominal).nextLong(-jitterSec, jitterSec + 1) * 1000
            val time = nominal + jitter
            GV(raw = 0.0, noise = 0.0, value = bgAt(time), timestamp = time, sourceSensor = SourceSensor.LIBRE_3, trendArrow = TrendArrow.FLAT)
        }
    }

    /** One calculation cycle as the workflow does it: load, bucket, then publish a clone. */
    private fun cycle(store: AutosensDataStoreObject, newest: Long, stepSec: Long = 60, jitterSec: Long = 0): AutosensDataStoreObject {
        store.bgReadings = readings(newest, stepSec, jitterSec)
        store.createBucketedData(aapsLogger, dateUtil)
        return store.clone() as AutosensDataStoreObject
    }

    private fun minute(m: Int) = start + T.mins(m.toLong()).msecs()

    @Test
    fun `one minute source keeps the fixed grid and gives the newest reading to the status data`() {
        var store = AutosensDataStoreObject()
        for (m in 0 until 30) {
            store = cycle(store, minute(m))
            val bucketed = store.getBucketedDataTableCopy()!!
            val status = store.getStatusDataCopy()!!

            // Autosens grid: phase of the first reading, never moves.
            assertThat(bucketed.map { (it.timestamp - start) % T.mins(5).msecs() }.toSet()).containsExactly(0L)
            assertThat(bucketed[0].timestamp).isEqualTo(minute(m / 5 * 5))
            // Glucose status: the newest reading itself, 5 minute steps below it.
            assertThat(status[0].timestamp).isEqualTo(minute(m))
            assertThat(status[0].value).isEqualTo(bgAt(minute(m)))
            assertThat(status.zipWithNext { a, b -> a.timestamp - b.timestamp }.toSet()).containsExactly(T.mins(5).msecs())
            // On the grid there is nothing to add.
            assertThat(store.statusData == null).isEqualTo(m % 5 == 0)
        }
    }

    @Test
    fun `status data is exactly the bucketing from before the anchor was kept`() {
        var store = AutosensDataStoreObject()
        for (m in 0 until 30) {
            store = cycle(store, minute(m), jitterSec = 3)
            // A new store has no anchor yet, which is what every cycle looked like before issue #5066.
            val oldBehaviour = AutosensDataStoreObject().also {
                it.bgReadings = store.bgReadings
                it.createBucketedData(aapsLogger, dateUtil)
            }.getBucketedDataTableCopy()!!

            assertThat(store.getStatusDataCopy()!!.map { it.timestamp to it.value })
                .containsExactlyElementsIn(oldBehaviour.map { it.timestamp to it.value }).inOrder()
        }
    }

    @Test
    fun `one minute status data has no filled gaps`() {
        val store = cycle(AutosensDataStoreObject(), minute(0))
        val next = cycle(store, minute(3))

        assertThat(next.getStatusDataCopy()!!.none(InMemoryGlucoseValue::filledGap)).isTrue()
    }

    @Test
    fun `5 minute source has no separate status data`() {
        var store = AutosensDataStoreObject()
        for (m in 0 until 60 step 5) {
            store = cycle(store, minute(m), stepSec = 300, jitterSec = 10)

            assertThat(store.statusData).isNull()
            assertThat(store.getStatusDataCopy()).isEqualTo(store.getBucketedDataTableCopy())
        }
    }

    @Test
    fun `clone keeps the status data`() {
        val store = cycle(AutosensDataStoreObject(), minute(0))
        val next = cycle(store, minute(2))

        assertThat(next.statusData).isNotNull()
        assertThat((next.clone() as AutosensDataStoreObject).statusData).isEqualTo(next.statusData)
        assertThat((next.clone() as AutosensDataStoreObject).lastUsed5minCalculation).isFalse()
    }

    /**
     * Load test: 34 hours of 1 minute readings (the window the calculation loads), 300 cycles, one per
     * minute. Compares cycles where the newest reading is on the grid (one bucketing pass, as on `dev`
     * today) with cycles between grid points (a second pass for the status data).
     *
     * Wall clock on a JVM, so only the order of magnitude matters. The budget only catches a real
     * regression, like a pass that became quadratic.
     */
    @Test
    fun `load - bucketing cost of the status data on a one minute source`() {
        var store = AutosensDataStoreObject()
        repeat(20) { store = cycle(store, minute(it)) } // warm up the JIT
        val onGrid = mutableListOf<Long>()
        val betweenGrid = mutableListOf<Long>()
        for (m in 20 until 320) {
            val newest = minute(m)
            val readings = readings(newest, 60)
            store.bgReadings = readings
            val took = measureTime { store.createBucketedData(aapsLogger, dateUtil) }.inWholeMicroseconds
            store = store.clone() as AutosensDataStoreObject
            if (m % 5 == 0) onGrid.add(took) else betweenGrid.add(took)
        }
        val onGridAvg = onGrid.average()
        val betweenAvg = betweenGrid.average()
        println(
            "Bucketing 34h of 1 min readings (${readings(minute(0), 60).size} values, " +
                "${store.getBucketedDataTableCopy()!!.size} buckets): " +
                "on grid ${(onGridAvg / 1000).fmt()} ms, between grid points ${(betweenAvg / 1000).fmt()} ms, " +
                "ratio ${(betweenAvg / onGridAvg).fmt()}"
        )

        assertThat(betweenAvg).isLessThan(onGridAvg * 3.5)
        assertThat(betweenAvg / 1000).isLessThan(200.0)
    }

    private fun Double.fmt() = "%.2f".format(this)

    /** The bucket loop as it was before the single pass: findNewer and findOlder for every bucket. */
    private fun referenceBuckets(store: AutosensDataStoreObject, startTime: Long): List<InMemoryGlucoseValue> {
        val result = ArrayList<InMemoryGlucoseValue>()
        val lastBg = store.bgReadings[0]
        var currentTime = startTime
        while (true) {
            val newer = store.findNewer(currentTime)
            val older = store.findOlder(currentTime)
            if (newer == null || older == null) break
            if (older.timestamp == newer.timestamp) {
                result.add(InMemoryGlucoseValue.fromGv(newer))
            } else {
                val bgDelta = newer.value - older.value
                val timeDiffToNew = newer.timestamp - currentTime
                val gapThreshold = store.typicalIntervalMs(store.bgReadings) + T.secs(AutosensDataStoreObject.IRREGULAR_DATA_SEC).msecs()
                val filledGap = newer.timestamp - older.timestamp > gapThreshold
                val currentBg = newer.value - timeDiffToNew.toDouble() / (newer.timestamp - older.timestamp) * bgDelta
                result.add(InMemoryGlucoseValue(currentTime, currentBg.roundToLong().toDouble(), filledGap = filledGap, sourceSensor = lastBg.sourceSensor))
            }
            currentTime -= T.mins(5).msecs()
        }
        return result
    }

    private fun key(list: List<InMemoryGlucoseValue>) = list.map { Triple(it.timestamp, it.value, it.filledGap) }

    /** Readings with jitter, some duplicate timestamps and some gaps, newest first. */
    private fun messyReadings(seed: Int, newest: Long, stepSec: Long): List<GV> {
        val random = Random(seed)
        val out = ArrayList<GV>()
        var time = newest
        while (time > newest - T.hours(34).msecs()) {
            val jitter = if (out.isEmpty()) 0L else random.nextLong(-20, 21) * 1000
            val t = time + jitter
            out.add(GV(raw = 0.0, noise = 0.0, value = bgAt(t), timestamp = t, sourceSensor = SourceSensor.LIBRE_3, trendArrow = TrendArrow.FLAT))
            if (random.nextInt(40) == 0) out.add(out.last().copy(value = out.last().value + 1)) // same timestamp twice
            time -= T.secs(stepSec).msecs() * (if (random.nextInt(60) == 0) random.nextLong(2, 8) else 1) // sometimes a gap
        }
        return out.sortedByDescending { it.timestamp }
    }

    // ---------- filledGap: a value made up across a missing reading ----------

    /** Regular readings every [stepSec] seconds ending at [newest], newest first, without the ones in [missing]. */
    private fun regular(newest: Long, stepSec: Long, count: Int, missing: Set<Int> = emptySet()): List<GV> =
        (0 until count).filter { it !in missing }.map { i ->
            val time = newest - i * T.secs(stepSec).msecs()
            GV(raw = 0.0, noise = 0.0, value = bgAt(time), timestamp = time, sourceSensor = SourceSensor.LIBRE_2, trendArrow = TrendArrow.FLAT)
        }

    private fun gapFlags(store: AutosensDataStoreObject, readings: List<GV>): Pair<List<Boolean>, List<Boolean>> {
        store.bgReadings = readings
        store.createBucketedData(aapsLogger, dateUtil)
        return store.getBucketedDataTableCopy()!!.map { it.filledGap } to store.getStatusDataCopy()!!.map { it.filledGap }
    }

    @Test
    fun `three minute source has no filled gaps`() {
        var store = AutosensDataStoreObject()
        for (m in 0 until 15 step 3) {
            val (bucketed, status) = gapFlags(store, regular(minute(m), 180, 200))
            assertThat(store.lastUsed5minCalculation).isFalse()
            store = store.clone() as AutosensDataStoreObject

            assertThat(bucketed.count { it }).isEqualTo(0)
            assertThat(status.count { it }).isEqualTo(0)
        }
    }

    @Test
    fun `five minute source out of phase with the grid has no filled gaps`() {
        // The case from the #5148 addendum: readings at :x1:00, grid anchored at :x0:00, and one 60 s
        // irregularity in the history.
        //
        // With isAbout5minData now only sampling the newest SAMPLE_SIZE gaps, a single old irregularity
        // this far back (index 60 of 120) no longer routes every pass to the recalculated path - instead
        // createBucketedData5min() sees it (still within its own 90 s re-anchor tolerance) and re-anchors
        // the whole list onto a consistent grid on its own, healing the phase shift completely rather
        // than living with a permanent offset. Verified by running this scenario: lastUsed5minCalculation
        // comes back true and every one of the 120 buckets is filledGap=false.
        val store = AutosensDataStoreObject()
        store.referenceTime = minute(0)
        val readings = regular(minute(61), 300, 120).mapIndexed { i, gv -> if (i == 60) gv.copy(timestamp = gv.timestamp - T.mins(1).msecs()) else gv }
        val (bucketed, _) = gapFlags(store, readings)

        assertThat(store.lastUsed5minCalculation).isTrue()
        assertThat(bucketed.count { it }).isEqualTo(0)
    }

    @Test
    fun `an old glitch far back in the loaded window does not force a five minute source onto the recalculated path`() {
        // isAbout5minData used to scan the whole loaded window (up to 34h): one 45 s irregularity from
        // hours ago classified an otherwise steady 5 minute source as "not 5 minute data" for as long as
        // that one reading stayed loaded, which (via the recalculated path) attached the SMB-interval
        // loop cadence and a second grid to a source that never asked for either. Only the newest
        // SAMPLE_SIZE gaps decide this now, so a glitch that far back no longer matters.
        val readings = regular(minute(0), 300, 40).toMutableList() // ~3h20m of clean 5 minute data
        readings[readings.lastIndex] = readings.last().copy(timestamp = readings.last().timestamp + T.secs(45).msecs())
        val store = AutosensDataStoreObject()
        store.bgReadings = readings

        assertThat(store.isAbout5minData(aapsLogger)).isTrue()
    }

    @Test
    fun `a recent glitch still classifies the source as not five minute data`() {
        // Sanity check for the above: the cap only widens the horizon, it does not disable detection.
        val readings = regular(minute(0), 300, 40).toMutableList()
        readings[5] = readings[5].copy(timestamp = readings[5].timestamp + T.secs(45).msecs())
        val store = AutosensDataStoreObject()
        store.bgReadings = readings

        assertThat(store.isAbout5minData(aapsLogger)).isFalse()
    }

    @Test
    fun `a missing reading is still a filled gap`() {
        // 3 minute source with readings 20 and 21 missing: a 9 minute hole
        val (bucketed, status) = gapFlags(AutosensDataStoreObject(), regular(minute(0), 180, 200, missing = setOf(20, 21)))
        val holeFrom = minute(0) - 22 * T.mins(3).msecs()
        val holeTo = minute(0) - 19 * T.mins(3).msecs()
        val statusBuckets = AutosensDataStoreObject().also {
            it.bgReadings = regular(minute(0), 180, 200, missing = setOf(20, 21))
            it.createBucketedData(aapsLogger, dateUtil)
        }.getStatusDataCopy()!!

        assertThat(status.count { it }).isGreaterThan(0)
        statusBuckets.forEach { assertThat(it.filledGap).isEqualTo(it.timestamp in (holeFrom + 1) until holeTo) }
        assertThat(bucketed.count { it }).isGreaterThan(0)
    }

    @Test
    fun `a single missing reading on a one minute source is still a filled gap`() {
        // A flat 5:30 threshold missed this: one missing reading on a 1 minute source only widens the
        // bracket to 2 minutes, nowhere near 5:30, so it used to slip through unflagged into AutoISF's
        // fit as if it were a real measurement. The threshold now scales with the source's own interval.
        val store = AutosensDataStoreObject()
        store.bgReadings = regular(minute(0), 60, 300, missing = setOf(20))
        store.createBucketedData(aapsLogger, dateUtil)

        // The 5 minute status grid steps land exactly on minute(0), minute(-5), minute(-10), ...; the
        // bucket at minute(-20) is exactly where reading #20 would be, so it is the one bracketing the
        // hole (readings #19 and #21 straddle it, 2 minutes apart).
        val holeBucket = store.getStatusDataCopy()!!.first { it.timestamp == minute(-20) }
        assertThat(holeBucket.filledGap).isTrue()

        // Sanity: on the same source with nothing missing, nothing is flagged.
        val (_, statusNoGap) = gapFlags(AutosensDataStoreObject(), regular(minute(0), 60, 300))
        assertThat(statusNoGap.count { it }).isEqualTo(0)
    }

    @Test
    fun `single pass gives the same buckets as findNewer and findOlder`() {
        var compared = 0
        for (seed in 0 until 40) {
            val stepSec = listOf(60L, 60L, 120L, 300L)[seed % 4]
            var store = AutosensDataStoreObject()
            for (m in 0 until 12) {
                store.bgReadings = messyReadings(seed * 100 + m, minute(m * (stepSec / 60).toInt()) + seed * 7_000L, stepSec)
                store.createBucketedData(aapsLogger, dateUtil)
                store = store.clone() as AutosensDataStoreObject
                if (store.lastUsed5minCalculation == true) continue // that path does not use the bucket loop
                val bucketed = store.getBucketedDataTableCopy() ?: continue
                if (bucketed.isEmpty()) continue
                // Skip the one bucket taken from the newest reading ahead of the loop, if there is one.
                val loopStart = if (bucketed[0].timestamp > store.bgReadings[0].timestamp) 1 else 0
                if (loopStart < bucketed.size) {
                    assertThat(key(bucketed.drop(loopStart))).isEqualTo(key(referenceBuckets(store, bucketed[loopStart].timestamp)))
                    compared++
                }
                store.statusData?.let {
                    assertThat(key(it)).isEqualTo(key(referenceBuckets(store, store.bgReadings[0].timestamp)))
                    compared++
                }
            }
        }
        assertThat(compared).isGreaterThan(300)
    }
}
