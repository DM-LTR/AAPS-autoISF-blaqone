package app.aaps.core.interfaces.aps

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoIsfFactorsTest {

    @Test
    fun finiteOnlyReplacesNaNAndInfinityWithNull() {
        val factors = AutoIsfFactors(
            acceIsf = Double.NaN,
            bgIsf = 1.1,
            ppIsf = Double.POSITIVE_INFINITY,
            duraIsf = Double.NEGATIVE_INFINITY,
            finalIsf = 1.2,
            iobThEffective = null
        ).finiteOnly()
        assertEquals(AutoIsfFactors(acceIsf = null, bgIsf = 1.1, ppIsf = null, duraIsf = null, finalIsf = 1.2, iobThEffective = null), factors)
    }

    @Test
    fun rtWithFactorsWritesThemUnderAutoIsf() {
        val rt = RT(runningDynamicIsf = true, autoIsf = AutoIsfFactors(acceIsf = 1.3, finalIsf = 1.2))
        val json = Json.encodeToString(RT.serializer(), rt)
        assertTrue(json.contains("\"autoIsf\":{"))
        assertTrue(json.contains("\"acceIsf\":1.3"))
    }

    @Test
    fun rtWithoutFactorsHasNoAutoIsfField() {
        // Default Json does not write null defaults, so other algorithms do not change their JSON
        val json = Json.encodeToString(RT.serializer(), RT(runningDynamicIsf = false))
        assertFalse(json.contains("autoIsf"))
    }
}
