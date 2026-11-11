package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Smallest temp basal above 0 a Dana pump gives: its driver turns anything below 0.10 U/h into 0 %. */
const val DANA_MIN_TEMP_BASAL = 0.10

/** Smallest temp basal above 0 when the pump does not say it: one usual pump step. */
const val DEFAULT_MIN_TEMP_BASAL = 0.05

/**
 * Sport fork: the smallest temp basal above 0 that [pumpDescription] really gives, in U/h, at the profile basal
 * [profileBasal].
 * - Dana (temp basal in percent): 0.10 U/h, see [DANA_MIN_TEMP_BASAL].
 * - Other pumps with percent: one percent step of the profile basal.
 * - Pumps with absolute rates (Medtrum and others): one rate step, at least [DEFAULT_MIN_TEMP_BASAL].
 */
internal fun pumpMinimumTempBasal(pumpDescription: PumpDescription, profileBasal: Double): Double = when {
    pumpDescription.pumpType.isDana()                                   -> DANA_MIN_TEMP_BASAL
    pumpDescription.tempBasalStyle and PumpDescription.PERCENT != 0 &&
        pumpDescription.tempBasalStyle and PumpDescription.ABSOLUTE == 0 -> max(DEFAULT_MIN_TEMP_BASAL, profileBasal * pumpDescription.tempPercentStep / 100.0)
    pumpDescription.tempAbsoluteStep > 0.0                              -> pumpDescription.tempAbsoluteStep
    else                                                                -> DEFAULT_MIN_TEMP_BASAL
}

private fun PumpType.isDana(): Boolean = name.startsWith("DANA")

/**
 * Sport fork, keto protection: the smallest temp basal the loop may set, in U/h. [percent] of the profile
 * basal, but at least what the pump really gives (see [pumpMinimumTempBasal]), so it never ends as 0.
 * Never more than the profile basal itself: with a very small basal the protection must not give more
 * insulin than the profile would.
 */
internal fun ketoProtectionRate(profileBasal: Double, percent: Int, pumpDescription: PumpDescription): Double {
    val wanted = profileBasal * percent / 100.0
    val rate = min(max(wanted, pumpMinimumTempBasal(pumpDescription, profileBasal)), profileBasal)
    return round(rate * 100.0) / 100.0
}
