/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.common.wrappers.world;

/**
 * Helpers for MC's int-based dimension IDs ({@code WorldProvider.dimensionId}).
 *
 * <p>Centralising this means {@link DimensionTypeWrapper}, {@link ClientLevelWrapper}
 * and {@link ServerLevelWrapper} all derive dimension names from a single source
 * of truth, in the {@code "{name}:{id}"} format the rest of DH uses.</p>
 */
public final class LegacyDimensionInfo
{
	public static final int OVERWORLD = 0;
	public static final int NETHER = -1;
	public static final int THE_END = 1;



	private LegacyDimensionInfo() {}



	/** Maps a vanilla-style dimension id to the same canonical name modern MC uses. */
	public static String nameOf(int dimensionId)
	{
		switch (dimensionId)
		{
			case OVERWORLD: return "overworld";
			case NETHER:    return "nether";
			case THE_END:   return "the_end";

			default:        return "DIM" + dimensionId;
		}
	}

	/** Canonical {@code "{name}:{id}"} string DH uses as the dimension identifier. */
	public static String fullName(int dimensionId) { return nameOf(dimensionId) + ":" + dimensionId; }



}
