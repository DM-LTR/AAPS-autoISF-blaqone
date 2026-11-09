package app.aaps.core.interfaces.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue

interface Smoothing {

    /**
     * Smooth values in List
     *
     * @param data  input glucose values ([0] to be the most recent one)
     *
     * @return new List with smoothed values (smoothed values are stored in [InMemoryGlucoseValue.smoothed])
     */
    fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue>

    /**
     * Smooth a second list in the same calculation, like the status data that starts at the newest
     * reading. It is the same readings on a shifted grid, so it must not change anything the plugin
     * learns or saves between calls.
     *
     * No default: a plugin with no state between calls can implement this as `= smooth(data)`, but
     * that has to be a decision made while reading this plugin's own state, not an inherited default
     * that silently stays correct only for as long as nobody adds state to it.
     */
    fun smoothWithoutLearning(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue>
}