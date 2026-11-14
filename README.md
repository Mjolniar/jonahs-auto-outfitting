# Auto Outfitting

> [!WARNING]
> AI-generated. The mod, docs and build checks were written by AI coding agents. Versions are checked offline (compile, bytecode checks, test fits on vanilla hulls). Newer changes are untested in a real game. Report bugs in the [issues](https://github.com/Mjolniar/auto-outfitting/issues) with `starsector-core/starsector.log` and the modlist.

- Auto Outfitting: Adds an Optimal option to the refit screen's autofit list. It picks weapons, fighters, hullmods, vents and capacitors from cargo, storage and optionally the market. Options are scored on combat numbers and on how the game's AI flies the ship.

- Fleet buttons: Fit the whole fleet together, order missing weapons and analyze fleet weaknesses.
- Forum: [topic 36377](https://fractalsoftworks.com/forum/index.php?topic=36377.0) on the Starsector forum.
- Other autofit goals: Not changed.

## Install

1. Download the zip from the [latest release](https://github.com/Mjolniar/auto-outfitting/releases/latest).
2. Extract it into `Starsector/mods/`.
3. Enable Auto Outfitting in the launcher. LunaLib is optional and adds an in-game settings menu.

- Game version: Starsector 0.98a and 0.98.5a. One jar runs on both.
- Saves: Can be added to or removed from an existing save.

## Update

1. Download the new zip from the latest release.
2. Extract it over `Starsector/mods/Auto Outfitting`.

## Guides

- [Getting started](docs/getting-started.md): Install, first fit, Autofit panel boxes, troubleshooting.
- [How a ship gets fitted](docs/how-it-fits.md): Scoring of weapons, wings and hullmods.
- [The fleet buttons](docs/fleet-tools.md): Optimize fleet, Order fleet weapons, Fleet analysis.
- [Civilian ships](docs/civilian-ships.md): Campaign fit and fleet-wide hullmods.
- [S-mods and story points](docs/s-mods.md): When hullmods are built in.
- [Other mods](docs/other-mods.md): Handling of other mods and editable data files.
- [Settings](docs/settings.md): Every setting.
- [Patch notes](docs/changelog.md): Version history.
- [Design notes](docs/design.md): Code-level design.

## Permissions

- License: Modpacks, reuploads, forks, copies and derivative work are allowed without asking or credit. Selling is not allowed. See [LICENSE](LICENSE).

## Building

- Requirements: JDK 17, sources in `src/`.
- Command: `javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>`, then `jar cf jars/jonahsautooutfitting.jar -C build data`.
- Core jars (`starsector-core`): `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`.
- Optional jars: LunaLib (with LazyLib's Kotlin runtime), Ashes of the Domain.
