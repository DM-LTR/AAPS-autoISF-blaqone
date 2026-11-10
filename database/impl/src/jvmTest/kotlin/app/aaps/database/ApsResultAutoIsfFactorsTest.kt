package app.aaps.database

import app.aaps.database.daos.ApsResultAutoIsfFactors
import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.APSResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The AutoISF factor query of the overview graphs, against a real database file, like
 * [ApsResultVariableSensTest]. The factors are read with `json_extract` out of `resultJson.autoIsf`.
 */
class ApsResultAutoIsfFactorsTest {

    private val directory = createTempDirectory("aaps-autoisf-test")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")

    @AfterTest
    fun tearDown() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `reads the factors of AutoISF results in the interval, oldest first`() = runTest {
        database.apsResultDao.insert(
            apsResult(
                NOW + FIVE_MIN,
                """{"bg":140.0,"autoIsf":{"acceIsf":1.3,"bgIsf":1.1,"ppIsf":1.0,"duraIsf":1.05,"finalIsf":1.2,"iobThEffective":4.5}}"""
            )
        )
        database.apsResultDao.insert(apsResult(NOW, """{"autoIsf":{"acceIsf":-1.8,"finalIsf":0.9}}"""))
        // Outside the interval
        database.apsResultDao.insert(apsResult(NOW - 1, """{"autoIsf":{"finalIsf":2.0}}"""))

        assertThat(database.apsResultDao.getAutoIsfFactors(NOW, NOW + FIVE_MIN)).containsExactly(
            ApsResultAutoIsfFactors(NOW, -1.8, null, null, null, 0.9, null, null, null),
            ApsResultAutoIsfFactors(NOW + FIVE_MIN, 1.3, 1.1, 1.0, 1.05, 1.2, 4.5, null, null)
        ).inOrder()
    }

    @Test
    fun `results of other algorithms are left out`() = runTest {
        database.apsResultDao.insert(apsResult(NOW, """{"bg":120.0,"variable_sens":48.0}"""))
        database.apsResultDao.insert(apsResult(NOW + FIVE_MIN, """{"autoIsf":{"finalIsf":1.0}}"""))

        assertThat(database.apsResultDao.getAutoIsfFactors(NOW, NOW + FIVE_MIN)).containsExactly(
            ApsResultAutoIsfFactors(NOW + FIVE_MIN, null, null, null, null, 1.0, null, null, null)
        )
    }

    private fun apsResult(timestamp: Long, resultJson: String) = APSResult(
        timestamp = timestamp,
        algorithm = APSResult.Algorithm.AUTO_ISF,
        glucoseStatusJson = null,
        currentTempJson = null,
        iobDataJson = null,
        profileJson = null,
        autosensDataJson = null,
        mealDataJson = null,
        resultJson = resultJson
    )

    private companion object {

        const val NOW = 1_800_000_000_000L
        const val FIVE_MIN = 5 * 60 * 1000L
    }
}
