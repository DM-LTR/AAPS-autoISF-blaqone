package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.data.model.advancedFilteringSupported
import app.aaps.core.data.model.rawObservationEstimatorSupported
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.aps.smoothedFitInput
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSAutoISF.extensions.asRounded
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@SingleIn(AppScope::class)
@Inject
class GlucoseStatusCalculatorAutoIsf(
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil,
    private val deltaCalculator: DeltaCalculator,
    private val preferences: Preferences,
) {

    fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatusAutoIsf? {
        val data = iobCobCalculator.ads.getStatusDataCopy() ?: return null
        val orig = iobCobCalculator.ads.getBgReadingsDataTableCopy()

        var sizeRecords = data.size
        if (sizeRecords == 0) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
            return null
        }
        if (data[0].timestamp < dateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
            aapsLogger.debug(LTag.GLUCOSE, "oldData")
            return null
        }
        val now = data[0]
        val nowDate = now.timestamp
        val nowValue = now.value
        val recalc = now.recalculated
        val smooth = now.smoothed
        val filled = now.filledGap
        val cgm = now.sourceSensor
        val fsl = orig.firstOrNull()
        val fslDate = fsl?.timestamp
        val fslValue = fsl?.raw
        val fslRaw = fsl?.noise
        val fslSmooth = fsl?.value

        // Fit directly on the raw stream instead of the bucketed grid whenever the source has recently
        // been running faster than the grid's own 5 minute spacing. A fast or phase-shifted source
        // leaves most 5 minute buckets interpolated (filledGap=true), and this calculator refuses those
        // for the fit; on the grid that means bgAcceleration and duraISFminutes sit at zero for as long
        // as the source stays off-phase (issues #3688, #5148). The raw stream has no such gap - every
        // point in it is a real measurement - so there is nothing to refuse, but only a source this app
        // already trusts is used: the advanced-filtering list or the raw-observation list.
        var useRawFit = false
        var fslMinDur = 15
        if (orig.size > 2) {
            val sourceTrusted = orig[0].sourceSensor.advancedFilteringSupported() || orig[0].sourceSensor.rawObservationEstimatorSupported()
            val recentSpacingMs = (orig[0].timestamp - orig[2].timestamp) / 2.0
            if (sourceTrusted && recentSpacingMs < 4 * 60_000) {
                useRawFit = true
                // AutoISF fork: the shortest parabola for a 1 minute CGM is a setting, 10 minutes as in the original
                fslMinDur = if (recentSpacingMs < 90_000) preferences.get(IntKey.ApsAutoIsfParabolaMinMinutes) else 15
            }
        }
        // AutoISF fork: the readings the parabola and dura_ISF use on the raw path. The raw readings as in the
        // original, or, with the setting on and the UKF smoothing each reading, the smoothed readings.
        val smoothedForFit =
            if (useRawFit && preferences.get(BooleanKey.ApsAutoIsfParabolaSmoothed)) iobCobCalculator.ads.smoothedReadings?.takeIf { it.size > 2 }
            else null
        val fitSource: List<Pair<Long, Double>> = smoothedForFit?.smoothedFitInput() ?: orig.map { it.timestamp to it.value }
        if (useRawFit) sizeRecords = fitSource.size

        if (data.size == 1) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
            return GlucoseStatusAutoIsf(
                glucose = now.recalculated,
                noise = 0.0,
                delta = 0.0,
                shortAvgDelta = 0.0,
                longAvgDelta = 0.0,
                date = nowDate,
                duraISFminutes = 0.0,
                duraISFaverage = now.value,
                parabolaMinutes = 0.0,
                deltaPl = 0.0,
                deltaPn = 0.0,
                bgAcceleration = 0.0,
                a0 = now.value,
                a1 = 0.0,
                a2 = 0.0,
                corrSqu = 0.0
            ).asRounded()
        }

        val deltaResult = deltaCalculator.calculateDeltas(data)

        // calculate 2 variables for 5% range
        //
        // Raw readings are never filledGap (they are real measurements by definition), and the
        // reference point is the raw newest reading rather than the bucketed one: on a fast source the
        // bucketed nowDate can be stale by close to a bucket width.
        val nowDateForSteadyState = if (useRawFit) fitSource[0].first else nowDate
        val bw = 0.05
        var sumBG: Double = if (useRawFit) fitSource[0].second else now.recalculated
        var oldAvg: Double = sumBG
        var minutesDur = 0L
        var n = 1
        for (i in 1 until sizeRecords) {
            val thenValue = if (useRawFit) fitSource[i].second else data[i].value
            val thenIsGap = !useRawFit && data[i].filledGap
            if (thenValue > 39 && !thenIsGap) {
                n += 1
                val thenDate: Long = if (useRawFit) fitSource[i].first else data[i].timestamp
                val thenRecalculated = if (useRawFit) fitSource[i].second else data[i].recalculated
                //  stop the series if there was a CGM gap greater than 13 minutes, i.e. 2 regular readings
                //  needs shorter gap for Libre?
                if (((nowDateForSteadyState - thenDate) / (1000.0 * 60)).roundToInt() - minutesDur > 13) {
                    break
                }
                if (thenRecalculated > oldAvg * (1 - bw) && thenRecalculated < oldAvg * (1 + bw)) {
                    sumBG += thenRecalculated
                    oldAvg = sumBG / n  // was: (i + 1)
                    minutesDur = ((nowDateForSteadyState - thenDate) / (1000.0 * 60)).roundToLong()
                } else {
                    break
                }
            }
        }

        // calculate best parabola and determine delta by extending it 5 minutes into the future
        // after https://www.codeproject.com/Articles/63170/Least-Squares-Regression-for-Quadratic-Curve-Fitti
        //
        //  y = a2*x^2 + a1*x + a0      or
        //  y = a*x^2  + b*x  + c       respectively
        var duraP = 0.0
        var deltaPl = 0.0
        var deltaPn = 0.0
        var bgAcceleration = 0.0
        var corrMax = 0.0
        var a0 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        //var b = 0.0
        aapsLogger.debug(LTag.GLUCOSE, "BgReadings stamp=$fslDate; raw=$fslRaw; value=$fslValue; rawFit=$useRawFit; smoothedFit=${smoothedForFit != null}; fitMinutes=$fslMinDur; fslSmooth=$fslSmooth; " +
            "BgBucketed value=$nowValue; recalc=$recalc; smooth=$smooth; filled=$filled; CGM=$cgm")

        if (sizeRecords > 3) {
            var sy = 0.0 // y
            var sx = 0.0 // x
            var sx2 = 0.0 // x^2
            var sx3 = 0.0 // x^3
            var sx4 = 0.0 // x^4
            var sxy = 0.0 // x*y
            var sx2y = 0.0 // x^2*y
            val time0 = if (useRawFit) fitSource[0].first else data[0].timestamp
            var tiLast = 0.0
            //# for best numerical accuracy time and bg must be of same order of magnitude
            val scaleTime = 1.0 // was 00.0 // in 5m; values are  0, -1, -2, -3, -4, ...
            val scaleBg = 1.0   // was 50.0 // TIR range is now 1.4 - 3.6

            // if (data[i].recalculated > 38) {  } // not checked in past 1.5 years
            var n = 0
            for (i in 0 until sizeRecords) {
                val noGap = if (useRawFit) true else !data[i].filledGap
                val usableValue = if (useRawFit) fitSource[i].second else data[i].value
                if (usableValue > 39 && noGap) {
                    n += 1
                    val thenDate: Long
                    var bg: Double
                    if (useRawFit) {
                        val then = fitSource[i]
                        thenDate = then.first
                        bg = then.second / scaleBg
                    } else {    // all other including standard 5m CGM smoothed
                        val then = data[i]
                        thenDate = then.timestamp
                        bg = then.recalculated / scaleBg
                    }
                    val ti = (thenDate - time0) / 1000.0 / scaleTime
                    if (-ti * scaleTime > 47 * 60) {                       // skip records older than 47.5 minutes
                        break
                    } else if (ti < tiLast - 11.0 * 60 / scaleTime) {      // stop scan if a CGM gap > 11 minutes is detected
                        if (i < 3 || -ti * scaleTime < fslMinDur * 60) {   // history too short for fit
                            duraP = -tiLast * scaleTime / 60.0
                            deltaPl = 0.0
                            deltaPn = 0.0
                            bgAcceleration = 0.0
                            corrMax = 0.0
                            a0 = 0.0
                            a1 = 0.0
                            a2 = 0.0
                        }
                        break
                    }
                    tiLast = ti
                    sx += ti
                    sx2 += ti.pow(2.0)
                    sx3 += ti.pow(3.0)
                    sx4 += ti.pow(4.0)
                    sy += bg
                    sxy += ti * bg
                    sx2y += ti.pow(2.0) * bg
                    //val n = i + 1
                    var detH = 0.0
                    var detA = 0.0
                    var detB = 0.0
                    var detC = 0.0
                    if (n > 3 && -ti * scaleTime > fslMinDur * 60) {
                        detH = sx4 * (sx2 * n - sx * sx) - sx3 * (sx3 * n - sx * sx2) + sx2 * (sx3 * sx - sx2 * sx2)
                        detA = sx2y * (sx2 * n - sx * sx) - sxy * (sx3 * n - sx * sx2) + sy * (sx3 * sx - sx2 * sx2)
                        detB = sx4 * (sxy * n - sy * sx) - sx3 * (sx2y * n - sy * sx2) + sx2 * (sx2y * sx - sxy * sx2)
                        detC = sx4 * (sx2 * sy - sx * sxy) - sx3 * (sx3 * sy - sx * sx2y) + sx2 * (sx3 * sxy - sx2 * sx2y)
                    }
                    if (detH != 0.0) {
                        val a: Double = detA / detH * scaleBg * (300 / scaleTime).pow(2.0)
                        val b = detB / detH * scaleBg * (300 / scaleTime)
                        val c: Double = detC / detH * scaleBg
                        val yMean = sy / n
                        var sSquares = 0.0
                        var sResidualSquares = 0.0
                        //var rawBg: Double
                        for (j in 0..i) {
                            if (useRawFit) {
                                val before = fitSource[j]
                                val scaledBg = before.second / scaleBg
                                sSquares += (scaledBg - yMean).pow(2.0)
                                val deltaT: Double = (before.first - time0) / 1000.0 / 300
                                val bgj: Double = a * deltaT.pow(2.0) + b * deltaT + c
                                sResidualSquares += (scaledBg - bgj/scaleBg).pow(2.0)
                            } else {                            // default case anyway
                                val before = data[j]
                                val scaledBg = before.recalculated/ scaleBg
                                sSquares += (scaledBg - yMean).pow(2.0)
                                val deltaT: Double = (before.timestamp - time0) / 1000.0 / 300.0
                                val bgj: Double = a * deltaT.pow(2.0) + b * deltaT + c
                                sResidualSquares += (scaledBg - bgj/scaleBg).pow(2.0)
                            }
                        }
                        var rSqu = 0.0
                        if (sSquares != 0.0) {
                            rSqu = 1 - sResidualSquares / sSquares
                        }
                        if (rSqu >= corrMax) {
                            corrMax = rSqu

                            duraP = -ti * scaleTime / 60.0 // remember we are going backwards in time
                            val delta5Min = 1.0 //5 * 60 / scaleTime
                            deltaPl = - (a * (-delta5Min).pow(2.0) - b * delta5Min)    // 5 minute slope from last fitted bg ending at this bg, i.e. t=0
                            deltaPn =   (a * delta5Min.pow(2.0) + b * delta5Min)    // 5 minute slope to next fitted bg starting from this bg, i.e. t=0
                            bgAcceleration = 2 * a
                            a0 = c
                            a1 = b
                            a2 = a
                        }
                    }
                }
            }
        }
        // End parabola fit

        return GlucoseStatusAutoIsf(
            glucose = now.recalculated,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = deltaResult.shortAvgDelta,
            delta = deltaResult.delta,
            longAvgDelta = deltaResult.longAvgDelta,
            duraISFminutes = minutesDur.toDouble(),
            duraISFaverage = oldAvg,
            parabolaMinutes = duraP,
            deltaPl = deltaPl,
            deltaPn = deltaPn,
            corrSqu = corrMax,
            bgAcceleration = bgAcceleration,
            a0 = a0,
            a1 = a1,
            a2 = a2,
        )   //.also { aapsLogger.debug(LTag.GLUCOSE, it.log(decimalFormatter)) }.asRounded()
    }

    companion object {
        //this will be useful in the next step when I replace magic numbers with vals
    }
}