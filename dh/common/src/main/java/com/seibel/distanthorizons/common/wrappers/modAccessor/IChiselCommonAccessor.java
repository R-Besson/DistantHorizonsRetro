package com.seibel.distanthorizons.common.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Chisel's blocks look different depending on where they are:
 * connected textures depend on the neighbors, variants on the position.
 */
public interface IChiselCommonAccessor extends IModAccessor
{
	/** @return true if the block's textures depend on where it is */
	boolean hasPositionedTextures(Block block);

	/**
	 * @param world has to return the neighbors' blocks and metas
	 * @return the icons MC draws this face with, either one for the whole face
	 *         or four quarters (bottom left, bottom right, top right, top left).
	 *         Null if they couldn't be found.
	 */
	@Nullable
	IIcon[] getFaceIcons(IBlockAccess world, Block block, int x, int y, int z, int side);

}
