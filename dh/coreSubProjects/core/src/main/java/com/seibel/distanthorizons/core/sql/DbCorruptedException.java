package com.seibel.distanthorizons.core.sql;

import java.sql.SQLException;

/** 
 * Used to simplify handling when a database is corrupted
 * since Java doesn't have a specific exception to handle corrupted databases 
 */
public class DbCorruptedException extends SQLException
{
	// helper methods //
	
	public static boolean isCorruptedException(SQLException e) 
	{
		String message = e.getMessage().toLowerCase();
		return message.contains("sqlite_corrupt")
				|| message.contains("malformed");
	}
	
}
