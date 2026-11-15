package com.oersted.trainer.sim

import com.oersted.trainer.sim.NavaidsChangi.Navaid
import com.oersted.trainer.sim.NavaidsChangi.NavaidKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Hand-written helpers from navaids-changi.ts (the table itself is generated in NavaidsChangi.kt). */

/** TS findChangiNavaid: frequency match (MHz for VOR/ILS, kHz for NDB), available aids only. */
fun findChangiNavaid(freq: Double, kind: String? = null): Navaid? {
    val tol = if (kind == "NDB") 0.6 else 0.01
    return NavaidsChangi.CHANGI_NAVAIDS.firstOrNull { n ->
        if (!n.available) return@firstOrNull false
        if (abs(n.frequency - freq) > tol) return@firstOrNull false
        when (kind) {
            null -> true
            "VOR" -> n.kind == NavaidKind.VOR || n.kind == NavaidKind.VOR_DME
            "ILS" -> n.kind == NavaidKind.ILS || n.kind == NavaidKind.LOC
            else -> n.kind == NavaidKind.NDB
        }
    }
}

fun changiIlsList(): List<Navaid> = NavaidsChangi.CHANGI_NAVAIDS.filter { it.available && it.kind == NavaidKind.ILS }

fun changiVorList(): List<Navaid> =
    NavaidsChangi.CHANGI_NAVAIDS.filter { it.available && (it.kind == NavaidKind.VOR || it.kind == NavaidKind.VOR_DME) }

/** Great-circle distance (NM). */
fun distNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 3440.065
    val phi1 = lat1 * PI / 180
    val phi2 = lat2 * PI / 180
    val dPhi = (lat2 - lat1) * PI / 180
    val dLam = (lon2 - lon1) * PI / 180
    val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLam / 2) * sin(dLam / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** True bearing from A to B, degrees in [0, 360). */
fun bearingTrue(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val phi1 = fromLat * PI / 180
    val phi2 = toLat * PI / 180
    val dLam = (toLon - fromLon) * PI / 180
    val y = sin(dLam) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLam)
    return (atan2(y, x) * 180 / PI + 360) % 360
}
