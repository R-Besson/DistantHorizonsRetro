package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

/**
 * Currently this wrapper is just used to prevent 
 * accidentally passing in the wrong object to
 * {@link #getBlockState(int, int, int, IBlockStateWrapper)}
 */
public interface IMutableBlockPosWrapper extends IDhApiUnsafeWrapper
{
	
	
}
