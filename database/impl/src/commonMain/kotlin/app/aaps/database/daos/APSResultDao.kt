package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.APSResult
import app.aaps.database.entities.TABLE_APS_RESULTS

@Dao
internal interface APSResultDao : TraceableDao<APSResult> {

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE id = :id")
    override suspend fun findById(id: Long): APSResult?

    @Query("DELETE FROM $TABLE_APS_RESULTS")
    override suspend fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE timestamp < :than")
    override suspend fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_APS_RESULTS WHERE referenceId IS NOT NULL")
    override suspend fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<APSResult>

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE timestamp > :since AND timestamp <= :until ORDER BY timestamp DESC LIMIT 1")
    suspend fun getApsResult(since: Long, until: Long): APSResult?

    @Query("SELECT * FROM $TABLE_APS_RESULTS WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getApsResults(start: Long, end: Long): List<APSResult>

    /**
     * Only the time and the variable sensitivity of each result, for the graph.
     *
     * The value is a field of `resultJson`, and SQLite reads it there. Loading whole rows and decoding
     * them in Kotlin decodes all seven JSON columns of about 288 rows on every calculation. In a desktop
     * benchmark that was about 29 times slower (#5248). `json_extract` gives NULL when the field is
     * missing and when it is `NaN`.
     */
    @Query("SELECT timestamp, json_extract(resultJson, '\$.variable_sens') AS variableSens FROM $TABLE_APS_RESULTS WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getVariableSens(start: Long, end: Long): List<ApsResultVariableSens>

    /**
     * AutoISF fork: only the time and the AutoISF factors of each result, for the overview graphs. Read with
     * `json_extract` for the same reason as [getVariableSens]. Results that are not from AutoISF have no
     * `autoIsf` object and are left out.
     */
    @Query(
        "SELECT timestamp, " +
            "json_extract(resultJson, '\$.autoIsf.acceIsf') AS acceIsf, " +
            "json_extract(resultJson, '\$.autoIsf.bgIsf') AS bgIsf, " +
            "json_extract(resultJson, '\$.autoIsf.ppIsf') AS ppIsf, " +
            "json_extract(resultJson, '\$.autoIsf.duraIsf') AS duraIsf, " +
            "json_extract(resultJson, '\$.autoIsf.finalIsf') AS finalIsf, " +
            "json_extract(resultJson, '\$.autoIsf.iobThEffective') AS iobThEffective, " +
            "json_extract(resultJson, '\$.autoIsf.parabolaRaw') AS parabolaRaw, " +
            "json_extract(resultJson, '\$.autoIsf.parabolaSmoothed') AS parabolaSmoothed " +
            "FROM $TABLE_APS_RESULTS WHERE timestamp >= :start AND timestamp <= :end " +
            "AND json_extract(resultJson, '\$.autoIsf') IS NOT NULL ORDER BY timestamp ASC"
    )
    suspend fun getAutoIsfFactors(start: Long, end: Long): List<ApsResultAutoIsfFactors>
}

/** One row of [APSResultDao.getAutoIsfFactors]. A value is null when the result has none. */
data class ApsResultAutoIsfFactors(
    val timestamp: Long,
    val acceIsf: Double?,
    val bgIsf: Double?,
    val ppIsf: Double?,
    val duraIsf: Double?,
    val finalIsf: Double?,
    val iobThEffective: Double?,
    val parabolaRaw: Double?,
    val parabolaSmoothed: Double?
)

/** One row of [APSResultDao.getVariableSens]. [variableSens] is in mg/dL, null when the result has no value. */
data class ApsResultVariableSens(
    val timestamp: Long,
    val variableSens: Double?
)
