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

package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.api.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.api.converters.RenderModeEnabledConverter;

public class DhApiGraphicsConfig implements IDhApiGraphicsConfig
{
	public static DhApiGraphicsConfig INSTANCE = new DhApiGraphicsConfig();
	
	private DhApiGraphicsConfig() { }
	
	
	
	//==============//
	// inner layers //
	//==============//
	
	@Override
	public IDhApiFogConfig fog() { return DhApiFogConfig.INSTANCE; }
	@Override
	public IDhApiAmbientOcclusionConfig ambientOcclusion() { return DhApiAmbientOcclusionConfig.INSTANCE; }
	@Override
	public IDhApiNoiseTextureConfig noiseTexture() { return DhApiNoiseTextureConfig.INSTANCE; }
	@Override
	public IDhApiTextureConfig texture() { return DhApiTextureConfig.INSTANCE; }
	@Override
	public IDhApiGenericRenderingConfig genericRendering() { return DhApiGenericRenderingConfig.INSTANCE; }
	
	
	
	//========================//
	// basic graphic settings //
	//========================//
	
	@Override
	public IDhApiConfigValue<Integer> chunkRenderDistance()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius); }
	
	@Override
	public IDhApiConfigValue<Boolean> renderingEnabled()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Debugging.rendererMode, new RenderModeEnabledConverter()); }
	
	@Override
	public IDhApiConfigValue<EDhApiRendererMode> renderingMode()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Debugging.rendererMode); }
	
	
	
	//==================//
	// graphic settings //
	//==================//
	
	@Override
	public IDhApiConfigValue<EDhApiMaxHorizontalResolution> maxHorizontalResolution()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution); }
	
	@Override
	public IDhApiConfigValue<EDhApiVerticalQuality> verticalQuality()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.verticalQuality); }
	
	@Override
	public IDhApiConfigValue<EDhApiHorizontalQuality> horizontalQuality()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.horizontalQuality); }
	
	@Override
	public IDhApiConfigValue<Boolean> useCameraPositionForQualityDropOff()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.useCameraPositionForQualityDropOff); }
	
	@Override
	public IDhApiConfigValue<EDhApiTransparency> transparency()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.transparency); }
	
	@Override
	public IDhApiConfigValue<EDhApiBlocksToAvoid> blocksToAvoid()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.blocksToIgnore); }
	
	@Override
	public IDhApiConfigValue<Boolean> tintWithAvoidedBlocks()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks); }
	
	@Override
	public IDhApiConfigValue<Integer> getBiomeBlending()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.lodBiomeBlending); }
	
	
	
	//===========================//
	// advanced graphic settings //
	//===========================//

	@Override
	public IDhApiConfigValue<Float> overdrawPreventionRadius()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.overdrawPrevention); }
	
	@Override
	public IDhApiConfigValue<Float> brightnessMultiplier()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.brightnessMultiplier); }
	
	@Override
	public IDhApiConfigValue<Float> saturationMultiplier()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.saturationMultiplier); }
	
	@Override
	public IDhApiConfigValue<Boolean> caveCullingEnabled()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.enableCaveCulling); }
	
	@Override
	public IDhApiConfigValue<Integer> caveCullingHeight()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.caveCullingHeight); }
	
	@Override
	public IDhApiConfigValue<Integer> earthCurvatureRatio()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Experimental.earthCurveRatio); }
	
	@Override
	public IDhApiConfigValue<Boolean> lodOnlyMode()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Debugging.lodOnlyMode); }
	
	@Override
	public IDhApiConfigValue<EDhApiLodShading> lodShading()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Quality.lodShading); }
	
	@Override
	public IDhApiConfigValue<Boolean> disableFrustumCulling()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.disableFrustumCulling); }
	
	@Override
	public IDhApiConfigValue<Boolean> disableShadowFrustumCulling()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling); }
	
	
	
}
