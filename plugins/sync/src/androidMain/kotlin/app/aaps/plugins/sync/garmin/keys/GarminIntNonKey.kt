package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

/** Garmin values that the plugin keeps for itself, not shown in the settings. */
enum class GarminIntNonKey(
    override val key: String,
    override val defaultValue: Int,
) : IntNonPreferenceKey {

    /** Last total step count of the day that a watch face sent with `steps=`. -1 means: none yet. */
    LastTotalSteps("garmin_http_last_steps", -1),
    ;

    /** State of the phone and the watch, not a setting: an import must not bring back an old count. */
    override val exportable: Boolean get() = false
}
