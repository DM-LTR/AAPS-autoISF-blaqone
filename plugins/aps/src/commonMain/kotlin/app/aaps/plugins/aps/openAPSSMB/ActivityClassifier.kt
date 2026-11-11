package app.aaps.plugins.aps.openAPSSMB

import kotlin.concurrent.Volatile

object ActivityClassifier {

    // Values match DetectedActivity constants from com.google.android.gms.location
    const val PHONE_BICYCLE = 1
    const val PHONE_STILL   = 3
    const val PHONE_WALKING = 7
    const val PHONE_RUNNING = 8

    @Volatile var phoneActivity: Int = PHONE_STILL

    /** AutoISF fork: steps in the last 15 minutes from which the steps alone count as endurance activity (40 per minute). */
    const val AEROBIC_STEPS_15M = 600

    /** AutoISF fork: below this many steps in the last 15 minutes an elevated heart rate counts as strength training. */
    const val RESISTANCE_MAX_STEPS_15M = 45

    /**
     * [steps15m] is the SUM of the steps in the last 15 minutes. Before, the AutoISF plugin gave only one 5 minute
     * block from 10 to 15 minutes ago: rule 3 then needed 150 steps in 5 minutes (30 per minute) and reacted
     * 10 minutes late, and rule 4 fired on a single quiet block. The limits are now a little less sensitive
     * than that: 40 steps per minute for rule 3, almost no steps (3 per minute) for rule 4.
     */
    fun classify(
        steps15m: Int,
        avgHrLast15m: Double,
        phoneActivity: Int = this.phoneActivity,
        restingHr: Int = 60
    ): ActivityClass {
        // Rule 1: cycling — few steps but OS knows for sure
        if (phoneActivity == PHONE_BICYCLE) return ActivityClass.AEROBIC
        // Rule 2: explicit OS signal
        if (phoneActivity == PHONE_WALKING || phoneActivity == PHONE_RUNNING) return ActivityClass.AEROBIC
        // Rule 3: brisk walking or more, from the steps alone
        if (steps15m > AEROBIC_STEPS_15M) return ActivityClass.AEROBIC
        // Rule 4: almost no steps + elevated HR = gym/strength training
        if (steps15m < RESISTANCE_MAX_STEPS_15M && avgHrLast15m > 0.0 && avgHrLast15m > restingHr * 1.25) return ActivityClass.RESISTANCE
        return ActivityClass.REST
    }
}
