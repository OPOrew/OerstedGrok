package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of src/lib/sim/navData.ts: real-chart navigation data used by the flight-plan guidance
 * (trainer use only). Every number was read from the CAAP / CAAS AIP pages in the web source, and each
 * block records its source edition. Nothing here is a certified navigation database.
 *
 * Magnetic variation is east-positive (magnetic = true - variation).
 */
object NavData {
    object Editions {
        const val RPLL_SID = "RPLL AD 2-15/16 SID, AIRAC AIP AMDT 003/25 (30 OCT 25)"
        const val RPLL_AREA = "RPLL AD 2-13 Area Chart, AIRAC AIP AMDT 087/24 (26 DEC 24)"
        const val RPLL_AERODROME = "RPLL AD 2 aerodrome data, AIRAC AIP AMDT 003/26 (14 MAY 26) - pages marked DRAFT"
        const val WSSS_ILS_02L = "WSSS AD 2 IAC-1 ILS/DME RWY 02L (ICW), AIP Singapore AMDT 01/2025 (20 FEB 2025)"
    }

    data class Runway(
        val id: String,
        /** true bearing of the runway centreline, degrees */
        val trueBrgDeg: Double,
        val thrLat: Double,
        val thrLon: Double,
        val lengthM: Double,
    )

    data class AirportData(
        val icao: String,
        val name: String,
        /** aerodrome reference point (used for distance-to-destination only) */
        val arpLat: Double,
        val arpLon: Double,
        val magVarDeg: Double,
        val runways: Map<String, Runway>,
    )

    /** AIP-style degrees/minutes/seconds to decimal degrees; south and west are negative. */
    fun dms(d: Double, m: Double, s: Double, hemi: Char = 'N'): Double {
        val v = d + m / 60 + s / 3600
        return if (hemi == 'S' || hemi == 'W') -v else v
    }

    private val AIP_COORD = Regex("^(\\d{2,3})(\\d{2})(\\d{2}(?:\\.\\d+)?)([NSEW])$")

    /** Parse an AIP style coordinate such as 140248.9N / 1210134.9E. */
    fun parseAipCoord(text: String): Double {
        val m = AIP_COORD.find(text.trim()) ?: throw IllegalArgumentException("Bad AIP coordinate: $text")
        return dms(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), m.groupValues[3].toDouble(), m.groupValues[4][0])
    }

    val AIRPORT_DATA: Map<String, AirportData> = mapOf(
        "RPLL" to AirportData(
            icao = "RPLL", name = "Manila / Ninoy Aquino Intl",
            arpLat = dms(14.0, 30.0, 36.0), arpLon = dms(121.0, 0.0, 49.0),
            // AIP: 3 deg W (2021), increasing ~6 min/yr -> about 2.5 deg W for 2026.
            magVarDeg = -2.5,
            runways = mapOf(
                "06" to Runway("06", 60.3, dms(14.0, 29.0, 54.83), dms(121.0, 0.0, 5.11), 3514.0),
                // RWY 24 position = the end-of-runway coordinate in the AIP aerodrome table. It lies about 100 m
                // short of the listed 3514 m, so treat it as the runway end, within roughly 100 m of the threshold.
                "24" to Runway("24", 240.3, dms(14.0, 30.0, 49.78), dms(121.0, 1.0, 44.02), 3514.0),
            ),
        ),
        "WSSS" to AirportData(
            icao = "WSSS", name = "Singapore Changi",
            arpLat = 1.3502, arpLon = 103.9940,
            // IAC-1: VAR 0 deg 23' E (2020)
            magVarDeg = 0.38,
            runways = mapOf(
                "02L" to Runway("02L", 23.02, dms(1.0, 20.0, 56.27), dms(103.0, 58.0, 38.82), 4000.0),
                "02C" to Runway("02C", 23.01, dms(1.0, 19.0, 43.51), dms(103.0, 59.0, 5.86), 4000.0),
            ),
        ),
        "WMKK" to AirportData(icao = "WMKK", name = "Kuala Lumpur Intl", arpLat = 2.7456, arpLon = 101.7072, magVarDeg = 0.2, runways = emptyMap()),
    )

    /** East-positive magnetic variation, interpolated by latitude between the two charted endpoints (RPLL -> WSSS). */
    fun magVarAt(latitude: Double): Double {
        val northLat = 14.5
        val northV = AIRPORT_DATA.getValue("RPLL").magVarDeg
        val southLat = 1.35
        val southV = AIRPORT_DATA.getValue("WSSS").magVarDeg
        val t = max(0.0, min(1.0, (latitude - southLat) / (northLat - southLat)))
        return southV + (northV - southV) * t
    }

    /* ── RPLL published SID reporting points (SID chart coordinate table) ── */
    private val RAW_FIXES: Map<String, Pair<String, String>> = mapOf(
        "ATMIG" to ("142808.0N" to "1202705.6E"), "BALAY" to ("135409.6N" to "1204342.0E"), "BATAN" to ("143937.0N" to "1203333.3E"),
        "BETEL" to ("141038.1N" to "1201046.1E"), "BORGA" to ("142259.8N" to "1204210.6E"), "BUCAL" to ("133559.6N" to "1203456.0E"),
        "CAMBA" to ("141926.6N" to "1211457.4E"), "CARMO" to ("141540.0N" to "1210407.5E"), "DILAN" to ("142925.9N" to "1204552.7E"),
        "FONSO" to ("150454.3N" to "1205705.8E"), "IBAGO" to ("133237.8N" to "1211834.3E"), "INDAN" to ("141124.9N" to "1205203.0E"),
        "IPATA" to ("135157.4N" to "1214848.0E"), "KEERO" to ("141355.9N" to "1201907.2E"), "LABON" to ("143826.0N" to "1205218.7E"),
        "LAIYA" to ("133418.9N" to "1212340.4E"), "LIGPA" to ("135722.4N" to "1210736.1E"), "MIGEL" to ("150313.6N" to "1210318.5E"),
        "NATIB" to ("144247.2N" to "1202352.8E"), "OLIVA" to ("145657.8N" to "1204320.4E"), "RADEG" to ("145221.3N" to "1205449.6E"),
        "REPAT" to ("144952.8N" to "1203920.5E"), "SABIG" to ("140918.7N" to "1212727.6E"), "SIMBO" to ("144740.2N" to "1202352.8E"),
        "SIRCA" to ("142738.3N" to "1202002.3E"), "TAALA" to ("140248.9N" to "1210134.9E"), "TALIM" to ("142123.7N" to "1211941.2E"),
        "TANAY" to ("143326.0N" to "1212039.4E"), "TIMON" to ("140309.5N" to "1215622.7E"), "TONDO" to ("143343.6N" to "1205127.8E"),
        "VERDE" to ("133013.2N" to "1210154.4E"), "VOTAS" to ("144238.1N" to "1205304.2E"),
    )

    data class LatLon(val lat: Double, val lon: Double)

    val RPLL_FIXES: Map<String, LatLon> = RAW_FIXES.mapValues { (_, c) -> LatLon(parseAipCoord(c.first), parseAipCoord(c.second)) }

    /** MIA DVOR/DME 114.4 (Area chart / SID chart). */
    val MIA_DVOR = Triple(dms(14.0, 30.0, 28.7), dms(121.0, 1.0, 18.2), 114.4)

    data class SidFix(val name: String, val minAltFt: Double?)

    data class SidDef(
        val base: String,
        /** MIA radial the SID tracks out on (magnetic) - informational */
        val radial: Int,
        val fixes: List<SidFix>,
        /** Suffix printed on the chart for RWY 06 / 24 / 13 (SID chart page 2, AMDT 003/25). */
        val suffixes: Triple<String, String, String>,
        /** Take-off instruction printed for each runway. */
        val initial: Map<String, String>,
        val remarks: String? = null,
    )

    private const val L5 = "Left turn within 5.0 NM"
    private const val R5 = "Right turn within 5.0 NM"
    private const val S3R = "Straight-out departure to 3000 FT, then right climbing turn"
    private const val S3L_P1 = "Straight-out departure to 3000 FT, then left climbing turn avoiding RP-P1"
    private const val H113R = "Turn left heading 113°M to 3000 FT, then right climbing turn"
    const val RPLL_SID_NOTES = "RWY 06 PDG 5.0% until 3000 FT then 3.3%. From sunrise to sunset IFR departures on RWY 06/24 climb in the IFR Climb/Descent Area to cross the VFR area at or above 3000 FT."

    private fun initial(r06: String, r24: String, r13: String) = mapOf("06" to r06, "24" to r24, "13" to r13)
    private val SID_SUFFIX_A = Triple("2A", "2B", "2C")

    /**
     * CAB, JOM and SIMBO SIDs (VOR track-in or special turns) are not encoded: they need leg types this
     * planner does not model.
     */
    val RPLL_SIDS: Map<String, SidDef> = mapOf(
        "VERDE" to SidDef("VERDE", 182, listOf(SidFix("TAALA", 8000.0), SidFix("VERDE", 11000.0)), SID_SUFFIX_A, initial(S3R, L5, H113R),
            "Crossing altitude restriction at TAALA may be increased by ATC subject to RP-R72 and MTA-2 activities."),
        "BETEL" to SidDef("BETEL", 251, listOf(SidFix("BORGA", 5000.0), SidFix("KEERO", 6000.0), SidFix("BETEL", null)), SID_SUFFIX_A, initial(S3R, R5, H113R)),
        "BUCAL" to SidDef("BUCAL", 208, listOf(SidFix("INDAN", 5000.0), SidFix("BALAY", 6000.0), SidFix("BUCAL", 9000.0)), SID_SUFFIX_A, initial(S3R, L5, H113R)),
        "IBAGO" to SidDef("IBAGO", 172, listOf(SidFix("CARMO", 5000.0), SidFix("LIGPA", 9000.0), SidFix("IBAGO", null)), SID_SUFFIX_A, initial(S3R, L5, H113R)),
        "IPATA" to SidDef("IPATA", 133, listOf(SidFix("CAMBA", 6000.0), SidFix("SABIG", 10000.0), SidFix("IPATA", null)), SID_SUFFIX_A, initial(S3R, L5, H113R)),
        "LAIYA" to SidDef("LAIYA", 172, listOf(SidFix("CARMO", 5000.0), SidFix("LIGPA", 8000.0), SidFix("LAIYA", null)), SID_SUFFIX_A, initial(S3R, L5, H113R),
            "Crossing altitude at LIGPA may be increased by ATC subject to RP-R72 and MTA-2 activities."),
        "TIMON" to SidDef("TIMON", 120, listOf(SidFix("TALIM", 5000.0), SidFix("TIMON", 8000.0)), SID_SUFFIX_A, initial(S3R, L5, H113R)),
        "NATIB" to SidDef("NATIB", 292, listOf(SidFix("TONDO", 6000.0), SidFix("BATAN", null), SidFix("NATIB", null)), SID_SUFFIX_A, initial(S3L_P1, S3R, H113R)),
        "OLIVA" to SidDef("OLIVA", 330, listOf(SidFix("VOTAS", 6000.0), SidFix("OLIVA", null)), SID_SUFFIX_A, initial(S3L_P1, S3R, H113R)),
        "SIRCA" to SidDef("SIRCA", 269, listOf(SidFix("DILAN", 6500.0), SidFix("ATMIG", 7000.0), SidFix("SIRCA", null)), Triple("2D", "2E", "2F"), initial(S3R, S3R, H113R)),
    )

    fun sidProcedureName(base: String, rwy: String): String? {
        val def = RPLL_SIDS[base] ?: return null
        return when (rwy) {
            "06" -> def.base + def.suffixes.first
            "24" -> def.base + def.suffixes.second
            "13" -> def.base + def.suffixes.third
            else -> null
        }
    }

    /* ── WSSS ILS/DME RWY 02L (ICW) ── */
    data class IlsFix(val name: String, val dmeNm: Double, val altFt: Double?, val lat: Double? = null, val lon: Double? = null)

    data class IlsScene(
        val id: String,
        val airport: String,
        val runway: String,
        val stationId: String,
        val frequencyMHz: Double,
        /** published final approach course, magnetic */
        val courseMag: Double,
        val trueCourseDeg: Double,
        val gsDeg: Double,
        /** distance (NM) from threshold along the course to the localizer antenna. ASSUMED, not charted. */
        val locAntBeyondThrNm: Double,
        /** glide-slope validity range (NM). */
        val gsRangeNm: Double,
        val thrLat: Double,
        val thrLon: Double,
        /** charted fixes along the final approach, with distance from threshold (NM) */
        val fixes: List<IlsFix>,
        val source: String,
    )

    val WSSS_ILS_02L = IlsScene(
        id = "WSSS-02L", airport = "WSSS", runway = "02L", stationId = "SIM-WSSS-ILS-02L",
        frequencyMHz = 110.9, courseMag = 23.0, trueCourseDeg = 23.02, gsDeg = 3.0,
        locAntBeyondThrNm = 2.4, gsRangeNm = 12.0,
        thrLat = AIRPORT_DATA.getValue("WSSS").runways.getValue("02L").thrLat,
        thrLon = AIRPORT_DATA.getValue("WSSS").runways.getValue("02L").thrLon,
        fixes = listOf(
            IlsFix("SAMKO", 16.6, null, dms(1.0, 6.0, 18.0), dms(103.0, 52.0, 28.0)),
            IlsFix("APIPA", 16.0, 3500.0),
            IlsFix("ANUMA", 11.1, 3500.0),
            IlsFix("AKIPO", 7.8, 2500.0),
            IlsFix("ADNIK", 4.6, 1500.0, dms(1.0, 16.0, 51.0), dms(103.0, 56.0, 55.0)),
            IlsFix("ABVON", 0.7, null),
        ),
        source = Editions.WSSS_ILS_02L,
    )

    /** True for the WSSS 02L arrival scene id. Accepts the legacy un-hyphenated spelling too. */
    fun isWsssArrivalSceneId(id: String?): Boolean = id == WSSS_ILS_02L.id || id == "WSSS02L"

    /* ── geometry helpers ── */
    private const val R_NM = 3440.065
    private fun toRad(d: Double) = d * PI / 180

    /** Point reached from (lat, lon) on a true bearing after distNm. */
    fun destPoint(lat: Double, lon: Double, brgTrueDeg: Double, distNm: Double): LatLon {
        val la = toRad(lat)
        val lo = toRad(lon)
        val b = toRad(brgTrueDeg)
        val d = distNm / R_NM
        val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
        return LatLon(la2 * 180 / PI, ((lo2 * 180 / PI + 540) % 360) - 180)
    }

    data class PlanWaypoint(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val altitudeFt: Double,
        val speedKt: Double,
        /** "charted", "user", "vector" or null */
        val source: String? = null,
    )

    /** Departure fixes for a SID, using published coordinates and chart minimum altitudes. */
    fun buildSidWaypoints(base: String): List<PlanWaypoint> {
        val def = RPLL_SIDS[base] ?: return emptyList()
        return def.fixes.map { f ->
            val c = RPLL_FIXES.getValue(f.name)
            PlanWaypoint("SID-${f.name}", f.name, c.lat, c.lon, f.minAltFt ?: 0.0, 0.0, "charted")
        }
    }

    /**
     * Synthetic radar-vector pattern for a conventional (non-RNAV) arrival to an ILS runway.
     * Fixes VCT-A/B/C are NOT charted points: they stand in for ATC vectors (downwind, base, intercept).
     * Coming from the right of the final course the pattern is built on the right, otherwise on the left.
     */
    fun buildVectorArrival(scene: IlsScene, fromLat: Double, fromLon: Double): Pair<List<PlanWaypoint>, Int> {
        val c = scene.trueCourseDeg
        val brgThrToFrom = atan2(
            sin(toRad(fromLon - scene.thrLon)) * cos(toRad(fromLat)),
            cos(toRad(scene.thrLat)) * sin(toRad(fromLat)) - sin(toRad(scene.thrLat)) * cos(toRad(fromLat)) * cos(toRad(fromLon - scene.thrLon)),
        ) * 180 / PI
        val rel = ((((brgThrToFrom - c) + 540) % 360) - 180)
        val side = if (rel >= 0) 1 else -1
        val recip = (c + 180) % 360
        val offBrg = (c + 90 * side + 360) % 360
        val a = destPoint(scene.thrLat, scene.thrLon, offBrg, 8.0)
        val b = destPoint(a.lat, a.lon, recip, 25.0)
        val cc = destPoint(scene.thrLat, scene.thrLon, recip, 13.5)
        fun mk(n: String, p: LatLon, alt: Double, spd: Double) = PlanWaypoint("VCT-$n", "VCT-$n", p.lat, p.lon, alt, spd, "vector")
        return Pair(listOf(mk("A", a, 5000.0, 220.0), mk("B", b, 4000.0, 200.0), mk("C", cc, 3500.0, 190.0)), side)
    }
}
