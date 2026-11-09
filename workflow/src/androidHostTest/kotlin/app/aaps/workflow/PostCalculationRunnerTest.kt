package app.aaps.workflow

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.scenes.SceneExpiryCheck
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.keys.IntKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PostCalculationRunnerTest : TestBaseWithProfile() {

    @Mock lateinit var workflowChainData: WorkflowChainData
    @Mock lateinit var loop: Loop
    @Mock lateinit var widgetUpdater: WidgetUpdater
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var ads: AutosensDataStore
    @Mock lateinit var sceneExpiryCheck: SceneExpiryCheck

    private fun runner() =
        PostCalculationRunner(
            aapsLogger, workflowChainData, iobCobCalculator, loop,
            widgetUpdater, config, processedDeviceStatusData, profileUtil, preferences,
            persistenceLayer, constraintsChecker, dateUtil, sceneExpiryCheck
        )

    // Nothing in these tests stops a run part way.
    private suspend fun run() = runner().run(job = "main", generation = 1L, isStopped = { false })

    private fun dataWith(runLoopAndWidgetPhase: Boolean): PostCalculationData {
        val cache = mock<OverviewDataCache>()
        whenever(cache.timeRangeFlow).thenReturn(MutableStateFlow(null))
        return PostCalculationData(
            overviewData = mock<OverviewData>(),
            cache = cache,
            signals = mock<CalculationSignalsEmitter>(),
            runLoopAndWidgetPhase = runLoopAndWidgetPhase
        )
    }

    // Built before the stubbing call: dataWith() stubs its own mocks, which must not happen inside another stubbing.
    private fun stubFullPhase() {
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(anyOrNull(), any())).thenReturn(data)
    }

    private fun bgAt(timestamp: Long): InMemoryGlucoseValue = InMemoryGlucoseValue(timestamp, 120.0)

    @BeforeEach
    fun setup() {
        whenever(config.APS).thenReturn(false)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(preferences.get(IntKey.ApsLoopInterval)).thenReturn(5)
    }

    @Test
    fun `missing or stale data returns failure`() = runTest {
        whenever(workflowChainData.postFor(any(), any())).thenReturn(null)

        assertIs<WorkOutcome.Failure>(run())
    }

    @Test
    fun `predictions only phase returns success without loop or widget`() = runTest {
        val data = dataWith(runLoopAndWidgetPhase = false)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater, never()).update(any())
    }

    @Test
    fun `full phase invokes loop on new bg and updates widget`() = runTest {
        // lastBgTriggeredRun is 0 after app start, so any real reading is far more than 5 minutes newer.
        whenever(ads.actualStatusBg()).thenReturn(bgAt(T.hours(10).msecs()))
        whenever(loop.lastBgTriggeredRun).thenReturn(0L)
        stubFullPhase()

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }

    // The chain that reaches this runner does not have to be the chain the new BG started - a BG chain
    // can be cancelled and replaced by one started by another database change (issue #5066). The only
    // gate left is lastBgTriggeredRun, so the two cases below are what keeps the loop from running twice.
    @Test
    fun `full phase invokes loop even when the chain was not started by a new bg`() = runTest {
        val newest = T.mins(60).msecs()
        whenever(ads.actualStatusBg()).thenReturn(bgAt(newest))
        // A loop run happened for an older BG, the newest one was never used
        whenever(loop.lastBgTriggeredRun).thenReturn(newest - T.mins(5).msecs())
        stubFullPhase()

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop).invoke(any(), any(), any())
    }

    @Test
    fun `full phase skips loop when newest bg was already used`() = runTest {
        whenever(ads.actualStatusBg()).thenReturn(bgAt(5000L))
        whenever(loop.lastBgTriggeredRun).thenReturn(5000L)
        stubFullPhase()

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }

    /**
     * The chain was replaced while the loop ran, and the loop had not stored a decision yet. It did
     * nothing for this BG, so the BG must be given back, or the replacing chain skips it and this BG
     * gets no loop run at all.
     */
    @Test
    fun `a loop run cancelled before its decision gives the bg back`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(T.hours(10).msecs())
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualStatusBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(4000L)
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { lastAPSRun = 1000L })
        whenever(loop.invoke(any(), any(), any())).doSuspendableAnswer { throw CancellationException("replaced") }
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        assertFailsWith<CancellationException> { run() }

        verify(loop).lastBgTriggeredRun = T.hours(10).msecs()
        verify(loop).lastBgTriggeredRun = 4000L
    }

    /**
     * Once the loop stored a decision, the enactment after it cannot be cancelled and may have reached
     * the pump. The BG stays used, so no second automatic run follows for it.
     */
    @Test
    fun `a loop run cancelled after its decision keeps the bg used`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(T.hours(10).msecs())
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualStatusBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(4000L)
        val lastRun = Loop.LastRun().apply { lastAPSRun = 1000L }
        whenever(loop.lastRun).thenReturn(lastRun)
        whenever(loop.invoke(any(), any(), any())).doSuspendableAnswer {
            lastRun.lastAPSRun = 6000L // the decision is stored
            throw CancellationException("replaced")
        }
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        assertFailsWith<CancellationException> { run() }

        verify(loop).lastBgTriggeredRun = T.hours(10).msecs()
        verify(loop, never()).lastBgTriggeredRun = 4000L
    }

    /**
     * The scheduled end of a scene can wait for job quota while the loop goes on. Where the loop runs,
     * an overdue scene is ended first, so the loop does not dose with that scene's SMB setting.
     */
    @Test
    fun `on a master an overdue scene is ended before the loop runs`() = runTest {
        whenever(config.APS).thenReturn(true)
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(T.hours(10).msecs())
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualStatusBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(0L)
        val calls = mutableListOf<String>()
        whenever(sceneExpiryCheck.endIfOverdue()).doSuspendableAnswer { calls += "scene" }
        whenever(loop.invoke(any(), any(), any())).doSuspendableAnswer { calls += "loop" }
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        run()

        assertThat(calls).containsExactly("scene", "loop").inOrder()
    }

    /** A client receives the end of a scene from the master; it does not end scenes itself. */
    @Test
    fun `a client does not end scenes`() = runTest {
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualStatusBg()).thenReturn(null)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        run()

        verifyBlocking(sceneExpiryCheck, never()) { endIfOverdue() }
    }

    /** Missing a loop run is worse than ending a scene late. */
    @Test
    fun `a failing scene check does not stop the loop`() = runTest {
        whenever(config.APS).thenReturn(true)
        whenever(sceneExpiryCheck.endIfOverdue()).doSuspendableAnswer { throw IllegalStateException("broken scene") }
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(T.hours(10).msecs())
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualStatusBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(0L)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop).invoke(any(), any(), any())
    }

    @Test
    fun `full phase skips loop when no actual bg is available`() = runTest {
        whenever(ads.actualStatusBg()).thenReturn(null)
        stubFullPhase()

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }

    // ---------- Loop cadence on synthetic BG streams ----------

    /**
     * Feeds [minutes] of readings, one every [readingEveryMin] minutes, through the runner the way the
     * calculation does: one run per reading, [processingDelaySec] after the reading. When [smbEachRun]
     * is set, every loop run records a bolus at the moment it runs, like an SMB would.
     *
     * Returns the minutes (from the start) of the readings that started a loop run.
     */
    private suspend fun simulate(
        minutes: Int,
        readingEveryMin: Int,
        smbEnabled: Boolean,
        smbIntervalMin: Int,
        smbEachRun: Boolean,
        loopIntervalMin: Int = 1,
        processingDelaySec: (Int) -> Long = { 20L }
    ): List<Int> {
        val start = T.hours(10).msecs()
        var lastRun = 0L
        var lastBolusTime: Long? = null
        var wallClock = start
        val runs = mutableListOf<Int>()
        whenever(loop.lastBgTriggeredRun).thenAnswer { lastRun }
        doAnswer { lastRun = it.getArgument(0); null }.whenever(loop).lastBgTriggeredRun = any()
        doAnswer { if (smbEachRun) lastBolusTime = wallClock; null }.whenever(loop).invoke(any(), any(), any())
        whenever(dateUtil.now()).thenAnswer { wallClock }
        whenever(constraintsChecker.isSMBModeEnabled()).thenReturn(ConstraintObject(smbEnabled, aapsLogger))
        whenever(preferences.get(IntKey.ApsMaxSmbFrequency)).thenReturn(smbIntervalMin)
        whenever(preferences.get(IntKey.ApsLoopInterval)).thenReturn(loopIntervalMin)
        val bolus = mock<BS>()
        whenever(bolus.timestamp).thenAnswer { lastBolusTime }
        whenever(persistenceLayer.getNewestBolus()).thenAnswer { if (lastBolusTime == null) null else bolus }
        stubFullPhase()

        for (minute in 0 until minutes step readingEveryMin) {
            val readingTime = start + T.mins(minute.toLong()).msecs()
            wallClock = readingTime + T.secs(processingDelaySec(minute)).msecs()
            whenever(ads.actualStatusBg()).thenReturn(bgAt(readingTime))
            val before = lastRun
            run()
            if (lastRun != before) runs.add(minute)
        }
        return runs
    }

    private fun gaps(runs: List<Int>) = runs.zipWithNext { a, b -> b - a }.toSet()

    @Test
    fun `default loop interval of 5 runs every 5 minutes on a one minute source`() = runTest {
        for (smb in listOf(false, true)) {
            val runs = simulate(minutes = 60, readingEveryMin = 1, smbEnabled = smb, smbIntervalMin = 1, smbEachRun = smb, loopIntervalMin = 5)

            Assertions.assertEquals(setOf(5), gaps(runs), "SMB $smb")
        }
    }

    @Test
    fun `without SMB the loop runs at the loop interval`() = runTest {
        for (interval in 1..10) {
            val runs = simulate(minutes = 120, readingEveryMin = 1, smbEnabled = false, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = interval)

            Assertions.assertEquals(setOf(interval), gaps(runs), "loop interval $interval")
        }
    }

    @Test
    fun `with an SMB on each run the loop waits for the SMB interval`() = runTest {
        for (interval in 1..4) {
            val runs = simulate(minutes = 60, readingEveryMin = 1, smbEnabled = true, smbIntervalMin = interval, smbEachRun = true)

            Assertions.assertEquals(setOf(interval), gaps(runs), "SMB interval $interval")
        }
    }

    @Test
    fun `an SMB interval of 5 or more still gives a run every 5 minutes`() = runTest {
        for (interval in 5..10) {
            val runs = simulate(minutes = 60, readingEveryMin = 1, smbEnabled = true, smbIntervalMin = interval, smbEachRun = true)

            Assertions.assertEquals(setOf(5), gaps(runs), "SMB interval $interval")
        }
    }

    @Test
    fun `without a recent bolus the loop interval sets the cadence`() = runTest {
        val runs = simulate(minutes = 60, readingEveryMin = 1, smbEnabled = true, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = 2)

        Assertions.assertEquals(setOf(2), gaps(runs))
    }

    @Test
    fun `5 minute source runs every 5 minutes with a short loop interval`() = runTest {
        val runs = simulate(minutes = 120, readingEveryMin = 5, smbEnabled = true, smbIntervalMin = 1, smbEachRun = true, loopIntervalMin = 1)

        Assertions.assertEquals(setOf(5), gaps(runs))
    }

    @Test
    fun `a loop interval above 5 makes a 5 minute source run less often`() = runTest {
        Assertions.assertEquals(setOf(10), gaps(simulate(minutes = 120, readingEveryMin = 5, smbEnabled = false, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = 10)))
        // 7 minutes: the next reading after 7 minutes is the one after 10
        Assertions.assertEquals(setOf(10), gaps(simulate(minutes = 120, readingEveryMin = 5, smbEnabled = false, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = 7)))
    }

    @Test
    fun `2 minute source with SMB interval 3 runs every 4 minutes`() = runTest {
        val runs = simulate(minutes = 60, readingEveryMin = 2, smbEnabled = true, smbIntervalMin = 3, smbEachRun = true)

        Assertions.assertEquals(setOf(4), gaps(runs))
    }

    // The bolus of run N is recorded at wall clock time. When run N+1 is processed faster than run N,
    // the SMB interval is not over yet at N+1. Running then would block the SMB for a whole interval,
    // so the runner waits for the next reading instead.
    @Test
    fun `run that the SMB interval would still block waits one reading`() = runTest {
        val slowThenFast: (Int) -> Long = { minute -> if ((minute / 3) % 2 == 0) 40L else 10L }

        val runs = simulate(minutes = 60, readingEveryMin = 1, smbEnabled = true, smbIntervalMin = 3, smbEachRun = true, processingDelaySec = slowThenFast)

        Assertions.assertEquals(setOf(3, 4), gaps(runs))
    }

    @Test
    fun `loop runs per day for a one minute source`() = runTest {
        Assertions.assertEquals(288, simulate(minutes = 1440, readingEveryMin = 1, smbEnabled = false, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = 5).size)
        Assertions.assertEquals(1440, simulate(minutes = 1440, readingEveryMin = 1, smbEnabled = false, smbIntervalMin = 3, smbEachRun = false, loopIntervalMin = 1).size)
        Assertions.assertEquals(480, simulate(minutes = 1440, readingEveryMin = 1, smbEnabled = true, smbIntervalMin = 3, smbEachRun = true, loopIntervalMin = 1).size)
    }
}
