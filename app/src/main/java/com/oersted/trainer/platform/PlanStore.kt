package com.oersted.trainer.platform

import android.content.Context
import com.oersted.trainer.sim.ActiveArrival
import com.oersted.trainer.sim.ActiveFlightWaypoint
import com.oersted.trainer.sim.FlightPlanDraft
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the PLAN draft on this device, the Android side of the web's localStorage "oersted-flight-plan-v1".
 * A missing or unreadable value gives the default draft, as the web does. Not compiled in the sandbox.
 */
class PlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("oersted-flight-plan", Context.MODE_PRIVATE)

    fun load(): FlightPlanDraft {
        val raw = prefs.getString(KEY, null) ?: return FlightPlanDraft()
        return try {
            val o = JSONObject(raw)
            val d = FlightPlanDraft()
            FlightPlanDraft(
                callsign = o.optString("callsign", d.callsign),
                departure = o.optString("departure", d.departure),
                destination = o.optString("destination", d.destination),
                alternate = o.optString("alternate", d.alternate),
                departureRunway = o.optString("departureRunway", d.departureRunway),
                arrivalRunway = o.optString("arrivalRunway", d.arrivalRunway),
                cruiseAltitudeFt = o.optDouble("cruiseAltitudeFt", d.cruiseAltitudeFt),
                cruiseSpeedKt = o.optDouble("cruiseSpeedKt", d.cruiseSpeedKt),
                waypoints = readWaypoints(o.optJSONArray("waypoints")),
                arrival = o.optJSONObject("arrival")?.let { readArrival(it) },
            )
        } catch (e: Exception) {
            FlightPlanDraft()
        }
    }

    fun save(d: FlightPlanDraft) {
        val o = JSONObject()
            .put("callsign", d.callsign)
            .put("departure", d.departure)
            .put("destination", d.destination)
            .put("alternate", d.alternate)
            .put("departureRunway", d.departureRunway)
            .put("arrivalRunway", d.arrivalRunway)
            .put("cruiseAltitudeFt", d.cruiseAltitudeFt)
            .put("cruiseSpeedKt", d.cruiseSpeedKt)
            .put("waypoints", JSONArray().apply { d.waypoints.forEach { put(writeWaypoint(it)) } })
            .apply { d.arrival?.let { put("arrival", writeArrival(it)) } }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private fun readWaypoints(a: JSONArray?): List<ActiveFlightWaypoint> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { i ->
            val w = a.getJSONObject(i)
            ActiveFlightWaypoint(
                id = w.getString("id"), name = w.getString("name"),
                latitude = w.getDouble("latitude"), longitude = w.getDouble("longitude"),
                altitudeFt = w.getDouble("altitudeFt"), speedKt = w.getDouble("speedKt"),
            )
        }
    }

    private fun writeWaypoint(w: ActiveFlightWaypoint): JSONObject = JSONObject()
        .put("id", w.id).put("name", w.name).put("latitude", w.latitude).put("longitude", w.longitude)
        .put("altitudeFt", w.altitudeFt).put("speedKt", w.speedKt)

    private fun readArrival(o: JSONObject): ActiveArrival = ActiveArrival(
        airport = o.getString("airport"), runway = o.getString("runway"), sceneId = o.getString("sceneId"),
        firstFixName = o.getString("firstFixName"), lastFixName = o.getString("lastFixName"),
        ilsFrequencyMHz = o.getDouble("ilsFrequencyMHz"), courseMag = o.getDouble("courseMag"),
    )

    private fun writeArrival(a: ActiveArrival): JSONObject = JSONObject()
        .put("airport", a.airport).put("runway", a.runway).put("sceneId", a.sceneId)
        .put("firstFixName", a.firstFixName).put("lastFixName", a.lastFixName)
        .put("ilsFrequencyMHz", a.ilsFrequencyMHz).put("courseMag", a.courseMag)

    private companion object {
        const val KEY = "draft-v1"
    }
}
