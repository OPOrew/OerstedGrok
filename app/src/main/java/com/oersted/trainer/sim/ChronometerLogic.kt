package com.oersted.trainer.sim

/** Chronometer rules from MainInstrumentPanel.tsx (AnalogClock): hand angles, the elapsed-time readout and its flag, and the run / reset controls. */
object ChronometerLogic {
    /** Elapsed-time chronometer state: [elapsed] is whole seconds. */
    data class State(val running: Boolean, val elapsed: Long)

    /** Flag on the chronometer face: all white = reset, half white / half black = stopped, all black = running. */
    enum class Flag { RESET, STOPPED, RUNNING }

    /** Hour, minute and second hand angles (clockwise degrees), from UTC hours, minutes and seconds, as the web draws them. */
    data class Hands(val hourDeg: Double, val minuteDeg: Double, val secondDeg: Double)

    fun hands(utcHour: Int, utcMinute: Int, utcSecond: Int): Hands {
        val hh = utcHour % 12
        return Hands(
            hourDeg = hh * 30.0 + utcMinute * 0.5,
            minuteDeg = utcMinute * 6.0 + utcSecond * 0.1,
            secondDeg = utcSecond * 6.0,
        )
    }

    /** The elapsed readout on the face: mm:ss, with minutes taken modulo 100. */
    fun elapsedText(elapsed: Long): String {
        val mm = (elapsed / 60) % 100
        val ss = elapsed % 60
        return mm.toString().padStart(2, '0') + ":" + ss.toString().padStart(2, '0')
    }

    fun flag(s: State): Flag = when {
        s.running -> Flag.RUNNING
        s.elapsed > 0 -> Flag.STOPPED
        else -> Flag.RESET
    }

    /** One second of the 1 Hz tick: the elapsed time counts only while running. */
    fun tick(s: State): State = if (s.running) s.copy(elapsed = s.elapsed + 1) else s

    /** The dial or left lever: run / stop. */
    fun toggle(s: State): State = s.copy(running = !s.running)

    /** The right knob: stop and zero the elapsed time. */
    fun reset(): State = State(running = false, elapsed = 0)
}
