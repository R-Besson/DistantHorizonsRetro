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

package com.seibel.distanthorizons.core.util.objects;


/**
 * Represents an IP and includes a couple helper methods.
 *
 * @author James Seibel
 * @version 3-7-2022
 */
public class ParsedIp
{
	/** can be used to find numeric IPs, IE: "192.168.1.19" */
	public static final String NUMERIC_IP_REGEX = "^[0-9]*\\.[0-9]*\\.[0-9]*\\.[0-9]*(:[0-9]*)?$";


	/** Examples: "192.168.1.19", "mc.hypixel.net", or "localhost" */
	public final String ip;
	/**
	 * null if the ip isn't numeric (IE: "mc.hypixel.net" or "localhost") <br>
	 * Example: "25586"
	 */
	public final String port;
	public final boolean isNumeric;
	
	
	/** parses a standard IP string */
	public ParsedIp(String fullIp)
	{
		fullIp = fullIp.trim();
		
		isNumeric = fullIp.matches(NUMERIC_IP_REGEX);
		if (isNumeric)
		{
			// attempt to separate the IP and the Port
			String[] list = fullIp.split(":");
			if (list.length == 2)
			{
				// IP and Port successfully separated
				ip = list[0];
				port = list[1];
			}
			else
			{
				// this IP must not have a port
				ip = fullIp;
				port = null;
			}
		}
		else
		{
			// text based IP, IE: "localhost"
			ip = fullIp;
			port = null;
		}
	}

	@Override
	public String toString()
	{
		return ip +
				// only print the ":port" if a port is present
				(port != null ? (":" + port) : "");
	}
	
}


