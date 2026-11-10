package app.aaps.core.objects.aps

import app.aaps.core.data.iob.InMemoryGlucoseValue
import kotlin.math.pow

/**
 * Result of the AutoISF parabola fit.
 *
 * @property acceleration the BG acceleration (`bgAcceleration` in AutoISF, 2 * a)
 * @property correlation the best r² found; AutoISF uses acce_ISF only from 0.9 up
 * @property minutes how far back the best fitting parabola reaches
 */
data class ParabolaFitResult(val acceleration: Double, val correlation: Double, val minutes: Double)

/** Correlation from which AutoISF uses the parabola for acce_ISF. */
const val PARABOLA_MIN_CORRELATION = 0.9

/**
 * The AutoISF parabola fit on 1 minute readings, newest first.
 *
 * FOR COMPARISON ONLY. It is a copy of the raw reading branch of `GlucoseStatusCalculatorAutoIsf`, so
 * the same fit can be run on the UKF-smoothed readings and shown next to the raw one. Dosing still
 * uses the fit inside `GlucoseStatusCalculatorAutoIsf`, which this function does not touch. A test
 * checks that both give the same result on the same raw readings.
 *
 * @param readings timestamp and BG in mg/dl, newest first
 * @param minDurationMinutes the shortest parabola that counts (`fslMinDur`: 10 for about 1 minute
 *        readings, 15 otherwise)
 * @return null when there are not more than 3 readings, as in AutoISF
 */
fun parabolaFit(readings: List<Pair<Long, Double>>, minDurationMinutes: Int): ParabolaFitResult? {
    if (readings.size <= 3) return null
    var duraP = 0.0
    var bgAcceleration = 0.0
    var corrMax = 0.0
    var sy = 0.0
    var sx = 0.0
    var sx2 = 0.0
    var sx3 = 0.0
    var sx4 = 0.0
    var sxy = 0.0
    var sx2y = 0.0
    val time0 = readings[0].first
    var tiLast = 0.0
    var n = 0
    for (i in readings.indices) {
        val (thenDate, bg) = readings[i]
        if (bg <= 39) continue
        n += 1
        val ti = (thenDate - time0) / 1000.0
        if (-ti > 47 * 60) {                                      // skip records older than 47.5 minutes
            break
        } else if (ti < tiLast - 11.0 * 60) {                     // stop scan if a CGM gap > 11 minutes is detected
            if (i < 3 || -ti < minDurationMinutes * 60) {         // history too short for fit
                duraP = -tiLast / 60.0
                bgAcceleration = 0.0
                corrMax = 0.0
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
        var detH = 0.0
        var detA = 0.0
        var detB = 0.0
        var detC = 0.0
        if (n > 3 && -ti > minDurationMinutes * 60) {
            detH = sx4 * (sx2 * n - sx * sx) - sx3 * (sx3 * n - sx * sx2) + sx2 * (sx3 * sx - sx2 * sx2)
            detA = sx2y * (sx2 * n - sx * sx) - sxy * (sx3 * n - sx * sx2) + sy * (sx3 * sx - sx2 * sx2)
            detB = sx4 * (sxy * n - sy * sx) - sx3 * (sx2y * n - sy * sx2) + sx2 * (sx2y * sx - sxy * sx2)
            detC = sx4 * (sx2 * sy - sx * sxy) - sx3 * (sx3 * sy - sx * sx2y) + sx2 * (sx3 * sxy - sx2 * sx2y)
        }
        if (detH != 0.0) {
            val a = detA / detH * 300.0.pow(2.0)
            val b = detB / detH * 300.0
            val c = detC / detH
            val yMean = sy / n
            var sSquares = 0.0
            var sResidualSquares = 0.0
            // Over all readings up to i, as in AutoISF (also the ones skipped above for bg <= 39)
            for (j in 0..i) {
                val (beforeDate, beforeBg) = readings[j]
                sSquares += (beforeBg - yMean).pow(2.0)
                val deltaT = (beforeDate - time0) / 1000.0 / 300
                val bgj = a * deltaT.pow(2.0) + b * deltaT + c
                sResidualSquares += (beforeBg - bgj).pow(2.0)
            }
            var rSqu = 0.0
            if (sSquares != 0.0) rSqu = 1 - sResidualSquares / sSquares
            if (rSqu >= corrMax) {
                corrMax = rSqu
                duraP = -ti / 60.0
                bgAcceleration = 2 * a
            }
        }
    }
    return ParabolaFitResult(acceleration = bgAcceleration, correlation = corrMax, minutes = duraP)
}

/**
 * The shortest parabola that counts, as AutoISF chooses it: [oneMinuteMinutes] (the setting, 10 in the
 * original) for about 1 minute readings, else 15.
 */
fun parabolaMinDurationMinutes(newestFirstTimestamps: List<Long>, oneMinuteMinutes: Int = 10): Int {
    if (newestFirstTimestamps.size <= 2) return 15
    val recentSpacingMs = (newestFirstTimestamps[0] - newestFirstTimestamps[2]) / 2.0
    return if (recentSpacingMs < 90_000) oneMinuteMinutes else 15
}

/** UKF-smoothed readings as fit input: the smoothed value where there is one, else the reading itself. */
fun List<InMemoryGlucoseValue>.smoothedFitInput(): List<Pair<Long, Double>> = map { it.timestamp to (it.smoothed ?: it.value) }
