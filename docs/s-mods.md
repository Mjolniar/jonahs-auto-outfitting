# S-mods and story points

- Default: Story points are not spent unless Build in s-mods (story points, permanent) is ticked in the Autofit panel. The game saves the box per save.
- Single Optimal fit: The preview shows s-mods. Points are spent on Confirm only.
- Optimize fleet: Uses the same box and lists each new s-mod before Apply to all ships.
- Existing s-mods: Not changed.

## How it picks

- Method: Each candidate is judged by refitting the whole ship with it built in. The best goes first.
- Stops at: The ship's s-mod limit, S-mods per fit (1), story points minus Story points to keep (3), or a gain below Minimum gain per s-mod (2%).
- Optimize fleet: Spends at most Story points per Optimize fleet (2) in one run, biggest ships first.
- Civilian ships: Use their own list. See [Civilian ships](civilian-ships.md).
- Escort Package: Valued only as an s-mod. 0.98.5a: shield and armor damage taken -20% on destroyers, -10% on cruisers, counted for 60% of a fight. 0.98a: destroyer shields only.

## Waiting for better hullmods

- Wait for the best hullmods (on): If a set not yet buildable is better by 10%, only those slots stay open. Other slots get the best available.
- Most days to hold a slot (30): Afterward held slots get the best available.
- Log: `starsector.log` states what each held slot waits for.

## Limiting spending

- Box unticked: Nothing is built in.
- Story points to keep (3): Floor the mod does not spend below.
- S-mods per fit (1): New s-mods one fit builds into one ship.
- Story points per Optimize fleet (2): Total one Optimize fleet spends.
- Example: With 5 story points, one Optimize fleet builds 2 s-mods and keeps 3. Single fits build one s-mod each until 3 are left.
- Minimum gain per s-mod (2%): Raise it to build in only large upgrades.
