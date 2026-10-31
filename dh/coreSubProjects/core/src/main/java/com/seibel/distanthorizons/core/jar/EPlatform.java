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

/**
 * A simple OS getting util based off LWJGL's EPlatform. <br>
 * This version includes some extra utils that we need and removes stuff which we don't.
 *
 * @author coolGi
 */
public enum EPlatform
{
	WINDOWS("Windows"),
	LINUX("Linux"),
	MACOS("macOS"),
	BSD("BSD"),
	UNIX("Unix");

	private static final EPlatform current;
	static
	{
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("windows"))
		{ // Proper name is "Windows"
			current = WINDOWS;
		}
		else if (osName.contains("linux"))
		{ // Proper name is "Linux"
			current = LINUX;
		}
		else if (osName.contains("mac") || osName.contains("darwin"))
		{ // For macOS it should either output "Mac OS X" or "Darwin" depending on the version of macOS
			current = MACOS;
		}
		else if (osName.startsWith("bsd") || osName.startsWith("freebsd"))
		{ // Depending on the BSD distro this will be different
			current = BSD;
		}
		else if (osName.startsWith("unix"))
		{ // In case you are running some very niece OS which didn't change their OS name from Unix
			current = UNIX;
		}
		else
		{
			// We only test on Windows, Linux and macOS
			// If you use a different os (eg, BSD, Unix) then it may or may not work
			// Otherwise, good luck
			throw new LinkageError("Unknown platform: " + osName);
		}
	}

	private final String name;

	EPlatform(String name)
	{
		this.name = name;
	}

	/** Returns the platform name. */
	public String getName()
	{
		return name;
	}

	/** Returns the platform on which the library is running. */
	public static EPlatform get()
	{
		return current;
	}

	@Override
	public String toString()
	{
		return this.getName();
	}
}
