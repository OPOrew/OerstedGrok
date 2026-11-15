package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.tan

/**
 * Port of src/lib/sim/radionav.ts: training radio-navigation scenes (local NM frame, threshold at the
 * origin), the training VOR and ILS stations, and the VOR/ILS receiver model. Synthetic training values;
 * not an RF model.
 */
object RadioNav {
    const val RWY_CRS = 62.0
    const val RWY_GS_DEG = 3.01

    enum class NavSceneId { RPLL06, WSSS02L }

    enum class NavStationKind { VOR, ILS }

    data class NavSceneGeom(
        val id: NavSceneId,
        /** inbound course used for the beam geometry, degrees */
        val crs: Double,
        val gsDeg: Double,
        val locAntX: Double,
        val locAntY: Double,
        /** glide-slope validity range, NM from the threshold */
        val gsRangeNm: Double,
        /** threshold elevation subtracted from the altitude for the glide-slope angle, ft */
        val fieldElevFt: Double,
        val label: String,
    )

    data class TrainingStation(
        val id: String,
        val kind: NavStationKind,
        val frequencyMHz: Double,
        val label: String,
        val available: Boolean,
        val scene: NavSceneId?,
    )

    data class Point(val x: Double, val y: Double)

    data class RadioNavData(
        val vorValid: Boolean,
        val locValid: Boolean,
        val gsValid: Boolean,
        /** bearing from the VOR to the airplane (radial), deg */
        val radial: Double,
        /** localizer deviation, deg: positive = deflect bar right (airplane left of course) */
        val locDev: Double,
        /** glide-slope error, deg: positive = above the beam */
        val gsErr: Double,
        val vorDist: Double,
        val thrDist: Double,
    )

    val NO_NAV = RadioNavData(vorValid = false, locValid = false, gsValid = false, radial = 0.0, locDev = 0.0, gsErr = 0.0, vorDist = 0.0, thrDist = 0.0)

    private const val NM_FT = 6076.12
    private const val DEG = 180 / PI

    /** JS-style angle difference wrapped to -180..180 (TS angDiff). */
    fun angDiff(to: Double, from: Double): Double = ((((to - from) % 360) + 540) % 360) - 180

    val TRAINING_NAV_STATIONS: List<TrainingStation> = listOf(
        TrainingStation("SIM-VOR-1", NavStationKind.VOR, 112.0, "Training VOR 1", true, null),
        TrainingStation("SIM-RPLL-ILS-06", NavStationKind.ILS, 109.1, "Simulated RPLL ILS RWY 06", true, NavSceneId.RPLL06),
        TrainingStation(NavData.WSSS_ILS_02L.stationId, NavStationKind.ILS, NavData.WSSS_ILS_02L.frequencyMHz, "Simulated WSSS ILS RWY 02L (ICW)", true, NavSceneId.WSSS02L),
    )

    fun findTrainingNavStation(frequencyMHz: Double): TrainingStation? =
        TRAINING_NAV_STATIONS.firstOrNull { kotlin.math.abs(it.frequencyMHz - frequencyMHz) < 0.0001 }

    val VOR_POS = Point(0.5, -0.3)

    private val RWY_RAD = RWY_CRS * PI / 180
    val LOC_ANT = Point(1.6 * sin(RWY_RAD), 1.6 * cos(RWY_RAD))
    val NAV_START_X = -12 * sin(RWY_RAD)
    val NAV_START_Y = -12 * cos(RWY_RAD)
    const val NAV_START_HDG = 62.0

    private val WSSS_C = NavData.WSSS_ILS_02L.trueCourseDeg * PI / 180

    val NAV_SCENES: Map<NavSceneId, NavSceneGeom> = mapOf(
        NavSceneId.RPLL06 to NavSceneGeom(NavSceneId.RPLL06, RWY_CRS, RWY_GS_DEG, LOC_ANT.x, LOC_ANT.y, 10.0, 0.0, "RPLL RWY 06"),
        NavSceneId.WSSS02L to NavSceneGeom(
            NavSceneId.WSSS02L,
            NavData.WSSS_ILS_02L.trueCourseDeg,
            NavData.WSSS_ILS_02L.gsDeg,
            NavData.WSSS_ILS_02L.locAntBeyondThrNm * sin(WSSS_C),
            NavData.WSSS_ILS_02L.locAntBeyondThrNm * cos(WSSS_C),
            NavData.WSSS_ILS_02L.gsRangeNm,
            22.0,
            "WSSS RWY 02L",
        ),
    )

    fun sceneGeom(id: NavSceneId?): NavSceneGeom = NAV_SCENES.getValue(id ?: NavSceneId.RPLL06)

    /** Local position (NM east / north of the 02L threshold) for a geographic position, for the WSSS scene. */
    fun wsssLocalPos(latitude: Double, longitude: Double): Point {
        val w = NavData.WSSS_ILS_02L
        val d = PlanGuidance.distNm(w.thrLat, w.thrLon, latitude, longitude)
        val b = PlanGuidance.brgTrue(w.thrLat, w.thrLon, latitude, longitude) * PI / 180
        return Point(d * sin(b), d * cos(b))
    }

    fun stepPos(p: Point, hdg: Double, tasKt: Double, dt: Double): Point {
        val d = (tasKt / 3600) * dt
        val h = hdg / DEG
        return Point(p.x + d * sin(h), p.y + d * cos(h))
    }

    /** TS radioNav: VOR or ILS reception at a local position and altitude. */
    fun radioNav(p: Point, altFt: Double, tuned: NavStationKind, frequencyMHz: Double? = null, sceneId: NavSceneId = NavSceneId.RPLL06): RadioNavData {
        val scene = NAV_SCENES.getValue(sceneId)
        val station = if (frequencyMHz == null) {
            TRAINING_NAV_STATIONS.firstOrNull { it.kind == tuned && it.available && (it.kind == NavStationKind.VOR || it.scene == sceneId) }
        } else {
            findTrainingNavStation(frequencyMHz)
        }
        // A wrong frequency or unavailable station produces invalid navigation, never a plausible live signal.
        if (station == null || !station.available || station.kind != tuned) return NO_NAV
        // An ILS only works in its own scene.
        if (station.kind == NavStationKind.ILS && station.scene != sceneId) return NO_NAV

        val dxv = p.x - VOR_POS.x
        val dyv = p.y - VOR_POS.y
        val vorDist = hypot(dxv, dyv)
        val radial = (atan2(dxv, dyv) * DEG + 360) % 360

        val dxa = p.x - scene.locAntX
        val dya = p.y - scene.locAntY
        val antDist = hypot(dxa, dya)
        val brg = (atan2(dxa, dya) * DEG + 360) % 360
        val locDev = angDiff(brg, (scene.crs + 180) % 360)
        val locValid = tuned == NavStationKind.ILS && antDist < 25 && antDist > 0.1 && kotlin.math.abs(locDev) < 35

        val thrDist = hypot(p.x, p.y)
        val c = scene.crs * PI / 180
        val along = p.x * sin(c) + p.y * cos(c) // distance past the threshold along the course
        val gsAng = atan2(max(0.0, altFt - scene.fieldElevFt), max(thrDist, 0.01) * NM_FT) * DEG
        // RPLL06 keeps the original east-coordinate test.
        val pastThreshold = if (sceneId == NavSceneId.RPLL06) p.x >= 0.2 else along >= 0.2
        val gsValid = locValid && !pastThreshold && thrDist < scene.gsRangeNm && thrDist > 0.1 && kotlin.math.abs(locDev) < 8 && gsAng < 8

        return RadioNavData(
            vorValid = tuned == NavStationKind.VOR && vorDist < 80 && vorDist > 0.2,
            locValid = locValid,
            gsValid = gsValid,
            radial = radial,
            locDev = locDev,
            gsErr = gsAng - scene.gsDeg,
            vorDist = vorDist,
            thrDist = thrDist,
        )
    }

    data class VorDeviation(val dev: Double, val to: Boolean)

    /** TS vorDevOf: VOR deviation for a selected course; positive = bar right; `to` = TO flag. */
    fun vorDevOf(radial: Double, crs: Double): VorDeviation {
        val from = kotlin.math.abs(angDiff(radial, crs)) < 90
        val dev = if (from) angDiff(crs, radial) else angDiff(radial, (crs + 180) % 360)
        return VorDeviation(max(-90.0, min(90.0, dev)), !from)
    }
}
