package app.aaps.plugins.aps.openAPSAutoISF

/**
 * Sensitivity ratio to use while the activity monitor reports activity or inactivity.
 *
 * - Activity: the smaller of the autosens ratio and the activity ratio. A ratio below 1 means less
 *   insulin, so detected movement can never give more insulin than autosens alone would.
 * - Inactivity: the activity ratio, as in AutoISF 3.2.1, but never above [autosensMax]. With the
 *   inactivity scale factor at 1.5 the step monitor gives up to 1.3, which is more than the user allows
 *   autosens to give (1.2 by default).
 *
 * Used by the plugin (iobTH and autoISF) and by `DetermineBasalAutoISF`, so all three agree.
 */
internal fun activitySensitivityRatio(autosensRatio: Double, activityRatio: Double, activityDetected: Boolean, autosensMax: Double): Double =
    if (activityDetected) minOf(autosensRatio, activityRatio) else minOf(activityRatio, autosensMax)

/**
 * What the activity monitor and the activity classifier found in one loop run. Handed directly from the
 * plugin to `autoISF` and `determine_basal`, so a value of an earlier run can never be used. The default is
 * "nothing detected".
 */
data class ActivityState(val ratio: Double = 1.0, val activityDetected: Boolean = false, val inactivityDetected: Boolean = false)

/** Activity ratio used when the classifier detects activity but the step monitor does not: same as full step activity. */
internal fun defaultActivityRatio(activityScaleFactor: Double): Double = 1 - 0.3 * activityScaleFactor
