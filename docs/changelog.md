# Patch notes

## 0.5.35 - 2026-11-14

- Optimize fleet: Spends story points down to Story points to keep. Fixed it stopping at 2 per run.
- Story points per Optimize fleet: Default 0, was 2. 0 = no limit.
- Story points to keep: Default 5, was 3.

## 0.5.34 - 2026-11-14

- Optimize fleet: Fixed civilian ships taking the per-run story points before warships. Warships are planned first.
- S-mod note: Fixed the note naming the story point reserve when Story points per Optimize fleet was the limit.
- Optimize fleet panel: Names the per-run cap when no s-mods are built.

## 0.5.33 - 2026-11-14

- Debug: Added Remove all s-mods and refund story points to the LunaLib menu (F3). Turned on during a game, it removes every s-mod from every ship in the fleet, modules included, and returns one story point per s-mod. Built-in hullmods lose their s-mod bonus. Bonus XP is kept. Runs once per switch to On.

## 0.5.32 - 2026-11-14

- S-mods: Picked for the ship without the d-mods restoration removes. D-mods built into the hull stay (Ill-Advised Modifications on the Colossus Mk.II and Luddic Path hulls). Weapons, vents and ordinary hullmods still count every d-mod.
- Fit log: The s-mod note names the d-mods left out.

## 0.5.31 - 2026-11-14

- Surveying Equipment: Added only while the fleet's survey supply reduction is below the sector's need. The need brings 90% of the planets not yet fully surveyed to the 5-supply floor. A fully surveyed sector needs none.
- Treasure Hunt Package: Added only while the fleet's boost is below 2x the median reward (35 with Treasure Hunt 2.3.1).
- High Resolution Sensors: Added only while one more adds at least half its own range to the fleet's sensor bonus.
- Burn hullmods: Added only on civilian ships slower than the slowest warship.
- Fleet needs: Apply to logistics fits and built-in fleet-wide s-mods, in a single fit and in Optimize fleet. Other mods' hullmods with the same effect follow the same rule.
- Fit log: Lists skipped utility hullmods and the fleet totals.

## 0.5.30 - 2026-11-14

- Story points: With 5 story points and the new defaults, one Optimize fleet builds 2 s-mods and keeps 3.
- S-mods per fit: Renamed from S-mods per ship. Default 1, was 3.
- Story points to keep: Default 3, was 0.
- Story points per Optimize fleet: Added. Default 2. One Optimize fleet spends at most this many, biggest ships first.
- Civilian ships: build in fleet-wide hullmods: Default off, was on.
- Optimize fleet panel: Shows the per-run cap next to the story points kept.
- Existing settings: Values already saved in LunaLib stay as set.

## 0.5.29 - 2026-11-13

- Officer skills: The captain's ship skills count in every fit, elite effects included. Ships without a captain score as before.
- Officer skills: Optimize fleet and Order fleet weapons use each ship's own captain. The flagship uses the player's skills. AI cores on automated ships count as captains.
- Ordnance Expertise: Adds 1.5 dissipation per OP of weapons and wings in the fit, and 20 capacity per OP when elite.
- Point Defense: +50% damage to missiles and fighters and the elite +200 point-defense range raise point-defense value.
- Target Analysis: Damage bonuses by target size shorten the time to kill each target size.
- Energy Weapon Mastery: Energy weapons gain up to +30% damage within 600 range, scored at half flux. The elite -10% energy weapon flux counts.
- Missile Specialization: Ammo, rate of fire, damage and missile hit points count.
- Field Modulation: Shield damage taken counts. The elite hard flux dissipation with shields up raises staying power.
- Systems Expertise: Shorter cooldowns raise the value of Accelerated Ammo Feeder and High Energy Focus. The elite damage reduction counts.
- Helmsmanship, Damage Control, Impact Mitigation, Combat Endurance, Gunnery Implants: Speed, handling, damage taken, peak time, CR, range and recoil effects count.
- Polarized Armor: The 90% armor cap counts. Its speed and EMP resistance count at half hard flux. The elite vent rate counts.
- Not scored: Missile ammo regeneration, damage to and from weapons and engines, overload time, phase cloak upkeep and cooldown, target leading, the elite zero-flux boost, hull regeneration and repairs under fire.
- CR model: CR loss past peak time follows the ship's own rate (Combat Endurance, Fragile Subsystems).
- Fit log: Lists the captain skills applied to each fit.

## 0.5.28 - 2026-11-13

- Compatibility: The game version in mod_info is 0.98.5a-RC4. TriOS no longer marks the mod incompatible. 0.98a still loads it.
- Install: The installed AutoOutfitting.version matches the installed version. TriOS no longer offers an update for a version already installed.

## 0.5.27 - 2026-11-13

- Order fleet weapons: Added Random Assortment of Things settlement orders. A Frontiers settlement with Ordnance Manufacturing takes the biggest ships' items while its budget lasts, at its prices (20% over base), paid when ordered.
- Order fleet weapons: With custom production too, the rest of the order goes there. Without Heavy Industry or Orbital Works, the settlement takes what fits its budget and the rest is ordered next time.
- Order fleet weapons: The settlement's storage and its open orders count as owned stock.
- Order panel: Shows the settlement's share, its budget, its monthly refill and the delivery time. The item list splits into Settlement and Custom production.
- Settings: Added RAT settlement orders (On).

## 0.5.26 - 2026-11-12

- Optimize fleet: Fixed ships off the refit screen skipping the CR loss of an in-space refit. Every changed ship loses the same CR as on the refit screen.
- Optimize fleet: The panel shows each ship's CR loss before Apply to all ships.
- AI Tweaks: Fixed Strip and Optimize fleet removing its five 0-OP hullmods.
- Priority weapons: Fixed Respect priority weapons having no effect. Doctrine priority weapons and fighters win a mount when they score within 1% of the best choice.
- Market prices: Added the setting Minimum gain to buy (1% per 10,000 credits). A market weapon or LPC must make the ship that much stronger than owned items. Survival mode prices weapons x10 and LPCs x5.
- Hullmod data: Fixed 4 duplicate rows in `jao_hullmods.csv` (Expanded Deck Crew, Insulated Engine Assembly, Recovery Shuttles, Solar Shielding).
- Expanded Deck Crew: Fixed it being refused on automated carriers such as the Blinding-class.
- Recovery Shuttles: Fixed the 0.98.5a note. Only automated ships are refused.
- Escort Package: Added a value as an s-mod. 0.98.5a: shield and armor damage taken -20% on destroyers and -10% on cruisers. 0.98a: destroyer shields only.
- CR model: Changed the malfunction thresholds to the game's values for each ship. The fit log names the 0.98.5a risk tier (Low, Serious, Critical).
- Docs: Fixed the design notes on the Auto style and officer skills. Officer skills are not counted in fits.
- README: Lists Starsector 0.98a and 0.98.5a.

## 0.5.25 - 2026-11-12

- Version Checker: Added AutoOutfitting.version. TriOS and MagicLib show the installed and latest version.
- Docs: Rewrote all docs at half length.
- Mod info: Shorter description.

## 0.5.24 - 2026-11-12

- Starsector 0.98.5a: Added support. The same jar works on 0.98a and 0.98.5a.
- Phase Anchor: Value follows the game's dissipation and weapon recharge bonus while phased. 0.98a: x2, score bonus 0.12. 0.98.5a: x1.5, score bonus 0.09. Adaptive Phase Coils stays at 0.10.
- Crew salary: With Allow hullmods with drawbacks off, a hullmod that raises crew salary or crew losses counts as a drawback. On 0.98.5a this covers Safety Overrides.
- D-mods: Degraded Shields, Faulty Power Grid, Malfunctioning Comms and Damaged Flight Deck use the ship's live stats. Weapon durability is not scored.

## 0.5.23 - 2026-11-12

- Starsector 0.98.5a: Fixed loading. The mod's stand-in ship has the six new `ShipAPI` methods.

## 0.5.22 - 2026-11-12

- Name: Renamed to Auto Outfitting. Folder `Auto Outfitting`, zip `AutoOutfitting.zip`, repository [Mjolniar/auto-outfitting](https://github.com/Mjolniar/auto-outfitting). Mod id `jonahs_auto_outfitting` is unchanged, so saves, LunaLib settings and ship locks keep working.
- Updating: Delete the old mod folder in `mods/` before extracting this version.

## 0.5.21 - 2026-11-11

- Locked ships: Added Lock this ship and Unlock this ship buttons on the refit screen. Optimize fleet and Order fleet weapons leave a locked ship as it is and do not use its weapons for other ships.
- Locked ships: It still counts toward the fleet's balance. Optimal fit still works on it.
- Locked ships: See [The fleet buttons](fleet-tools.md#locked-ships).

## 0.5.20 - 2026-11-11

- Settings page: Fixed a crash opening the LunaLib page. "Lean on community weapon ratings" and "Rare weapon bonus" used a single % sign, now %%.

## 0.5.19 - 2026-11-11

- Empty mounts: An empty or under-used mount costs up to 1.0 of the fit score (was 0.5). Mount size left empty with every mod's weapons in stock: 17% to 7%.
- Tahlan Shipworks and Diable Avionics: Added script effects for 38 weapons. Project Silk is rated on its arc damage, 0.75 of listed damage.
- `jao_weapon_effects.csv`: Added columns extraDamage, extraType, extraChance, extraOnShields, extraPerSecond, damageMult, fluxMult and selfFluxPerShot.
- Tahlan hullmods: Legio Infernalis ships get no Unstable Injector. Stahlherz and Glanzwurf get no Safety Overrides. Added rule kind `removed_by` in `jao_rules.csv`.
- Manannan (Tahlan): Never used. Firing it before its quest flag is set deals 50,000 damage to its own ship.
- Ship Mastery System: Turn off its "S-mod autofit option" in LunaLib settings. With it on, its autofit replaces Optimal fit.

## 0.5.18 - 2026-11-06

- Civilian ships: Fixed s-mods and built-in hullmods counting against the limit of two logistics hullmods. Leftover OP goes to Reinforced Bulkheads, then Blast Doors.

## 0.5.17 - 2026-11-06

- Civilian ships: Removed vents and capacitors.
- Civilian ships: Added setting Civilian ships: build in fleet-wide hullmods (on). With Build in s-mods ticked, cruisers, capitals and salvage rigs build in Treasure Hunt Package (with the Treasure Hunt mod), Surveying Equipment, High Resolution Sensors and Burn speed, in that order.
- Treasure Hunt Package: Placed right after cargo, fuel or berths on every civilian ship.
- Salvage rigs: Fixed rigs with a built-in Salvage Gantry not being recognized.

## 0.5.16 - 2026-11-06

- Free wings: A wing that costs 0 OP is valued at what it does. Was rated as 10 OP. Mining Pod bays in the test fleet: 6 to 1.

## 0.5.15 - 2026-11-06

- Phase ships: Adaptive Phase Coils and Phase Anchor are valued on phase hulls. Survival grows with flux capacity.

## 0.5.14 - 2026-11-06

- Armor: Valued as cutting each hit by hit / (hit + armor). Heavy Armor goes on heavily armored hulls such as the Onslaught and Paragon.

## 0.5.13 - 2026-11-06

- Search: Runs twice (biggest mounts first, then smallest first) and keeps the better fit. Tries pairs of big mounts and trades between hullmods. A mount is left empty only when filling it costs more than about 2%.

## 0.5.12 - 2026-11-06

- Doctrine personality: Ships without an officer are fitted for the personality from the doctrine aggression slider.
- Shield Conversion - Omni: Small bonus on slow, low-tech, front-shielded ships.
- Charged guns: Autopulse, Thermal Pulse Cannon and Storm Needler count the burst from stored charges. Expanded Magazines is valued on those ships.

## 0.5.11 - 2026-11-06

- Scoring: Loadouts are scored by how the game's AI fights, read from the game's combat code.
- Missiles: HE torpedoes and rockets are priced against armor like guns. Unguided torpedoes count only against slow, big targets. Enemy point defense shoots down weak missiles.
- Shields and flux: Shields use 80% of flux capacity. AI ships get dissipation to 1.25x their weapons' flux before capacitors.
- Aiming and point defense: Only the main weapon group is held back against shields. Large slow turrets cannot track frigates.
- Carriers: Escort only when about two thirds of wings are fighters, interceptors or support. The shortest-range wing caps strike range.
- Lean on community weapon ratings: Added setting (on). Of two weapons that score about the same, the one rated higher for AI ships (Grievous69's 0.98a tier lists) wins. Moves a value by about 5% at most.

## 0.5.10 - 2026-11-06

- Max CR: Fits are priced by each ship's max CR (skills, hullmods, d-mods), minus the plague the fit carries.
- D-mods: Added Damaged Flight Deck, Malfunctioning Comms, Defective Manufactory, Damaged Weapon Mounts and Ill-Advised Modifications.
- Optimize fleet: Reports how many ships have d-mods.

## 0.5.9 - 2026-11-06

- Optimize fleet: Fits civilian ships with cargo, fuel, burn, sensors, surveying and salvage hullmods and no weapons. Their weapons return to stock.

## 0.5.8 - 2026-11-05

- Use items you can only get with cheats: Added setting (off). Console-only weapons and LPCs are left out of fits, Optimize fleet and orders.

## 0.5.7 - 2026-11-05

- Launchers: Count for at most what they can fire within the fight length.

## 0.5.6 - 2026-11-05

- Yunru's Let's Bayblade!: Never added. Speed and handling count only up to 1.5x speed and 2x handling.
- Inverted Reverse Thrusters: Counts as a hullmod with a drawback.
- 0-OP weapons: Built-in and fighter guns do not go in ordinary mounts.

## 0.5.5 - 2026-11-05

- `jao_rules.csv`: Fixed only the last row of each rule kind applying.

## 0.5.4 - 2026-11-05

- Optimize fleet: Builds s-mods only with Build in s-mods ticked (off by default). Removed the "Optimize fleet builds s-mods" setting.

## 0.5.3 - 2026-11-05

- Order panel: Shows what Auto Colonies covers. Auto Colonies 0.2.39 plans producers only for goods no colony makes.

## 0.5.2 - 2026-11-05

- Other mods' scripts: Seeker plague hulls (the Guru) fly only plague LPCs. Trinity Worlds hulls fly only Nimbus drones. Wings you cannot buy or drop stay in their bays.
- Installer hullmods: Yunru's Install hullmods and other zero-OP installers are not added.
- AoTD orders: List what they take from your colonies and flag what no colony makes.

## 0.5.1 - 2026-11-05

- Optimize fleet: Fixed carriers left without fighters. Added its own button under Order fleet weapons.

## 0.5.0 - 2026-11-05

- Optimize fleet: Added button. Plans every combat ship together from ship contents, cargo and storage where docked, balancing anti-shield, armor-cracking and fighter roles. Refits all ships (s-mods with story points, leftovers to storage).
- Order fleet weapons: Uses the same plan.

## 0.4.1 - 2026-11-03

- Fighters: Rated against community picks (Broadsword, Dagger, Spark or Wasp, Xyphos or Sarissa). Wings that stay by their carrier count as support.
- Carriers and phase ships: Capacitors first.

## 0.4.0 - 2026-11-03

- Fighters: Rated against a reference wing per role (Broadsword for fighters, Dagger for bombers).
- Optimal: Balances fighter roles, repeated wings and anti-shield against anti-armor across the fleet.
- Valkyrian Bryncore: Hullmod weapon-cost discounts count.
- Spare OP after Confirm: Goes into vents and capacitors.

## 0.3.0 - 2026-11-02

- Weapons: Real weapons in every mount. No 1-OP fillers or small guns in big mounts.
- Loadouts: Matched weapon sets in one range band, within the game's 7 weapon groups.

## 0.2.1 - 2026-11-02

- Optimal: Added goal on the refit screen's autofit list.
- S-mods: Added optional s-mods. Story points are spent on Confirm.
- Order weapons for fleet: Added button. Queues missing weapons in custom production or Ashes of the Domain's.
- Requirements: No required mods. LunaLib adds the settings menu. Otherwise use `data/config/jonahs_auto_outfitting.json`.
