package com.seibel.distanthorizons.common.wrappers.misc;

import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.common.backports.BlockPos;

public class MutableBlockPosWrapper implements IMutableBlockPosWrapper
{
	public final BlockPos pos;



	//=============//
	// constructor //
	//=============//
	//region

	public MutableBlockPosWrapper()
	{
		this.pos = new BlockPos();
	}

	//endregion



	//===========//
	// overrides //
	//===========//
	//region

	@Override
	public Object getWrappedMcObject() { return this.pos; }

	//endregion



}
