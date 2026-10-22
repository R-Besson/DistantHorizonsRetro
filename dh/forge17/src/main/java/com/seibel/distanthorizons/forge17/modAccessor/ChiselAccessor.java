package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.wrappers.modAccessor.IChiselCommonAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.jetbrains.annotations.Nullable;

import team.chisel.ctmlib.CTM;
import team.chisel.ctmlib.ICTMBlock;
import team.chisel.ctmlib.ISubmapManager;
import team.chisel.ctmlib.RenderBlocksCTM;
import team.chisel.ctmlib.TextureSubmap;

/**
 * Picks the icons the same way Chisel's CTMRenderer and RenderBlocksCTM draw a block in the world.
 */
public class ChiselAccessor implements IChiselCommonAccessor
{
	/** connected texture pieces 16 and up are the quarters of the block's plain texture */
	private static final int FIRST_SMALL_SUBMAP_INDEX = 16;

	/** handed to the managers that draw with MC's renderer, which only set its bounds */
	private static final ThreadLocal<RenderBlocks> PLACEHOLDER_RENDER_BLOCKS = ThreadLocal.withInitial(RenderBlocks::new);
	/** CTM keeps its connections between calls, so each thread needs its own */
	private static final ThreadLocal<CTM> CTM_REF = ThreadLocal.withInitial(CTM::getInstance);



	@Override
	public String getModName() { return "Chisel"; }

	@Override
	public boolean hasPositionedTextures(Block block) { return block instanceof ICTMBlock; }

	@Override
	@Nullable
	public IIcon[] getFaceIcons(IBlockAccess world, Block block, int x, int y, int z, int side)
	{
		int meta = world.getBlockMetadata(x, y, z);
		ISubmapManager manager = ((ICTMBlock<?>) block).getManager(world, x, y, z, meta);
		RenderBlocks renderContext = (manager != null) ? manager.createRenderContext(PLACEHOLDER_RENDER_BLOCKS.get(), block, world) : null;
		if (!(renderContext instanceof RenderBlocksCTM)
			|| ((RenderBlocksCTM) renderContext).submap == null)
		{
			// drawn like a normal block
			return new IIcon[] { block.getIcon(world, x, y, z, side) };
		}
		RenderBlocksCTM ctmContext = (RenderBlocksCTM) renderContext;

		CTM ctm = CTM_REF.get();
		ctm.disableObscuredFaceCheck = ctmContext.ctm.disableObscuredFaceCheck;
		int[] submapIndices = ctm.getSubmapIndices(world, x, y, z, side);

		IIcon[] quarters = new IIcon[submapIndices.length];
		for (int i = 0; i < submapIndices.length; i++)
		{
			int index = submapIndices[i];
			TextureSubmap submap = ctmContext.submap;
			if (index >= FIRST_SMALL_SUBMAP_INDEX)
			{
				index -= FIRST_SMALL_SUBMAP_INDEX;
				submap = ctmContext.submapSmall;
			}

			quarters[i] = (submap != null)
				? submap.getSubIcon(index % submap.getWidth(), index / submap.getHeight())
				: block.getIcon(side, meta);
		}
		return quarters;
	}

}
