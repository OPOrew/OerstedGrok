package com.oersted.trainer.sim

/**
 * Glareshield warning and annunciator lamp rules (GlareshieldPanel.tsx: the fire, master caution, warning/caution and
 * amber system lights). Pure: each function returns whether the lamp is lit. "pw" is DC power.
 */
object GlareshieldAnnunciators {
    /** The lamp-test condition: the light test switch, the recall, the TEST key or the annunciator test. */
    fun testActive(pw: Boolean, ltTest: Boolean, recall: Boolean, lightKeyTest: Boolean, annTest: Int): Boolean =
        pw && (ltTest || recall || lightKeyTest || annTest == 1)

    /** An amber system lamp: lit by its condition, or by the lamp test. */
    fun systemLamp(pw: Boolean, condition: Boolean, test: Boolean): Boolean = pw && (condition || test)

    /** Master caution is pending: the system caution is set and not yet acknowledged. */
    fun cautionPending(masterCaution: Boolean, masterCautionAck: Boolean): Boolean = masterCaution && !masterCautionAck

    /** FIRE WARN lamp: a fire warning (engine, APU, wheel well or cargo) without the bell cut-out, or the OVHT/FIRE test. */
    fun fireWarnLit(pw: Boolean, fireWarning: Boolean, bellSilenced: Boolean, fireTestOvhtFire: Boolean): Boolean =
        pw && ((fireWarning && !bellSilenced) || fireTestOvhtFire)

    /** Any fire warning source (the web's fire list). */
    fun anyFireWarning(engine1: Boolean, engine2: Boolean, apu: Boolean, wheelWell: Boolean, cargoFwd: Boolean, cargoAft: Boolean): Boolean =
        engine1 || engine2 || apu || wheelWell || cargoFwd || cargoAft

    /**
     * MASTER CAUTION lamp: caution pending, the recall, a lamp test, the TEST key, or the OVHT/FIRE or FAULT fire tests.
     * Matches the web: the FAULT/INOP test lights only the master caution.
     */
    fun masterCautionLit(
        pw: Boolean, cautionPending: Boolean, recall: Boolean, ltTest: Boolean, lightKeyTest: Boolean,
        fireTestOvhtFire: Boolean, fireTestFault: Boolean,
    ): Boolean = pw && (cautionPending || recall || ltTest || lightKeyTest || fireTestOvhtFire || fireTestFault)

    /** W/S WARNING and W/S CAUTION lamps: power with the lamp test, the RA test or the TEST key. */
    fun windshearLit(pw: Boolean, ltTest: Boolean, raTest: Boolean, lightKeyTest: Boolean): Boolean =
        pw && (ltTest || raTest || lightKeyTest)

    /** Anti-ice problem: anti-ice selected without bleed air, or an alpha vane heat failure. */
    fun antiIceProblem(
        antiIce1: Boolean, bleed1Available: Boolean,
        antiIce2: Boolean, bleed2Available: Boolean,
        alphaVaneFailL: Boolean, alphaVaneFailR: Boolean,
    ): Boolean = (antiIce1 && !bleed1Available) || (antiIce2 && !bleed2Available) || alphaVaneFailL || alphaVaneFailR
}
