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

package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;

public class RenderBlockCacheCsvHandler extends AbstractDelayedConfigEventHandler
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static RenderBlockCacheCsvHandler INSTANCE = new RenderBlockCacheCsvHandler();
	
	
	
	//=============//
 	// constructor //
	//=============//
	//region
	
	/** private since we only ever need one handler at a time */
	private RenderBlockCacheCsvHandler() { super(AbstractDelayedConfigEventHandler.DEFAULT_TIMEOUT_IN_MS); }
	
	//endregion
	
	
	
	//=================//
	// config handling //
	//=================//
	//region
	
	@Override
	public void onConfigTimeout()
	{
		IWrapperFactory wrapperFactory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
		if (wrapperFactory != null)
		{
			wrapperFactory.resetCachedIgnoredBlocksSets();
			DhApi.Delayed.renderProxy.clearRenderDataCache();
		}
	}
	
	//endregion
	
	
	
}
