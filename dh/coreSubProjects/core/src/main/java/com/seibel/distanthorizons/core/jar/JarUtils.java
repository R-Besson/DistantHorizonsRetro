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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Some general utils for the jar
 * this includes stuff like accessing files inside the jar
 *
 * @author coolGi
 */
public class JarUtils
{
	//=========//
	// methods //
	//=========//
	//region

	/**
	 * Get a file within the mods resources
	 *
	 * @param resource Location of the file you want to get
	 * @return InputStream
	 */
	public static InputStream accessFile(String resource)
	{
		final ClassLoader loader = JarUtils.class.getClassLoader();
		// this is the path within the jar file
		InputStream input = loader.getResourceAsStream(resource);
		if (input == null)
		{
			// this is how we load file within editor
			input = loader.getResourceAsStream(resource);
		}

		return input;
	}

	/** Convert inputStream to String. Useful for reading .txt or .json that are inside the jar file */
	public static String convertInputStreamToString(InputStream inputStream)
	{
		final char[] buffer = new char[8192];
		final StringBuilder result = new StringBuilder();

		// InputStream -> Reader
		try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8))
		{
			int charsRead;
			while ((charsRead = reader.read(buffer, 0, buffer.length)) > 0)
			{
				result.append(buffer, 0, charsRead);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return result.toString();
	}

	//endregion
}
