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

package com.seibel.distanthorizons.common.wrappers;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.DhChunkGenerator;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IDhGenericObjectVertexBufferContainer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.ILodContainerUniformBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhGenericRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.IChunkGenerator;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.IRoughGenerator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;


import com.seibel.distanthorizons.common.backports.FakeBlockState;
import com.seibel.distanthorizons.common.backports.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import java.io.IOException;

/**
 * This handles creating abstract wrapper objects.
 */
public class WrapperFactory implements IWrapperFactory
{
	public static final WrapperFactory INSTANCE = new WrapperFactory();



	//=====================//
	// internal properties //
	//=====================//
	//region

	private AbstractDhRenderApiDefinition renderDefinition;
	private AbstractDhRenderApiDefinition getRenderDefinition()
	{
		// delayed get to make sure we don't accidentally set the variable before it's bound
		if (this.renderDefinition != null)
		{
			return this.renderDefinition;
		}

		this.renderDefinition = SingletonInjector.INSTANCE.get(AbstractDhRenderApiDefinition.class);
		return this.renderDefinition;
	}

	//endregion



	//==============//
	// core methods //
	//==============//
	//region

	@Override
	public IChunkGenerator createChunkGenerator(IDhLevel targetLevel)
	{
		if (targetLevel instanceof IDhServerLevel)
		{
			return new DhChunkGenerator((IDhServerLevel) targetLevel);
		}
		else
		{
			throw new IllegalArgumentException("The target level must be a server-side level.");
		}
	}

	@Override
	public IRoughGenerator createRoughGenerator(IDhLevel targetLevel, IChunkGenerator batchChunkGenerator)
	{
		return null;
	}

	@Override
	public IDhApiBiomeWrapper getBiomeWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException
	{
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("levelWrapper must be returned by DH and of type ["+ILevelWrapper.class.getName()+"].");
		}

		return BiomeWrapper.deserialize(resourceLocationString, (ILevelWrapper)levelWrapper);
	}
	@Override
	public IDhApiBlockStateWrapper getDefaultBlockStateWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException
	{
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}

		return BlockStateWrapper.deserialize(resourceLocationString, (ILevelWrapper)levelWrapper);
	}

	@Override
	public IBiomeWrapper deserializeBiomeWrapper(String str, ILevelWrapper levelWrapper) throws IOException { return BiomeWrapper.deserialize(str, levelWrapper); }
	@Override
	public IBiomeWrapper getPlainsBiomeWrapper(ILevelWrapper levelWrapper)
	{
		try
		{
			return BiomeWrapper.deserialize(BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING, levelWrapper);
		}
		catch (IOException e)
		{
			throw new LodUtil.AssertFailureException("Unable to parse plains resource string ["+BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING+"], error:\n " + e.getMessage());
		}
	}

	@Override
	public IBlockStateWrapper deserializeBlockStateWrapper(String str, ILevelWrapper levelWrapper) throws IOException { return BlockStateWrapper.deserialize(str, levelWrapper); }
	@Override
	public IBlockStateWrapper getAirBlockStateWrapper() { return BlockStateWrapper.AIR; }
	@Override
	public IBlockStateWrapper getWaterBlockStateWrapper(ILevelWrapper levelWrapper) { return BlockStateWrapper.getWaterBlockStateWrapper(levelWrapper); }
	@Override
	public ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getRendererIgnoredBlocks(levelWrapper); }
	@Override
	public ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getRendererIgnoredCaveBlocks(levelWrapper); }
	@Override
	public ObjectOpenHashSet<IBlockStateWrapper> getWaterSubsurfaceReplacementBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getWaterSubsurfaceReplacementBlocks(levelWrapper); }
	@Override
	public ObjectOpenHashSet<IBlockStateWrapper> getWaterSurfaceReplacementBlocks(ILevelWrapper levelWrapper) { return BlockStateWrapper.getWaterSurfaceReplacementBlocks(levelWrapper); }

	@Override
	public void resetCachedIgnoredBlocksSets() { BlockStateWrapper.clearCachedIgnoreBlocks(); }


	/**
	 * Note: when this is updated, make sure you also update the documentation in
	 * {@link IDhApiWorldGenerator#generateChunks} and the type list in {@link WrapperFactory#createChunkWrapperErrorMessage}. <br><br>
	 *
	 * For full method documentation please see: {@link IWrapperFactory#createChunkWrapper}
	 *
	 * @see IWrapperFactory#createChunkWrapper
	 */
	public IChunkWrapper createChunkWrapper(Object[] objectArray) throws ClassCastException
	{
		if (objectArray.length == 1
			&& objectArray[0] instanceof IChunkWrapper)
		{
			try
			{
				// this path should only happen when called by Distant Horizons code
				// API implementors should never hit this path
				return (IChunkWrapper) objectArray[0];
			}
			catch (Exception e)
			{
				throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
			}
		}

		else if (objectArray.length != 2)
		{
			throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
		}


		// correct number of parameters from the API

		//=======//
		// chunk //
		//=======//
		//region

		if (!(objectArray[0] instanceof Chunk))
		{
			throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
		}

		Chunk chunk = (Chunk) objectArray[0];

		//endregion



		//=======//
		// level //
		//=======//
		//region

		if (!(objectArray[1] instanceof World))
		{
			throw new ClassCastException(createChunkWrapperErrorMessage(objectArray));
		}

		World level = (World) objectArray[1];

		//endregion



		//===============//
		// level wrapper //
		//===============//
		//region

		ILevelWrapper levelWrapper;
		if (level.isRemote)
		{
			levelWrapper = ClientLevelWrapper.getWrapper((WorldClient)level);
		}
		else
		{
			levelWrapper = ServerLevelWrapper.getWrapper((WorldServer)level);
		}

		//endregion



		return new ChunkWrapper(chunk, levelWrapper);
	}
	/**
	 * Note: when this is updated,
	 * make sure you also update the documentation in {@link IDhApiWorldGenerator#generateChunks}.
	 */
	private static String createChunkWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames = new String[]
			{
				Chunk.class.getName(),
				"[WorldClient] or [WorldServer]" // Classes are not referenced by names to avoid exception when one of them is missing
			};

		return createWrapperErrorMessage("Chunk wrapper", expectedClassNames, objectArray);
	}


	@Override public IVertexBufferWrapper createVboWrapper(String name) { return this.getRenderDefinition().createVboWrapper(name); }
	@Override public ILodContainerUniformBufferWrapper createLodContainerUniformWrapper() { return this.getRenderDefinition().createLodContainerUniformWrapper(); }
	@Override public IDhGenericObjectVertexBufferContainer createGenericObjectVboContainer() { return this.getRenderDefinition().createGenericVboContainer(); }
	@Override public IDhGenericRenderer createGenericRenderer() { return this.getRenderDefinition().createGenericRenderer(); }

	//endregion



	//=============//
	// api methods //
	//=============//
	//region

	// documentation should be in the API interface

	public IDhApiBiomeWrapper getBiomeWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper)
	{
		// confirm the API level wrapper is also a Core wrapper
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}
		ILevelWrapper coreLevelWrapper = (ILevelWrapper) levelWrapper;



		if (objectArray.length != 1)
		{
			throw new ClassCastException(createBiomeWrapperErrorMessage(objectArray));
		}

		if (!(objectArray[0] instanceof BiomeGenBase))
		{
			throw new ClassCastException(createBiomeWrapperErrorMessage(objectArray));
		}

		BiomeGenBase biome = (BiomeGenBase) objectArray[0];
		return BiomeWrapper.getBiomeWrapper(biome, coreLevelWrapper);
	}
	/**
	 * Note: when this is updated,
	 * make sure you also update the documentation in {@link IDhApiWrapperFactory#getBiomeWrapper}.
	 */
	private static String createBiomeWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames = new String[] { BiomeGenBase.class.getName() };

		return createWrapperErrorMessage("Biome wrapper", expectedClassNames, objectArray);
	}

	public IDhApiBlockStateWrapper getBlockStateWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper)
	{
		// confirm the API level wrapper is also a Core wrapper
		if (!(levelWrapper instanceof ILevelWrapper))
		{
			throw new ClassCastException("Invalid ["+IDhApiLevelWrapper.class.getSimpleName()+"] value given. Level wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ILevelWrapper.class.getName()+"].");
		}
		ILevelWrapper coreLevelWrapper = (ILevelWrapper) levelWrapper;



		if (objectArray.length != 3
			&& objectArray.length != 1)
		{
			throw new ClassCastException(createBlockStateWrapperErrorMessage(objectArray));
		}

		boolean blockClassCorrect =
			(
				(objectArray[0] instanceof Block)
				&& (objectArray[1] instanceof Integer)
				&& (objectArray[2] instanceof Integer)
			)
			||
			(objectArray[0] instanceof IBlockState)
		;
		if (!blockClassCorrect)
		{
			throw new ClassCastException(createBlockStateWrapperErrorMessage(objectArray));
		}

		IBlockState fakeBlockState;
		if (objectArray[0] instanceof IBlockState)
		{
			fakeBlockState = (IBlockState)objectArray[0];
		}
		else
		{
			fakeBlockState = new FakeBlockState(
				(Block) objectArray[0], // block
				(Integer) objectArray[1], // meta
				(Integer) objectArray[2] // blockId
			);
		}

		return BlockStateWrapper.fromBlockState(fakeBlockState, coreLevelWrapper);
	}
	/**
	 * Note: when this is updated,
	 * make sure you also update the documentation in {@link IDhApiWrapperFactory#getBlockStateWrapper}.
	 */
	private static String createBlockStateWrapperErrorMessage(Object[] objectArray)
	{
		String[] expectedClassNames = new String[] { IBlockState.class.getName() };

		return createWrapperErrorMessage("BlockState wrapper", expectedClassNames, objectArray);
	}

	//endregion



	//================//
	// helper methods //
	//================//
	//region

	private static String createWrapperErrorMessage(String wrapperName, String[] expectedClassNames, Object[] objectArray)
	{
		// error header
		StringBuilder message = new StringBuilder(
			wrapperName + " creation failed. \n" +
				"Expected object array parameters: \n");


		// expected parameters
		for (String expectedClassName : expectedClassNames)
		{
			message.append("[").append(expectedClassName).append("], \n");
		}


		// given parameters
		if (objectArray.length != 0)
		{
			message.append("Given parameters: ");
			for (Object obj : objectArray)
			{
				String objClassName = (obj != null) ? obj.getClass().getName() : "NULL";
				message.append("[").append(objClassName).append("], ");
			}
		}
		else
		{
			message.append(" No parameters given.");
		}


		return message.toString();
	}

	//endregion



}
