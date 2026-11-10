package app.aaps.core.objects.aps

import app.aaps.core.data.iob.InMemoryGlucoseValue

/**
 * AutoISF fork: UKF-smoothed readings as input for the AutoISF parabola fit (setting "parabola on smoothed
 * values"): the smoothed value where there is one, else the reading itself. Newest first, as the readings.
 */
fun List<InMemoryGlucoseValue>.smoothedFitInput(): List<Pair<Long, Double>> = map { it.timestamp to (it.smoothed ?: it.value) }
