# Distant Horizons Retro

A 1.7.10 / GTNH port of [Distant Horizons][dh], focused on performance, stability and new features.

Distant Horizons Retro is a fork of [DistantHorizonsStandalone][standalone] by DarkShadow44, which
brought Distant Horizons to 1.7.10. DistantHorizonsStandalone tracks upstream Distant Horizons
updates. Distant Horizons Retro is **developed independently**. It doesn't sync with either project
and has its own roadmap:

- **Optimizations**: faster world generation and LOD building
- **Bug fixes**: LOD holes, crashes, mod compatibility and server stability
- **New features**: planned, specific to this fork

Please report issues with this mod [here](https://github.com/R-Besson/DistantHorizonsRetro/issues),
not to the Distant Horizons or DistantHorizonsStandalone teams.

# What is Distant Horizons?

Distant Horizons is a mod which implements a [Level of Detail](https://en.wikipedia.org/wiki/Level_of_detail_(computer_graphics)) system to Minecraft.\
This allows for far greater render distances without harming performance by gradually lowering the quality of distant terrain.

Below is a video demonstrating the system:

<a href="https://youtu.be/SxQdbtjGEsc" target="_blank">![Distant Horizons - Alpha 2.0](https://i.ytimg.com/vi/SxQdbtjGEsc/hqdefault.jpg)</a>

# Installation

Download the latest version from [Releases](https://github.com/R-Besson/DistantHorizonsRetro/releases) and put it into your `mods` folder.

> Remove any other Distant Horizons / DistantHorizonsStandalone jar first. They share the same mod ID and can't be installed together.

Make sure the latest versions of each dependency are installed:

- [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify): 3.0.15 or higher
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)
- [UniMixins](https://github.com/LegacyModdingMC/UniMixins)

## Shaders

Shaders are supported with [Angelica](https://github.com/GTNewHorizons/Angelica) 2.1.12 or higher.
Tested with [Complementary 5.7.1](https://modrinth.com/shader/complementary-reimagined/version/r5.7.1) and [Euphoria Patches 1.8.6](https://modrinth.com/mod/euphoria-patches/version/1.8.6-r5.7.1-forge1.7.10).

## GTNH

Older GTNH packs ship versions of Angelica, lwjgl3ify and GTNHLib that are too old. To upgrade them in Prism Launcher:

1. Download the latest [Angelica](https://github.com/GTNewHorizons/Angelica/releases), [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify/releases) (both the `.jar` and the `-multimc.zip`) and [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases).
2. Delete the old `angelica-*.jar`, `lwjgl3ify-*.jar` and `gtnhlib-*.jar` from your `mods` folder.
3. Add the new jars and the Distant Horizons Retro jar to your `mods` folder.
4. Unzip `lwjgl3ify-*-multimc.zip` into your Prism Launcher instance. It should overwrite your `mmc-pack.json`.

# Known Issues

- Memory usage might creep up over time and crash the server
- Server side is not fully stable, use with caution
- Sometimes LODs don't update properly. Change the render distance and back to fix it

# Development

## Structure

```
DistantHorizonsRetro/
├── settings.gradle      # includeBuild('dh'), pins -PmcVer=1.7.10
├── build.gradle         # runs the mod build, copies jars to build/libs
├── .github/workflows/   # GTNH Actions
└── dh/                  # the mod
    ├── common/          # code shared between loaders
    ├── forge17/         # 1.7.10 Forge loader code
    └── coreSubProjects/ # core LOD engine
```

The code in `dh/` was originally imported from Distant Horizons, but it is now maintained here.
It is not synced from upstream.

## Build

```bash
./gradlew assemble       # jars land in build/libs
./gradlew :dh:forge17:runClient   # dev client (Java 25 + lwjgl3ify), -PdevJava8=true for Java 8
```

## Updates

Distant Horizons' built-in auto-updater has been removed, since it downloaded from the official
Distant Horizons Modrinth/GitLab and would have replaced this mod with upstream Distant Horizons.
Get updates from this repo's [Releases](https://github.com/R-Besson/DistantHorizonsRetro/releases).

# Credits

- [Distant Horizons][dh] by James Seibel and the Distant Horizons team
- [DistantHorizonsStandalone][standalone] by DarkShadow44, the original 1.7.10 port

# License

Distant Horizons Retro is licensed under the LGPL, like Distant Horizons. See the `LICENSE*` files in `dh/`.

[dh]: https://gitlab.com/distant-horizons-team/distant-horizons
[standalone]: https://github.com/DarkShadow44/DistantHorizonsStandalone
