package app.aaps.workflow

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.aps.bgParabolaCurve
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgParabolaGraphData
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.scenes.SceneExpiryCheck
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.WorkOutcome
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Merged tail-of-chain worker covering: APS loop invocation, widget update,
 * predictions prep, and the final DRAW_FINAL progress emit.
 *
 * Used by [CalculationWorkflow.runCalculation] (MAIN only, full phases) and by
 * [CalculationWorkflow.runOnReceivedPredictions] (predictions only).
 */
@Inject
class PostCalculationRunner(
    private val aapsLogger: AAPSLogger,
    private val workflowChainData: WorkflowChainData,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val widgetUpdater: WidgetUpdater,
    private val config: Config,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val constraintChecker: ConstraintsChecker,
    private val dateUtil: DateUtil,
    private val sceneExpiryCheck: SceneExpiryCheck,
    private val glucoseStatusProvider: GlucoseStatusProvider
) : CalculationPhase {

    /** Runs the phase that follows the prepare pass. [isStopped] is asked between steps. */
    override suspend fun run(job: String, generation: Long, isStopped: () -> Boolean): WorkOutcome {
        val data = workflowChainData.postFor(job, generation) ?: return WorkOutcome.StaleInput

        if (data.runLoopAndWidgetPhase) {
            endOverdueScene()
            invokeLoop()
            if (isStopped()) return WorkOutcome.Stopped
            widgetUpdater.update("WorkFlow")
            if (isStopped()) return WorkOutcome.Stopped
        }

        preparePredictions(data)
        if (isStopped()) return WorkOutcome.Stopped
        prepareBgParabola(data)
        if (isStopped()) return WorkOutcome.Stopped

        data.signals.emitProgress(CalculationWorkflow.ProgressData.DRAW_FINAL, 100)
        return WorkOutcome.Success
    }

    /**
     * Ends a scene whose time is over before the loop decides, see [SceneExpiryCheck].
     *
     * Only where the loop runs: the master owns the scene, and a client receives its end. A failure
     * here is logged and the loop runs anyway - missing one loop run is worse than ending a scene late.
     */
    private suspend fun endOverdueScene() {
        if (!config.APS) return
        try {
            sceneExpiryCheck.endIfOverdue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.WORKER, "Ending an overdue scene failed", e)
        }
    }

    /*
     * Runs after a completed MAIN calculation so the Loop has current data to work with.
     *
     * The loop is invoked when the newest BG has not been used for a loop run yet. The check is on
     * purpose not tied to what started this chain: a chain started by a new BG can be cancelled and
     * replaced by a chain started by another database change before it reaches this runner, and the
     * replacement must not lose the pending BG trigger (issue #5066).
     *
     * [app.aaps.core.interfaces.aps.Loop.lastBgTriggeredRun] is written before the call, so each BG
     * timestamp can drive at most one automatic loop run.
     *
     * One exception gives the BG back: a run that is cancelled before it stored a decision. That
     * happens when this chain is replaced or stopped while the loop runs, for example by a profile
     * switch. Such a run did nothing for this BG, so the chain that replaced this one runs the loop for
     * it. Before, that BG got no loop run at all. Once a decision is stored the BG stays used: the
     * enactment after it cannot be cancelled and may have reached the pump, and a second automatic run
     * for the same BG must not follow.
     */
    private suspend fun invokeLoop() {
        val glucoseValue = iobCobCalculator.ads.actualStatusBg() ?: return
        val sinceLastRun = glucoseValue.timestamp - loop.lastBgTriggeredRun
        if (sinceLastRun <= 0 || !isLoopRunDue(sinceLastRun)) return
        val previousTrigger = loop.lastBgTriggeredRun
        val decisionBefore = loop.lastRun?.lastAPSRun
        loop.lastBgTriggeredRun = glucoseValue.timestamp
        try {
            loop.invoke("Calculation for $glucoseValue", true)
        } catch (e: CancellationException) {
            if (loop.lastRun?.lastAPSRun == decisionBefore) loop.lastBgTriggeredRun = previousTrigger
            throw e
        }
    }

    /**
     * The loop runs at most once per loop interval ([IntKey.ApsLoopInterval], 1 to 10 minutes, 5 by
     * default). Less than 5 minutes only makes a difference with a source that sends a value more
     * often than every 5 minutes.
     *
     * Between the 5 minute runs one more rule applies, while SMB is enabled: a run is left out when
     * the SMB interval since the last bolus is not over yet, but will be over before the next normal
     * run. Such a run could not deliver an SMB (`LoopPlugin` and `CommandSMBBolus` ask the same
     * question), and it would push the next run, which could deliver, a whole loop interval further.
     * When the SMB interval ends only after the next normal run anyway, leaving the run out gains
     * nothing, so it happens. With a loop interval of 1 minute this means a run on every new value.
     * A run after 5 minutes or more always happens, so the loop is never slower than with a 5 minute
     * CGM unless the user set a loop interval above 5.
     *
     * [sinceLastRun] is measured between reading timestamps. [READING_JITTER] keeps a reading that
     * comes a few seconds early from pushing the run to the next reading.
     */
    private suspend fun isLoopRunDue(sinceLastRun: Long): Boolean {
        val loopInterval = T.mins(preferences.get(IntKey.ApsLoopInterval).toLong()).msecs()
        if (sinceLastRun < loopInterval - READING_JITTER) return false
        if (sinceLastRun >= T.mins(5).msecs() - READING_JITTER) return true
        if (!constraintChecker.isSMBModeEnabled().value()) return true
        val smbInterval = T.mins(preferences.get(IntKey.ApsMaxSmbFrequency).toLong()).msecs()
        val lastBolusTime = persistenceLayer.getNewestBolus()?.timestamp ?: 0L
        val now = dateUtil.now()
        val smbFreeAt = lastBolusTime + smbInterval
        if (smbFreeAt <= now) return true
        // Blocked now: wait only when the SMB interval ends before the next normal run would come
        return smbFreeAt >= now + loopInterval - READING_JITTER
    }

    /**
     * AutoISF fork: the BG parabola for the BG graph, as AutoISF 3.2.1 draws it. The glucose status is
     * read fresh here, like the original does, so the curve follows every new reading and not only the
     * loop runs. Only AutoISF gives a [GlucoseStatusAutoIsf]; with any other APS, and on a client, the
     * curve is empty. Display only.
     */
    private fun prepareBgParabola(data: PostCalculationData) {
        val status = if (config.APS) glucoseStatusProvider.glucoseStatusData as? GlucoseStatusAutoIsf else null
        val curve = status?.bgParabolaCurve()
        fun List<GraphDataPoint>.inUnits() = map { GraphDataPoint(it.timestamp, profileUtil.fromMgdlToUnits(it.value)) }
        data.cache.updateBgParabolaGraph(
            BgParabolaGraphData(fit = curve?.fit?.inUnits() ?: emptyList(), extrapolation = curve?.extrapolation?.inUnits() ?: emptyList())
        )
    }

    private fun preparePredictions(data: PostCalculationData) {
        val apsResult = if (config.APS) loop.lastRun?.constraintsProcessed else processedDeviceStatusData.getAPSResult()
        val predictionsAvailable = if (config.APS) loop.lastRun?.request?.hasPredictions == true else config.AAPSCLIENT
        // Align to the next local hour boundary. Local, not UTC: zones like +05:30 do not line up
        // with UTC hours, and this is what the Calendar version did.
        val zone = TimeZone.currentSystemDefault()
        val nowLocal = Clock.System.now().toLocalDateTime(zone)
        val nextHour = LocalDateTime(nowLocal.year, nowLocal.month, nowLocal.day, nowLocal.hour, 0)
            .toInstant(zone) + 1.hours
        if (predictionsAvailable && apsResult != null) {
            var predictionHours = (ceil(apsResult.latestPredictionsTime - Clock.System.now().toEpochMilliseconds().toDouble()) / (60 * 60 * 1000)).toInt()
            predictionHours = min(PREDICTION_WINDOW_HOURS, predictionHours)
            predictionHours = max(0, predictionHours)
            val hoursToFetch = Constants.GRAPH_TIME_RANGE_HOURS - predictionHours
            data.overviewData.toTime = nextHour.toEpochMilliseconds() + 100000 // GraphView-era nudge, retained while workers still consume this shape
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(hoursToFetch.toLong()).msecs()
            // The newest prediction, not the clamped window share. The axis is already
            // max(newest point, endTime), so this widens nothing - it only makes endTime agree with
            // the edge, so the series built against it (IOB, activity, basal) reach it. The clamp
            // above stays where it belongs: it decides how much history to fetch, not where to stop.
            data.overviewData.endTime = max(data.overviewData.toTime, apsResult.latestPredictionsTime)
        } else {
            data.overviewData.toTime = nextHour.toEpochMilliseconds() + 100000
            data.overviewData.fromTime = data.overviewData.toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
            data.overviewData.endTime = data.overviewData.toTime
        }

        val highMarkInUnits = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMarkInUnits = preferences.get(UnitDoubleKey.OverviewLowMark)

        val predictionDataPoints = apsResult?.predictionsAsGv
            ?.filter { it.value >= 40 }
            ?.map { gv ->
                val valueInUnits = profileUtil.fromMgdlToUnits(gv.value)
                BgDataPoint(
                    timestamp = gv.timestamp,
                    value = valueInUnits,
                    range = when {
                        valueInUnits > highMarkInUnits -> BgRange.HIGH
                        valueInUnits < lowMarkInUnits  -> BgRange.LOW
                        else                           -> BgRange.IN_RANGE
                    },
                    type = when (gv.sourceSensor) {
                        SourceSensor.IOB_PREDICTION   -> BgType.IOB_PREDICTION
                        SourceSensor.COB_PREDICTION   -> BgType.COB_PREDICTION
                        SourceSensor.A_COB_PREDICTION -> BgType.A_COB_PREDICTION
                        SourceSensor.UAM_PREDICTION   -> BgType.UAM_PREDICTION
                        SourceSensor.ZT_PREDICTION    -> BgType.ZT_PREDICTION
                        else                          -> BgType.IOB_PREDICTION
                    }
                )
            }
            ?.sortedBy { it.timestamp }
            ?: emptyList()

        data.cache.updatePredictions(predictionDataPoints)

        // Extend cached time range to include prediction horizon
        data.cache.timeRangeFlow.value?.let { current ->
            data.cache.updateTimeRange(current.copy(endTime = data.overviewData.endTime))
        }
    }

    companion object {

        /** Same "still the same slot" limit as `AutosensDataStoreObject.IRREGULAR_DATA_SEC`. */
        private val READING_JITTER = T.secs(30).msecs()
    }
}
