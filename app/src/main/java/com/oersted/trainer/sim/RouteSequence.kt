package com.oersted.trainer.sim

import com.oersted.trainer.sim.NavaidsChangi.Navaid
import com.oersted.trainer.sim.NavaidsChangi.NavaidKind
import java.util.Locale

/**
 * Port of routeSequence.ts: sequential radio-navigation targets for the Manila to Changi trainer. When the aircraft gets
 * within SWITCH_DISTANCE_NM of the current target, the next frequency is tuned (or an ILS is tuned from 30 nm).
 */
object RouteSequence {
    /** Frequency to tune: MHz for VOR/ILS, kHz for NDB. kind is "VOR", "ILS" or "NDB". */
    data class SequenceItem(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val frequency: Double,
        val kind: String,
        /** Course / OBS to set when tuning this station (magnetic) */
        val courseMag: Double? = null,
        /** Optional note shown to the student */
        val note: String? = null,
    )

    data class NextItem(val next: SequenceItem?, val index: Int, val distanceNm: Double)

    const val SWITCH_DISTANCE_NM = 4.0
    /** An ILS is tuned once the aircraft is this close to its localizer, even if the previous station was never overflown. */
    const val ILS_TUNE_DISTANCE_NM = 30.0

    /**
     * Ordered, chart-backed radio targets for the RPLL to WSSS training route. Not an airway database: no invented
     * intermediate aids are inserted.
     */
    val RPLL_WSSS_SEQUENCE: List<SequenceItem> = listOf(
        SequenceItem(
            id = "MIA",
            name = "Manila VOR/DME",
            lat = 14 + 30.0 / 60 + 28.0 / 3600,
            lon = 121 + 1.0 / 60 + 18.2 / 3600,
            frequency = 114.4,
            kind = "VOR",
            courseMag = 180.0,
            note = "RPLL VOR RWY 06/13/24 charts: MIA 114.4 MHz, DME channel 91X. Chart date 30 Nov 2023; verify current AIP before use.",
        ),
        SequenceItem(
            id = "VTK",
            name = "Tekong DVOR/DME",
            lat = 1 + 24.0 / 60 + 55.0 / 3600,
            lon = 104 + 1.0 / 60 + 20.0 / 3600,
            frequency = 116.5,
            kind = "VOR",
            courseMag = 200.0,
            note = "CAAS AIP ENR 4.1, effective 03 Sep 2026: VTK 116.5 MHz / 112X. Intermediate route/airway between MIA and VTK is not validated here.",
        ),
        SequenceItem(
            id = "ICW",
            name = "Changi ILS RWY 02L",
            lat = 1 + 23.0 / 60 + 7.03 / 3600,
            lon = 103 + 59.0 / 60 + 34.03 / 3600,
            frequency = 110.9,
            kind = "ILS",
            courseMag = 23.0,
            note = "CAAS AIP WSSS AD 2.19 / IAC-1: ICW localizer 110.9 MHz, RWY 02L. This target identifies the localizer; it does not encode the complete approach procedure.",
        ),
    )

    /** TS findNextSequenceItem: advance when the current target is reached, or when the following ILS is within 30 nm. */
    fun findNextSequenceItem(
        currentLat: Double,
        currentLon: Double,
        currentIndex: Int,
        sequence: List<SequenceItem> = RPLL_WSSS_SEQUENCE,
    ): NextItem {
        if (currentIndex >= sequence.size) return NextItem(null, currentIndex, 0.0)
        val target = sequence[currentIndex]
        val d = distNm(currentLat, currentLon, target.lat, target.lon)
        val following = sequence.getOrNull(currentIndex + 1)
        if (following != null) {
            val reachedTarget = d <= SWITCH_DISTANCE_NM
            val nearIls = following.kind == "ILS" && distNm(currentLat, currentLon, following.lat, following.lon) <= ILS_TUNE_DISTANCE_NM
            if (reachedTarget || nearIls) return NextItem(following, currentIndex + 1, d)
        }
        return NextItem(target, currentIndex, d)
    }

    private fun kindLabel(n: Navaid): String = when (n.kind) {
        NavaidKind.NDB -> "NDB"
        NavaidKind.ILS, NavaidKind.LOC -> "ILS"
        else -> "VOR"
    }

    /**
     * TS buildPlanSequence: every plan waypoint that is a catalogued VOR / NDB / ILS (matched by catalogue id, or by
     * identifier AND position within 1 nm, so synthetic fixes never pick up a station by name), in route order, then
     * the plan's selected ILS as the final target.
     */
    fun buildPlanSequence(plan: ActiveFlightPlan?): List<SequenceItem> {
        if (plan == null) return emptyList()
        val items = mutableListOf<SequenceItem>()
        val seen = HashSet<String>()
        fun toItem(n: Navaid): SequenceItem = SequenceItem(
            id = n.id,
            name = n.name,
            lat = n.lat,
            lon = n.lon,
            frequency = n.frequency,
            kind = kindLabel(n),
            courseMag = n.courseMag,
            note = n.source,
        )
        for (wp in plan.waypoints) {
            val aid = NavaidsChangi.CHANGI_NAVAIDS.firstOrNull { n ->
                n.available && n.kind != NavaidKind.DME &&
                    (n.id == wp.id || (n.ident == wp.name && distNm(wp.latitude, wp.longitude, n.lat, n.lon) <= 1))
            } ?: continue
            if (!seen.add(aid.id)) continue
            items.add(toItem(aid))
        }
        val ils = plan.ils
        if (ils != null && !seen.contains(ils.id)) {
            val cat = NavaidsChangi.CHANGI_NAVAIDS.firstOrNull { it.id == ils.id }
            items.add(
                SequenceItem(
                    id = ils.id,
                    name = cat?.name ?: "${ils.airport} ILS RWY ${ils.runway}",
                    lat = ils.latitude,
                    lon = ils.longitude,
                    frequency = ils.frequencyMHz,
                    kind = "ILS",
                    courseMag = ils.courseMag,
                    note = String.format(Locale.ROOT, "%s localizer %.2f MHz, RWY %s, course %03d° (selected in the Flight Plan).", ils.ident, ils.frequencyMHz, ils.runway, jsRoundInt(ils.courseMag)),
                ),
            )
        } else if (ils != null) {
            // The ILS was also added as a waypoint: make sure it is the last target.
            val i = items.indexOfFirst { it.id == ils.id }
            if (i >= 0 && i < items.size - 1) {
                val moved = items.removeAt(i)
                items.add(moved)
            }
        }
        return items
    }

    /** JS Math.round: floor(x + 0.5). */
    private fun jsRoundInt(x: Double): Int = Math.floor(x + 0.5).toInt()
}
