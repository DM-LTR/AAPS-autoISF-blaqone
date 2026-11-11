package app.aaps.plugins.aps.openAPSSMB

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import kotlin.math.roundToLong

actual object StepService : SensorEventListener {

    private const val TAG = "StepService"
    private var previousStepCount = -1
    private val stepsMap = LinkedHashMap<Long, Int>()
    private const val fiveMinutesInMs = 300000
    private const val numOf5MinBlocksToKeep = 20

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    private fun currentTimeIn5Min(): Long {
        return (System.currentTimeMillis() / fiveMinutesInMs.toDouble()).roundToLong()
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        sensorEvent ?: return

        val now = currentTimeIn5Min()
        val stepCount = sensorEvent.values[0].toInt()
        if(previousStepCount >= 0) {
            var recentStepCount = stepCount - previousStepCount
            if(stepsMap.contains(now)) {
                recentStepCount += stepsMap.getValue(now)
            }
            stepsMap[now] = recentStepCount
        }
        previousStepCount = stepCount

        if(stepsMap.size > numOf5MinBlocksToKeep) {
            val removeBefore = now - numOf5MinBlocksToKeep
            stepsMap.entries.removeIf { it.key < removeBefore}
        }
    }

    // AutoISF fork: 5, 10 and 15 minutes count the last 1, 2 and 3 finished 5 minute blocks, like the watch
    // (AAPS #5499). Before, 10 and 15 read only the one block 10 or 15 minutes ago.
    actual fun getRecentStepCount5Min(): Int = getStepsInFinishedBlocks(1)

    actual fun getRecentStepCount10Min(): Int = getStepsInFinishedBlocks(2)

    actual fun getRecentStepCount15Min(): Int = getStepsInFinishedBlocks(3)

    private fun getStepsInFinishedBlocks(count: Int): Int {
        val lastFinished = currentTimeIn5Min() - 1
        return (lastFinished - count + 1..lastFinished).sumOf { stepsMap[it] ?: 0 }
    }

    actual fun getRecentStepCount30Min(): Int {
        return getStepsInLastXMin(6)
    }

    actual fun getRecentStepCount60Min(): Int {
        return getStepsInLastXMin(12)
    }

    actual fun getRecentStepCount180Min(): Int {
        return getStepsInLastXMin(36)
    }

    private fun getStepsInLastXMin(numberOf5MinIncrements: Int): Int {
        var stepCount = 0
        val thirtyMinAgo = currentTimeIn5Min() - numberOf5MinIncrements
        for (entry in stepsMap.entries) {
            if (entry.key > thirtyMinAgo) {
                stepCount += entry.value
            }
        }
        return stepCount
    }

}
