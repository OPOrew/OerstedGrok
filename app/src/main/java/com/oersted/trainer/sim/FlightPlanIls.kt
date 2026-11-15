package com.oersted.trainer.sim

import java.util.Locale
import kotlin.math.floor

/**
 * ILS choice in the planner (flight-plan.tsx: ilsFromNavaid, isIlsNavaid, icwIls, setIls, clearIls). Pure: returns the new draft.
 * The ILS is the approach guidance source; it does not change the destination airport.
 * A vectored arrival is built for ILS 02L only, so choosing another ILS drops it.
 */
object FlightPlanIls {
    fun isIlsNavaid(n: NavaidsChangi.Navaid): Boolean = n.kind == NavaidsChangi.NavaidKind.ILS || n.kind == NavaidsChangi.NavaidKind.LOC

    fun fromNavaid(n: NavaidsChangi.Navaid): ActiveIls = ActiveIls(
        id = n.id,
        ident = n.ident,
        name = n.name,
        airport = n.airport,
        runway = n.runway ?: "",
        frequencyMHz = n.frequency,
        courseMag = n.courseMag ?: 0.0,
        latitude = n.lat,
        longitude = n.lon,
    )

    /** icwIls: the ICW 02L localizer from the catalog, or null when the catalog lacks it. */
    fun icw(): ActiveIls? = NavaidsChangi.CHANGI_NAVAIDS.firstOrNull { it.id == "WSSS-ILS-02L" }?.let { fromNavaid(it) }

    /** setIls: the chosen station becomes the approach ILS; the approach text is set from it. */
    fun set(draft: FlightPlanDraft, station: NavaidsChangi.Navaid): FlightPlanDraft {
        if (!isIlsNavaid(station)) return draft
        val ils = fromNavaid(station)
        val keepsVectors = draft.arrival == null || (draft.arrival.sceneId == NavData.WSSS_ILS_02L.id && station.id == "WSSS-ILS-02L")
        val withIls = draft.copy(
            ils = ils,
            arrivalRunway = ils.runway.ifEmpty { draft.arrivalRunway },
            approach = "ILS ${ils.ident} ${fmt2(ils.frequencyMHz)} MHz RWY ${ils.runway}, course ${pad3(ils.courseMag)}° - ${station.source}",
        )
        if (keepsVectors) return withIls
        // The VCT vector fixes exist only for the 02L arrival; they are identified by their VCT- ids here.
        return withIls.copy(arrival = null, waypoints = withIls.waypoints.filter { !it.id.startsWith("VCT-") })
    }

    /** clearIls: removes the ILS choice only. */
    fun clear(draft: FlightPlanDraft): FlightPlanDraft = draft.copy(ils = null)

    /** JS Math.round for the course, then zero-padded to three digits. */
    private fun pad3(deg: Double): String = String.format(Locale.ROOT, "%03d", floor(deg + 0.5).toInt())

    private fun fmt2(x: Double): String = String.format(Locale.ROOT, "%.2f", x)
}
