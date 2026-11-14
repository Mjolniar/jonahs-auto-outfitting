# Civilian ships

- Scope: Freighters, tankers, liners, salvage rigs and survey ships, in a single Optimal fit and in Optimize fleet.
- Fit for the campaign (on): Campaign fit with no weapons, vents or capacitors. Their weapons and LPCs return to stock for warships.

## What they get

1. Own job: Freighters Expanded Cargo Holds, tankers Auxiliary Fuel Tanks, liners and transports Additional Berthing, salvage and survey ships Surveying Equipment (while the fleet needs it).
2. Treasure Hunt Package: Added when the Treasure Hunt mod is present.
3. Within the limit: burn speed (Augmented Drive Field or a mod's version), Insulated Engine Assembly, High Resolution Sensors, Surveying Equipment, Efficiency Overhaul, Solar Shielding, Militarized Subsystems, then other mods' campaign hullmods.
4. Remaining OP: Reinforced Bulkheads, then Blast Doors.

## Fleet needs

- Rule: Surveying Equipment, Treasure Hunt Package, High Resolution Sensors and burn hullmods go on only while the rest of the fleet needs them. This covers ordinary hullmods and built-in s-mods. The next hullmod in the list takes the slot.
- Surveying Equipment: Survey supplies stop at 5. The fleet target is the reduction that brings 90% of the sector's planets not yet fully surveyed to 5 supplies, recounted once a day. A fully surveyed sector needs none.
- Treasure Hunt Package: Each progress source gives at most 3x its base. The fleet target is 2x the median reward in the mod's table (35 with Treasure Hunt 2.3.1).
- High Resolution Sensors: Each extra ship adds less. One more goes on while it adds at least half its own range to the fleet's bonus: two capitals, a third no.
- Burn speed: The fleet moves at its slowest ship. Burn hullmods go only on ships slower than the slowest warship. With no warships, every civilian takes them.
- Other mods: A mod's hullmod with the same effect follows the same rule.
- Optimize fleet: Each civilian counts civilians planned before it with their plan and every other ship as it is now.
- Log: `starsector.log` lists what was skipped and the fleet totals.

## Logistics limit

- Limit: Two logistics hullmods per ship (some skills raise it). Built-in hullmods do not count.
- Spaceport: Logistics hullmods are added only at a spaceport.

## Building in fleet-wide hullmods

- Requirements: Build in s-mods ticked and Civilian ships: build in fleet-wide hullmods on (off by default).
- Ships: Cruisers and capitals (Atlas, Prometheus, Colossus) and salvage rigs of any size.
- Order: Treasure Hunt Package (1, 2, 4 or 8 points by hull size, doubled as an s-mod), Surveying Equipment (5, 10, 20 or 40, doubled as an s-mod), High Resolution Sensors (frees a logistics slot), then burn speed.
- Cost: One story point each, spent on Confirm or Apply to all ships, not below the reserve. See [S-mods and story points](s-mods.md).
- Warships: No Treasure Hunt Package.
- Setting off: Fit for the campaign off gives a combat fit. Build in fleet-wide hullmods off gives the campaign fit without s-mods.
