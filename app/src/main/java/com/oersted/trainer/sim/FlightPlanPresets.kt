package com.oersted.trainer.sim

import java.util.Locale

/**
 * Built-in flight plan presets (flight-plan.tsx, loadManilaSingaporePreset). Pure: returns the draft; the caller
 * decides whether to publish it. Procedures stay marked unverified, as on the web.
 * Sets the ICW 02L approach ILS, as the web preset does with icwIls().
 */
object FlightPlanPresets {
    /** RPLL RWY 24 VERDE2B, then the synthetic VCT vectors to ILS RWY 02L at WSSS. */
    fun manilaSingapore(): FlightPlanDraft {
        val sidWps = NavData.buildSidWaypoints("VERDE")
        val verde = sidWps.last()
        val sidFixes = sidWps.map { ActiveFlightWaypoint(it.id, it.name, it.latitude, it.longitude, it.altitudeFt, it.speedKt) }
        val base = FlightPlanDraft(
            callsign = "OERSTED 01",
            departure = "RPLL",
            destination = "WSSS",
            alternate = "WMKK",
            departureRunway = "24",
            arrivalRunway = "02L",
            cruiseAltitudeFt = 31000.0,
            cruiseSpeedKt = 430.0,
            reserveFuelLb = 6000.0,
            routeText = "RPLL RWY 24 VERDE2B, then direct to VCT-A (synthetic vector point), then radar vectors to ILS RWY 02L at WSSS. " +
                "The VERDE to VCT-A leg is NOT a charted ATS route (Kota Kinabalu / Kuala Lumpur FIR charts are not loaded). " +
                "VCT-A/B/C are not charted fixes: they stand in for ATC vectors.",
            sid = "VERDE2B (RWY 24: ${NavData.RPLL_SIDS.getValue("VERDE").initial["24"]}; track out MIA R182 to TAALA, VERDE) - " +
                NavData.Editions.RPLL_SID,
            enrouteRoute = "VERDE DCT VCT-A - not a charted ATS route",
            star = "NONE - current WSSS STARs are RNAV (GNSS), which a 737-200 cannot fly; radar vectors VCT-A / VCT-B / VCT-C instead",
            approach = "ILS/DME RWY 02L (ICW ${String.format(Locale.ROOT, "%.1f", NavData.WSSS_ILS_02L.frequencyMHz)}), " +
                "course 023 deg, ADNIK FAF 4.6 DME - ${NavData.Editions.WSSS_ILS_02L}",
            procedureStatus = "unverified",
            cruiseTopOfClimbNm = 30.0,
            descentStartNm = 180.0,
            approachAltitudeFt = 3500.0,
            waypoints = sidFixes,
        )
        return base.copy(
            destinationKind = "airport",
            destinationLat = NavData.WSSS_ILS_02L.thrLat,
            destinationLon = NavData.WSSS_ILS_02L.thrLon,
            ils = FlightPlanIls.icw(),
        ).withVectoredArrival(verde.latitude, verde.longitude)
    }
}
