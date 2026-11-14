# Settings

- Location: LunaLib menu under Auto Outfitting, or `data/config/jonahs_auto_outfitting.json` without LunaLib. Read at startup and on save load.

## Optimal build

- Add the Optimal goal (On): Optimal entry in each ship's autofit list.
- Fit for each ship's fleet role (On): Off builds every ship as a generic duelist.
- Civilian ships: fit for the campaign (On): See [Civilian ships](civilian-ships.md).
- Civilian ships: build in fleet-wide hullmods (On): Needs Build in s-mods.
- Balance with the fleet (On): Owned fighter roles count for less. Ships lean toward missing damage.
- Build style (Auto): Auto, Balanced, Anti-shield, Anti-armor or Hull killer. See [How a ship gets fitted](how-it-fits.md#the-idea).
- One goal per style (Off): Adds an Optimal goal per style.
- Range (Auto): Long keeps guns near the longest range. Close favours damage.
- Fight length (90 s): Short favours burst and missiles. Long favours flux efficiency. Caps launcher ammo value.

## Weapon and hullmod choices

- Value special weapons (On): Counts scripted effects.
- Prefer rare weapons (Off): Bonus for rarer weapons.
- Rare weapon bonus (15%): Gain of the rarest weapon.
- Use items you can only get with cheats (Off): Off leaves out console-only items.
- Lean on community weapon ratings (On): Near-ties go to the higher rating, about 5% at most.
- Respect priority weapons (On): Doctrine priority weapons and fighters win when they score within 1% of the best choice for a mount.
- Minimum gain to buy (1% per 10,000 credits): A market weapon or LPC must make the ship this much stronger per 10,000 credits it costs, over owned items. Survival mode prices weapons x10 and LPCs x5. 0 ignores prices.
- Point defense floor (1): Minimum point defense by ship size. 0 requires none.
- Never use these weapons (none): Comma-separated ids or patterns, such as `stardust_*`.
- Allow hullmods with drawbacks (Off): On weighs drawbacks against gains.
- Safety Overrides (Frigates and destroyers): Never, Frigates and destroyers, or When it scores best (needs drawbacks allowed).
- Allow logistics hullmods (Off): Combat ships may take cargo, fuel, sensor and burn hullmods.

## S-mods (story points)

- S-mod settings: Used only while Build in s-mods is ticked. See [S-mods and story points](s-mods.md).

- S-mods per fit (1): New s-mods one fit or one Optimize fleet builds into one ship, within the ship's limit.
- Story points per Optimize fleet (2): Most story points one Optimize fleet spends, biggest ships first.
- Minimum gain per s-mod (2%): Required strength gain.
- Wait for the best hullmods (On): Holds slots for clearly better hullmods not yet available.
- How much better to wait for (10%): Margin that triggers waiting.
- Most days to hold a slot (30): Then the best available is built.
- Story points to keep (3): Never spends below this.

## Fleet production and logging

- Order weapons for fleet button (On): Shows Order fleet weapons, Optimize fleet and Fleet analysis.
- Production budget (50%): Share of credits one order may cost. The rest is ordered next time.
- RAT settlement orders (On): A Random Assortment of Things settlement with Ordnance Manufacturing takes the biggest ships' items up to its budget. See [The fleet buttons](fleet-tools.md#order-fleet-weapons).
- Log every fit (Off): Writes scores and reasons to `starsector.log`.
- Debug: remove all s-mods and refund story points (Off): Turn on during a game and save the settings. Every ship in the fleet loses its s-mods, built-in hullmods lose their s-mod bonus, and each s-mod returns one story point. Bonus XP is kept. Runs once per switch to On. LunaLib menu only.
