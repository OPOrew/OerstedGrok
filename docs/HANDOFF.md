# HANDOFF — Oersted 737 Line Lab → Android

## Status by phase
| Phase | Scope | Status |
|---|---|---|
| 0 | Gradle/Compose scaffold, GitHub Actions APK build, reference copy, docs | **Done** (CI not yet run — first run may need small fixes) |
| 1 | Static data export (systems, 183 parts, AMM chapters) + Systems / Library / AMM screens | **Done (basic UI)**. Hangar schematic (`AircraftPlan.tsx`), PartCard detail, procedures checklist, AMM PDF viewer **not ported** |
| 2 | Sim core TS → Kotlin (`derive.ts` 2917 lines, `initial.ts`, `store.ts`, `types.ts`, lights, flight phases, audio, nav…) | **2a/2b done**: `sim/SimTypes.kt` is **generated** by `tools/gen-sim.mts` from `types.ts` + `lights.ts`, with `initialSimState()` from `initial.ts` (27 classes, 0 warnings, initial values checked). `SimMath` ported. **2c: ported and parity-tested: electrical sources (`sim/ElectricalDerive.kt`, 13 scenarios) and services (`sim/ServicesDerive.kt`: recirc, fuel heaters + filter icing, equipment cooling, yaw damper, oxygen, doors, CVR, interphone, ELT, rain repellent, gen-drive indications; 20 scenarios). Entry point `sim/SimDerive.computeAll(s)` runs them in TS order.** Aux power, fuel feed, bleed and the pack model are also ported (see below). **Aux power also ported** (`sim/AuxPowerDerive.kt`: drain heaters/masts, alpha-vane heat, standby power, ground service, battery charger feed, 28VAC, electrical source labels; 14 scenarios). **Fuel feed also ported** (`sim/FuelFeedDerive.kt`: boost/center pumps, crossfeed, engine shutoff, feed pressure and source labels, low-pressure lights, scavenge; 12 scenarios). **Bleed/manifold also ported** (`sim/BleedDerive.kt`: engine bleed, isolation valve, APU/ground/engine manifolds, anti-ice load, duct pressure, DUAL BLEED; 9 scenarios). **Pack model also ported** (`sim/PackDerive.kt`: `derivePack` for left/right, mix valve, temp control faults, 35°F system, ACM overheat, cabin supply, ground conditioned air; 14 scenarios, 73 fields). Known gap: `packLStages`/`packRStages` (nested objects) are set in Kotlin but NOT parity-checked, because the generator handles only flat values. **Hydraulic pressures also ported** (`sim/HydraulicsDerive.kt`: System A/B pumps, ground service, ground interconnect, flight-control availability, thrust reversers, standby demand; 15 scenarios, 12 fields). **Gear position and lights also ported** (`sim/GearDerive.kt`: gear up/down/transit, selector disagreement, warning horn, per-gear green/red lights, injected gear faults; 9 scenarios). NOT yet ported from derive.ts: antiskid/autobrake (~813–866), engine start bleed + annunciators (~872–905), cargo fire detection (~906–1125), flight controls (~1170–1200), spool (~2200), flight phases, pitot-static (fuel feed/tanks, bleed/pneumatic, packs, hydraulics, gear), ~1173–1400 (flight controls, spoilers, EPR), ~1748–1900 (fuel temp/heat detail, drain/gen-drive responses, oxygen detail), ~1919–2226 (wheel well + cargo fire, scavenge, start control), ~2226–2917 (N1/N2 spool, brakes, flight phases, pitot-static, flight controls). **Rest of derive.ts (hydraulics, fuel, pneumatic, lights, flight, nav…), step(), store, audio not ported.** |
| 3 | Sim runtime (30 Hz ticker), state store, cockpit screens (`routes/cockpit.tsx` 2706 lines + ~50 panel components) | **Not started** (placeholder tab) |

## What works now
- `app/src/main/java/com/oersted/trainer/data/Repository.kt` loads `assets/data/*.json`.
- `DataParityTest` checks the exported counts (15 systems / 183 parts / 42 AMM chapters).
- `SimMathTest` checks spool and approach.
- CI: `.github/workflows/android.yml` runs unit tests, builds the debug APK, and uploads it as an artifact. Pushing a tag `v*` also creates a GitHub Release with the APK.

## Verified in sandbox
- `SimMath.kt` + an ad-hoc check compiled and passed with kotlinc 2.4.x (`check` main, all asserts true).
- The Gradle wrapper was generated (8.10.2). The Android build itself was **not** run (no Maven/Google access from sandbox).

## Generated code — read before editing
- `sim/SimTypes.kt` is generated. Classes are plain `class` with `var` fields (not `data class`: a 430-field constructor exceeds the JVM 255-slot limit). `copy()` is deep for nested objects, shallow for lists/maps (mirrors the TS `{...s}` spread).
- Strings replace TS string-literal unions (no Kotlin enums). Compare with the same literal strings the TS uses.
- `TimeScale` and other numeric literal unions are `Double`.
- Regenerate with `cd tools && npm install && npm run dump-init && npm run gen-sim`. Re-run `SimTypesTest` after.

## Parity workflow (use for every ported section)
1. Add scenarios to `tools/parity/scenarios.mts` (flat SimState keys; `faults`/`circuitBreakers` merge).
2. Add `tools/parity/sections/<name>.mts` exporting `FIELDS` and `SCENARIOS` (copy the pattern in `services.mts`). Then `node_modules/.bin/tsx parity/dump-section.mts parity/sections/<name>.mts <web src> parity/<name>.json`. The dump throws if a field is not returned by the web `derive()`.
3. Only assert fields the TS actually returns on `Derived`. Locals (e.g. `indicationPower`, `btb1Closed`) are NOT returned and cannot be tested this way. A missing key silently reads as `false` in the dump, so check the field exists first.
4. `node_modules/.bin/tsx parity/gen-parity-test.mts parity/<name>.json ../app/src/test/java/com/oersted/trainer/sim/<Name>ParityTest.kt <Name>ParityTest` generates the Kotlin test.
5. Port the section to Kotlin (`sim/<Name>Derive.kt`), keeping TS comments. Add it to `SimDerive.computeAll` after the sections it depends on. Local locals from earlier sections go through `ElecLocals` (extend it if needed).
6. Run `bash tools/parity/run-local-checks.sh` (with `KOTLINC=/path/to/kotlinc` if needed). In CI, Gradle runs the same tests.

## Recommended next steps (in order)
1. **Push to GitHub, run the workflow, fix compile errors.** The UI code was written blind. Expect small import or API fixes in `AppRoot.kt`, `Repository.kt`, `MainActivity.kt`.
2. **Phase 2c — derive (continue).** Next: autobrake/antiskid (~813–866), then engine start bleed and annunciators (~872–905), then cargo fire detection (~906–1125).
   Parity note: `tools/parity/dump-section.mts` now flattens nested outputs into dotted names (e.g. `gearLights.nose.green`). Those names work directly in the generated Kotlin tests. Gear/brake logic reads `hydA`/`hydB` from `HydraulicsDerive`, so add that section's outputs to `Derived` reads, not locals. Add a stage-object check to the parity generator when convenient (extend `gen-parity-test.mts` to flatten nested objects into dotted names). Add `ElecLocals` fields as needed. Note: `s.wow`, `s.standbyPower` etc. are already in SimState. Note `derive()` in TS is a single function; port each section as a function that writes into `Derived`, then call them in the same order as the TS. Port `derive.ts` in sections (electrical → hydraulic → pneumatic → fuel → …). Each section gets a test with a hand-computed expected value taken from running the TS (`tsx` script in `tools/`).
3. **Phase 2d — step.** Port `step(s, dt)` after derive. Keep `Date.now()` log timestamps injectable.
4. **Phase 3 — runtime.** `SimStore` as a `StateFlow<SimState>` with a 30 Hz coroutine (`SimTicker`). Expose `patch()`, `startEngine`, `shutdownEngine`, `toggleFault`, `runScenario`.
5. **Phase 3 — cockpit.** Start with the smallest panel: `RealisticElecPanel.tsx` (253 lines), wired to `patch()`. Then `FuelTrainerPanel` / `RealisticFuelPanel`. Leave the 3D instrument panels (`MainInstrumentPanel3D*`, three.js, React Three Fiber) for last; decide with the product owner whether they should be Sceneform/OpenGL, a WebView, or dropped.

## Known gaps / risks
- `derive.ts` relies on `Math.*`, `??`, optional chaining, `Record` lookups, and `Object` spreads. Map these to Kotlin carefully (`?:`, `maps.getOrDefault`, `copy`).
- Audio is ported (see the Sounds section at the end): software mixer + AudioTrack. It has never been heard: check engine level, loop seams, click latency and underruns on a real device. Not ported: the engine-space reverb/echo and the horn/wailer/shaker synth fallbacks.
- `leaflet`/`react-leaflet` map (`InteractiveNavMap.tsx`) → MapLibre or osmdroid, or a WebView. Not started.
- Live weather (`liveWeather.ts`) uses `fetch`. Use Ktor or `HttpURLConnection`.
- `routes/flight-plan.tsx`, `procedures.tsx`, `library.tsx`, `systems.$id.tsx` are not ported.
- Do not invent aircraft values. If a constant is missing from the TS, copy it from the TS, don't guess.

## How to do the next phase efficiently
- Work in the `reference/web/src/lib/sim/` tree. Use `grep -n` to find the function you need, then read it in ranges.
- Port by reading a function, writing Kotlin, adding a test, and committing. Stop when context is tight and write the next agent's notes into this file's "Progress log" below.

## Progress log
- 2026-11-01 (session 1): Phase 0 scaffold; Phase 1 data export and basic screens; Phase 2 started with `SimMath`.
- 2026-11-01 (session 10, Haiku 5.5): Gear position and lights ported (GearDerive.kt, 9 scenarios). Parity dump now flattens nested outputs (also covers packLStages now). 110 local checks pass.
- 2026-11-01 (session 9, Haiku 5.5): Hydraulic pressure section ported (HydraulicsDerive.kt, 15 scenarios). ElecLocals gained g1Healthy, g2Healthy, tieSource1/2. 101 local checks pass.
- 2026-11-01 (session 8, Haiku 5.5): Pack model ported (PackDerive.kt, 14 scenarios, 73 fields). Fixed a bug where right-side pack outputs were never written (putSide only handled the left). 86 local checks pass.
- 2026-11-01 (session 7, Haiku 5.5): Bleed/manifold section ported (BleedDerive.kt, 9 scenarios). 72 local checks pass.
- 2026-11-01 (session 6, Haiku 5.5): Fuel feed section ported (FuelFeedDerive.kt, 12 scenarios). `crossfeedOpen` is a TS local, so it is recomputed in Kotlin and excluded from parity. 63 local checks pass.
- 2026-11-01 (session 5, Haiku 5.5): Aux-power section ported (AuxPowerDerive.kt, 14 scenarios). Parity dump now keeps string outputs. 51 local checks pass.
- 2026-11-01 (session 4, Haiku 5.5): Services section ported (ServicesDerive.kt, 20 scenarios); SimDerive.computeAll entry point; generic parity generator and dump (tools/parity/dump-section.mts, gen-parity-test.mts, sections/*.mts). 37 local checks pass.
- 2026-11-01 (session 3, Haiku 5.5): Phase 2c electrical section ported and parity-tested (13 scenarios, 17 local checks pass). Parity workflow documented above.
- 2026-11-01 (session 2, Haiku 5.5): Phase 2a/2b. `SimTypes.kt` generated from TS AST + initialSim JSON. Verified with kotlinc (initial values, deep copy). Gradle/Android build still unverified.

- Session 11: ported antiskid/autobrake section (derive.ts ~813-871) to AutobrakeDerive.kt; parity scenarios tools/parity/sections/autobrake.mts (11 scenarios, 9 returned fields); AutobrakeParityTest.kt generated; local checks pass=121 fail=0. Note: probe-fields.mts lists FIELDS the web derive() does not return (TS locals) and they must be removed from FIELDS.

- Session 12: ported start-valve / 13th-stage bleed / off-bus and fire annunciator block (derive.ts ~870-905) to StartBleedDerive.kt; parity scenarios tools/parity/sections/startbleed.mts (12 scenarios, 11 fields); StartBleedParityTest.kt generated; local checks pass=133 fail=0. Cargo fire detection (derive.ts 906+) is next.

- Session 13: ported cargo fire detection and FIRE WARN summary (derive.ts ~906-911, 1 line for fireWarn) to CargoFireDerive.kt; parity scenarios tools/parity/sections/cargofire.mts (9 scenarios, 3 fields); CargoFireParityTest.kt generated; local checks pass=142 fail=0. Next: hydraulic LOW PRESS lights and masterCaution (derive.ts ~913-930), then interlocks list (~932-1010+).

- Session 14: ported hydraulic LOW PRESS lights and MASTER CAUTION (derive.ts ~913-930) to HydraulicCautionDerive.kt; parity scenarios tools/parity/sections/hydcaution.mts (11 scenarios, 5 fields); HydraulicCautionParityTest.kt generated; local checks pass=153 fail=0. Next: interlocks list (derive.ts ~932 onward).

- Session 15: ported interlocks advisory list (derive.ts ~932-1123) to InterlocksDerive.kt; also sets apuLowOil and generator1/2ProtectionTripped. Parity tooling extended: arrays of {id,text,tone} dump as "id|text|tone" lines, and a section may export EXPR with a Kotlin expression for a field. Scenarios tools/parity/sections/interlocks.mts (17 scenarios, 4 fields); local checks pass=170 fail=0. Next: standby rudder, flight-control PCUs, spoilers (derive.ts ~1125-1290).

- Session 16: ported standby rudder, alternate flaps (TE/LE drive), control-surface mixer (controlSurfaces), and adverse-yaw cue (derive.ts ~1125-1257) to FlightControlDerive.kt; parity scenarios tools/parity/sections/flightcontrol.mts (14 scenarios, 10 fields incl. controlSurfaces dotted names); FlightControlParityTest.kt generated; local checks pass=184 fail=0. Next: hydraulicUsers and electricalUsers arrays (derive.ts ~1260-1400), which need TR/battery-charger/recirc-fan/equipment-cooling locals still to be ported.

- Session 17: ported hydraulic users list (derive.ts ~1148-1170) to HydraulicUsersDerive.kt; parity scenarios tools/parity/sections/hydusers.mts (8 scenarios, 1 array field); HydraulicUsersParityTest.kt generated; local checks pass=192 fail=0. Next: electricalUsers array (derive.ts ~1170-1200), which needs TR, battery-charger, static-inverter, recirc-fan and equipment-cooling locals.

- Session 18: ported electrical users list (derive.ts ~1170-1200) to ElectricalUsersDerive.kt, recomputing inner locals hydBBus1/2Electrical, tr3InputAvailable and stbyNeed; parity scenarios tools/parity/sections/elecusers.mts (17 scenarios, 1 array field); ElectricalUsersParityTest.kt generated; local checks pass=209 fail=0. Next: components list (derive.ts ~1290-1410), EPR, then the derive() return object.

- Session 19: ported components list (derive.ts ~1290-1410) to ComponentsDerive.kt (71 modeled entries plus PartsTable inventory); parity scenarios tools/parity/sections/components.mts; ComponentsParityTest.kt generated. Fixed BleedDerive never storing leftManifold/rightManifold (pneumatic systemHealth mismatch).

- Session 20: return-object assignment pass. ElectricalDerive now stores groundPowerAvailable, batteryPower, generator1/2BusPowered, generator1/2ThreePhaseHealthy, transferRelay1/2Alt, hotBatteryBusPowered, essentialDcPower, externalDcControl, ignition1/2Left/RightOn. Also stored isolationOpen (BleedDerive), standbyAutoTransfer (AuxPowerDerive), cvrOff (ServicesDerive), apuGenOnBus (StartBleedDerive). Fixed a real bug: drainMastForwardLinePowered was read by ComponentsDerive but never assigned (AuxPowerDerive now sets it). local checks pass=227 fail=0.
  STILL NOT PORTED (the TS computes these in sections the Kotlin port hasn't reached; they need real ports, not just assignments): oilPressure1/2, oilChipDetected1, epr1/epr2 (eprFromN1, TS ~1402), reservoirPressurized, reservoirGroundAir, hydAQty/hydBQty/hydSQty reads exist but the reservoir outputs don't, targetDecel/manualBrakeOverride/actualDecel/brakeSource/wheelSpeeds/wheelBrakePressures (TS ~1245-1262), leExtended/leTransit/speedBrakeArmed/feelDiff/controlAuthority/noseWheelSteeringAngle/noseWheelSteeringAvailable (TS ~1249 onward), stbyOn, lightComponents and deriveLights (lib/sim/lights.ts), controlCabinTempF/passengerCabinTempF (the Pack code reads them as inputs; the return values still need to be stored). gen1/gen2DriveDisconnected are not in the TS return type, so they are skipped.
  Next: oil and EPR (TS ~1400), then reservoirs, then brake/decel (TS ~1245-1262), then flight-control outputs, then deriveLights.

- Session 21: finished derive.ts in code. (a) Return-object outputs: ReturnFieldsDerive.kt (nose-wheel steering, brake source, wheel speeds/pressures, decel, control authority, LE/speed-brake flags, EPR, oil, cabin temps, stbyOn, quantities), reservoir outputs in HydraulicsDerive.kt, generatorDrive1/2Disconnected in ElectricalDerive.kt. (b) LightsDerive.kt ports lib/sim/lights.ts (90-light table, deriveLights, lightComponents). (c) step() ported in StepPart1/2/3.kt and orchestrated in StepSim.kt, plus controlLabel, pushLog and runway constants. Verified against the web step() over 17 trajectories (557 leaf fields each, up to 2400 ticks at 1/30 s) in StepParityTest.kt, plus AllReturnParityTest (derive() return object, 8 scenarios) and LightsParityTest. Local checks pass=287 fail=0.
  Parity tooling added: tools/parity/dump-step.mts + gen-step-test.mts (trajectory parity; sections/step.mts holds scenarios with `steps`, `dt`, and optional `wx`/`wxAfter` for live weather). dump-section.mts now walks dotted names (e.g. "brakeSource.li") into nested outputs.
  Known gaps and decisions:
  - Live weather: step() takes `liveWxIntensity` as a parameter. The web app reads the global `__oerstedLiveWxIntensity` (liveWeather.ts). The Android caller must pass the live value per tick.
  - Icing rate: the web `__iceRate` is an untyped property that persists across ticks. Kotlin keeps it in StepSim.iceRateByState, keyed by the returned state object (WeakHashMap, identity). Replace with a real SimState field when the TS SimState type gains it (then regenerate SimTypes.kt).
  - `__wxDragPenalty` and `__wxTurbulence` are written by the web step() but never read, so the port drops them.
  - pushLog takes nowMs (the web uses Date.now()), so it is deterministic in tests.
  - Not yet covered by trajectories: pack overheat trips, bleed overheat latches, the autobrake RTO timer through full stop, manual gear release, and the center-pump relay trip. The next agent can add scenarios to sections/step.mts; the harness catches regressions there.
  Next: the UI (ui/) and the remaining modules: flightPhases.ts, autoflight.ts, flightNavigation.ts, pitotStatic.ts, store.ts, nav/radio, audio.ts, procedures.ts, then the cockpit screens.

## Session 22: flightPhases.ts (pure part only)
- Ported to app/.../sim/FlightPhases.kt: Phase enum (labels match the web), PHASE_BUTTONS, FIELD_ELEV_FT, V1/VR/V2, the trainer constants, PHASE_CONFIG, fieldElev, tasFromCas/casFromTas, clamp, climbPerformance (returns ClimbPerformance), descentTargetFt (takes the fix altitude as Double?).
- Unit tests: app/src/test/.../FlightPhasesTest.kt (registered in tools/parity/run-local-checks.sh). Local suite pass=294 fail=0.
- NOT ported yet (blocked on dependencies): usePhase store, setPhase/say, groundReadyPatch and the other plan-to-controls patches, applyPhaseToGuidance (needs GuidanceResult from planGuidance), throttleFor/slewThrottle, controllerTick, touchdown, rollout, canStart/startPhase.
- Blockers: store.ts (844 lines, Zustand over the UI with patch/tick/readyApu/startEngine/runScenario), flightNavigation.ts (314), planGuidance.ts, navReceiver.ts, activeFlightPlan.ts, mipLocal.ts, autoPhases.ts, FlightModeControl.ts.
- Next: port the flight-plan and navigation data types needed by applyPhaseToGuidance, then store.ts as a Kotlin state holder (no UI).

## Session 23: plan data types and PLAN NAV guidance (pure part)
- app/.../sim/PlanGuidance.kt: port of planGuidance.ts (wrap360/wrap180, distNm, brgTrue, leadDistanceNm, lateralCommandTrue, todDistanceNm, remainingRouteNm, computeGuidance, INACTIVE_GUIDANCE). Note: the TS `toFixed(0)` in the TOD note is reproduced with String.format "%.0f", and `${altitude}` uses jsInt (whole numbers print without ".0").
- app/.../sim/ActiveFlightPlan.kt: data types from activeFlightPlan.ts, ActiveFlightPlanLogic.isUsable (the readActiveFlightPlan validity check) and applyFlightLevelCap(plan, capFt). The cap is passed in because flightLevelCap reads localStorage.
- NOT ported (storage/UI): publishActiveFlightPlan, readActiveFlightPlan (JSON + localStorage), useActiveFlightPlan, flightLevelCap read/set. These go in the Android storage layer.
- Tests: PlanGuidanceTest.kt (registered in run-local-checks.sh). Suite pass=306 fail=0.
- The local JUnit stub has no assertNull; use assertTrue(x == null).

## Session 24: flightNavigation.ts (pure geometry part)
- app/.../sim/FlightNavigation.kt: enrouteCompression, blendDeg, distanceNm, bearingDeg, routeGuidance (takes the leg start and end as plain values). FlightNavigationTest.kt is registered. Suite pass=314 fail=0 after fixing two test expectations (the shortest-arc midpoint of 350→20 is 5, and a 180 deg arc is ambiguous, so it is not tested).
- NOT ported: arrivalScene, departurePoint, departureRunwayHeadingMag, targetList, planRoutePoints, legStart, the state storage, and advance/reset. They need navData.ts (223 lines: AIRPORT_DATA, magVarAt, WSSS_ILS_02L, isWsssArrivalSceneId). navData.ts is the next dependency to port.

## Session 25: navData.ts
- app/.../sim/NavData.kt: port of navData.ts (editions, Runway/AirportData, dms, parseAipCoord, AIRPORT_DATA, magVarAt, RPLL_FIXES (32), MIA_DVOR, RPLL_SIDS (10), sidProcedureName, WSSS_ILS_02L, isWsssArrivalSceneId, destPoint, buildSidWaypoints, buildVectorArrival).
- Verified by script against the TS source: all 32 fix coordinates and all 10 SID fix lists/radials match. The SID suffixes and initial instructions were checked by eye.
- NavDataTest.kt registered. Suite pass=323 fail=0.
- Still open for flightNavigation: arrivalScene, departurePoint, departureRunwayHeadingMag, targetList, planRoutePoints, legStart, and the state/advance/reset logic. These can now be ported, since navData exists.

## Session 26: derive.ts coverage gaps closed
- Added six trajectory scenarios to tools/parity/sections/step.mts (now 23): pack_overheat_trip_latched, bleed1_overheat_trip, rto_full_stop_autobrake (reaches STOPPED), manual_gear_release_extend (both gears extend from UP to DN), center_pump_relay_cutout_unpowered (relay trips at 15 s), cabin_temp_packs_on_supply.
- Regenerated StepParityTest.kt from step.json (23 tests, 557 fields each). Local suite pass=329 fail=0.
- Mutation check: changing the cabin air-approach constant in StepPart3 (0.22 -> 0.23) now fails 3 scenarios (it passed before this session). File restored and verified with cmp.
- Deliberately NOT changed: the icing-rate side map (StepSim.iceRateByState). The web SimState does not declare the `__iceRate` field, and adding one would change the web source, which is the reference of truth. Documented above as the known design point.
- derive.ts port status: code complete, all derive sections and the return object are parity-tested, and step() has trajectory parity including the previously uncovered paths. Remaining work is outside derive.ts: flightPhases controller, flightNavigation state machine, store.ts, the UI.

## Session 27: flightNavigation.ts state machine
- app/.../sim/FlightRoute.kt: arrivalScene, departurePoint, departureRunwayHeadingMag, targetList (returns GuidanceTarget list), planRoutePoints, legStart, routeGuidance wrapper.
- app/.../sim/FlightNavigator.kt: class FlightNavigator with buildState, reset, advance(plan, telemetry), liveState/liveGuidance/liveArrival, readState. Types: FlightPhase, PositionMode, NavigationTelemetry, FlightNavigationState.
- Platform hooks (Android must supply these): clock (ISO timestamp, web's Date.toISOString), restore (persisted state read at startup, web's readState), onPublish (the once-per-second storage write and the change event). The plan is passed to advance/reset; the web read it from localStorage and listened for ACTIVE_FLIGHT_PLAN_EVENT, so the Android side must call reset when a plan is activated.
- The web `typeof window` guard is not ported: the Kotlin side has no window object.
- Behaviour preserved: sub-stepping never overshoots a compressed waypoint, fly-by sequencing with turn anticipation (min 0.7 NM), the 1 s publish throttle, and the NO PLAN branch when there is no departure point.
- FlightNavigatorTest.kt registered. Suite pass=339 fail=0. The move test checks 250 kt for 10 s covers 0.694 NM along heading.
- Still open: flightPhases.ts controller and plan-to-controls patches (they need store.ts and the planner), store.ts itself, and the UI.

## Session 28: flightPhases controller rules (pure part) and autoPhases
- app/.../sim/FlightPhaseControl.kt: cruiseCasKt, slewThrottle, throttleFor, takeoffRollStep (pure roll integrator with lever and 0.1 s sub-steps), rolloutStep, accelHold, schedule (per-phase CAS and flaps), verticalCommand (climb/descent/landing-path command with the ramps), canStart (returns StartGate), totalRouteDistanceNm, autoNext (AUTO PHASES, from autoPhases.ts).
- Each function takes the values the web controller read from the sim/MIP/phase stores and returns what it would have written.
- FlightPhaseControlTest.kt registered. Suite pass=354 fail=0.
- NOT ported (stateful / UI-coupled): controllerTick wiring (timers, performance.now, the trail, patchSim/patchChanged), touchdown and rollout state changes (setPhase), applyPhaseToGuidance (ported in session 28, see FlightPhaseControl.kt), mirrorToControls (MIP store), startPhase, loadPlanIntoControls, parkAtDeparture, groundReadyPatch, usePhase store, and the hint text (uses toLocaleString/toFixed).
- applyPhaseToGuidance also ported (FlightPhaseControl.applyPhaseToGuidance, tested). Suite pass=361 fail=0.
- Next: the usePhase/sim/MIP store interfaces are needed before the controller wiring.

## Session 29: store.ts core (SimStore + tick body)
- app/.../sim/SimTick.kt: port of the store tick() body (relay and generator-protection timers, one-touch auto start, hydraulic servicing and leaks, ATA 34 test clock, navigation advance, transition log entries). Inputs the web read from globals are parameters: nav/plan (advanceFlightNavigation), nowMs (Date.now), liveWxIntensity.
- app/.../sim/SimStore.kt: SimStore holds the state, a per-state derived cache (WeakHashMap, identity), update(...) (the patch equivalent: the caller passes the changed control for the log line), tick, reset, clearLog, and a plain listener list (no kotlinx dependency, so the local runner still compiles). The Android layer wraps subscribe in a StateFlow.
- Deliberately NOT ported: panel render throttling, the urgent-commit flag, the switch-click sound (the caller decides when to click), React hooks.
- Not yet ported from store.ts: readyApu, readyForDeparture, startEngine, shutdownEngine, toggleFault, injectLandingGearFault, clearLandingGearFault, runScenario.
- Verified by hand against the web: the reservoir servicing is applied twice per tick in the web (step() at 2.5 %/s and the store tick at 12 %/s). The test expects 57.25 for a 0.5 s tick from 50 %, which matches the web.
- SimStoreTest.kt registered. Suite pass=371 fail=0.
- Note: the tick is not yet covered by a web parity dump. The web store imports React and zustand, so it needs a stub harness before it can be dumped with tsx. That is the next verification gap.

## Session 30: store actions (startEngine, shutdownEngine, gear fault inject/clear, toggleFault)
- app/.../sim/SimActions.kt: pure actions returning the new SimState. Caller plays the switch click. injectLandingGearFault takes a random source for tests.
- app/.../sim/FaultTable.kt: GENERATED from store.ts (93 toggleFault labels) and the Faults class; the generator checks that the TS keys and the Kotlin Faults fields match exactly. Accessors FaultTable.get/set are generated too.
- SimStore.apply(transform) commits a pure action's result.
- SimActionsTest.kt registered. Suite pass=379 fail=0.
- readyApu also ported (SimActions.readyApu, tested). Suite pass=381 fail=0.
- Still not ported from store.ts: readyForDeparture (about 115 lines, the largest remaining action) and runScenario (five named scenarios).

## Session 31: store.ts complete (pure part)
- SimActions.readyForDeparture and SimActions.runScenario ported (runScenario's fallback is the TE flap scenario, as in the web). readyForDeparture appends its log entry at the END of the log, unlike pushLog which prepends; this is kept as the web does it (flagged as a possible web quirk).
- store.ts is now fully ported as pure functions plus SimStore/SimTick. Its React and Zustand plumbing is intentionally not ported.
- Suite pass=386 fail=0.

## Remaining non-UI sim work (line counts from the web source; NOT ported unless stated)
- audio.ts 1406 (WebAudio: needs an Android audio design decision; engineSpoolAudio.ts 81 goes with it)
- pitotStatic.ts 686 (partly represented by the derive fields; the module's own logic is not ported)
- navaids-changi.ts 536, radionav-changi.ts 144, radionav.ts (partly), dmeModel.ts 112, adfBearing.ts 71, markerBeacons.ts 47, markerLamps.ts 15, hfModel.ts 94
- instrumentSources.ts 419, mipLocal.ts 263, situation.ts 161, routeSequence.ts 149, overheadLocal.ts 106, wxrWorld.ts 66, lrra.ts 75, liveWeather.ts 57, maintenanceInterlocks.ts 8, airportCoords.ts 6
- flightPhases.ts controller wiring (controllerTick, touchdown, rollout, startPhase, mirrorToControls, loadPlanIntoControls, parkAtDeparture, usePhase store). The pure rules are ported in FlightPhaseControl.kt.
- The UI (about 28,700 lines of TSX) is not started.

## Session 32
- RadioNav.kt compiles; RadioNavData renamed; test class RadioNavTest.kt also covers markers, interlocks, airport coords.
- Local suite: pass=396 fail=0.
- TODO: split markers/interlocks/airport tests into their own test classes and file names (MarkerBeaconsTest.kt etc.) and register them in run-local-checks.sh.
- Remaining sim modules: autoflight.ts, adfBearing.ts, navaids-changi.ts, radionav-changi.ts, dmeModel.ts, overheadLocal.ts, hfModel.ts, lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts. audio.ts design decision still open.
- Session 32 (cont.): marker, interlock and airport tests split into MarkerBeaconsTest, MaintenanceInterlocksTest, AirportCoordsTest.
- autoflight.ts ported to sim/AutoFlight.kt with AutoFlightTest.kt. Local suite: pass=408 fail=0.
- Next: adfBearing.ts (needs navaids-changi, pedestal, autoflight), then the remaining list above.
- Optional: a parity dump for autoflight (tsx script like dump-section.mts) would confirm the hand-derived expectations.
- Session 32 (cont. 2): navaids-changi catalogue generated to sim/NavaidsChangi.kt (tools/gen-navaids.mts); helpers in sim/NavaidLookup.kt; adfBearing ported to sim/AdfBearing.kt (pure, stores passed as parameters). Local suite: pass=415 fail=0.
- Still open: pedestal.ts (usePedestal store) needs a Kotlin state holder before the Android layer can feed AdfBearing; WSSS_RUNWAYS port is pending.
- Next from the list: radionav-changi.ts (144), dmeModel.ts, overheadLocal.ts, hfModel.ts, lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- radionav-changi.ts ported to sim/RadioNavChangi.kt (RadioNavChangiTest.kt). Local suite: pass=421 fail=0.
- Next from the list: dmeModel.ts, overheadLocal.ts, hfModel.ts, lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- dmeModel.ts ported to sim/DmeModel.kt (DmeModelTest.kt). Local suite: pass=428 fail=0.
- Next from the list: overheadLocal.ts, hfModel.ts, lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- overheadLocal.ts ported to sim/OverheadLocal.kt (OverheadLocalTest.kt). Local suite: pass=430 fail=0.
- Next from the list: hfModel.ts, lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- hfModel.ts ported to sim/HfModel.kt (HfModelTest.kt). Local suite: pass=434 fail=0.
- Next from the list: lrra.ts, wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- lrra.ts ported to sim/Lrra.kt (LrraTest.kt). Local suite: pass=442 fail=0 (after removing one weak assertion).
- Next from the list: wxrWorld.ts, instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- wxrWorld.ts ported to sim/WxrWorld.kt (WxrWorldTest.kt). Local suite: pass=451 fail=0.
- Next from the list: instrumentSources.ts, mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- sources.ts and instrumentSources.ts ported (SourcesTest, InstrumentSourcesTest). Local suite: pass=464 fail=0.
- Next from the list: mipLocal.ts, situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- mipLocal.ts ported to sim/MipLocal.kt (MipLocalTest.kt). Local suite: pass=468 fail=0.
- Next from the list: situation.ts, routeSequence.ts, pitotStatic.ts, engineSpoolAudio.ts.
- situation.ts and routeSequence.ts ported (SituationTest, RouteSequenceTest). Local suite: pass=484 fail=0.
- Note for the next agent: kotlinc prints non-ASCII characters as '?' in error output. That is only display; the source files are UTF-8 and correct. Check with grep before editing.
- Next from the list: pitotStatic.ts (686 lines), engineSpoolAudio.ts (81). Then decide audio.ts.
- pitotStatic.ts ported to sim/PitotStatic.kt (PitotStaticTest.kt). Local suite: pass=495 fail=0.
- Next from the list: engineSpoolAudio.ts (81 lines). Then the audio.ts decision.
- engineSpoolAudio.ts ported to sim/EngineSpoolAudio.kt (EngineSpoolAudioTest.kt). Local suite: pass=501 fail=0.
- All modules on the sim list are now ported except audio.ts (see below) and the platform-only items.

## Status at the end of the sim port (session 32)
- Every module on the non-UI sim list is ported and covered by local checks (pass=501 fail=0).
- NOT ported, by design:
  - audio.ts (1406 lines): a WebAudio node graph (oscillators, noise buffers, sample loops, gain envelopes) plus a tick that fires effects from SimAudioInputs. Needs a decision: Android AudioTrack with the event/parameter logic ported as pure Kotlin, or a different engine. It cannot be run or heard in the sandbox.
  - liveWeather.ts (network + canvas) and markerLamps.ts (React hook): Android layer.
  - pedestal.ts store and the stateful flight-phase controller wiring: need a Kotlin state-holder design.
  - WSSS_RUNWAYS (navaids-changi.ts), the re-exports in radionav-changi.ts.
- Not yet done: parity dumps (tsx) for the newer hand-derived modules; SimTick parity needs a React/zustand stub harness.
- Commit: nothing committed in this session. Commit only when asked.

## Session 33: audio.ts extras() event logic
- Read audio.ts extras() in full (lines 1095–1296) plus rate() and mkBus().
- Ported the pure event mapping to sim/AudioEvents.kt (AudioEventsTest.kt, 12 tests). Local suite: pass=513 fail=0.
- Still to port from audio.ts: tick() (APU update, ignition clicks, light-off on lever, fire bell cadence BELL_PERIOD=0.15 s, signs), tickEngines() (90 ms engine update with engSnap), and the synth/sample graph (Android layer).
- Open decision: audio is pure Kotlin event/parameter mapping, with synthesis on Android AudioTrack. Not yet confirmed by the user.
- Session 33 (cont.): tick()/tickEngines() scheduling ported to sim/AudioTicker.kt (AudioTickerTest.kt, 7 tests). Local suite: pass=522 fail=0.
- Still open for audio: the synthesis itself (click, lightOff, bell mode bank, gear/flap/brake loops, AP wailer, shaker, clacker) and the APU/engine voices (buildFx, engVoices, apuVoice), all Android-layer work. Sample-loop loading also belongs to the Android layer.
- Session 33 (cont.): WSSS_RUNWAYS ported to sim/WsssRunways.kt (WsssRunwaysTest.kt, 4 tests). Local suite: pass=526 fail=0. Remaining sim items: pedestal store, flight-phase controller wiring, SimTick parity harness, parity dumps.
- Session 33 (cont.): pedestal.ts ported to sim/Pedestal.kt (PedestalTest.kt, 7 tests). Local suite: pass=533 fail=0. Remaining sim items: flight-phase controller wiring (controllerTick, touchdown/rollout, startPhase, mirrorToControls, loadPlanIntoControls, parkAtDeparture), SimTick parity harness, parity dumps.
- Session 33 (cont.): flight-phase preset builders ported to sim/FlightPhasePresets.kt (FlightPhasePresetsTest.kt, 5 tests). Local suite: pass=538 fail=0.
- Still NOT ported: the stateful flight-phase controller in flightPhases.ts (controllerTick ~388-589, touchdown/rollout glue, mirrorToControls ~364, startPhase ~625, loadPlanIntoControls and parkAtDeparture bodies, timers, usePhase store). It needs a Kotlin sim-patch interface and phase holder first; the pure math and presets are already in place.

## Session 33 close-out: sim logic port status
- Ported this session: AudioEvents.kt (extras), AudioTicker.kt (tick/tickEngines), WsssRunways.kt, Pedestal.kt, FlightPhasePresets.kt, NavReceiver.kt, FlightModes.kt, PhaseController.kt (stateful flight-phase controller), Procedures.kt.
- SimTick parity: real web store tick() dumped with tsx (tools/parity/dump-tick.mts over sections/step.mts, 23 scenarios, 555 fields) and generated into TickParityTest.kt (gen-tick-test.mts). All match.
- Local suite: 584 checks pass, 0 fail (561 in the main run + 23 tick parity).
- Tooling notes:
  - dump-tick needs zustand and react resolvable from reference/web/src. In this sandbox that is provided by a symlink at /home/claude/node_modules pointing to tools/node_modules (installed with `npm install --no-save zustand@4.5.5 react`). Outside the sandbox, install zustand/react and link them the same way, or add them to tools/package.json.
  - The "OutOfMemoryError" seen earlier was kotlinc running out of heap while compiling the whole sim package, not a runtime leak. run-local-checks.sh now sets JAVA_OPTS=-Xmx4g for kotlinc and runs the suite with -Xmx6g; TickParityTest is back in the main run. Gradle test heap is 3g (app/build.gradle.kts).
- Still open, sim side (verification only, not porting):
  - Parity dumps for the hand-derived modules (AutoFlight, MarkerBeacons, RadioNav, DME, HF, LRRA, Pitot/Static, Situation, RouteSequence, FlightNavigation, PhaseController). Their tests were checked by hand against the TS source, not by dump.
- Still open, Android layer (not sim logic): audio synthesis and sample loading, the PhaseController platform timer and SimStore/MIP/pedestal adapters for PhaseWorld, liveWeather (network + canvas), markerLamps (React hook).
- Still open, UI: components/panel and routes (about 30,000 lines TSX).

## Sim-logic parity status (latest)
Function-level parity against the web code, generated by `tools/parity/fn/*` and registered in `tools/parity/run-local-checks.sh`:
- RadioNav (radioNav), LRRA (resolveLrra), DME (stepDmeChannel, step sequences)
- AutoFlight and markers (single calls, marker keying), route sequence (findNextSequenceItem), pitot helpers (vmoMmoLimitKt, pitotIced)
- Situation explain (text and tone), 12 cases; fixed JS number formatting of capture degrees ("3", not "3.0")
- PitotStatic computeAirData: 23 scenarios, about 3,900 primitive leaves
- FlightNavigator advance: telemetry sequences with a window/localStorage shim in the web harness
- PhaseController loadPlanIntoControls and canStart: changed store keys and start gates after load
- Store tick (TickParityTest), 23 scenarios

- PhaseController timed tick (controllerTick): 483 controller events from takeoff roll through the climb (953 store changes).
  The harness records, per event, the store values the tick reads and the leaves it changes. The Kotlin test replays
  those inputs through PhaseController.tick, so it checks the controller logic, not the SimState-to-PhaseWorld adapter.
  Ticks use 250 ms (exact in binary) because 0.2 s drifts between the JS and Kotlin clocks.
- Situation publisher throttle (publishSituation) and trail rule: 9 timed steps.
SimPhaseWorld (sim/SimPhaseWorld.kt) is the PhaseWorld over SimStore, MipLocal.Holder, Pedestal.Holder and FlightNavigator.
Its key mapping was generated from the typed state. Unmapped keys throw, so a missing mapping fails loudly. Tested by
SimPhaseWorldTest (round trips, a load, and a 100 s takeoff through the adapter).
End-to-end: PhaseE2EWebParityTest runs the Kotlin SimStore tick, PhaseController and SimPhaseWorld over the same 483 web
events (6 sim ticks per controller tick, as the web). It checks the sim, MIP and navigation values the controller reads before
each event, and every value it changes after. Passes.
Not covered: the pedestal store is only read for NAV L (no pedestal physics in this run), and the run is one scenario (takeoff
roll and climb from a plan with no waypoints). Other scenarios (approach, landing, rollout) are not yet recorded.
Suite: pass=716 fail=0.

Harness notes: the web flightPhases and navigation modules need `TSX_TSCONFIG_PATH=<web>/tsconfig.json` (for the `@/` alias)
and the packages zustand@4.5.5, react@18.3.1, clsx, tailwind-merge installed with `npm install --no-save` in tools/
(a later `--no-save` install prunes the others, so install them together).

## Long end-to-end parity (takeoff → approach)
- `PhaseLongE2EWebParityTest.kt` (generated by `tools/parity/fn/dump-phase-long.mts`): 5000 controller ticks (250 ms, 6 sim ticks each), scripted starts TAKEOFF@0, CLIMB@1200, CRUISE@2400, DESCENT@3600, APPROACH@4400, 200 checkpoints every 25 ticks. Checkpoints compare sim, MIP, nav (lat/lon/leg), guidance (distToTodNm, targetAltFt, vertMode, nav dDest, route total) and the phase label.
- Numeric checks use tolerance max(1e-6, 1e-6·|expected|): last-bit libm differences between V8 and the JVM compound over thousands of ticks. Discrete values (phase, booleans, enums as strings) are exact.
- Bug found and fixed in `PhaseController.kt` (tick): the vertical guidance called `applyPhaseToGuidance` with the phase captured before the automatic CRUISE→DESCENT switch. The web reads the live phase from the store, so the 3000 ft descent target was written one tick later in the web. Now `applyPhaseToGuidance(guide, phase, …)`; `mirrorToControls` and the rest of the tick still use `curPhase`, as in the web.
- Not yet covered: LAND and ROLLOUT (needs a plan with the vectored ILS arrival, WSSS RWY 02L).
- Suite: pass=717 fail=0 (`tools/parity/run-local-checks.sh`, KOTLINC pointed at the scratchpad kotlinc).

## Android platform layer (started)
- Done: liveWeather.ts. Pure analysis in `sim/LiveWeather.kt` (tested in `LiveWeatherTest.kt`); the network/bitmap side is `platform/LiveWeatherSampler.kt`. Rule: anything that needs Android APIs goes under `platform/`, never `sim/`, because the local runner compiles all of `sim/` with plain kotlinc.
- Not yet compiled: `platform/LiveWeatherSampler.kt` (needs the Android SDK). Check it first when CI runs.
- Next: audio playback (audio.ts `tick`/`extras` are already ported as pure logic in AudioEvents/AudioTicker; the SoundPool/AudioTrack player and sample loading are not), then the phase timer, then marker lamps.
- Suite: pass=721 fail=0.

- Also done (CI-only compile): platform/SamplePlayer.kt (six sample loops), platform/PhaseTimer.kt (200 ms controller loop), sim/MarkerLamps.kt + platform/MarkerLampTimer.kt (40 ms lamp refresh).
- Still open in the Android layer: the synthesised audio voices (audio.ts build/buildFx: clicks, bell strikes, whooshes, thumps, engine voices, APU; roughly 900 lines of Web Audio nodes). They need a software mixer or AudioTrack design, which is a decision for the user.
- Suite: pass=726 fail=0.

## UI (started)
- Cockpit tab now shows the phase, hint, ALT/CAS/VS/HDG/THR readouts and the six phase buttons, driven by the same wiring as the end-to-end parity test (`ui/cockpit/CockpitModel.kt`, `ui/cockpit/CockpitScreen.kt`). Compose code is not compiled in the sandbox; the first CI run is the check.
- Gap: no flight-plan activation yet, so the cockpit stays on NO PLAN with the phase buttons disabled. `CockpitModel.setPlan(...)` is the hook: the next step is the flight-plan screen (`routes/flight-plan.tsx`, 539 lines).
- Not started: the cockpit route's panels (~2,700 lines of route plus ~50 panel components), the live weather hook-up, and sound.
- Suite: pass=728 fail=0.

- Flight-plan screen (PLAN tab): edits callsign, airports, runways, cruise altitude and speed; saves on this device; Activate publishes the plan, sets PLAN NAV and the runway-heading request, and loads the controls, as the web's publishAndArm does. The cockpit now shows the active route and can fly TAKEOFF → CLIMB → CRUISE → DESCENT → APPROACH.
- Gaps: no waypoints, map picker, SID/STAR/approach selection, vectored ILS arrival (LAND needs it), reserve fuel or the top-of-climb / descent point fields. Text fields for the numbers do not clear to empty.
- Suite: pass=731 fail=0.

- Vectored ILS arrival (WSSS RWY 02L) can now be added on the PLAN screen, from the last fix or the departure airport, as the web does. The arrival and VCT fixes are saved with the plan, so LAND's gate (hasArrivalScene) can pass after a restart.
- LAND and ROLLOUT are now verified end to end: the harness plan carries the WSSS 02L vectored arrival (built by the web's buildVectorArrival), and `PhaseLongE2EWebParityTest` runs 8000 controller ticks through TAKEOFF, CLIMB, CRUISE, DESCENT, APPROACH, LAND, ROLLOUT and COMPLETE (checkpoints every 50 ticks plus each scripted start). Limit: divergences that appear and vanish between checkpoints would not be caught; use FINE_FROM/FINE_TO in dump-phase-long.mts to look closer.
- SimPhaseWorld now maps navFrequencyMHz, navOn and navTuned.
- Suite: pass=733 fail=0.

## Cockpit panels (started)
- Six-pack (primary flight instruments): data pass in `sim/SixPackData.kt` (tested), drawn by `ui/cockpit/SixPackView.kt` (attitude ball and readouts). The roll sign and pitch scale are a first pass to check against `components/panel/SixPack.tsx`.
- Cockpit route (`routes/cockpit.tsx`, 2,706 lines) still to port: MainInstrumentPanel (and its 3D version), the overhead and pedestal panels, the hydraulic and pneumatic diagrams, and about 10 trainer screens (brakes, fire, ice, pitot-static, electrical, APU, lights, navigation, autopilot, radio, 360 view). The hydraulic diagram alone uses about 100 nodes.
- Suite: pass=737 fail=0.

## Main instrument panel: what the next step needs
- `components/panel/MainInstrumentPanel.tsx` (1,680 lines) has a stateful attitude integrator, `useAttitude` (about lines 643–1145, ~500 lines). It sets `mip.att`, `attLive` and the heading, and it drives the radio-nav position, the pedestal receivers, the autopilot and flight-director modes, the GS / LOC capture and the attitude-heading request.
- It is a React hook with refs and a 'setInterval'. Port it as a whole, as a Kotlin class with an explicit state object and a per-tick step function, and check it with a parity harness that feeds the same inputs to both sides, like the sim harness. A partial port would be unverifiable against the web.
- Suggested order: (1) pure helpers (flightStateActive, stepPos, wsssLocalPos, the gear / glideslope capture rules); (2) the attitude integrator with the flight-path and yoke logic; (3) the autopilot and flight-director modes; (4) the drawing of the attitude and HSI.

### useAttitude section map (MainInstrumentPanel.tsx, lines 643–1145)
- 643–684: refs, the setInterval start (`attLive` on), dt clamp 0.5 s, `step()` rate limiter, IRS / EFIS source selection (P5 transfer switches).
- 685–696: radio navigation position (nav reset, heading request, WSSS 02L frame vs RPLL 06 frame, weather-radar position publish).
- 697–865: ATA 34 pedestal NAV 1/2 is the frequency authority; instrument sources (`resolveInstrumentSources`); IRS source override; radio-nav solutions for CAPT and F/O (VOR / LOC / GS, plus the Changi catalog path when a plan position exists).
- 866–1033: SP-77 solenoids and interlocks.
- 1034–1063: autopilot references.
- 1064–1090: attitude targets.
- 1091–1145: flight director steering commands (V-pointers).
- Helpers already ported: flightStateActive, stepPos, wsssLocalPos, resolveNavReceiver, NAV_START, isWsssArrivalSceneId, resolveInstrumentSources, radioNav, computeChangiRadioNav (verify each name before relying on it).
- Status: about a third of the hook has been read; nothing ported from it yet.

- Attitude integrator: the kinematic core is ported (`sim/AttitudeKinematics.kt`: step, pitch rate by turbulence, roll-rate-limited approach, heading from bank at the standard turn rate). It is checked by hand-computed tests only. Next: the attitude-target choice (the 'attitude targets' section, lines ~1064–1090, and the `dyn` flight-path branch), then the flight director (lines ~1091–1145), then a parity harness that drives the whole hook.
- Suite: pass=743 fail=0.

- Attitude targets and the dyn flight-path branch are ported (`sim/AttitudeTargets.kt`, 8 hand-computed tests). The caller must pass in the lateral law result, the altitude or glide capture gamma, and the signed heading errors. Still open: the flight director (lines ~1091–1145), the autopilot mode logic that sets those flags, and the parity harness for the whole hook.
- Suite: pass=751 fail=0.

- Flight director (V-pointers) is ported: `sim/FlightDirector.kt`, 6 hand-computed tests; half-degree quantisation uses JS Math.round semantics. Still open in the hook: the autopilot mode logic that sets its flags, the MIP change-detection writes, the altitude and attitude publish, and the whole-hook parity harness.
- Suite: pass=757 fail=0.

- Autopilot mode gating is ported: `sim/AutopilotModes.kt`, 10 hand-computed tests. Still open in the hook: the glide-slope and lateral sub-mode steps (already ported in AutoFlight, but their caller wiring is not), the MIP change-detection writes, publishSituation, and the whole-hook parity harness.
- PLAN NAV gating ported: `sim/PlanNav.kt`, 9 tests.
- ILS handoff ported: `sim/IlsHandoff.kt`, 6 tests. Attitude integration and targets were already ported (AttitudeKinematics, AttitudeTargets). Output step ported: `sim/AttitudeOutputs.kt`, 7 tests.
- Per-tick composition ported: `sim/AttitudeHook.kt` (targets → integrate → altitude → FD → write gate), 3 tests. Upstream inputs still come from the caller.
- Remaining in the hook: the caller that builds the upstream inputs (autopilot, plan, ILS, radio), the side effects (SimStore/MIP writes, advanceFlightNavigation) in the platform layer, then the whole-hook parity harness.
- Flight plan: procedure fields (sid, star, approach, procedureStatus) and planning fields (reserve fuel, TOC, descent start, approach altitude) added to `FlightPlanDraft`, carried into the active plan as the web does. 2 tests. Not yet ported: the editor UI for these.
- Manila–Singapore preset ported: `sim/FlightPlanPresets.kt`, 4 tests.
- ILS choice ported: `sim/FlightPlanIls.kt` (set, clear, icw), 5 tests. Vector removal keys on the VCT- id, not web source "vector".
- Approach progress display ported: lamp state in `sim/ApproachProgressLamps.kt` (3 tests), Compose view in `ui/cockpit/ApproachProgressView.kt` (not compiled locally).
- Fold (collapsible group) ported as Compose only in `ui/common/Fold.kt`; not compiled locally.
- ApTrainerView layout math ported: `sim/ApTrainerLayout.kt`, 5 tests. The view waits on the full instrument Panel.
- Audio mixer decision (default, the user has not confirmed it): software mixer in pure Kotlin. Primitives ported: `sim/AudioDsp.kt` (biquads with Web Audio coefficients, oscillators, deterministic noise, linear-ramp envelopes), 5 tests. First sound builder ported: the switch click in `sim/AudioSounds.kt`, with exponential envelopes, equal-power pan and a stereo mixer in `sim/AudioDsp.kt` (4 tests). Still to port: the other builders in audio.ts (build/buildFx, about 900 lines), the playSwitchClick throttle, and a PCM output path in platform/.
- Burst builders ported in `sim/AudioSounds.kt`: toneBurst, noiseBurst, thump, clunk (4 tests). Also ported: whoosh, wheelSpinDown, tyreChirp, lockThunk (4 tests; `Biquad.retune` for the swept filter). Also ported: bell strike, bell pan and chime (3 tests), and the shaker and clack loops (3 tests). Still to port: the engine and APU voices, and the sample-loop builders.
- CI run 1 (android.yml, testDebugUnitTest) failed at compileDebugKotlin with 3 errors, all fixed in this round: LiveWeatherSampler.sample was an expression body with returns (now a block body); SixPackView and ApproachProgressView imported the internal layout `weight` extension (import removed; Modifier.weight is the scope member). Re-run CI to confirm.
- AudioParam model ported: `TargetParam` in `sim/AudioDsp.kt` (setValueAtTime and setTargetAtTime, 5 tests). It caps its history at 64 events. Still to port: the engine and APU voices built on it.
- APU voice ported: `sim/ApuVoice.kt` (streaming, 4 tests). Its bandpass retunes every 32 samples. Still to port: the engine voices and the engine space reverb.
- Recorded-engine layer mapping ported: `sim/EngineSampleLayer.kt` (5 tests). Still to port: the engine's synth voices (fan, compressor, roar, body), the engine space reverb, and the sample attach wiring.
- Dial angle maps ported: `sim/DialGeometry.kt` (radio altimeter and flap), 5 tests. The Compose dials are in `ui/instruments/Dials.kt` (not compiled locally; the height dial's red sector is not drawn yet).
- Instrument readout rules ported: `sim/InstrumentReadouts.kt` (yaw needle, fuel LED text, IAS strip text, JS-style rounding), 5 tests.
- Chronometer rules ported: `sim/ChronometerLogic.kt` (hands, elapsed text, flag, tick, toggle, reset), 6 tests.
- Selector and EPR rules ported: `sim/SelectorLogic.kt`, 3 tests.
- Compose controls written in `ui/instruments/Controls.kt` (chronometer face, three-position selector, EPR counter). Not compiled locally; simplified visuals noted in PORT_MAP.
- Suite: pass=868 fail=0.
- Engine synth targets ported: `sim/EngineSynthTargets.kt` (6 tests). Still open for the engine voice: the node graph and ramps in platform/, engFx (space reverb and echo), and attachSamples wiring.
- Suite: pass=874 fail=0.
- buildFx method targets ported: `sim/FxTargets.kt` (11 tests). Still open: the buildFx node graph and loops in platform/, the wailer siren LFO, and the engine reverb and echo (engFx).
- Suite: pass=885 fail=0.
- Plan waypoint editing rules ported: `sim/FlightPlanEdits.kt` (5 tests). Gap: the per-waypoint source tag is not stored (the Kotlin waypoint type has no field). Still open: destination lat/lon and kind on the draft, applySid and applyVectorArrival, and the editor UI.
- Suite: pass=891 fail=0.
- SID and vector-arrival apply rules ported: `sim/FlightPlanProcedures.kt` (4 tests). Destination kind and lat/lon added to the draft (draft-only, as on the web). Still open: the plan editor UI, the time compression field, and the publish-and-arm actions that write the MIP store.
- Suite: pass=895 fail=0.
- Panel strips written in `ui/instruments/PanelStrips.kt` (IAS strip, QTY LED, yaw indicator, annunciator lamp). Compose only, not compiled locally; CI must verify before more UI is built on them.
- Light test rules ported: `sim/LightTestLogic.kt` (4 tests). LightTestKey written as Compose in `ui/instruments/LightTestControls.kt` (not compiled locally). Still open: GearColumn, GearPairMini and Comparator views.
- Suite: pass=899 fail=0.
- Gear lamp group and comparator plate written in `ui/instruments/GearAndComparator.kt` (Compose only, not compiled locally). Still open: the gear lever and its WOW lock-out tag, and the glareshield band.
- Gear lever rules ported: `sim/GearLeverLogic.kt` (5 tests). Gear lever view in `ui/instruments/GearLever.kt` (Compose only, not compiled locally). Still open: the glareshield band and the remaining panel parts.
- Suite: pass=904 fail=0.
- Glareshield drag rules ported: `sim/GlareshieldDrag.kt` (6 tests). Still open: the glareshield views (knobs, HDG and ALT HOLD buttons, FD controllers, engage paddles, photo background from GlareshieldPanel.tsx, 378 lines).
- Suite: pass=910 fail=0.
- Course knob and HDG SEL button written in `ui/glareshield/GlareshieldKnobs.kt` (Compose only, not compiled locally). Still open: ALT HOLD, FD controllers, engage paddles, the photo band.
- ALT HOLD, engage paddle and pitch knob rules ported: `sim/GlareshieldLogic.kt` (5 tests). Switch and paddle views in `ui/glareshield/GlareshieldSwitches.kt` (Compose only). Still open: the FD controller knobs, the photo band and the FD mode legends.
- Suite: pass=915 fail=0.
- Detent knob rules and view ported (`sim/KnobPick.kt`, 6 tests; `ui/glareshield/DetentKnob.kt`, Compose only). Still open: the FdController layout, the SP-77 centre controller and the photo band.
- Suite: pass=921 fail=0.
- FD controller layout written in `ui/glareshield/FdController.kt` (Compose only, not compiled locally). Still open: the SP-77 centre controller, the engage paddles' layout, and the photo band.
- SP-77 centre controller rules ported (engage rule on the requested state, HDG switch, heading wrap, INOP lamps, knob marks) in `sim/GlareshieldLogic.kt`; view in `ui/glareshield/ApCentreController.kt` (Compose only). Still open: the photo band, and the glareshield state holder that wires these views to the MIP store.
- Suite: pass=926 fail=0.
- Glareshield annunciator rules ported: `sim/GlareshieldAnnunciators.kt` (7 tests). Correction: the glareshield has no photo; the 'photo band' in earlier notes is the CSS housing. Still open: the Lens views, the housing drawing, and the glareshield state holder wiring.
- Suite: pass=933 fail=0.
- Lens view written in `ui/glareshield/Lens.kt` (Compose only, not compiled locally). Still open: the housing drawing and the glareshield state holder that wires the views to the stores.
- Glareshield state changes ported as reducers (`sim/GlareshieldReducer.kt`, 6 tests) and a StateFlow holder `platform/MipStore.kt` (not compiled locally). Still open: wiring the glareshield views to MipStore and the pedestal store (fire test, bell cut-out), and the housing drawing.
- Suite: pass=939 fail=0.
- Warning reducers ported (`sim/WarningReducer.kt`, 3 tests): master caution ack, caution-ack sync, bell cut-out. The pedestal Holder already exists in sim/Pedestal.kt. Still open: wiring the glareshield views to MipStore and the pedestal Holder, and the housing drawing.
- Suite: pass=942 fail=0.
- Engine gauge rules ported: `sim/EngineGaugeRules.kt` (5 tests). Still open: the engine gauge views and the cream and dark strip layout in EnginePanels (MainInstrumentPanel.tsx ~388-533), the glareshield views wiring, and the housing drawing.
- Suite: pass=947 fail=0.
- Engine gauge views written in `ui/instruments/EngineGauges.kt` (band bar and readout box, Compose only, not compiled locally). Simplified: linear bars instead of the web's round dials. Still open: the EnginePanels layout placement and the glareshield views.
- Time compression field and distance clamps ported (`FlightPlanDraft.timeCompression`, `FlightPlanEdits`), 2 more tests. Still open: the plan editor screen, the attitude hook caller, the panel and glareshield layouts, and the trainer screens.
- Suite: pass=949 fail=0.
- Plan procedures section written (`ui/flightplan/ProcedureSection.kt`) and wired into the existing PLAN screen; the vector button now uses the ported rule. Compose only, not compiled locally. Still open: the waypoint add form and the navaid picker on the PLAN screen, the attitude hook caller, and the panel layouts.
- Simulator LOG drawer rules ported: `sim/LogDrawerRules.kt` (4 tests): row title fallback to SYSTEM, CAUSE/COMMAND line, em-dash fallbacks, empty-state text. Still open: the LOG drawer Compose view, the TRAINING console and the rest of the cockpit route (Cockpit() top level: audio wiring, 360 view, 3D panel).
- Suite: pass=953 fail=0.
- LOG drawer view written in `ui/cockpit/LogDrawerView.kt` (Compose only, not compiled locally). It takes the entries and callbacks; not yet wired to CockpitModel, so the log is not visible in the app. Still open: exposing the sim log from CockpitModel, the TRAINING console and the rest of the cockpit route.
- LOG drawer wired: `CockpitModel` exposes the sim log (`log` StateFlow, `clearLog()`), and `CockpitScreen` shows `LogDrawerView` over the cockpit tab. Compose only, not compiled locally; verify on CI. Still open: the TRAINING console, the 360° view, the 3D panel, the main instrument panel and the rest of the cockpit route.
- Suite: pass=953 fail=0 (no sim change in this step).
- TRAINING console rules ported: `sim/TrainingConsoleRules.kt` (section list, default section, toggles for Flight Controls, Landing Gear servicing, Brakes / Antiskid, autobrake levels; 7 tests). Still open: the console and drawer Compose views, and the section panels that depend on other trainers (Electrical, APU, Ice & Rain, Pitot/Static, Navigation, Autopilot, Lights, Fire, Fault Injection, Component flow).
- Suite: pass=961 fail=0.
- TRAINING console view written in `ui/cockpit/TrainingConsoleView.kt` (Compose only, not compiled locally) and opened from the cockpit tab (`CockpitScreen`, TRAINING button top-right). `CockpitModel` now exposes `simState` and `applySim`. Flight Controls, Landing Gear and Brakes / Antiskid sections work; the other sections show "not ported yet". Still open: the rest of the section panels, the 360° view, the 3D panel, the main instrument panel.
- Navigation section switch rules ported: `sim/NavigationTrainerRules.kt` (source failures, circuit breakers, VG FAST ERECT, COMP sync flags, COMP modes, RMI needles, LRRA TEST; 7 tests). Still open: the Navigation section view (needs NavSharedDisplay, FlightPhasePanel, ApTrainerView and NavAutopilotDiagram), and the Autopilot section (AutopilotSimulator.tsx, 92 lines, mostly layout over ported pieces).
- Suite: pass=969 fail=0.
- Lights section rules ported: `sim/LightsTrainerRules.kt` (toggleInList, bulb failures, breakers, reset switches / reset breakers and bulbs, six presets; 7 tests). Still open: the Lights section view (LightsTrainer.tsx, 371 lines, mostly the switch grid and per-light status).
- Suite: pass=976 fail=0.
- APU section rules ported: `sim/ApuTrainerRules.kt` (MASTER selection with the no-power refusal, START EFFECTIVITY, battery and bleed toggles, APU GEN L/R, SHUTDOWN, the nine start-sequence steps with blocked and active states, the AUTO START demo step, caption lamp test; 9 tests). Still open: the APU section view (ApuTrainer.tsx, 176 lines, plus ApuDiagram.tsx, the animated cutaway).
- Suite: pass=987 fail=0.
- Fire Protection handler rules ported: `sim/FireProtectionRules.kt` (engine and APU fire handles, bottle discharges with the stubborn-bottle rule, cargo arm / discharge / installed / detector loops, fire and overheat injection, gear lever, clear all, set up running; 12 tests). Still open: the Fire Protection view (FireProtectionTrainer.tsx, 541 lines, mostly the diagram and the status cards and the mist animation).
- Suite: pass=1000 fail=0.
- Pitot / Static handler rules ported: `sim/PitotStaticRules.kt` (generic flip over the web's 50 boolean flags, CADC test cycle, static-port blocking with the frozen altitude, clear all faults, ground / cruise / climb profiles, exclusive pitot and vacuum test sources, the four scenarios; 9 tests). Still open: the Pitot / Static view (PitotStaticTrainer.tsx, 452 lines) and its schematic (PitotStaticSchematic).
- Suite: pass=1009 fail=0.
- Ice & Rain handler rules ported: `sim/IceRainRules.kt` (icing conditions, window heat switches with their overheat latch clear, window heat test hold, overheat latch effect, wipers, probe heat, engine and wing anti-ice messages by weight-on-wheels, fuel heat timers, the four quick setups; 9 tests). Rain and wing-test switches stay local to the web panel. Still open: the Ice & Rain view (IceRainTrainer.tsx, 756 lines) with its animated window and wing drawings.
- Suite: pass=1018 fail=0.
- Electrical Power control rules ported: `sim/ElectricalTrainerRules.kt` (ground cart, battery, bus tie, GEN 1/2 with engine-running gate, GRD PWR, GROUND SERVICE, standby selector, APU control via ApuTrainerRules.toggleGen, APU MASTER cycle; 7 tests). Still open: the Electrical view and wiring diagram (ElectricalPowerTrainer.tsx, 622 lines).
- Suite: pass=1025 fail=0.
- Fault injection and scenarios: toggleFault, gear fault and runScenario were already in `sim/SimActions.kt`. Added `sim/FaultInjectionRules.kt` for the centre pump auto-shutoff toggle and the adverse-yaw cue text (4 tests). Still open: the Fault module and Scenario module views (Overhead.tsx, FaultModule and ScenarioModule, about 120 button rows).
- Suite: pass=1029 fail=0.
- Fuel, engine-start and ground-service rules ported: `sim/FuelAndGroundRules.kt` (six boost pumps, crossfeed label, low fuel and reset quantity, START / SHUTDOWN enable rules and labels, ground air cart, ground power cart, APU + BLEED; 8 tests). START / SHUTDOWN engine actions were already in `sim/SimActions.kt`. Still open: the Fuel, Engine Start and Pneumatic panel views in routes/cockpit.tsx.
- Suite: pass=1037 fail=0.
- Air conditioning and cabin pressurization rules ported: `sim/AirConditioningRules.kt` (packs and trip resets, P5-17 selector modes and the AUTO selector, fine mix with MANUAL, temperature control faults, pressurization modes, outflow valve steps with the manual-mode gate; 7 tests). Still open: the AC and cabin panel views and wiring drawings in routes/cockpit.tsx.
- Suite: pass=1044 fail=0.
- Hydraulics panel switch rules ported: `sim/HydraulicTrainerRules.kt` (engine and electric pumps, standby selector, ground interconnect, ground servicing and hydraulic power switches, ground air cart, enable rules for the electric pumps and the weight-on-wheels gate; 6 tests). Still open: the Hydraulics panel view and its flow drawing (HydraulicsTrainerPanel and the wire builders in routes/cockpit.tsx).
- Suite: pass=1050 fail=0.
- Hydraulics section view written in `ui/cockpit/TrainingConsoleView.kt` (Compose only, not compiled locally): ENG 1/2 and ELEC 1/2 pumps (ELEC enabled by the AC transfer bus), STBY HYD selector, GND INTERCONNECT, GND SERVICING, GND HYD POWER and GND AIR CART (ground only). `CockpitModel` now exposes `derived`, and `CockpitScreen` passes it to the console. Still open: the hydraulic flow drawing, and the other section views.
- Fuel and Engine Start section views written in `ui/cockpit/TrainingConsoleView.kt` (Compose only, not compiled locally): tank readouts, six boost pumps, crossfeed, low fuel and reset quantity; engine status line, START and SHUTDOWN (through SimActions). Still open: the other section views, the hydraulic flow drawing and the LOG/TRAINING polish.

## Shell layout matches the web AppShell
- Header "Classic 737 Line Lab" with "model · book Rev revision" (from AMM meta), as in `components/layout/AppShell.tsx`.
- Bottom nav in web NAV order: Hangar (systems list), Flight deck (cockpit), Flight plan, Procedures (placeholder, not ported), Library (parts).
- The Android-only AMM tab was removed from the bottom bar; `AmmScreen` is still in `AppRoot.kt` but unused, pending a decision on where the AMM chapter index goes (web: Library page).
- Not yet done: light/dark theme toggle (web `classic737-theme`), StatusStrip and SimTicker in the shell. Not compiled locally; verify in GitHub Actions.

## Shell chrome ported (StatusStrip, sim-time bar, theme)
- `ui/common/ShellChrome.kt`: `Classic737Palette` (web @theme tokens), `classic737Scheme(light)` (dark and html.light-mode values), `StatusStrip` (MASTER CAUTION, FIRE, GND/AIR, APU, N2-1/2, DUCT, HYD A/B tapes; 1x/4x/10x time, Set APU running, Cold & dark).
- `MainActivity` stores the theme under `classic737-theme` (SharedPreferences), like the web's localStorage key.
- `CockpitModel.cycleTimeScale()`, `readyApu()`, `coldAndDark()`. Cold & dark uses `initialSimState()`, so it also clears the log (the web keeps the log).
- Not compiled locally; verify in GitHub Actions.

## Library and Hangar match the web (not compiled locally)
- Library: search box and system chips (All plus each system's short name) filter parts the same way as `routes/library.tsx`.
- Hangar: system cards show "OPEN LAYOUT →". The link is not wired, because the system layout screen is not ported.

## Procedures page ported (not compiled locally)
- `ui/procedures/ProceduresScreen.kt` mirrors `routes/procedures.tsx`: Normal and Emergency switch, procedure chips, live step list (done / next highlighted), Complete / In progress badge, Reset airplane and APU running preset buttons.
- Uses `sim/Procedures.kt` (already ported and tested). Not yet ported from the web page: the Overhead and GroundModule panels under the steps.

## System layout screens (Hangar → system) ported (not compiled locally)
- `ui/aircraft/AircraftPlan.kt`: `components/aircraft/AircraftPlan.tsx` as a Compose Canvas in the web's 1200 x 520 space (wings, flaps, spoilers, fuselage, windows, APU cone and inlet door, JT8D nacelles, gear, pack bays, part hotspots). Tap selects the nearest part. Not ported: the flow and spool CSS animations and the hover card.
- `ui/systems/SystemScreen.kt`: `routes/systems.$id.tsx` (header, system chips, fault buttons, plan, selected part detail, part list). Not ported: the Overhead and GroundModule panels under the plan.
- AppRoot: the Hangar opens a system through `openSystemId` (back button returns to the list). The Hangar cards are now clickable.

## Overhead panel port (in progress, not compiled locally)
- Done: `ui/panel/Widgets.kt` (widgets.tsx), `ui/panel/OverheadModules.kt` (Flight controls, Hydraulics, Electrical, APU). Remaining from Overhead.tsx: Bleed/packs, Fuel, Environment, Anti-ice + window heat, Start, Exterior lights, Flight deck lights, Signs/emergency, Annunciator bar, and the `Overhead` composable with `focus`.
- Sim edits use `SimEdit` = `(SimState.() -> Unit) -> Unit` (the Kotlin form of `s.patch`). Web patch log notes are not written yet.
- Overhead port is now complete for the Overhead module grid and lighting modules (`ui/panel/Overhead.kt`, `OverheadModules.kt`, `OverheadModules2.kt`). The Flight deck shows the overhead below the phase buttons (`CockpitScreen.kt`).
- `CockpitModel.patchSim(edit)` is the Kotlin form of the web `s.patch`. Log notes from the web patch calls are not written yet.
- Not ported from `Overhead.tsx`: Landing gear, Brake, Fire, Fault, Scenario, Electrical distribution and Ground modules (they belong to their own section views).
- Not compiled locally; verify in GitHub Actions.
- Brakes and fire ported (`ui/panel/BrakeFireModules.kt`): `BrakeModule`, `BrakeWheelReadout` (text rows, the wheel graphic is not ported), `FireModule`. The web uses these in their own sections (brakes, fire), so they are not on the Overhead grid yet. Fire actions use `FireProtectionRules`; the stubborn-bottle rule is passed as `stubborn = false` for now.
- Scenarios and fault injection ported (`ui/panel/ScenarioFaultModules.kt`): the five scenario buttons (SimActions.runScenario), the full fault button table (SimActions.toggleFault), the center pump auto-shutoff toggle (FaultInjectionRules), and the landing-gear fault box (injectLandingGearFault / clearLandingGearFault). Not yet placed on a screen; the web uses them in the training views.
- Electrical distribution and ground module ported (`ui/panel/GroundElectricalModules.kt`). Not yet on a screen; the web uses them in the electrical and gear/ground views.
- All of `components/panel/Overhead.tsx` is now ported as Kotlin files. The grouped overhead screen (`ui/panel/Overhead.kt`) shows only the overhead modules; brakes, fire, scenarios, faults, electrical distribution and ground are still to be placed on their section screens.
- Glareshield panel composed in `ui/glareshield/GlareshieldPanel.kt` (Compose only, not compiled locally): housing shell, warning clusters, both FD controllers, the SP-77 centre controller, annunciator lenses and legends. It is wired to `CockpitModel.mipState` / `pedestalState` through `updateMip` / `updatePedestal`, and shown on the Flight deck tab under GLARESHIELD. Still open: the inset shadows, and the glareshield's place in the MainInstrumentPanel layout (not ported).
- Caption strips, annunciator groups and alert lenses from MainInstrumentPanel.tsx written in `ui/instruments/Annunciators.kt` (Compose only, not compiled locally). Uses private PushButton and Toggle copies until `PedestalParts` is ported. Still open: the F/O side of AnnPanel and AlertLenses is shown only as captain's layout, the MainInstrumentPanel's own layout (`Panel()`), the attitude hook (`useAttitude`), the 3D views and the radio and transponder blocks.
- Attitude tick: `sim/RadioStage.kt` (web useAttitude ~690–850: position, NAV/ILS, instrument sources, ADF, DME, LRRA, HF) and `sim/AutopilotStage.kt` (web ~850–1145: interlocks, lateral and glide submodes, lights, markers, ALT HOLD, FD glide capture, AP references, PLAN NAV, ILS handoff) are pure and CI green on 0a9c0d8. `sim/AttitudeTicker.kt` is the caller: it runs every 100 ms (dt capped at 0.5 s), gathers the stores, runs both stages and `AttitudeHook.tick`, and writes back MIP, pedestal, sim altitude and VS, `FlightNavigator.advance` and the situation sample. `CockpitModel` constructs it and drives it from the sim loop (`start`/`stop`/`maybeTick`). Compiled and tested on CI only once that commit is green. Still open: the ADF frequency units (RadioStage) need a parity check; the web's `tuneNav1` powerOn semantics are unverified; the weather radar publish is not ported; the situation holder is not shown in the UI; the web's tolerance checks are approximated by unconditional MIP writes.
- `PedestalParts.tsx` ported: pure rules in `sim/PedestalPartsRules.kt` (tested) and the Compose widgets in `ui/pedestal/PedestalParts.kt` (not compiled locally). PedestalLevers is ported too (`sim/PedestalLeversRules.kt`, `ui/pedestal/PedestalLevers.kt`; CI result pending at the time of writing). Next: `CenterPedestal.tsx` (1644 lines) built on these parts, then swap the private PushButton / toggle copies in `ui/instruments/Annunciators.kt` for the shared ones.
- `CenterPedestal.tsx` is being ported in slices. Done: stage helpers (`ui/pedestal/PedestalStage.kt`) and the TOP DECK (`PedestalTopDeck.kt`, rules in `sim/PedestalTopDeckRules.kt`). Still to do from line 475: CONTROL STAND (Detent2, thrust levers, stab trim, flap and speed brake placement), the radio stack and the remaining units, then the `CenterPedestal` stage that places them (700 x 1642 design px, scale to the screen like `GlareshieldPanel`) and its tab or section in `CockpitScreen`. Also: `PrimusWxr` WxrScope picture, and the private PushButton / toggle copies in `Annunciators.kt`.
- CenterPedestal CONTROL STAND ported (`ui/pedestal/PedestalControlStand.kt`, rules in `sim/PedestalStandRules.kt`). Remaining in `CenterPedestal.tsx` from line 854: FIRE / OVERHEAT panel (FireHandle, FirePanel), then the radio stack and the other units, then the stage that places everything and a Flight Deck section for it.

## Sounds (audio.ts, MarkerAudio.tsx)
- Design: a software mixer (`sim/AudioMixer.kt`) renders blocks of 1024 stereo frames on a high-priority thread into a streaming `AudioTrack` (`platform/AudioEngine.kt`). All voice changes are queued with `post`, so voices live on one thread. The sim is read on the main thread through `CockpitModel.audioFeed()`; one-shots are synthesised on a worker thread.
- Layers: recorded loops decoded once by `platform/SampleBank.kt` (fire bell, stick shaker, A/P disconnect, glideslope, GPWS, config horn; the replacement for the unused MediaPlayer `SamplePlayer`); engine voices (`sim/EngineVoice.kt`: synth + recorded idle/power loops through shelves); effect bank (`sim/FxBank.kt`); APU voice (`sim/ApuVoice.kt`); Mach clacker; marker tone (400 / 1300 / 3000 Hz keyed, gain 0.12); switch clicks (8 pre-rendered variants, 35 ms throttle) from `CockpitModel.applySim` (so `patchSim`, the training console, engine start/stop, fault buttons and presets all click) and `patchSimNote`.
- UI: the SOUND ON/OFF button and volume slider (`ui/cockpit/SoundControls.kt`) sit under the cockpit title. On at launch and while the app is in front (paused in the background, resumed on return, unless the person turned it off); volume default 0.6.
- Tests (pure): AudioMixerTest, AudioFeedTest, AudioEventRenderTest, AudioSwitchSoundsTest, FxBankTest, EngineVoiceTest, plus the earlier AudioEvents/Ticker/Sounds/Dsp tests.
- Known gaps: the engine-space reverb and echo; synth fallbacks for horn, wailer and shaker (only matter if a sample fails to decode); MP3 decoder padding may leave a tiny gap at a loop seam; only the cockpit tab's audio feed exists (it runs for the whole app once SOUND is on).
- Status: sound port feature-complete apart from the gaps above; unverified by ear.

## Main instrument panel on the cockpit tab
- `MainInstrumentPanel` (3036 x 720 design canvas) is wired into `CockpitScreen` under the caption strips, before the CENTER PEDESTAL section. It scrolls sideways; the `-` / `+` buttons zoom from 30 % to 100 % in 10 % steps (default 50 %). Not yet viewed on a device: check scroll versus knob drag conflicts and the default zoom.

## Pedestal FIRE / OVERHEAT panel
- `ui/pedestal/PedestalFirePanel.kt` (rules in `sim/FirePanelRules.kt`, tested) is placed on the pedestal stage; `PEDESTAL_VISIBLE_H` is now 918. Still to port from `CenterPedestal.tsx`: the radio stack (VhfUnit from line 982, NavUnit, AdfUnit, HfUnit, and the units above it at 258 to 980 that are not yet on the stage), placed from y 920 to about 1296.


## Pedestal radio stack
- `ui/pedestal/PedestalRadios.kt` (VHF COMM L/R, VHF NAV L/R with DME mode and DME / NAV test switches, ADF head, HF L/R, DATA display) with pure rules in `sim/PedestalRadioRules.kt` (tested: tune wrap/clamp, ADF mode and ANT flag, lit conditions, DATA lines). `PEDESTAL_VISIBLE_H` is now 1296 (`PEDESTAL_RADIOS_BOTTOM`). The pedestal case body (web Box 240,755,600,821) is also drawn now.
- The web puts both HF units in the ADF box's normal flow (y 1296 and 1420), so the LOWER PLATE (drawn after them) hides most of them. The Compose stage keeps the same order and positions.
- HF is lit only when the `hf-1` / `hf-2` breaker is explicitly true (JS truthiness), as on the web.

## Pedestal lower plate and other functions
- `ui/pedestal/PedestalLower.kt`: LowerPlate (PANEL / FLOOD bright knobs, rudder trim knob with double-tap centre, access cover that slides open, STAB BRAKE RELEASE hold-to-pull knob which sets the `stabBrakeRel` switch) and the AILERON TRIM drum (horizontal drag, 0.004 per design px). The stage is now complete (`PEDESTAL_VISIBLE_H` = `PEDESTAL_STAGE_H` = 1642).
- `ui/pedestal/PedestalOther.kt`: the web "Other pedestal functions" (STAB TRIM cutouts and NOSE UP / DN, FIRE SWITCHES LOCK OVRD, STAB TRIM OVERRIDE + CAB DOOR, CARGO FIRE, MIC L/R, DATA PRINTER) in a collapsible section under the pedestal on the cockpit tab, plus `ThrustLinkButton` (the `thrustLinked` switch, next to the CENTER PEDESTAL title). The web grid becomes one column.
- Rules: `sim/PedestalLowerRules.kt` (tested in PedestalLowerRulesTest). The fire panel now repaints when the 12 s lock override lapses (web setTimeout 12100).
- `stabBrakeRel` and `hornCutout` are stored but nothing in the sim reads them yet.
- Drags use design px in the scaled stage, the web uses raw screen px, so drag speed differs with the stage scale.
- Pedestal NAV radios for the Navigation / Autopilot training tabs (web `PedestalNavRadios`, a cut-out of the stage, NR_X 80, NR_Y 1042, 575 x 260) can reuse `PedestalNavUnit`, `PedestalAdfUnit` and `PedestalDataDisplay`; not placed yet.
