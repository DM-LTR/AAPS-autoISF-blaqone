package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/** Garmin values that the plugin keeps for itself, not shown in the settings. */
enum class GarminLongNonKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    /** When [GarminIntNonKey.LastTotalSteps] was received, in epoch milliseconds. 0 means: never. */
    LastTotalStepsTime("garmin_http_last_steps_time", 0L),
    ;

    /** State of the phone and the watch, not a setting: an import must not bring back an old time. */
    override val exportable: Boolean get() = false
}
