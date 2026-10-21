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

package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

import java.awt.*;

/**
 * Angelica is a backport of Sodium
 * to MC 1.7.10
 */
public interface IAngelicaAccessor extends IModAccessor
{
	
	Color getFogColor();
	
	int getDepthTextureId();
	
	boolean canDoFadeShader();

	/**
	 * True if Angelica's fancy biome blending is on, which tints every vanilla leaf
	 * (including birch and spruce) with the biome's foliage color.
	 */
	boolean leavesUseBiomeFoliageColor();

	/**
	 * Uploads the "dhProjection" and "heightLimit" uniforms to the currently bound shader pack program. <br>
	 * Angelica doesn't upload them to its DH programs without a shadow pass,
	 * without one (IE in the Nether or with shadows off) they stay zero:
	 * the opaque LODs aren't drawn at all and everything above Y 96 is discarded as clouds.
	 */
	void uploadMissingShaderUniforms();

}
