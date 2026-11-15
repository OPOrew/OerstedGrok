package com.oersted.trainer.sim

/**
 * Entry point that runs ported derive.ts sections in the same order as the TS derive().
 * Add each new section here, after the sections it depends on.
 * Sections NOT yet ported: pneumatic start/cross-bleed remainder, fuel (other than heater/filter), cargo fire,
 * standby rudder / flight-control PCUs / spoilers, derive() return object, step(), flight phases, autoflight, navigation, pitot-static, audio.
 */
object SimDerive {
    fun computeAll(s: SimState): Derived {
        val d = Derived()
        val elec = ElectricalDerive.apply(s, d)
        ServicesDerive.apply(s, d, elec)
        AuxPowerDerive.apply(s, d, elec)
        FuelFeedDerive.apply(s, d)
        BleedDerive.apply(s, d)
        PackDerive.apply(s, d)
        HydraulicsDerive.apply(s, d, elec)
        GearDerive.apply(s, d)
        AutobrakeDerive.apply(s, d)
        StartBleedDerive.apply(s, d, elec)
        CargoFireDerive.apply(s, d, elec)
        HydraulicCautionDerive.apply(s, d, elec)
        InterlocksDerive.apply(s, d)
        FlightControlDerive.apply(s, d)
        HydraulicUsersDerive.apply(s, d)
        ElectricalUsersDerive.apply(s, d, elec)
        ComponentsDerive.apply(s, d, elec)
        ReturnFieldsDerive.apply(s, d)
        LightsDerive.apply(s, d)
        return d
    }
}
