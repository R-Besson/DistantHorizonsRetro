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

package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateFaceTextureProvider;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes {@link ClientBlockStateTextureCache} to DH core.
 *
 * @see IBlockStateFaceTextureProvider
 */
public class BlockStateTextureProvider implements IBlockStateFaceTextureProvider
{
	public static final BlockStateTextureProvider INSTANCE = new BlockStateTextureProvider();
	
	
	
	@Nullable
	@Override
	public BlockFaceTexture getFaceTexture(IBlockStateWrapper blockState, EDhDirection direction)
	{
		if (!(blockState instanceof BlockStateWrapper))
		{
			// shouldn't happen, but just in case
			throw new UnsupportedOperationException("blockState must be a ["+BlockStateWrapper.class.getSimpleName()+"]");
		}
		return ClientBlockStateTextureCache.getFaceTexture((BlockStateWrapper) blockState, direction);
	}

	@Nullable
	@Override
	public Object getPositionedAppearance(
		IBlockStateWrapper blockState, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ)
	{
		if (!(blockState instanceof BlockStateWrapper))
		{
			return null;
		}
		return ClientBlockStateTextureCache.getPositionedAppearance((BlockStateWrapper) blockState, levelWrapper, fullDataSource, blockX, topY, blockZ);
	}

	@Nullable
	@Override
	public BlockFaceTexture getPositionedFaceTexture(Object appearance, EDhDirection direction)
	{
		return ClientBlockStateTextureCache.getPositionedFaceTexture(appearance, direction);
	}

	@Override
	public int getPositionedSideRowCount(Object appearance) { return ClientBlockStateTextureCache.getPositionedSideRowCount(appearance); }

	@Nullable
	@Override
	public BlockFaceTexture getPositionedSideRowTexture(Object appearance, EDhDirection direction, int row)
	{
		return ClientBlockStateTextureCache.getPositionedSideRowTexture(appearance, direction, row);
	}

	@Override
	public void clear() 
	{
		ClientBlockStateTextureCache.clearCache();
	}
	
}
