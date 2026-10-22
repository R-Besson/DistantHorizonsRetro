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

package com.seibel.distanthorizons.core.wrapperInterfaces.block;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the baked {@link BlockFaceTexture} for a given block state and face,
 * used when building textured LODs.
 */
public interface IBlockStateFaceTextureProvider extends IBindable
{
	/**
	 * May be slow the first time a given block state is requested
	 * since the texture needs to be baked.
	 *
	 * @param direction which face of the block to get,
	 *                  only cardinal directions and up/down are valid
	 * @return null if no texture could be baked for this block state
	 */
	@Nullable
	BlockFaceTexture getFaceTexture(IBlockStateWrapper blockState, EDhDirection direction);

	/**
	 * Some blocks look different depending on where they are
	 * (IE Chisel's connected textures and position based variants). <br>
	 * The returned object describes what each face looks like at this position
	 * and is used as a key, so equal appearances share a texture set.
	 *
	 * @param topY the world Y of the LOD's highest block
	 * @return null if the block looks the same everywhere, {@link #getFaceTexture} is used then
	 */
	@Nullable
	Object getPositionedAppearance(
		IBlockStateWrapper blockState, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ);

	/**
	 * @param appearance from {@link #getPositionedAppearance}
	 * @return null if no texture could be baked for this face
	 */
	@Nullable
	BlockFaceTexture getPositionedFaceTexture(Object appearance, EDhDirection direction);

	/**
	 * A wall of a block can look different on each row (IE the top and bottom of a connected texture,
	 * or variants that change every block), the LOD's sides are split into rows then.
	 *
	 * @param appearance from {@link #getPositionedAppearance}
	 * @return how many rows, counted down from the top block, have their own side textures.
	 *         0 if every row uses {@link #getPositionedFaceTexture}
	 */
	int getPositionedSideRowCount(Object appearance);

	/**
	 * @param direction one of the 4 sides
	 * @param row counted down from the top block
	 * @return null if no texture could be baked for this face
	 */
	@Nullable
	BlockFaceTexture getPositionedSideRowTexture(Object appearance, EDhDirection direction, int row);

	/** Should be called whenever MC's textures change, IE when resource packs are swapped. */
	void clear();

}
