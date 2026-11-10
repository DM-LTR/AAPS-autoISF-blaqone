package app.aaps.core.interfaces.overview.graph

import app.aaps.core.interfaces.overview.graph.GraphConfig.Companion.MAX_GRAPH_HEIGHT_DP
import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for all graphs (BG primary and secondary).
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 *
 * Not all types are allowed on all graphs — the UI enforces allowed sets per graph type.
 * BG graph: only BASAL, ACTIVITY.
 * Secondary graphs: all types.
 */
enum class SeriesType {

    IOB,
    ABS_IOB,
    COB,
    BGI,
    DEVIATIONS,
    SENSITIVITY,
    VAR_SENSITIVITY,
    DEV_SLOPE,
    HEART_RATE,
    STEPS,
    ACTIVITY,
    PREDICTIONS,
    ACCE_ISF,
    BG_ISF,
    PP_ISF,
    DURA_ISF,
    FINAL_ISF,
    IOB_TH_EFFECTIVE,
    PARABOLA_RAW,
    PARABOLA_SMOOTHED
}

/**
 * Secondary graph entry: series list + per-graph height (dp).
 * Max 2 series per graph. Height is per-graph user-adjustable.
 */
data class SecondaryGraph(
    val series: List<SeriesType>,
    val height: Int = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
)

/**
 * Configuration for the overview graphs.
 *
 * Fixed graphs (not removable):
 * - BG graph: blood glucose with optional activity overlay (toggled via [bgOverlays]).
 * - IOB graph: IOB line with bolus markers + flipped basal overlay, with optional activity
 *   overlay (toggled via [iobOverlays]).
 *
 * @param bgOverlays       Overlay toggles for the BG graph (currently only [SeriesType.ACTIVITY]).
 * @param iobOverlays      Overlay toggles for the fixed IOB graph ([SeriesType.ACTIVITY], and [SeriesType.IOB_TH_EFFECTIVE] with AutoISF).
 * @param secondaryGraphs  Ordered list of user-configurable secondary graph configurations.
 *   IOB cannot appear here (it has a dedicated fixed slot). Each graph is a List (not Set) to
 *   preserve selection order:
 *   - list[0] = left axis (start), list[1] = right axis (end).
 *   Max 2 series per graph. FIFO: adding a 3rd deselects the oldest.
 */
data class GraphConfig(
    val bgOverlays: List<SeriesType> = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS),
    val iobOverlays: List<SeriesType> = listOf(SeriesType.ACTIVITY),
    val bgHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val iobHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val secondaryGraphs: List<SecondaryGraph> = listOf(
        SecondaryGraph(listOf(SeriesType.COB))
    )
) {

    companion object {

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5

        /** Default graph height in dp (minimum value — user-adjustable up to [MAX_GRAPH_HEIGHT_DP]) */
        const val DEFAULT_GRAPH_HEIGHT_DP = 100

        /** Maximum graph height in dp (2.5x default) */
        const val MAX_GRAPH_HEIGHT_DP = 250

        /**
         * AutoISF factors. They are all multipliers around 1.0, so they share one axis (the left one).
         */
        val ISF_FACTOR_TYPES = setOf(
            SeriesType.ACCE_ISF,
            SeriesType.BG_ISF,
            SeriesType.PP_ISF,
            SeriesType.DURA_ISF,
            SeriesType.FINAL_ISF
        )

        /**
         * AutoISF series that may share one panel, any number of them: the factors plus the effective IOB
         * threshold. IOBth is in U, not a factor, so it is drawn against its own axis on the right.
         */
        val ISF_COMPANION_TYPES = ISF_FACTOR_TYPES + SeriesType.IOB_TH_EFFECTIVE

        /**
         * AutoISF fork: series that only AutoISF fills. They are not drawn and not offered while AutoISF is
         * not the active APS algorithm; the saved graph settings keep them for when it is active again.
         */
        val AUTOISF_ONLY_TYPES = ISF_COMPANION_TYPES + SeriesType.PARABOLA_RAW + SeriesType.PARABOLA_SMOOTHED
    }
}

/**
 * Repository for graph configuration persistence.
 * Loads/saves GraphConfig and exposes it as a reactive StateFlow.
 */
interface GraphConfigRepository {

    /** Current graph configuration, updated reactively on changes */
    val graphConfigFlow: StateFlow<GraphConfig>

    /** Update and persist graph configuration */
    fun update(config: GraphConfig)
}
