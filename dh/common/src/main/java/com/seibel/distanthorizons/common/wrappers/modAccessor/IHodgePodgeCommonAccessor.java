/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IHodgePodgeAccessor;
import net.minecraft.world.World;

public interface IHodgePodgeCommonAccessor extends IHodgePodgeAccessor
{
	void preventChunkSimulation(World world, int x, int z);
	void allowChunkSimulation(World world, int x, int z);

	/**
	 * Hodgepodge's throttled chunk generation blocks generating chunks during the server tick,
	 * outside of its own scheduler. Missing chunks are then replaced with an empty chunk,
	 * so anything world gen places in them is lost (IE the half of a big tree reaching into the next chunk). <br>
	 * DH's world gen runs during the tick, so it has to allow it while it generates.
	 *
	 * @return true if chunk generation was blocked, pass it to {@link #restoreChunkGeneration}
	 */
	boolean allowChunkGeneration();
	/** @param wasBlocked the value {@link #allowChunkGeneration} returned */
	void restoreChunkGeneration(boolean wasBlocked);

	/**
	 * Runs the given population like Hodgepodge's own,
	 * chunks it generates on the way are left for Hodgepodge to populate instead of cascading further.
	 */
	void runAsPopulation(World world, Runnable populate);
}
