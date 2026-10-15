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

package com.seibel.distanthorizons.common.wrappers.world;

import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.world.WorldServer;

import net.minecraft.world.World;

import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.Nullable;

public class ServerLevelWrapper implements IServerLevelWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	/**
	 * weak references are to prevent rare issues
	 * where, upon world closure, some levels aren't shutdown/removed properly
	 */
	private static final Map<WorldServer, WeakReference<ServerLevelWrapper>>
		LEVEL_WRAPPER_REF_BY_SERVER_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());


	private final WorldServer level;
	private IDhLevel dhLevel;

	/**
	 * this name is cached to prevent issues during shutdown where
	 * the server variables needed may no longer be available.
	 */
	private final String keyedLevelDimensionName;



	//==============//
	// constructors //
	//==============//
	//region

	public static ServerLevelWrapper getWrapper(WorldServer level)
	{
		return LEVEL_WRAPPER_REF_BY_SERVER_LEVEL.compute(level, (newLevel, levelRef) ->
		{
			if (levelRef != null)
			{
				ServerLevelWrapper oldLevelWrapper = levelRef.get();
				if (oldLevelWrapper != null)
				{
					return levelRef;
				}
			}

			return new WeakReference<>(new ServerLevelWrapper(newLevel));
		}).get();
	}

	public ServerLevelWrapper(WorldServer level)
	{
		this.level = level;
		this.keyedLevelDimensionName = this.createKeyedLevelDimensionName();
	}

	//endregion



	//==================//
	// instance methods //
	//==================//
	//region

	@Override
	public File getMcSaveFolder()
	{
		return new File(this.level.getChunkSaveLocation(), "data");
	}

	@Override
	public String getKeyedLevelDimensionName() { return this.keyedLevelDimensionName; }

	private String createKeyedLevelDimensionName()
	{
		String dimensionName = this.getDhIdentifier();

		if (Config.Server.sendLevelKeys.get())
		{
			String levelKeyPrefix = Config.Server.levelKeyPrefix.get();

			if (SharedApi.getEnvironment() == EWorldEnvironment.CLIENT_SERVER)
			{
				String cleanWorldFolderName = this.getWorldFolderName()
						.replaceAll("[^" + LevelInitMessage.ALLOWED_CHARS_REGEX + " ]", "")
						.replaceAll(" ", "_");

				levelKeyPrefix += (!levelKeyPrefix.isEmpty() ? "_" : "") + cleanWorldFolderName
						+ "_" + this.getHashedSeedEncoded();
			}

			if (levelKeyPrefix.isEmpty())
			{
				levelKeyPrefix = this.getHashedSeedEncoded();
			}

			String mainPart = "@" + dimensionName;

			return levelKeyPrefix.substring(0, Math.min(
					LevelInitMessage.MAX_LENGTH - mainPart.length(),
					levelKeyPrefix.length()
			)) + mainPart;
		}

		return dimensionName;
	}
	private String getWorldFolderName()
	{
		try
		{
			// We use the overworld since it's the only dimension that is stored in the server root folder

			return net.minecraft.server.MinecraftServer.getServer().worldServers[0]
				.getSaveHandler().getWorldDirectory().getParentFile().getName();
		}
		catch (Exception e)
		{
			LOGGER.warn("Unable to get world folder name. LODs may not load or save correctly. Error: ["+e.getMessage()+"].", e);
			return "unknown_world";
		}
	}


	@Override
	public DimensionTypeWrapper getDimensionType()
	{
		// Casting to World is necessary to fix a reobfuscation issue.
		// otherwise it sometimes fails to obfuscate `provider` properly.
		return DimensionTypeWrapper.getDimensionTypeWrapper(((World) this.level).provider.dimensionId);
	}

	@Override
	public String getDimensionName()
	{
		// Casting to World is necessary to fix a reobfuscation issue.
		// otherwise it sometimes fails to obfuscate `provider` properly.
		return LegacyDimensionInfo.fullName(((World) this.level).provider.dimensionId);
	}

	@Override
	public long getHashedSeed()
	{
		return this.level.getSeed();
	}

	@Override
	public String getDhIdentifier() { return this.getDimensionName(); }

	@Override
	public EDhApiLevelType getLevelType() { return EDhApiLevelType.SERVER_LEVEL; }

	public WorldServer getLevel() { return this.level; }

	@Override
	public boolean hasCeiling()
	{
		// Casting to World is necessary to fix a reobfuscation issue.
		// otherwise it sometimes fails to obfuscate `provider` properly.
		return ((World) this.level).provider
			// WorldProvider has no isNether(), isHellWorld is its equivalent
			.isHellWorld;
	}

	@Override
	public boolean hasSkyLight()
	{
		// Casting to World is necessary to fix a reobfuscation issue.
		// otherwise it sometimes fails to obfuscate `provider` properly.
		return !((World) this.level).provider
			// hasNoSky is true when the dimension lacks skylight
			.hasNoSky;
	}

	@Override
	public int getMaxHeight() { return this.level.getHeight(); }

	@Override
	public int getMinHeight()
	{
		return 0;
	}

	@Override
	public WorldServer getWrappedMcObject() { return this.level; }

	@Override
	public void onUnload() { LEVEL_WRAPPER_REF_BY_SERVER_LEVEL.remove(this.level); }


	@Override
	public void setDhLevel(IDhLevel dhLevel) { this.dhLevel = dhLevel; }
	@Override
	@Nullable
	public IDhLevel getDhLevel() { return this.dhLevel; }

	@Override
	public IDhApiCustomRenderRegister getRenderRegister()
	{
		if (this.dhLevel == null)
		{
			return null;
		}

		return this.dhLevel.getGenericRenderer();
	}

	@Override
	public File getDhSaveFolder()
	{
		if (this.dhLevel == null)
		{
			return null;
		}

		return this.dhLevel.getSaveStructure().getSaveFolder(this);
	}

	@Override
	public DhApiResult<Color> getBlockColorPreApi(
		IDhApiBlockStateWrapper blockStateWrapper,
		IDhApiBiomeWrapper biomeWrapper,
		int blockWorldPosX, int blockWorldPosY, int blockWorldPosZ,
		IDhApiFullDataSource dataSource)
	{ return DhApiResult.createFail("["+ServerLevelWrapper.class.getSimpleName()+"]'s cannot get block colors, please use a ["+ClientLevelWrapper.class.getSimpleName()+"] instead."); }

	//endregion



	//================//
	// base overrides //
	//================//
	//region

	@Override
	public String toString() { return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}"; }

	//endregion



}
