package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

/**
 * The AutoISF factors of one loop run, stored in [RT.autoIsf] and so in the APSResult of that run.
 * The overview graphs read them back from there.
 *
 * A factor of 1.0 is neutral. [acceIsf] is the raw factor from the BG acceleration (parabola fit) before
 * the AutoISF limits, so it can be far from 1.0. [finalIsf] is the factor that was applied.
 * [iobThEffective] is the effective IOB threshold in U.
 */
@Serializable
data class AutoIsfFactors(
    var acceIsf: Double? = null,
    var bgIsf: Double? = null,
    var ppIsf: Double? = null,
    var duraIsf: Double? = null,
    var finalIsf: Double? = null,
    var iobThEffective: Double? = null
) {

    /**
     * Copy with every value that is not finite replaced by null. The device status JSON throws on NaN and
     * Infinity, so a broken factor must never reach [RT].
     */
    fun finiteOnly(): AutoIsfFactors = AutoIsfFactors(
        acceIsf = acceIsf.finiteOrNull(),
        bgIsf = bgIsf.finiteOrNull(),
        ppIsf = ppIsf.finiteOrNull(),
        duraIsf = duraIsf.finiteOrNull(),
        finalIsf = finalIsf.finiteOrNull(),
        iobThEffective = iobThEffective.finiteOrNull()
    )

    private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
}
