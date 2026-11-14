# Auto Outfitting: design

- Scoring core: `JAOModel` (pure, offline-testable). Three-phase TTK, survival, uptime, AI engagement range, PD floor, flux cap.
- Search: `JAOSearch` (pure). Greedy, then swaps and hullmods. Vents and caps are refilled on every evaluation. Filler hullmods come once vents and caps are maxed.
- Game adapters: `JAORatings` (live specs, reference targets, tables merged across mods), `JAOShip` (scratch fleet member stats, OP, hullmod legality), `JAOOptimizer` (delegate strip/pool/apply, modules, built-in and code-owned slots as fixed fits), `JAOGoals` (SavedVariantData injection via MethodHandles, cleanup).
- Offline check: `jtbuild\JAOFitCheck.java`, run by `ao_build.ps1`.
- Research notes: in the author's research notes (not included in this repository). "R:" below points into them.

## 1. Goal

- Optimal card: an extra card in the refit screen's autofit row. It builds the strongest loadout (weapons, fighter LPCs, hullmods, vents, capacitors) from cargo, storage, and the market or black market when those boxes are ticked. Other cards keep the game's autofit.
- Balanced: break the shield, strip the armor, kill the hull.
- Anti-shield, Anti-armor, Hull killer: most firepower goes into one layer.
- Auto: picks a style per ship.

## 2. What vanilla autofit does

- Research: `apisrc_full\...\plugins\impl\CoreAutofitPlugin.java`.

- Weapons: each goal weapon's autofit categories (tags `kinetic3`, `he2`, `pd1`...) are tried in order. The available weapon with the highest hand-set tag level wins, then a fixed category list is the fallback. Damage, flux, hit strength vs armor and special effects are not measured.
- Hullmods: copies the goal's, pads leftover OP with vents/caps, then adds Reinforced Bulkheads, Blast Doors, Hardened Subsystems, Flux Distributor/Coil and Armored Weapon Mounts (`:443-508`).
- R: `hull_profiles.md`: 171 of 1,095 combat hulls have no goal.

## 3. How it hooks in

- Research: `engine_report.md` sections 1, 2, 8.

- Plugin: `JAOCampaignPlugin` (transient, registered in `onGameLoad`) returns `JAOAutofitPlugin` from `pickAutofitPlugin` at `MOD_GENERAL`, which beats vanilla's `CORE_GENERAL`. `JAOAutofitPlugin extends CoreAutofitPlugin`, so checkboxes, credit cost, "Spend free OP" and officer auto-assign stay.
- Goal row: 4 slots per base hull (`SavedVariantData.MAX_VISIBLE`). 12 vanilla hulls fill all 4.
- Card insertion: `SavedVariantData.getVisible(base)` / `addToVisible(i, variant)`, reached with MethodHandles and feature-detected. The dialog calls `pickAutofitPlugin(member)` immediately before drawing the goal row, and the card is added there.
- Card contents: display name "Optimal" (or "Optimal (Anti-armor)" etc. with "One goal per style"), marker tag `jao_optimal_<style>`, and a real loadout (a clone of the hull's first stock goal, or the current variant). It is not empty, because NPC fleets build half their ships from goal variants (`DefaultFleetInflater` `GOAL_VARIANT_PROBABILITY = 0.5`).
- Removal: `JAOCleanup` (transient EveryFrameScript, runs while paused) removes tagged entries when the core tab is not REFIT, in `beforeGameSave` and in `onGameLoad`. It covers visible slots, `getRecent(base)` and `getVariantMap()`. Saves do not hold the card.
- Full rows: hulls with no free slot, or without `SavedVariantData` access, use the autofit checkbox "Optimal fit for every goal", stored per save in the dialog's `optionState`.
- Run: preview runs `doFit(current.clone(), goal, 0, previewDelegate)` on every card click and option toggle. Confirm runs the same with the real delegate and the same `Random` seed.
- Requirements: deterministic (fixed tie order, the plugin's `random` only) and synchronous (target under 50 ms per fit).
- `maxSMods` is 0. The game applies the CR penalty for refitting away from port at save.

## 4. What the optimizer may use

- Research: `engine_report.md` 3, 7.

- Weapons and LPCs: `delegate.getAvailableWeapons()/getAvailableFighters()`, filtered by `CoreAutofitPlugin.getWeapons/getFighters` (cargo / storage / market / black market). Use the delegate's own entry objects, since it casts them.
- Quantity: respect `getQuantity()` (live). Price is per unit with tariff. Storage costs 0.
- Strip: `clearWeaponSlot`/`clearFighterSlot` first, so mounted weapons join the pool. Then `syncUIWithVariant(current)` once.
- Hullmods: `delegate.getAvailableHullmods()`. Also checked: `isApplicableToShip` via `ship.setVariantForHullmodCheckOnly`, item requirements, phase tags, incompatibilities (ITU/DTC/ATC/DFC, HSA vs Advanced Optics, shield conversions), the 2-Logistics limit, `canAddRemoveHullmodInPlayerCampaignRefit`.
- OP: hull `hullSpec.getOrdnancePoints(stats)`. Weapon `w.getOPCost(stats, current.getStatsForOpCosts())` with the player's stats, re-read after hullmod changes. Max vents/caps 10/20/30/50 × the player's `getMaxVentsBonus/getMaxCapacitorsBonus`. The engine does not check these afterwards.
- Slot rules: `slot.weaponFits(spec)`. Exact mount type may go one size down. Hybrid / Composite / Synergy / Universal need the same size. Built-in, decorative, hidden, system and station slots are skipped.
- Priority: the game's delegate reports nothing as priority for the player. With "Respect priority weapons" on, `getPlayerFaction().isWeaponPriority(id)` / `isFighterPriority(id)` mark the candidate (`Cand.priority`, set in `JAOOptimizer.buildSlots`). `pickBest`, `pairSwap` and `fillEmpty` compare score x 1.01 for marked picks (`JAOSearch.PRIORITY_MARGIN`), so a marked weapon or wing wins its mount when it scores within 1% of the best other choice. The objective itself is unchanged.
- Price: a single Optimal fit pays for market and black-market items in score: `objective` x (1 - `Params.creditCost` x credits spent), capped at 0.9 (`JAOSearch.spendCost`). `creditCost` = setting "Minimum gain to buy" / 10,000 (default 1% per 10,000 credits). Survival mode (ext_weaponPriceMult 10, ext_fighterPriceMult 5) raises the gain a bought item needs by the same factor. Planning (Optimize fleet, Order fleet weapons) sets it to 0.

## 5. Ship model

- Research: `engine_report.md` 5.1, `mechanics_report.md` 1, 3, 5.

- Stats: one scratch `FleetMemberAPI` per fit. `createFleetMember(SHIP, clone)`, `setFleetCommanderForStats(player, playerFleet.getFleetData())`, then per candidate `setVariant(candidate, false, true)` and `getStats()`.
- After-creation hullmods (Ballistic Rangefinder, High Scatter Amp, Coherer, PD Integration ...) do not run on the scratch member. The model adds them.
- Captain (`JAOShip.captainOf`): an active officer, the player on the flagship, or an AI core. None for the default captain or an inactive officer. Its personality sets the engagement range (`JAOShip.personalityOf`).
- Captain skills (0.5.29, `JAOShip.applyCaptain`): `FleetMember.updateStats` applies personal skills only to a member inside a fleet (`getFleetData() != null`), and the scratch member is in none. `applyCaptain` does what `CharacterStats.applyPersonalToStats` does, after each `setVariant`: every `SHIP` effect in `SkillSpecAPI.getEffectsAPI()` whose governing skill level reaches `getRequiredSkillLevel()`, through the game's own effect class, id `<skill>_ship_<index>`. Elite effects follow from the level. The fleet planner's `PlanDelegate` passes each ship's own member, so fleet plans use each ship's captain.
- Ordnance Expertise (`OrdnanceExpertise$Level1`, `$Level3`): reads the variant's weapon OP, which the search changes. `Ship.fluxPerWeaponOp` and `capPerWeaponOp` (the class's `FLUX_PER_OP` 1.5, `CAP_PER_OP` 20) are added by `JAOModel.ordnance` for the fit's weapon and wing OP (`JAOSearch.weaponOp`: picks plus `World.fixedWeaponOp`, kept ones from `computeWeaponOPCost`; built-ins excluded as the game excludes them).
- Combat listeners added by hand: Energy Weapon Mastery (`Ship.ewm` from `getEnergyWeaponFluxBasedBonusDamageMagnitude`, scored at `EWM_FLUX` 0.5, full to 600 and none past 1000 at the fight's two distances). Polarized Armor speed and EMP resistance at 50% hard flux.
- Stats read for skills (`JAOShip.skillStats`): damage by target size (`Ship.sizeDmg`, divides each phase in `phases`), damage to fighters (averaged with damage to missiles for point defense), point-defense range (`range`), missile hit points (`pdSurvival`), hard flux dissipation with shields up (`duty`), the armor's damage reduction cap (`armorHullEhp`), CR loss past peak time (peak time term), system cooldown and recharge (`systemUptime`: Accelerated Ammo Feeder and High Energy Focus).
- Not scored: missile ammo regeneration, damage to and from weapons and engines, overload time, phase cloak upkeep and cooldown, target leading, Helmsmanship's elite zero-flux boost, Combat Endurance's elite hull regeneration, Damage Control's elite repairs under fire.
- The player's fleet-wide skills apply through `setFleetCommanderForStats`.
- Derived: shield-tank share, flux budget (dissipation − shield upkeep − system flux), peak time, slot geometry (front arc share, hardpoints, mirror pairs).

## 6. Weapon model

- Research: `mechanics_report.md` 1-2, 6, 8.

- Numbers: `WeaponSpecAPI.getDerivedStats()` (dps, sustained dps, burst, flux/s, EMP), scaled by the ship's ROF, damage and flux mods.
- Hit strength: damage per projectile × armor multiplier. Beams: 0.5 × DPS.
- Damage multipliers (shield / armor / hull): kinetic 2/.5/1, HE .5/2/1, frag .25/.25/1, energy 1/1/1.
- Armor: damage taken `max(0.15, h/(h+P))`, P = pooled armor, minimum 5% of max armor. Hull damage while armor remains: `D × max(0.15, h/(h + 0.05A))`.
- Beams: soft flux on shields unless the ship has High Scatter Amp. Soft flux counts above the target's dissipation.
- Uptime U: `min(1, (budget × T + 0.5 × capacity) / (load × T))`, load = non-PD flux/s + 0.5 × PD flux/s. Applies to every DPS. Load above 1.3 × budget is rejected (`mechanics` 3.4).
- Range: the AI fights at the shortest range of its heaviest group and stands off at most 200 past the enemy's reach (`mechanics` 3.2-3.3). Weapons shorter than the main band count ×(R/R_eng)². Aggressive officers count them at 1. Reach above about 1,100 adds nothing.
- PD value: missile DPS × hit factor × arc cover × reach. Need per size is 100/200/350/500 missile-dps × the PD floor setting.
- Special effects: `data/config/jao_weapon_effects.csv` maps weapon id to extra shield / armor / hull / EMP damage per hit, or a multiplier. Vanilla rows from `mechanics` 6.2, mod rows from `modded_report.md`. Unknown scripted weapons (R: `data\weapons.json` `scripts`) get no bonus and no penalty.
- Missiles (finite ammo): a damage pool spent in the phase they suit. Kinetic to shields. HE / energy to armor / hull after overload. STRIKE only vs destroyers and up, fired when the AI picks their group.

## 7. Build objective

- Research: `mechanics_report.md` 8.6-8.7.

TTK of the whole build against reference targets, in three phases:

```
t1 shield  = kC x flux capacity / (hard shield DPS + max(0, soft shield DPS - target dissipation))   kC 0.6 (AI vents/backs off)
t2 armor   = integrate pooled armor from A to 0.2A:  dP/dt = -0.8 x sum DPS x aM x g(HS x aM, max(P, 0.05A))  x k_spread 1.5
t3 hull    = H / sum hull DPS (residual armor)
TTK        = t1 + t2 + t3,  every DPS x uptime x range factor
```

- Targets: medians of all loaded combat hulls per size, computed at load. R: `data\reference_targets.json`. Cruiser median: armor 1000, hull 8000, flux 10000, dissipation 525, shield eff 0.8.

Weights by the ship's own size:

| Ship size | Frigate targets | Destroyer | Cruiser | Capital |
|---|---|---|---|---|
| Frigate | 0.35 | 0.35 | 0.2 | 0.1 |
| Capital | 0.1 | 0.2 | 0.35 | 0.35 |

Styles (`JAOStyle`):

| Style | Minimise |
|---|---|
| Balanced | Σ w·TTK |
| Anti-shield | Σ w·(0.7 t1 + 0.15 t2 + 0.15 t3) |
| Anti-armor | TTK vs armored targets (1250-1750) with 0.15/0.55/0.3 |
| Hull killer | Σ w·(0.1 t1 + 0.3 t2 + 0.6 t3) plus burst damage inside the overload window (4/6/8/10 s) |

- Auto with "Fit for each ship's fleet role" on (default): the role's kill-layer weights (`JAORole` shield/armor/hull, `Params.apply`). Missile boats 0.6/1.6/0.8, Phase 0.5/1.2/1.3, Skirmisher and Support 1.2/0.9/0.9, Flanker 0.7/1.15/1.15, Anchor 1.1/1.0/0.9, Line, Carrier, Battle carrier and Civilian 1/1/1.
- Auto with fleet roles off: `JAOOptimizer.resolve`. Missile mounts at 40% or more of the mount weight (small 1, medium 2, large 4) use Anti-armor. Every other hull uses Balanced.
- Auto and Balanced with "Balance with the fleet" on: `JAOFleetMix.complement` then leans the shield and armor weights toward the damage the rest of the fleet lacks (§7k).
- Survival: EHP = hull + kA·armor + shield HP. Shield HP = tank share × (capacity + 30 s × spare dissipation) / shield efficiency. kA = 10/15/20/25 per size.
- Score: `survival^0.5 / TTK^0.5`.
- Hard rules: OP within limit. Load ≤ 1.3 × budget. PD ≥ 0.5 × need for destroyers and up. One range band for the main guns. Mirror pairs symmetric. No STRIKE-only fits on frigates.

## 7b. Any mod list: slot goals and points

- Nothing is hard-wired per mod. `JAORatings` reads `getAllWeaponSpecs()`, `getAllFighterWingSpecs()`, `getAllHullModSpecs()` and `getAllShipHullSpecs()` at first use after a load. It computes the reference targets and a rating for every weapon, wing and hullmod, cached until the next load or settings change.
- Slot goals: each mountable slot gets a role from its size, type, mount and arc, the hull's role (`hull_profiles` rules) and the build style. Roles: main gun (anti-shield or anti-armor), finisher (hull damage), PD, strike (anti-armor alpha, missiles), support (EMP, utility), flex.
- Points: every weapon has a score per role on one scale for vanilla and modded weapons. 100 is the best weapon in the loaded game for that role at that slot size, per OP, against the reference targets.
- Tier 1, in stock: the autofit pool (fleet cargo, plus storage and the docked market or black market when ticked). Only this tier is fitted.
- Tier 2, known: learned blueprints. `getPlayerFaction().knowsWeapon(id)` / `knowsFighter(id)` / `knowsHullMod(id)`, plus the always-unlocked hullmods.
- Tier 2, known: Known hullmods are fittable. Known weapons and fighters are fitted once in the pool.
- Tier 2, known: With "Log every fit" on, the log names the known item that would raise the score most.
- Tier 3, exists in the game: the slot's ideal. It sets the 100-point scale.
- Nothing in stock fits a slot's goal: the slot is re-goaled to the role with the best available score, and the whole-build TTK check (§7) confirms it. If nothing helps, the slot stays empty and its OP goes to hullmods, vents or caps.
- Damage type OTHER or unknown: rated as energy. SYSTEM-hint weapons are not candidates. Unknown tags and hints are ignored. Scripted weapons with no table row are rated on plain numbers.
- Data tables (`jao_weapon_effects.csv`, `jao_hullmods.csv`, `jao_rules.csv`) are keyed by id. Rows for unloaded mods are ignored. Mod-dependent rules use `isModEnabled`. No compile-time dependency on any content mod. LunaLib is optional.

## 7c. Calibration targets

- Research: `meta_report.md` (sources graded CONS / MAJ / SINGLE / OLD), section 10.

- Rarity: rare and high-tier weapons are often worse than common ones in AI hands. Poor: Gauss, Plasma, Mjolnir, Hellbore, Hurricane, Mark IX, LR PD Laser.
- Rarity: Top tier: Hephaestus, HIL, Harpoon, Typhoon, Heavy Autocannon, Phase Lance, Burst PD, Flak, HVD. Weapons are ranked by measured strength including scripted effects.
- Rarity: Tier, rarity and tag level are not scores.
- Prefer rare weapons: optional, off by default. Settings "Prefer rare weapons" and "Rare weapon bonus" (0.15).
- Prefer rare weapons: Rarity r in 0..1 is the max of (1 − drop weight `rarity`, default 1), tier / 3, and 1 for `rare_bp` / `restricted` / omega / threat / dweller tags. When on, a weapon's points and marginal score in the fill and swap search are × (1 + rareBonus × r).
- Prefer rare weapons: Hard rules still apply.

Rules the model reproduces (offline test expectations only):

1. Flux: sustained non-PD flux + shield upkeep is 0.8-1.1 × dissipation. Hard cap about 1.5×.
2. Range band: main guns within about ±200 after bonuses, kinetic at or slightly past the HE range. Steady/Cautious officers 900-1000. Aggressive/Reckless 450-700.
3. Damage: every combat ship gets a hard-flux kinetic source and an armor-cracking source (HE hit ≥ ~150-200, or energy/HE hit ≥ ~400). Kinetic is weighted higher.
4. Slot conventions: small ballistic is kinetic or PD. Medium ballistic is kinetic + one HE. Large ballistic is HE / high hit or the kinetic anchor. Synergy / composite takes missiles.
5. Missiles: fill missile slots. Unguided only within ~20° of the bow. Homing only off-bore.
6. PD: at least one PD per threatened arc. Prefer Burst PD / Flak / Vulcans. Penalise LR PD. IPDAI once PD OP ≥ ~4× its cost.
7. Hullmod break-evens: Hardened Shields beats capacitors above about 4,000 / 8,000 / 12,000 / 20,000 flux capacity (frigate to capital). Stabilized Shields beats vents above 60 / 120 / 180 / 300 upkeep. Expanded Magazines for charge-weapon builds (Autopulse, Storm Needler, IR Autolance, Ion Pulser, Thumper, Burst PD). ITU/DTC on gun cruisers and capitals.
8. Bulkheads and Blast Doors go in after every combat-relevant option is filled.
9. Safety Overrides: only on frigates/destroyers whose main guns are all ≤ ~500 range, with max vents and Hardened Subsystems.
10. Version guard: 0.98.5a changes phase ships, the Antimatter Blaster, the Odyssey, Anubis and XIV Onslaught. Ratings come from live specs. `jao_weapon_effects.csv` carries the game version and is re-checked on release.

## 7d. Fleet roles

- Setting: `jao_roles` "Fit for each ship's fleet role", on by default. `JAORole` (pure) holds the defaults.

| Role | Offense weight | Speed | Targets F/D/C/Cap | Auto kill layers S/A/H | Range | PD | Wings |
|---|---|---|---|---|---|---|---|
| Skirmisher | 0.45 | 0.25 | .45/.35/.15/.05 | 1.2/0.9/0.9 | any | ×0.7 | ×1 |
| Flanker | 0.65 | 0.12 | .3/.35/.25/.1 | 0.7/1.15/1.15 | any | ×0.8 | ×1 |
| Line | 0.6 | 0.03 | .1/.25/.35/.3 | 1/1/1 | long | ×1 | ×1 |
| Anchor | 0.55 | 0 | .05/.15/.4/.4 | 1.1/1/0.9 | long | ×1.1 | ×1 |
| Carrier | 0.55 | 0.05 | by size | 1/1/1 | long | ×1.3 | ×1.4 |
| Battle carrier | 0.62 | 0.03 | .1/.25/.35/.3 | 1/1/1 | any | ×1.1 | ×1.2 |
| Support | 0.45 | 0.05 | .35/.35/.2/.1 | 1.2/0.9/0.9 | long | ×1.6 | ×1.1 |
| Phase | 0.7 | 0.1 | .2/.35/.3/.15 | 0.5/1.2/1.3 | close | ×0.5 | ×1 |
| Missile boat | 0.6 | 0.05 | .1/.3/.35/.25 | 0.6/1.6/0.8 | long | ×1 | ×1 |
| Civilian | 0.3 | 0.15 | .6/.3/.1/0 | 1/1/1 | any | ×1.3 | ×1 |

- Detection (`JAORole.detect`), designation keywords first: phase; battlecarrier; carrier/mothership with bays; escort/support/picket; missile; civilian hull; battleship/dreadnought/battlewagon/juggernaut/titan/doomwagon is Anchor; battlecruiser/artillery is Line; fast/light cruiser/light destroyer/strike/raider/pursuit/interceptor is Flanker (Skirmisher for frigates).
- Detection by numbers: phase; ≥2 bays with guns under half the OP is Carrier; bays on a cruiser or bigger is Battle carrier; missile share ≥ 0.45 is Missile boat. Otherwise by size: frigate Skirmisher, destroyer Flanker, cruiser Line, capital Anchor.
- Override: `jao_rules` `role,<hull id or pattern>,<Role>`.
- Score: `Params.apply` takes kill-layer weights from an explicit style, or from the role for Auto. Score = offense ^ role.offense × survival ^ (1 − offense). The role also sets the target mix, the preferred range when the Range setting is Auto, the PD need multiplier and the wing value multiplier.
- Variant name: "Optimal: <Role>[, <style>]".

## 7e. Range

- Enemy main-battery range per size (`Target.threat`): measured at load from every stock goal variant. It is the shortest range in the heaviest non-missile, non-PD group, with ITU or DTC added, median per size. Fallback `DEFAULT_THREAT`: frigate 660, destroyer 800, cruiser 1000, capital 1250.
- Two distances (mechanics 3.3): the ship fights at R_main and closes to its shortest non-PD gun R_close part of the time. Share closed in: steady 0.4, aggressive 0.8 (PD included in the minimum), cautious 0.15.
- Weapon credit: (1 − close) × (1 if in range at R_main, else (R/R_main)²) + close × (1 if in range at R_close, else (R/R_close)²).
- Survival: per target class, S(R) = 1 + k × clamp((R − threat)/400, −1, 0.75), k = 0.5 (0.6 Range Long, 0.3 Close). Blended over both distances and weighted by the role's target mix.
- `JAOFitCheck` requires a closing range ≥ 0.8 × main range for anchors and ≥ 0.75 for line ships. Mounts with only short guns in stock stay empty or get PD.

## 7f. Determinism

- `JAOSearch` sorts every slot's candidates canonically (weapon id, price, stock key) before searching, because the delegate lists stock in cargo-stack order.
- `JAOFitCheck` runs every fit with the stock reversed and requires the same result.
- A fit changes with different stock, "Strip before autofitting" unticked, and settings changes.

## 7g. Leftover OP and drawback hullmods

- `JAOSearch.spendLeftover`: while OP remains after the flux fill, take the best use that fits: any hullmod (filler included, no minimum gain), a bigger weapon in a slot, a weapon for an empty mount. A modelled loss is accepted up to `LEFTOVER_TOLERANCE` 5%.
- Fleet hullmods (ECM, Nav Relay, Escort Package, Operations Center on the flagship; `JAOSearch.fleetMods`) are the last fallback. If OP is still spare, the variant name says "(N OP spare: drawbacks off)" and the log names the drawback hullmods that would fit.
- Apply: vents and caps are recomputed from the OP left after fitting, vents first, then caps, up to max. Weapons the game refuses and unused leftover are logged.
- Drawbacks: setting `jao_allowDrawbacks`, off by default. With it off, `JAOShip.drawback` compares a 49-stat snapshot of the scratch member with and without the hullmod.
- Drawbacks: A stat more than 0.5% worse disqualifies it. Listener drawbacks the snapshot cannot see are in `KNOWN_DRAWBACKS` (High Scatter Amp's beam range).
- Drawbacks: Penalty s-mods (`smodPenalty`) are excluded. Safety Overrides needs the setting on.

## 7h. Civilian ships

- Setting: `jao_civilianLogistics`, on by default. Fit: no weapons or wings, then campaign hullmods in `logisticsOrder`.
- Hull test (`JAOOptimizer.civilian`): the game's civilian flag (`isCivilianNonCarrier`), or salvage/survey/tanker/liner/freighter/transport designations. Hulls with the COMBAT hint, fighter bays, or "modified"/"combat" in the designation are warships.
- Order by type: salvage/survey: Surveying Equipment, Treasure Hunt Package. Tanker: Auxiliary Fuel Tanks.
- Order by type: Liner/transport: Additional Berthing. Freighter: Expanded Cargo Holds.
- Order by type: Then the remaining logistics hullmods in `logisticsOrder` (drive fields, sensors, Efficiency Overhaul, shielding, Militarized Subsystems), unknown mod ones last.
- Limits: 2 logistics hullmods (built-in and s-modded do not count). Combat drawbacks are ignored. With drawbacks off, increases to supply, recovery, deployment or fuel cost are refused.
- "Build in s-mods" ticked: cruisers and capitals build the first ones in (story points on Confirm only).
- Spaceport: logistics hullmods are `req_spaceport`. Away from a spaceport the existing ones stay, weapons are removed, and the name says "(dock at a spaceport to add logistics hullmods)".
- Fleet needs (0.5.31, `JAOUtility`, `JAOOptimizer.fleetNeeds`): each logistics pick and fleet-wide s-mod is measured on the scratch stats without and with it (`JAOShip.utility`: survey supplies reduction, `th_treasure_hunt_boost`, `HRS_SENSOR_RANGE_MOD`, max burn). If it raises any of them, it goes on only if one still helps the fleet (`JAOUtility.useful`):
  - Survey: rest of fleet + this ship below `surveyTarget()`: 90th percentile over planets not `SurveyLevel.FULL` of the survey plugin's supplies (empty scratch fleet with the player's `SURVEY_COST_MULT` mods) minus `MIN_SUPPLIES_OR_MACHINERY`. Daily cache. Fallback 75.
  - Treasure Hunt: below `(th_treasure_hunt_package_mult_clamp - 1) x median(th_rewards values)` (`THUtils.applyBoost`: min(3p, p + boost)). Fallback 20.
  - Sensors: gain in `sensorBonus` (copy of `HighResSensors.getAdjustedHRSModifier`: max x (log2.5(total/max) + 1)) at least `HRS_MIN_SHARE` 0.5 of the ship's own value. Ships under 10% CR don't count.
  - Burn: the ship's burn below `burnFloor`, the slowest non-civilian member (mothballed included).
  - Others: single fit `ofFleet` (player fleet without this member, live stats); Optimize fleet `JAOFleetPlan.utilityOthers` (planned civilians from a scratch member of their plan).

## 7i. Scoring rework 0.3.0

- Benchmark: `jtbuild/ao_bench.ps1` (JAOBench) fits 19 combat hulls from a real 276-weapon pool offline. Data: `research/fleet_diag`, `research/weapon_eval`.
- Result, before / after: weapon OP 43% / 53%, plain-PD share 39% / 28%, mount use 75% / 93%, empty mounts 1% / 0%, flux ratio 1.05 / 1.21.
- Flux: burst windows (`BURST_SHARE` 0.5, `BURST_CAP` 0.6 of capacity over `BURST_SECONDS` 10). The AI opens every group up to 2x while flux is hard (mechanics 3.4). All three kill phases use the sustained/burst mix. Pressure from 1.4x (exponent 0.3), linear cut past `FLUX_LIMIT` 1.75x.
- Mount credit: a mount counts as used by its weapon's OP against `MOUNT_OP` 4/8/16, with `EMPTY_COST` 0.5. Padding hullmods (Bulkheads, Blast Doors) come once mounts are 90% used (`mountsUsed()`).
- PD: plain PD hits ships at 0.25 and pays 0.5 of its flux. PD + PD_ALSO hits at 0.6 and pays full flux. PD bonus 0.9-1.0. PD need is about 1, 2, 3 or 4.5 Burst PD by hull size (35/65/100/150).
- Groups: distinct weapons are groups, all PD is one. ×0.97 per group past 7, ×0.985 per distinct gun past 3. ×(1 − 0.25 × gun damage share outside ±200 of the main battery range).
- Arcs (`JAOModel.bearing`): 1.0 if the mount can face the bow, 0.8 within 30°, 0.35 beyond. Flux is still paid. Broadside mode applies when side-only mounts carry over 40% of mount weight.
- Weapon numbers (`JAORatings`): burst beams hit at 0.5 × in-burst DPS. MIRV hit is the submunition.
- Weapon numbers (`JAORatings`): DEM hit is half the payload beam's DPS. Finite-ammo guns that cost flux (Antimatter Blaster) are guns at their one-minute average.
- Weapon numbers (`JAORatings`): Sustained DPS is capped at the fire rate. GUIDED_POOR missiles get about 0.85 accuracy against frigates.
- Tests: `JAOFitCheck` per vanilla hull and style: mount use ≥ 0.85 (Balanced) or 0.75, plain PD ≤ 55% (Balanced) or 65%, groups ≤ 8, at most 2 Antimatter Blasters. The flux check allows up to `FLUX_LIMIT`. With `-DallMods=true`, 11 of 511 fail on modded-stat oddities.

## 7j. Fighters and fleet balance (0.4.0)

- Wing value: `WING_VALUE_PER_OP` (20) × OP × quality. Quality is 0.5-1.0 (`JAOModel.wingQuality`), the wing compared with its role's reference wing on four axes. Matching the reference scores 1. Beating it scores no more.

| Axis | Weight | Measure |
|---|---|---|
| Firepower | 0.40 | Per OP. Guns at 0.35 of paper. Melee (range < 150) at a further 0.35. Finite missiles once per refit + 25 s. Bombers use armor damage: type × hit / (hit + 500). |
| Durability | 0.25 | Craft × (hull + 2 × armor) per OP, × 1.25 with a shield. |
| Point defense | 0.20 | 0.4 + 0.6 × the share of firepower from PD, PD_ALSO or ANTI_FTR guns. |
| Refit tempo | 0.15 | 1 / (refit + 4000 / speed) for wings that leave the carrier. |

- Reference wings, first loaded wins (0.4.1): Broadsword; Dagger; Spark, then Wasp; Xyphos, then Sarissa. If none is loaded, the 75th percentile of the role's priced wings (OP < 100, not restricted + no_sell).
- Bomber firepower: max(armor damage, shield breaking = damage × shield multiplier × 0.5) × payload reliability (guided 1.0, GUIDED_POOR 0.8, unguided 0.55, bombs 0.35). EMP counts half.
- Per carrier: interceptors on a ship with any bomber, or on a NO_AUTO_ESCORT hull, ×0.4 on value and PD. A lone bomber on a one-bay ship ×0.7. Value is linear in OP.
- PD credit: non-interceptor wings with PD-capable guns add half their value to the ship's point defense.
- Fleet balance: `JAOFleetContext`, setting `jao_fleetBalance`, default on. Each wing is × `wingRoleFactor` (0.6-1.3) toward `WING_MIX` (35 fighters / 35 bombers / 10 interceptors / 20 support), counted across every bay in the fleet. ×0.97 per copy of the same wing elsewhere in the fleet (up to 6), ×0.95 per repeat on the same ship.
- Damage mix: with Auto or Balanced and at least three other combat ships, shield and armor weights move by up to 20-25% toward 50% kinetic and 30% armor-cracking (hit × armor multiplier ≥ 300) fleet firepower. Wings count in the mix, interceptors excepted.
- Coverage (Auto/Balanced; Skirmisher, Flanker, Line, Anchor, Battle carrier, or no role; 3+ main weapons; not SO): ×(0.85 + 0.15 × min(1, kinetic / 20%)) × (0.85 + 0.15 × min(1, cracking / 15%)). Plain PD is left out of the shares. Judged in the swap passes only.
- Fleet analysis panel: a button under Order fleet weapons on the refit screen. It shows the fleet's firepower mix, fighter bays by role, recommendations and one line per ship.
- `settleOp` after Confirm: re-reads the real OP. Spare OP goes to vents, then capacitors. Overage cuts capacitors, then vents. Capacitors come first on Carrier, Phase, Support and Missile boat roles. Each change is logged.
- Valkyrians: Bryncore and Micro Bryncore add +100 base range to `valk*` weapons (`Ship.valkRange`, before percentage bonuses). Mount-use credit uses list OP (`World.baseOp`).
- R: `research/build_philosophy/report.txt`. Script: `wing_values2.py`.

## 7k. The fleet plan, 0.5.0

`JAOFleetMix` (pure; used by the plan, the analysis and the bench):
- Anti-shield: shield damage per point of damage, kinetic 1. Beams count fully.
- Armor-cracking: hit x armor multiplier of at least 300.
- Targets: 50% anti-shield, 30% armor-cracking.
- Weights: `PULL` 10 with lean 0.6-3 on the shield and armor kill weights.
- Wings: pull 3 with factor 0.4-1.6. Simulated bays went from 4/22/0/8 to 9/15/1/9.

`JAOFleetPlan` (engine behind both fleet buttons):
- Order: combat ships biggest first from one shared stock.
- Pass 1: full fit (hullmods, and s-mods for Optimize), balanced against the ships planned so far.
- Passes 2-3: re-pick weapons and wings with all others in view. A ship keeps its plan unless the new one beats it by `KEEP` (1%), judged by `JAOOptimizer.scoreAsIs` under the same params (30 of 44 ships flipped per pass without it; 8, 2, 0 with it).

`JAOCheckShip`:
- Stands in for a ShipAPI during off-screen hullmod checks. Built on `JAOStubShip`, `JAOStubShield`, `JAOStubSystem`, generated by `jtbuild/gen_stubs.py` from the installed API.
- Answers from the planned variant: hull size, shield (none with Shield Shunt), phase cloak, system, stats, member.
- The refit screen's ship cannot stand in for another hull: shield hullmods read `getShield()`; Safety Overrides and Dedicated Targeting Core read `getHullSize()`.
- `PlanDelegate` applies refit rules for the docked location: `canBeAddedOrRemovedNow` with the docked market and trade mode.

Optimize fleet (`JAOFleetApply`, `JAOFleetPanel`):
- Apply: strips changing ships into cargo, fits each plan from cargo then storage, sets hullmods (s-mods through the game's story point path), vents, capacitors (trimmed if over OP), weapon groups and variant name, then moves unused items to storage.
- The ship shown on the refit screen is changed through `getShipDisplay().getCurrentVariant()`, then `syncWithCurrentVariant()` and `saveCurrentVariant()`.
- In-space refit CR (0.5.26): the refit screen charges CR through `FleetMember.setVariant(variant, panel)` when its other entity is null (trade mode NONE). `JAOFleetApply.fieldRefit` reads `getOtherEntity()` from the panel (no panel: no market, or a condition-only market). Every other changed ship is charged through `setVariant(old, false, false)` then `setVariant(new, true, true)`, the same `computeVariantDiffCRImpact`. The shown ship pays through `saveCurrentVariant()`. The panel shows each ship's loss before Apply (`JAOModel.refitCrLoss` over `JAOFleetApply.refitDiff`, the same formula through the API).
- Hullmods: `hullmods()` keeps hidden hullmods and `never_remove` rule matches (AI Tweaks' AI hullmods), as the single fit's strip does.
- S-mods are built only while the Autofit panel's "Build in s-mods" box is ticked (`JAOGoals.optionChecked`, read from `SavedVariantData.getOptionState()`). The box is off by default.

Order fleet weapons (`JAOFleetPlanner.Job`):
- Stock: owned items plus unlimited blueprints.
- The order is everything the plan takes from blueprints.
- The budget decides how much is queued now (whole ships, biggest first). "N more items next time" is shown.

Other:
- Refit screen: Optimize fleet sits in the stack under Order fleet weapons. Fleet analysis takes the next free spot.
- Wing ids: `HullVariantSpec.setWingId(i, null)` stores "" and `getWings()` returns the raw list. `JAOFleetApply.wingAt` reads `getWingId` with "" as empty and iterates `computeNumFighterBays`.
- `purgeBlankLpcs()` cleans cargo and all storages on load, before each save and after each apply.
- Empty fighter bays count in `JAOSearch.emptyShare` as large mounts.

## 7l. Other mods' scripts, and what production takes

Script hazards and rules (`JAOCompat`, rows in `jao_rules.csv`, scan `modscan_1105_1605.txt`):
- Hull-bound wings: an LPC tagged no_drop and no_sell stays in its bay. It is not stripped, counted as stock (`addMounted`), taken from cargo or storage (`buildSlots`) or moved by Optimize fleet.
- `bay_wings`: `SKR_plagueCultist` / `SKR_plagueBearer` fly only Seeker's `plague_LPC` list (from merged modSettings). `tw_trinity_retrofit` flies only Nimbus drones. `tw_enhanced_control_node` flies anything. Bays left empty by a rule get a note.
- Script probe (`JAOCompat.probe`): after each fleet-plan fit, a scratch fleet member runs every hullmod's stat effects on a copy of the plan. Wings a script puts into empty bays join the plan (`ShipPlan.scripted`, not taken from cargo).
- Script probe (`JAOCompat.probe`): Their OP comes off capacitors then vents (vents first on carriers, phase, support, missile ships). Penalty hullmods a script adds (error/warning/malfunction/penalty) become a note.
- Script probe (`JAOCompat.probe`): Apply runs `settle` again after `updateStats` and refreshes once more if anything changed.
- Plague: weapons and LPCs in `seeker.plague_weapons` / `plague_LPC` carry `W.crCost` (3/6/12). `Ship.plagueMult` is 0 on `plague_immune` hulls and 0.666 with Hardened Subsystems. Priced through `crStart` (0.5.10). A wing priced 1000+ OP is rated at the OP past 1000.
- MagicLib interference is read from every mod's modSettings (`MagicLib.interferences_weapons`) after the jao_rules rows.

Script hazards:
- Seeker `SKR_plagueCultist` throws every non-plague LPC into cargo when the ship is shown.
- Trinity Worlds `TrinityRetrofit` fills a Maquech's empty bays with `nimbus_tw_wing_c` (10 OP) on every stat refresh and deletes Nimbus LPCs from cargo.
- Yunru zero-OP "Install:" hullmods (`yunru_silvervale_conversion`, `yunru_automation`, `yurnu_install_alpha/beta/gamma`, `yunru_lobster_install`, trigger `yunru_install_confirm`) become permanent when the ship is shown. `spendLeftover` does not take a zero-OP hullmod. `never_add` rows: `yunru_install_confirm`, `vice_apotheosis_protocol`, Roider's MIDAS levels; `never_add_ui,Installation`; `never_remove,ix_dawnstar_*`.
- `purge_lpc` `nimbus_tw_*`: removed from cargo and every storage on load, before saves and after each apply.

Ids kept: mod id `jonahs_auto_outfitting`, package `data.scripts.jonahsautooutfitting`, jar `jonahsautooutfitting.jar`, config `jonahs_auto_outfitting.json`, persistent keys `jao_*`, status key `$jonahs_status_jonahs_auto_outfitting`.

Production supply:
- AoTD's production order is a Theory of Toolbox trade contract that takes goods from colonies monthly: Heavy Armaments for every weapon and LPC, Ships for LPCs, Electronic Components for most manufacturers (`AoTDProductionSpec`).
- Auto Colonies 0.2.39.0+ reads each order line's `getReqResources()`, plans a producer for every good no colony makes and keeps back the credits the orders owe.
- The order panel lists what the order takes, which colony makes each good and which goods nobody makes (`JAOProduction.describe/unmade`). It says Auto Colonies plans a producer when 2.39+ is enabled (`colonyManagerSupplies`, by mod id and version, skipping Auto Colonies' leading 0), else that those items wait.
- Queue all logs what all of AoTD's orders still wait for.

Ship locks:
- `JAOLocks`: locked fleet member ids in a `HashSet<String>` under persistent data key `jao_locked`.
- `JAOScreenButton`: refit-screen button `jao_lock_ship` ("Lock this ship" / "Unlock this ship", follows `getMember()`, refreshed 4 times a second) in the next free cell of the Fleet analysis column.
- `JAOFleetPlan`: `ShipPlan.locked`. `readyLocked` gives each locked ship its current variant as its plan, takes what it carries out of stock (`keepOwn`) and adds its weapons to the balance (`ws`). `planOne` skips it.
- `JAOFleetApply.apply` skips locked ships. The Optimize panel lists them as "locked: left as it is". The order panel counts them.
- The single Optimal autofit ignores locks.

New-mod audit (`ao_modscan.py`, report modscan_1111):
- Diable's Versant and Virtuous selector weapons sit in BUILT_IN slots. Tahlan's swap, Sin and frame hullmods are hidden. Daemonic Heart is built in on `_dmn` hulls.
- `removed_by` (jao_rules kind, `JAOCompat.removedByHull`): built-in hullmod to hullmods its script removes when the ship is shown. Rows: `tahlan_lirefit` (10 Legio hulls) removes `unstable_injector`; `tahlan_silberherz` and `tahlan_silberherz_minor` remove `safetyoverrides`.
- `removed_by` (jao_rules kind, `JAOCompat.removedByHull`): Checked in `hullmodCandidates`, civilian picks and `fleetHullmods`.
- `never_use tahlan_manannan`: its on-fire script, in the campaign and without `$tahlan_daemons_scathach`, disables the weapon, deals 50000 energy and EMP to the firing ship and spawns a radius-10000 explosion that hits allies.
- Scripted weapons: 85 of 105 mountable new weapons carry scripts. 38 rows were added to `jao_weapon_effects.csv`. EMP-only arcs (Goll, Relparax, Karion, Omna, Nocturne) and guidance plugins (Balor, Tethra, Hueter) have no row. Thunderbolt and Plover need none.
- Effect columns (`JAORatings.applyEffectRow`, shared with JAOFitCheck): `extraDamage`/`extraType`/`extraChance`/`extraOnShields` give `W.extraFrac`, `extraHit`, `extraDmg`, `extraShields`. `JAOModel` adds the extra through the shield (hard flux), armor (`armorFactor` on the extra hit) and hull stages, for guns and finite missiles.
- `extraPerSecond` for beams (Strahlhammer 0.625 bursts/s, Strahlblick 0.5).
- `damageMult` scales dps, hit, burst and pool (Silk 0.75; Hexafire 1.4: damage x(1 + min(flux, 0.75)/0.75)).
- `fluxMult` (Velvet 0.7: flux cost falls to 50% after 5 s of fire). `selfFluxPerShot` (Taffeta 100).
- `EMPTY_COST` 1.0: all-mods Balanced empty share 7%, vanilla 1%. At 0.5 the all-mods share was 12% (19 fails); 0.75 gave 11%; 1.25 made vanilla Medusa take plain PD and Antimatter Blasters. `FILL_TOLERANCE` 0.04/0.06 changed nothing.

0.5.18 logistics count:
- `JAOShip.check` skips built-in, original perma and planned s-mod hullmods when counting logistics hullmods. The limit is `JAOShip.maxLogistics` (`maxLogisticsHullmods` with the member's `Stats.MAX_LOGISTICS_HULLMODS_MOD`, skills).
- `runLogistics` uses the same limit, leaves perma mods alone when stripping and checks limit and OP before `canAdd`.
- Leftover OP: `JAOOptimizer.SURVIVAL` after `logisticsOrder`: Reinforced Bulkheads, then Blast Doors. No vents, capacitors or weapons.

0.5.17 civilian ships:
- `runLogistics` does not fill leftover OP with vents or capacitors.
- Setting `jao_civilianSmods` (default on): with Build in s-mods ticked, keepers (`JAOOptimizer.keeper`: cruisers, capitals, and salvage/survey rigs of any size via `salvageRig`, which also looks for built-in `repair_gantry`) build in `FLEET_WIDE` first: Treasure Hunt Package (1/2/4/8, x2 as an s-mod, summed with `Misc.getFleetwideTotalMod`, capped per source at 3x base), Surveying Equipment (5/10/20/40, x2 as an s-mod), High Resolution Sensors, then burn mods.
- Built-in mods do not count against `maxLogisticsHullmods`.
- `planCivilian` passes `planSmods` and the plan's story points.
- `logisticsOrder`: Treasure Hunt Package comes right after cargo/fuel/berths for every civilian.
- Civilian planning (0.5.9): with "Civilian ships: fit for the campaign" on, Optimize fleet plans civilian hulls (`JAOFleetPlan.civilianShips`, `ShipPlan.civilian`, `planCivilian`). `JAOOptimizer.logistics` runs `runLogistics` from bare (`strip = planning || STRIP`) with `smodRoom` 0 unless s-mods apply.
- Civilian planning (0.5.9): Planned once, no stock taken, no share in the fleet mix. Order fleet weapons skips them.

0.5.16 free wings:
- `JAORatings.wingRaw`: a 0-OP wing's axes are taken at op 1 and priced by `JAOModel.freeWingOp`, the most OP at which it still matches its role's reference wing per OP on firepower and durability, capped at `FREE_WING_MAX_OP` 10. Mining Pod 2.3 OP, Brattice 4.5, JYD Termite 5.7, Dajeil drones 2.5.

0.5.15 phase ships and flux:
- `adaptive_coils` on a phase hull adds `s.extra` 0.10 (`JAOShip.ADAPTIVE_COILS`; Phase Anchor 0.12). `armorHullEhp` stays linear on phase hulls.
- Phase survival x(1 + 0.3 x sqrt(capacity / hull capacity)) (`PHASE_EHP`, `Ship.baseCap`).
- jao_rules kind `not_on_phase` (`JAOCompat.notOnPhase` in `hullmodCandidates`), row `heavyarmor`.

0.5.14 armor value:
- `JAOModel.armorHullEhp`: the armor pool (`KA` x rating) wears down in 10 steps while each hit of `INCOMING_MIX` is cut by the armor still standing (`armorFactor`, floor 15%), then the hull through the 5% floor. `INCOMING_MIX`: kinetic 120 x0.30, HE 350 x0.20, HE 1000 x0.10, energy 200 x0.30, frag 60 x0.10.
- Scaled by `ARMOR_EHP_SCALE` 0.614, blended `ARMOR_SHAPE` 0.5 with the linear term.

0.5.13 shield duty:
- `JAOModel.duty`: engaged t_e = 0.6 x capacity / incoming shield flux; break t_r = 0.6 x capacity / (2 x dissipation x vent rate; plain dissipation with Safety Overrides) + 5 s; score x t_e / (t_e + t_r). Logged as `stay`. `SHIELD_BLOCK` 0.35.
- Search: two starts (`improve` biggest mounts first, then smallest first, best kept); `pairSwap` (every pair of medium/large mounts, best 4 candidates each); `fillEmpty` (an empty mount takes its best weapon if that costs at most 2%). Offline fits average 58 ms, at most 243.

0.5.12 doctrine, groups, charged guns:
- `JAOShip.personalityOf`: an officer-less ship takes its personality from the player faction's doctrine aggression (`FactionPersonalityPickerPluginImpl`: 1 cautious, 2 steady, 3 aggressive, 4 aggressive/reckless, 5 reckless).
- jao_rules kind `min_size` (hullmod id, smallest hull size), row `targetingunit,DESTROYER`; `JAOCompat.tooSmall` in `hullmodCandidates`.
- Weapon groups (`JAOGroups.arrange`, after `autoGenerateWeaponGroups` in JAOOptimizer and JAOFleetApply): on broadside hulls (side mounts 40%+ of gun size weight) a group with the same weapon on both sides splits into left and right while under 7 groups. On missile boats (missiles 30%+ of weapon OP) groups of 2+ conserved launchers (max ammo under 20) are LINKED.
- Charged guns (`JAOModel.chargedDps`; `W.burstDps/ammoMax/regen` from `usesAmmo` + `getAmmoPerSecond`): recharge-limited dps plus stored charges, emptied `MAG_REFILLS` (3) times a fight. Autopulse 300 to 450; with Expanded Magazines 525; s-mod 675. Ship `ammoBal/ammoEn` (`get*AmmoBonus`), `regenBal/regenEn` (`get*AmmoRegenMult`).
- Resistant Flux Conduits: `Ship.empTaken` (`getEmpDamageTakenMult`) and `ventRate`; score x (1 + EMP_W[size] x EMP avoided) x (1 + 0.03 x extra vent rate); `EMP_W` {0, 0.01, 0.025, 0.05}.

0.5.11 combat AI research (`ai_behaviour_1106.md`, `tournaments_1106.md`, `builds_update_1106.md`, `ai_work\weapon_tiers.json`):
- Missiles one by one in `phases` (MI-1/2): `missileUse` = STRIKE_USE by target size (unguided {0.05, 0.3, 0.7, 0.95}, guided {0.05, 0.95, 1, 1}) or 0.5 + 0.5 SIZE_HIT. `pdSurvival` = 1 / (1 + 0.25 x 100 / missile HP) (`W.projHP`).
- Missiles one by one in `phases` (MI-1/2): Free-fire missiles (20+ shots, non-STRIKE) send 60% of non-kinetic damage into a raised shield.
- Shield-efficiency hold applies to the main group only (`mainId`, heaviest non-missile group): 1 - 0.6 x clamp((X - r)/0.6), X 0.9 steady / 0.75 aggressive / 1.0 cautious (SF-5).
- Carriers: escort when 66%+ of ranged wings are non-bombers (75% and half interceptors/support with an aggressive captain) (CF-1). Wing reach is capped by the shortest ranged non-support wing (`reachCap`, CF-2).
- Carriers: Defensive Targeting Array (`Ship.dta`, wing range 0): bombers x0.2, interceptors/PD wings x1.3, fighters x0.8 (CF-4).
- Handling counts on slow hulls: maneuver exponent x clamp(20 / hull turn rate, 0.3, 1) x (0.6 + 0.4 hardpoint share) (`TURN_REF`, `Ship.baseTurn`).
- Point defense: `pdKill` = clamp(dps x max(0.5, range/400) / 300, 0.2, 1); `PD_EXTRA` 0.04 up to 3x the floor. No PD at all: x0.95. `PD_NEED` 60/130/250/400 was reverted.
- Community tiers: `jao_weapon_tiers.csv` (85 vanilla weapons from Grievous69's lists), `JAORatings.prior`, `W.prior`, setting `jao_communityTiers`. Factor x(1 + 0.08 (score - 6)/4) on a weapon's dps and pool.

0.5.10 CR and d-mods:
- `Ship.maxCr` / `JAOShip.maxCr`: 0.7 plus every flat modifier on `getMaxCombatReadiness` except crew ones and `SKR_plagueWarning`, times its mult. Degraded Life Support, Faulty Automated Systems and Increased Maintenance take 5 points each.
- `crEffect(c)` follows `CRPluginImpl`: aim accuracy 1.5c - 0.5 weighted `CR_AIM` 0.25 of hits; above 70% up to +12% at 100% (damage, damage taken, speed: 10% each); below 50% -12% at 0; malfunctions below 40% (-20% at 0), critical ones below 20% (-40%). Value 1 at 70%.
- 0.5.26: the thresholds are the ship's own (`Ship.malfAt`, `critAt`, `degradeAt`). `JAOShip.crThresholds` reads `getCRPlugin().getMalfunctionThreshold(stats)` and `getCriticalMalfunctionThreshold(stats)`; the degrade line takes the same scale (all three follow the dynamic stat `cr_malfunction_range`). Defaults 0.4 / 0.2 / 0.5.
- 0.5.26: `crTier` gives the game's risk label: Low below the malfunction line, Serious below the midpoint of the two lines (0.98.5a; 0.98a used a fixed 30%), Critical below the critical line. The chances are linear in CR in both versions, so the tier changes no score. The fit log shows it as `crTier`.
- `crStart(max, deploy)` averages first deployment and the next (`Ship.deployCr` from `getCRPerDeploymentPercent`). Plague is `crStart(maxCr - plague/100)`.
- Carrier deck (`Ship.deck`, `deckFactor`): wing value x (1/refit mult)^0.5 x (1/replacement decrease mult)^0.25 x (replacement increase mult)^0.15 x (0.85 + 0.15 engagement range mult) x `JAOCompat.wingMult`. Damaged Flight Deck x0.88, Malfunctioning Comms x0.94, Expanded Deck Crew x1.08.
- Carrier deck (`Ship.deck`, `deckFactor`): `wing_mult` rule rows cover hullmods that change the craft: `defective_manufactory` 0.88, scaled by `dmod_effect_mult`.

0.5.8 cheat weapons and unguided missiles:
- `missileReliability`: guided 1, poorly guided 0.85, unguided 0.75, x0.9 under 600 speed, friendly-fire rockets x(1 - 0.25 x min(1, hit/1000)), floor at hit 200. Unguided counts as friendly fire. `Eff.rel` carries it. `missileShield` applies it and the target-size hit.
- Setting `jao_cheatItems` (off): `JAOCompat.cheatOnly` = no `_bp` tag, no NPC faction knows it, no non-fighter ship variant carries it in a normal mount, and not omega/threat/dweller. The player faction's known lists do not count. Applied to fit candidates and fleet-order blueprints.

0.5.7 missile pools:
- `phases` spreads a launcher's pool over the fight window (`missileWindow`). `W.poolRate` (dps while it has ammo) caps the pool at rate x fight length, so Expanded Missile Racks pay only on launchers that empty inside the fight.

0.5.6 all-weapons run:
- `SPEED_CAP` 1.5 and `MANEUVER_CAP` 2 in `JAOModel`; never_add row for `yunru_bayblade`.
- `yunru_irt` in `JAOShip.KNOWN_DRAWBACKS` (only with "Allow hullmods with drawbacks").

0.5.5 rules loading:
- `jao_rules.csv` is read with `loadCSV(RULES, MOD)` (no merge), and the load logs the count per kind. The game's merge keeps only the last row per kind when the folder path lacks the master mod id. `jao_hullmods.csv` and `jao_weapon_effects.csv` are keyed by unique ids and use the merge.

- `jtbuild\ao_modscan.py` scans every enabled mod: scripts that change loadouts or cargo, installers, zero-OP hullmods, hull-bound and gated wings, unsellable weapons.

## 8. Hullmods

- Research: `hullmods_vanilla.json` (129 vanilla, effect numbers with file:line), `hullmods_work\report_preview.txt`, `hullmods_mods.json`, `modded_report.txt`.

- Value of a hullmod: gain in overall score from applying it, divided by its OP. Compared with the same OP spent on vents (10 dissipation/OP), capacitors (200 capacity/OP) or a weapon upgrade. Greedy, re-scored after every pick (SO range cap, range stacking, shield mods vs Shunt).
- Known effects: shipped table `data/config/jao_hullmods.csv`. Vanilla rows come from `hullmods_vanilla.json`, mod rows from `hullmods_mods.json`.
- Never for combat: logistics/civilian mods (setting), Recovery Shuttles, Converted Fighter Bay, Neural Interface/Integrator, ECM/Nav Relay/Operations Center, Escort Package.
- Code facts that change values:
  - Heavy Armor has no maneuver penalty unless S-modded.
  - ECCM cuts missile range to x0.8.
  - Integrated PD AI turns small weapons into PD only when S-modded.
  - ITU always beats DTC when both are known.
  - Ballistic Rangefinder adds to base range (capped 800/900).
  - Flat range bonuses are not scaled by percent bonuses.
  - Safety Overrides keeps 25% of range past 450.
- Prototype `tools\hullmod_reco.py` writes `hull_hullmods.md` (picks for all 1,095 combat hulls). It matches 29% of the hullmods in stock goals. Calibration targets:

## 8b. Modded content rules

- Research: `modded_report.txt` section 2, `hullmods_mods.json` (700 mod hullmods), `weapons_mods_quirks.json`. Shipped as `data/config/jao_rules.csv`.

OP:
- Every weapon and wing is priced through the engine (`getOPCost(stats, variant.getStatsForOpCosts())`, `computeOPCost`) after hullmods are on. About 25 mod hullmods change weapon OP; Valkyrian BrynCore does it per weapon through a listener.
- Negative-cost hidden hullmods attached by mod code (ASM relic, TW, IX ECM, Diktat reverter, RAT Exceptional Construction) add OP. They survive the strip; hidden mods are not removed.

Variant re-read:
- The variant is re-read after every hullmod add. Mods delete conflicting hullmods: TBJ, TW and Vice targeting cores remove ITU/DTC; Amorphous Alloy removes Heavy Armor; missile forges remove Expanded Missile Racks and the Autoloader; Arma frames remove IPDAI and SO.

Code-owned slots (not filled or cleared):
- Arma Armatura frames (built-in slots driven by about 50 selector hullmods).
- A_S-F Phantasmagoria, SEEKER Siegfried.
- ET-IX/TW slots on paragon_ix_elite WS 001/002 and Tigershark IX/TW WS 004.

Never add:
- Work orders, RAT alterations, System Reset.
- Item or story-point installs (IX stabilizer/panoptic, ET masker/desync, Arma OVERLORD).
- `*_handler`, `*_error`, `*_checker`, `*_discount_*` helpers.
- Selectors.

Never remove:
- Anything hidden or built in, mode/selector hullmods.
- `rat_controller`, `rat_exceptional_construction`.
- Starship Legends `sun_sl_*`.
- AotD hull mods, IndEvo defects/pets.

Weapons:
- At most one `feedback_weapon` per ship.
- MagicLib interference weapons cost (n-1) x 20/40/80 dissipation each when 2+ are fitted.
- SEEKER plague items cost 3/6/12% max CR each on non-plague hulls (x0.666 with Hardened Subsystems), scored through `crStart`.
- Fragment weapons need `fragment_swarm`.
- `vice_seeker` and `archaic_c` weapons get range only from their own hullmods.
- A_S-F artillery hulls need a target designator fitted.
- Hull-wide modifiers appear in scratch-member stats: TTE_bolt energy +20% damage / -20% flux / +15% range; TW trinity range x0.9; TBJ +70-100% range.

Custom AI:
- Missile AI: armaa, A_S-F, IndEvo, Imperium, SEEKER, SWP, Valkyrians.
- Ship AI: Imperium, KoL, SotF, SWP, Underworld.
- Real value can differ from the vanilla-AI model. These are not penalised.

## 9. Optimizer

`JAOOptimizer`, stages in the class javadoc.

1. Profile and pool: strip through the delegate, sync once, scratch member, reference targets (cached per session), available weapons grouped by spec.
2. Candidates per slot: specs that fit, honour mount arcs (unguided missiles need the front: 20 / 45 degree rule), have quantity and pass the price/option filters. Mirror pairs (same size/type, mirrored angle) are one decision when enough copies exist.
3. Initial fill: big slots first. Each slot takes the candidate with the best marginal score per OP against the TTK of the build so far.
4. Improve: single-slot and pair swaps until no swap improves the score. Bounded passes, deterministic order.
5. Carriers: bays get the best wing by value per OP. Interceptors/PD wings if PD is short, bombers for Anti-armor, fighters otherwise. Replacement-rate math from `mechanics` 7.
6. Hullmods, vents, caps: greedy per OP (section 8), re-scored each step. Then stages 3-6 repeat once with the OP split moved (drop the worst weapon for a hullmod, or the reverse) while the score improves.
7. Apply: `delegate.fitWeaponInSlot(slot, entry, variant)` with engine slot objects, `fitFighterInSlot`, `variant.addMod`, vents/caps. Then `recordWeapon/recordFighter` (so `getCreditCost` is right), `getWeaponGroups().clear()`, `setMayAutoAssignWeapons(true)`, `autoGenerateWeaponGroups()`, display name, `syncUIWithVariant`.
8. Modules (multi-module ships): each module gets weapon optimization on its own slots, with no hullmods.

Runtime: up to about 60 slots on the largest hulls and about 200 distinct available specs. Greedy plus swaps over cached per-weapon numbers is O(slots x candidates x passes). TTK is closed-form per phase plus a 10-step armor integral.

## 10. When the player does not have the ideal weapons

- Pool: TTK picks the best mix of what exists. With no kinetic weapons, Balanced leans on energy/HE and spends more OP on flux. Anti-shield falls back to the best shield DPS available.
- Empty slot: allowed when no available weapon raises the score. Its OP goes to hullmods/vents.
- Bought weapons count by credit cost only through the panel's own checkboxes. A mild tie-break prefers owned items.

## 11. Settings

`JAOSettings` + `LunaSettings.csv` + `data/config/jonahs_auto_outfitting.json`.

| Setting | Default | Notes |
|---|---|---|
| Add the Optimal goal | on | |
| Build style | Auto | Auto, Balanced, Anti-shield, Anti-armor, Hull killer |
| One goal per style | off | Extra cards only fill free slots. |
| Range | Auto | Long / Close shift R_eng and the range factor. |
| Fight length | 90 s | T for uptime, missile pools, peak-time penalty. |
| Value special weapons | on | Uses `jao_weapon_effects.csv`. |
| Prefer rare weapons | off | Bias x (1 + bonus x rarity score), section 7c. |
| Rare weapon bonus | 0.15 | 0-0.5. |
| Respect priority weapons | on | Faction doctrine marks win within 1% (`PRIORITY_MARGIN`). |
| Minimum gain to buy | 0.01 | Score share per 10,000 credits a bought item must add (`Params.creditCost`). 0-0.1. |
| Point defense floor | 1 | x PD need. |
| Allow logistics hullmods | off | |
| Log every fit | off | Scores and reasons to starsector.log (no on-screen messages). |

Refit autofit always gets 0 s-mods, so there is no "Build in hullmods" setting.

## 10b. Model details

- Offense weight: score = offense^0.6 x (EHP x outrange)^0.4.
- Empty-mount cost (`JAOSearch.EMPTY_COST`): applied to the slot-size-weighted share of choosable mounts left empty (value 1.0 since 0.5.19). Balanced fits leave about 1% of mount size empty offline.
- MagicLib interference: `jao_rules` interference rows go to `W.interference` (20/40/80). Each costs (count - 1) x its strength in dissipation, halved by Resistant Flux Conduits (`interferenceMult`). It enters the flux budget and the vent fill.
- S-mod planner: beam search over sets (width 3). "Waiting" is reported even when nothing known is worth it.
- Known hullmods: the delegate's `getAvailableHullmods()` (learned + always-unlocked, minus hidden, minus spaceport-only when not docked). Excluded: logistics (setting off); `never_add`; fleet-level or campaign-only (ECM, Nav Relay, Ops Center, Escort Package, Recovery Shuttles, Stardust Harmonic/Overcharge).
- Known hullmods: S-mods also exclude `no_build_in` (Safety Overrides, ET adaptive subsystems, conversions) and penalty s-mods.
- Escort Package s-mod (0.5.26, `JAOShip.SMOD_VALUE`): an s-mod candidate only. `JAOModel.escortSMod`: 0.98.5a cuts shield and armor damage taken 20% on destroyers and 10% on cruisers near a bigger ship; 0.98a cuts destroyer shield damage only. `JAOShip.escortArmor` tells the versions apart by the hullmod's second s-mod text number. Counted for `ESCORT_UPTIME` 0.6 of a fight.
- Missile Autoloader and Phase Anchor have modelled effects (`JAOShip.MODELLED`).
- Safety Overrides: setting `jao_safetyOverrides` (Never / Frigates and destroyers (default) / When it scores best). The game bars SO from capitals.
- Safety Overrides: Model costs: peak time judged against a whole battle (max(180 s, 2 x fight length)), so x0.33 peak time costs readiness unless Hardened Subsystems offsets it; a range cap removes up to 60% of the firepower share from main guns longer than threshold + 100 (550 for SO); no venting costs x0.93. Offline: SO in 0 of 42 vanilla fits; every fit that picks it has a long-gun share of 25% or less (`JAOFitCheck`).

## 11a. S-mods with story points

Switch: autofit panel checkbox "Build in s-mods (story points, permanent)" (`jao_build_smods`). Off by default, remembered per save, applies to Optimal fits only.

| Setting | Default | Notes |
|---|---|---|
| S-mods per fit | 1 | New s-mods per ship in one fit or one Optimize fleet, within the ship's limit, `Misc.getMaxPermanentMods`: 2, +1/+1 with skills (`JAOOptimizer.smodLimit`) |
| Story points per Optimize fleet | 2 | `JAOFleetPlan.spendable`: the plan starts from min(story points, reserve + this) |
| Minimum gain per s-mod | 2% | |
| Wait for the best hullmods | on | |
| How much better to wait for | 10% | |
| Story points to keep | 3 | |

Planner (`JAOSMods`):
1. Candidates: known, buildable hullmods that are not `no_build_in`, hidden or never_add; combat value per `jao_hullmods.csv`.
2. Penalty s-mods (`smodPenalty`: Heavy Armor, Expanded Missile Racks, Missile Autoloader, Fragment Swarm) stay candidates. The scratch member applies the penalty stats; the model scores handling (mobility factor) and missile rate (pool x min(1, missile rate of fire)). Heavy Armor: -25% turning and acceleration. Expanded Missile Racks: missile rate of fire x0.8. Missile Autoloader: 10 s cooldown, ammo x1.35 (was x1.5).
3. Each candidate is tried as an s-mod: 0 OP plus its s-mod effects, measured on the scratch member via `addPermaMod(id, true)`. A cheap screen keeps the base fit's weapons and re-chooses hullmods, vents and caps. The top 3 get a full re-optimisation.
4. Greedy, best first. Each pick must add the minimum gain.
5. Wait: the same search over every buildable hullmod in the loaded game. If that set beats the best available set by the margin, only the slots its missing hullmods would take are held. Other slots get the best available set of that size. The log names what is held ("not known yet" or "dock at a spaceport").
6. A ship holds slots for at most `jao_smodWaitDays` (default 30), counted from the first run that held one. The start time is in sector persistent data `jao_smod_waits` (member id to timestamp, plain JDK types). After that it builds the best it has.
7. "No room" notes say which limit applies: "already has N of N s-mods", "no story points", or the per-ship setting.
8. The final fit is searched with the chosen s-mods as 0-OP keepers.

Spending happens only on Confirm:
- Confirm: `spendStoryPoints(n, ..., average getBuildInBonusXP, "Built X into ship (hull)")`, then `SModRecord` + `PlaythroughLog`, then `addPermaMod(id, true)`. If story points did not drop, the mods go on as ordinary hullmods, dropped again if they do not fit the OP.

## 11b. Phase 2: "Build the best weapons for this fleet" (production planner)

One press plans the Optimal fit for every ship in the player fleet as if every known blueprint were in stock. It works out which weapons and fighter LPCs are missing and queues them for custom production, delivered to the gathering point.

Planner (`JAOFleetPlanner`):
1. Pool: cargo + storage (counted once across the fleet) + every known weapon/fighter blueprint (`getPlayerFaction().knowsWeapon/knowsFighter`) at unlimited quantity, priced at `FactionProductionAPI.getUnitCost`. Items in the production queue (`getCurrent()`) count as incoming.
2. Order: ships biggest first, then by deployment cost, so scarce owned items go where they are worth most.
3. Shopping list: what the fits use that is not owned (id to count, with score gain), trimmed to a credit budget (Auto Colonies' reserve if installed, else a setting) and optionally to months of production capacity (`getMonthlyProductionCapacity()`).

Confirm screen:
- Lists every item with count, cost and "used by" ships, plus total cost and estimated months.
- Queue all: `FactionProductionAPI.addItem(WEAPON/FIGHTER, id, qty)` per line.
- Cancel.
- Nothing is ordered without the press. The game charges as production runs.

Button locations:
1. Refit screen: `JAOScreenButton` (transient script, no LunaLib needed) puts "Order fleet weapons" on the refit panel (190x25 button grid). First choice is the empty cell right of "Autofit... [V]", then right of Strip, then around Autofit and the OP readout and along the panel edges. It finds the refit panel as LunaLib does (core UI, current tab, the panel with `syncWithCurrentVariant`, OP readout from `getOpDisplay`) through `JAORefl` (method handles) and logs every button's text and rectangle. JAOFitCheck tests the placement against the logged layout (the refit panel is `coreui.refit.U`).
2. LunaLib entry `JAORefitButton` (`LunaRefitManager.addRefitButton`, LunaLib 2.0.5 lists it behind its small icon left of the OP readout).
3. Auto Colonies' empire panel: "Fleet outfitting" page, when both mods are enabled, through the guarded optional-integration pattern (`isModEnabled` + a class compiled against the optional jar, checked by `audit_combos`).

`JAOOrderPanel`: overlay in the core UI (900x580, game colors, dimmed screen, Escape or outside click closes). Shows items, ships, cost, credits, budget, monthly capacity and months, gathering point, notes, and a scrolling list (count, name, total and unit cost, first three ships). Buttons Queue all and Close. Queue all is disabled with nothing to build or no production capacity. The status line points to the Colonies tab.

Pool as built:
- Owned (price 0) = fleet cargo + every market's storage + weapons and LPCs mounted on fleet ships and modules (not built-in or `skip_slots`) + the production queue.
- Blueprints = `getKnownWeapons/getKnownFighters` (no SYSTEM/DECORATIVE/BUILT_IN) at `getUnitCost`, quantity 9999.
- Mounted weapons are pooled fleet-wide.

Fits as built:
- `JAOOptimizer` planning mode on a clone of each ship: always strips, takes the planner's pool directly, keeps the ship's hullmods (no hullmod search, no s-mods, no fleet mods), does not write module variants back, logs nothing per ship.

Budget:
- `jao_productionBudget` share of credits (default 0.5).
- Ships go biggest first. A ship whose blueprint items would take the order past the budget is re-planned with owned stock only (counted in the panel note).

Queue all (`JAOProduction`, decided at run time):
- Sets the gathering point to the player market with the most production capacity when none is set.
- Vanilla: `addItem` per line into `FactionProductionAPI.getCurrent()`. `CoreScript.doCustomProduction` builds it at month end within capacity and charges then.
- AoTD: Vaults of Knowledge (`aotd_vok`), which replaces the custom production screen: `JAOAotdProduction`, compiled against Vok.jar and AoTDToolboxTheory.jar, loaded only when that mod is enabled. It places the order as AoTD's Confirm does: `new AoTDProductionOrderSnapshot()`, then `productionData` gets `new AoTDProductionOrderData(spec.getId(), spec, amount)`, then `initSnapshotConsumption()` (registers with `AoTDProductionManager`, adds a `ProductionTradeContract` for the resources).
- AoTD: Each unit is paid when finished.
- AoTD prices come from `AoTDProductionSpecManager.getWeaponSpec/getFighterSpec(id).getProductionCost()`. Only items with `isLearnedByFaction` are offered. Incoming = remaining units of current snapshots plus the vanilla list.
- AoTD's patched CoreScript still builds vanilla's list at month end. The panel lists weapons and LPCs waiting in vanilla's list and Queue all moves them into the AoTD order (`removeItem` only after AoTD accepted it). If the AoTD probe fails, vanilla is used and the panel says so.

Not done: Auto Colonies page and monthly re-order; fleet-tab button; per-line deselection in the panel.

## 12. Testing plan

Offline, the jtbuild pattern as Auto Colonies:
- `ao_build.ps1`: javac, banned-class scan, verify with and without LunaLib, game script-loader check.
- `JAOFitCheck.java`: real `starfarer.api` specs loaded offline where possible, else fixed rows from `data\*.json`. Runs the optimizer on fixed pools and checks OP limits, slot legality, determinism (same seed, same fit), balance (Balanced has both kinetic and HE/energy when both exist), style shifts and empty-pool behaviour. `checks0529`: a captain with Ordnance Expertise raises the Eagle, Dominator and Medusa scores, elite raises them again, no captain leaves the fit unchanged, and each other skill term moves the model.
- The Python prototypes in `tools\` give expected values.

## 13. Risks

- `SavedVariantData` is engine code and a game update can change it. It is feature-detected, with the checkbox fallback.
- A missed cleanup leaves a card in the save. It carries a stock loadout and is purged on load.
- The scratch-member stats miss after-creation hullmods and the captain skills' combat listeners. These are modelled by hand. Captain skills from mods that read the variant's weapons see only the kept weapons.
- The scoring constants (kC, k_spread, kA, soft-flux value, PD need, target weights) are tuning knobs and need simulator checks.
- Scripted mod weapons without a table row are valued on plain numbers only.
- Performance on 60-slot capitals with large markets is unmeasured.

## 14. Research index

All in the author's research notes (not included in this repository).

| File | What |
|---|---|
| `data\hulls.json` | 1,647 hulls (1,095 combat) incl. 394 skins: CSV stats, slot geometry, built-ins, hints/tags, goal variants |
| `data\weapons.json` | 1,666 weapons (484 player-fittable): CSV + .wpn/.proj, derived DPS/flux/hit strength/armor DPS, autofit tags, script classes |
| `data\wings.json`, `data\hullmod_rows.json`, `data\goal_variants.json` | 200 wings, 825 hullmod CSV rows, 2,003 goal variants |
| `data\reference_targets.json` | median enemy per size, vanilla and all |
| `data\hull_profiles.json`, `hull_profiles.md` | roles, slot shares, designed range and damage mix per hull |
| `data\hull_hullmods.json`, `hull_hullmods.md` | prototype hullmod picks per hull |
| `weapon_rankings.md` | per slot type/size ranking by kill time, plus vanilla tag disagreements |
| `hull_overview.csv` | one row per combat hull |
| `engine_report.md` | refit autofit internals, hook design (decompiled under `engine_work\`) |
| `mechanics_report.md` | damage, armor, flux, AI, range, skills, scripted effects, scoring model (`mechanics_work\`) |
| `hullmods_vanilla.json`, `hullmods_work\report_preview.txt` | vanilla hullmods with numbers and scoring formulas |
| `hullmods_mods.json`, `modded_report.txt`, `weapons_mods_quirks.json` | 700 mod hullmods, cross-mod rules, 912 weapon-quirk entries |
| `meta_report.md` | community fitting knowledge (graded sources, 34 per-hull builds, 18 optimizer rules); raw texts and a 156-weapon stats table in `meta_work\` |
| `tools\*.py` | dump_data, analyze, profiles, hullmod_reco (rerun after mod changes) |
