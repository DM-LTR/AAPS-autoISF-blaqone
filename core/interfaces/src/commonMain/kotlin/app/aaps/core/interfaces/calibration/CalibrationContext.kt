package app.aaps.core.interfaces.calibration

/**
 * Optional hints passed to [Calibration.calibrate].
 * Plugins that do not use a hint may ignore it.
 */
data class CalibrationContext(
    /**
     * Start of the current sensor session in epoch ms.
     * `null` means the plugin should detect the session boundary itself
     * (e.g. by walking the input data for gaps).
     */
    val sensorSessionStart: Long? = null,
    /**
     * Whether the plugin may act on what it sees in [Calibration.calibrate] beyond returning
     * calibrated values - e.g. posting a notification or writing a therapy event when it detects a
     * sensor gap. `true` for the primary pass over a store's data.
     *
     * `false` for a second pass over the same underlying readings on a shifted grid (the status
     * data next to the fixed autosens grid): it would see the same gap again and re-post the same
     * side effect a second time per cycle for no new information.
     */
    val allowSideEffects: Boolean = true,
) {

    companion object {

        val NONE = CalibrationContext()
        val QUIET = CalibrationContext(allowSideEffects = false)
    }
}
