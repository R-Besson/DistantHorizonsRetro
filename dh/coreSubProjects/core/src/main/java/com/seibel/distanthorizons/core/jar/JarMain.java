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

package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.coreapi.ModInfo;

/**
 * Run when the mod jar is started directly (IE double-clicked or "java -jar"),
 * the jar only works as a Forge mod.
 */
public class JarMain
{
	public static void main(String[] args)
	{
		System.out.println(ModInfo.READABLE_NAME + " " + ModInfo.VERSION + " is a Minecraft Forge 1.7.10 mod, "
			+ "put this jar in your instance's mods folder to use it.");
	}

}
