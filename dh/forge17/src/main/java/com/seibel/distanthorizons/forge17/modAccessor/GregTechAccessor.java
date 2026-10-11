package com.seibel.distanthorizons.forge17.modAccessor;

import java.lang.reflect.Field;

import com.seibel.distanthorizons.common.backports.IBlockState;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IGregTechCommonAccessor;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import gregtech.api.interfaces.IBlockWithTextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.common.render.GTRenderedTexture;
import org.jetbrains.annotations.Nullable;

public class GregTechAccessor implements IGregTechCommonAccessor
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	/** 
	 * Looked up on {@link GTRenderedTexture} directly so subclasses still resolve, 
	 * and cached since {@link #resolveIcon} is called for every block. <br>
	 * Null if the field couldn't be found.
	 */
	@Nullable
	private static final Field ICON_CONTAINER_FIELD = getIconContainerField();
	
	
	
	@Override 
	public String getModName() { return "GregTech"; }
	
	
	
	@Override
	@Nullable
    public IIcon resolveIcon(IBlockState blockState)
    {
	    Block block = blockState.getBlock();
		int meta = blockState.getMeta();
		
        if (!(block instanceof IBlockWithTextures))
		{
			return null;
		}
	    IBlockWithTextures blockWithTextures = (IBlockWithTextures) block;
		
        ITexture[][] textures = blockWithTextures.getTextures(meta);
        if (textures == null 
	        || textures.length == 0
	        || textures[0] == null
	        || textures[0].length == 0) 
		{
			return null;
		}
		
        ITexture firstTexture = textures[0][0];
        if (!(firstTexture instanceof GTRenderedTexture)
	        || ICON_CONTAINER_FIELD == null) 
		{
           return null;
        }
	    
	    try
	    {
		    IIconContainer container = (IIconContainer) ICON_CONTAINER_FIELD.get(firstTexture);
		    if (container != null)
		    {
			    return container.getIcon();
		    }
	    }
	    catch (IllegalAccessException e)
	    {
		    LOGGER.error("Unable to read GregTech icon container: [" + e.getMessage() + "].", e);
	    }
		
        return null;
    }
	private static Field getIconContainerField()
	{
		try
		{
			Field field = GTRenderedTexture.class.getDeclaredField("mIconContainer");
			field.setAccessible(true);
			return field;
		}
		catch (NoSuchFieldException | SecurityException e)
		{
			LOGGER.error("Unable to find GregTech's icon container field, GregTech block textures won't be resolved: [" + e.getMessage() + "].", e);
			return null;
		}
	}
	
	
	
}
