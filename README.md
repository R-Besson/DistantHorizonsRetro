<p align="center">
  <img src="./.github/assets/logo.png" alt="Distant Horizons Retro" width="50%">
</p>

<p align="center">
  An unofficial 1.7.10 port of <a href="https://gitlab.com/distant-horizons-team/distant-horizons">Distant Horizons</a>, focused on optimized performance, stability and new features.
</p>

<p align="center">
  Fork of <a href="https://github.com/DarkShadow44/DistantHorizonsStandalone">DistantHorizonsStandalone</a> by DarkShadow44.
  Not affiliated with GTNH, the Distant Horizons team or DarkShadow44.
</p>

<p align="center">
  <a href="https://github.com/R-Besson/DistantHorizonsRetro/releases"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg" alt="Available on GitHub"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/distant-horizons-retro"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg" alt="Available on CurseForge"></a>
  <a href="https://github.com/R-Besson/DistantHorizonsRetro/releases"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg" alt="Supports Forge"></a>
</p>

[//]: # (<a href="https://modrinth.com/mod/distant-horizons-retro"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg" alt="Available on Modrinth"></a>)

# What is Distant Horizons Retro?

Distant Horizons Retro is a mod which implements a [Level of Detail](https://en.wikipedia.org/wiki/Level_of_detail_(computer_graphics)) system to Minecraft 1.7.10.\
This allows for far greater render distances without harming performance by gradually lowering the quality of distant terrain.

![](./.github/assets/dhpreview.jpg)

## Why Retro?

Distant Horizons Retro is a fork of [DistantHorizonsStandalone][standalone] by DarkShadow44, which
brought Distant Horizons to 1.7.10. DistantHorizonsStandalone tracks upstream Distant Horizons
updates. Distant Horizons Retro is **developed independently**. It doesn't sync with either project
and has its own roadmap:

- **Optimizations**: faster world generation and LOD building
- **Bug fixes**: LOD holes, crashes, mod compatibility and server stability
- **New features**: planned, specific to this fork

Please report issues with this mod [here](https://github.com/R-Besson/DistantHorizonsRetro/issues), not to Distant Horizons or DistantHorizonsStandalone.

# Installation

Download the latest version from [Releases](https://github.com/R-Besson/DistantHorizonsRetro/releases) and put it into your `mods` folder.

> [!WARNING]
> Remove any other Distant Horizons related .jar first.

> [!IMPORTANT]
> Make sure the latest versions of each dependency are installed:
> - [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify): 3.0.15 or higher
> - [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)
> - [UniMixins](https://github.com/LegacyModdingMC/UniMixins)

## Shaders

Shaders are supported with [Angelica](https://github.com/GTNewHorizons/Angelica) 2.1.12 or higher.
Tested with [Complementary Shaders](https://modrinth.com/shader/complementary-reimagined) and [Euphoria Patches](https://modrinth.com/mod/euphoria-patches).

## Key bindings

Toggle LOD rendering and open the config from Options → Controls → Distant Horizons. They're unbound by default.

## Block colors

LOD colors are averaged from each block's texture. If a block looks wrong in the distance, set its color in
`config/DistantHorizonsBlockColors.cfg` (created on first launch), then press F3+T to reload:

```
# modid:block=RRGGBB applies to every meta, modid:block:meta=RRGGBB to one
minecraft:wool:14=A12722
# AARRGGBB also sets the alpha, 00 hides the block in LODs
minecraft:tallgrass=00000000
```

## Using it in GTNH packs

This is NOT an official GTNH mod and isn't supported by the GTNH team, report issues here.

Older GTNH packs ship versions of Angelica, lwjgl3ify and GTNHLib that are too old. Use a daily build of GTNH instead.

# Roadmap

- [x] Key bindings (toggle LODs, open config)
- [x] Faster LOD building, loading and saving
- [x] Correct ceilings for modded dimensions
- [x] Textured LODs, with and without shaders
- [x] Vanilla-like LODs: biome blended grass and leaves, see-through leaves, tall grass and flowers, depth shaded water
- [x] Fences, walls, slabs and snow layers shaped like the real blocks
- [x] Mobs visible up to the render distance with shaders
- [ ] Better block colors for Chisel and other mods with connected textures (use the color override file for now)
- [ ] Block shapes (fences, walls, slabs) work with shaders
- [ ] Shallow water tint near shores with shaders
- [ ] Faster world generation and lower memory use

# Development

## Structure

```
DistantHorizonsRetro/
├── settings.gradle      # includeBuild('dh'), reads the version from git
├── build.gradle         # runs the mod build, copies jars to build/libs
├── .github/workflows/   # build and release workflows
└── dh/                  # the mod
    ├── common/          # Minecraft-facing code (wrappers, rendering)
    ├── forge17/         # Forge entrypoint and mixins
    └── coreSubProjects/ # core LOD engine and API
```

The code in `dh/` was originally imported from Distant Horizons, but it is now maintained here.
It is not synced from upstream.

## Build

```bash
./gradlew build # Jars land in build/libs
```

## Dev Client
```bash
./gradlew :dh:forge17:runClient # Run dev client (Java 25)
```

The dev client comes with [Angelica](https://github.com/GTNewHorizons/Angelica), Complementary Unbound
with [Euphoria Patches](https://modrinth.com/mod/euphoria-patches) (select it in the shader pack menu)
and [Twilight Forest](https://github.com/GTNewHorizons/twilightforest) for testing a modded dimension.
Gradle downloads them, no manual setup needed.

> [!TIP]
> Run with `-PdevProfile=true` for performance profiling, `-PdevJava8=true` for Java 8
> and `-PdevMods=false` to leave out the test mods

# Credits

- [Distant Horizons][dh] by James Seibel and the Distant Horizons team
- [DistantHorizonsStandalone][standalone] by DarkShadow44, the original 1.7.10 port
- Logo based on the Distant Horizons logo by Pankakes

# License

Distant Horizons Retro is licensed under the LGPL, like Distant Horizons. See the `LICENSE*` files in `dh/`.

The Distant Horizons Retro logo is a modified version of the Distant Horizons logo © 2024 by Pankakes,
and like the original is licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

<p align="center">
  <img src="./.github/assets/icon.png" alt="" width="96">
</p>

[dh]: https://gitlab.com/distant-horizons-team/distant-horizons
[standalone]: https://github.com/DarkShadow44/DistantHorizonsStandalone
