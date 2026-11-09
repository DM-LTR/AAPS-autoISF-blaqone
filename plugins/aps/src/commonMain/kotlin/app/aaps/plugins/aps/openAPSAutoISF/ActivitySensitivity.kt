package app.aaps.plugins.aps.openAPSAutoISF

/**
 * Sensitivity ratio to use while the activity monitor reports activity or inactivity.
 *
 * - Activity: the smaller of the autosens ratio and the activity ratio. A ratio below 1 means less
 *   insulin, so detected movement can never give more insulin than autosens alone would.
 * - Inactivity: the activity ratio, as in AutoISF 3.2.1.
 *
 * Used by the plugin (iobTH and autoISF) and by `DetermineBasalAutoISF`, so all three agree.
 */
internal fun activitySensitivityRatio(autosensRatio: Double, activityRatio: Double, activityDetected: Boolean): Double =
    if (activityDetected) minOf(autosensRatio, activityRatio) else activityRatio

/** Activity ratio used when the classifier detects activity but the step monitor does not: same as full step activity. */
internal fun defaultActivityRatio(activityScaleFactor: Double): Double = 1 - 0.3 * activityScaleFactor
