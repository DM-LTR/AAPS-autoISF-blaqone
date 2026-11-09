package app.aaps.plugins.aps.openAPSSMB

/**
 * Result of [ActivityClassifier].
 *
 * The sensitivity change for AEROBIC comes from the activity monitor ratio (capped by autosens), not from this
 * class. Here are only the optional target offset and the factor that makes SMBs smaller.
 */
enum class ActivityClass(
    val targetBgOffsetMgdl: Double,
    val smbCapFactor: Double
) {
    AEROBIC    (targetBgOffsetMgdl = 10.0, smbCapFactor = 0.7),
    RESISTANCE (targetBgOffsetMgdl = 15.0, smbCapFactor = 0.8),
    REST       (targetBgOffsetMgdl =  0.0, smbCapFactor = 1.0)
}
