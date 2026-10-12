![Distant Horizons Retro](https://raw.githubusercontent.com/R-Besson/DistantHorizonsRetro/refs/heads/main/.github/assets/logo.png)

**A 1.7.10 / GTNH port of [Distant Horizons](https://gitlab.com/distant-horizons-team/distant-horizons), focused on performance, stability and new features.**

Distant Horizons adds a [Level of Detail](https://en.wikipedia.org/wiki/Level_of_detail_%28computer_graphics%29) system to Minecraft. It lets you see far beyond your normal render distance without the usual performance cost, by gradually simplifying distant terrain.

![Distant Horizons Retro preview](https://raw.githubusercontent.com/R-Besson/DistantHorizonsRetro/refs/heads/main/.github/assets/dhpreview.jpg)

## Why Retro?

Distant Horizons Retro is a fork of [DistantHorizonsStandalone](https://github.com/DarkShadow44/DistantHorizonsStandalone) by DarkShadow44, which first brought Distant Horizons to 1.7.10. Retro is **developed independently** with its own roadmap:

- **Faster** world generation and LOD building
- **More stable**: fixes for LOD holes, generation stalls, crashes and mod compatibility
- **Server friendly**: world generation backs off when the server's TPS drops
- **New features** made for 1.7.10 and GTNH

## Requirements

Make sure you have the **latest** version of each:

- [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) 3.0.15 or higher
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib)
- [UniMixins](https://github.com/LegacyModdingMC/UniMixins)

> **Remove any other Distant Horizons jar before installing.**

## Shaders

Shaders work with [Angelica](https://github.com/GTNewHorizons/Angelica) 2.1.12 or higher. Tested with [Complementary Shaders](https://modrinth.com/shader/complementary-reimagined) and [Euphoria Patches](https://modrinth.com/mod/euphoria-patches).

## GTNH

Older GTNH packs ship versions of Angelica, lwjgl3ify and GTNHLib that are too old. To upgrade them in Prism Launcher:

1. Download the latest [Angelica](https://github.com/GTNewHorizons/Angelica/releases), [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify/releases) (both the `.jar` and the `-multimc.zip`) and [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases).
2. Delete the old `angelica-*.jar`, `lwjgl3ify-*.jar` and `gtnhlib-*.jar` from your `mods` folder.
3. Add the new jars and the Distant Horizons Retro jar to your `mods` folder.
4. Unzip `lwjgl3ify-*-multimc.zip` into your Prism Launcher instance. It should overwrite your `mmc-pack.json`.

## Bugs and feedback

Please report issues on [GitHub](https://github.com/R-Besson/DistantHorizonsRetro/issues), **not** to Distant Horizons or DistantHorizonsStandalone.

## Credits

- [Distant Horizons](https://gitlab.com/distant-horizons-team/distant-horizons) by James Seibel and the Distant Horizons team
- [DistantHorizonsStandalone](https://github.com/DarkShadow44/DistantHorizonsStandalone) by DarkShadow44, the original 1.7.10 port
- Logo based on the Distant Horizons logo by Pankakes, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
