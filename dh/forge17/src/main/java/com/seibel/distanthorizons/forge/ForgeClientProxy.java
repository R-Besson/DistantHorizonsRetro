package com.seibel.distanthorizons.forge;

/**
 * Only here for mods built against the DH 1.7.10 port this fork came from,
 * which look its client proxy up by this name. <br>
 * IE GuideNH unregisters the proxy's event listeners while it builds its preview worlds,
 * without this class it crashes with a NoClassDefFoundError. <br><br>
 *
 * The real proxy is {@link com.seibel.distanthorizons.forge17.ForgeClientProxy}, which extends this.
 */
public abstract class ForgeClientProxy { }
