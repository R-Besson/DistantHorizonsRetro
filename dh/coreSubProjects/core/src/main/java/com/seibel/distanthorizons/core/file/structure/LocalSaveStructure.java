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

package com.seibel.distanthorizons.core.file.structure;

import com.seibel.distanthorizons.api.interfaces.override.levelHandling.IDhApiSaveStructure;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.coreapi.util.StringUtil;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Designed for {@link EWorldEnvironment#CLIENT_SERVER} & {@link EWorldEnvironment#SERVER_ONLY} environments.
 */
public class LocalSaveStructure implements ISaveStructure
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final IMinecraftSharedWrapper MC_SHARED = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);

	private final ConcurrentHashMap<ILevelWrapper, File> levelWrapperToFileMap = new ConcurrentHashMap<>();

	public LocalSaveStructure() { }

	//================//
	// folder methods //
	//================//

	@Override
	public File getSaveFolder(ILevelWrapper levelWrapper)
	{
		return this.levelWrapperToFileMap.computeIfAbsent(levelWrapper, (newLevelWrapper) ->
		{
			IServerLevelWrapper serverLevelWrapper = (IServerLevelWrapper) levelWrapper;
			File saveFolder = serverLevelWrapper.getMcSaveFolder();

			// a folder outside the world keeps the (often very large) LOD databases out of world backups
			String folderOverride = Config.Common.LodBuilding.databaseFolderOverride.get();
			if (folderOverride != null && !folderOverride.trim().isEmpty())
			{
				File overrideRoot = new File(folderOverride.trim());
				if (!overrideRoot.isAbsolute())
				{
					overrideRoot = new File(MC_SHARED.getInstallationDirectory(), folderOverride.trim());
				}

				saveFolder = new File(new File(overrideRoot, cleanFolderName(serverLevelWrapper.getWorldFolderName())), cleanFolderName(levelWrapper.getDhIdentifier()));
				LOGGER.info("Using the configured database folder [" + saveFolder.getPath() + "] for [" + levelWrapper.getDhIdentifier() + "].");
			}

			// Allow API users to override the save folder
			IDhApiSaveStructure saveStructureOverride = OverrideInjector.INSTANCE.get(IDhApiSaveStructure.class);
			if (saveStructureOverride != null)
			{
				File overrideFile = saveStructureOverride.overrideFilePath(saveFolder, levelWrapper);
				if (overrideFile != null)
				{
					LOGGER.info("Save folder overridden from [" + saveFolder.getPath() + "] -> [" + overrideFile.getPath() + "].");
					saveFolder = overrideFile;
				}
			}

			return saveFolder;
		});
	}

	@Override
	public File getPre23SaveFolder(ILevelWrapper levelWrapper) { return this.getSaveFolder(levelWrapper); }

	@Override
	public void removeLevel(ILevelWrapper levelWrapper) { this.levelWrapperToFileMap.remove(levelWrapper); }

	/** dimension identifiers can contain characters (IE ':') that aren't allowed in folder names on every OS */
	private static String cleanFolderName(String name) { return name.replaceAll("[^A-Za-z0-9._ -]", "_"); }

	//==================//
	// override methods //
	//==================//

	@Override
	public void close() throws Exception { }

	@Override
	public String toString()
	{ return "[" + this.getClass().getSimpleName() + "@(" + StringUtil.join(";", this.levelWrapperToFileMap.values()) + ")]"; }
}
