package com.seibel.distanthorizons.core.sql;

import java.sql.SQLException;

/**
 * Used to simplify handling when a database has been closed
 * since Java doesn't have a specific exception to handle closed databases
 */
public class DbConnectionClosedException extends SQLException
{
	public DbConnectionClosedException() { super("The database connection is closed."); }
	public DbConnectionClosedException(String message) { super(message); }
	public DbConnectionClosedException(String message, Throwable cause) { super(message, cause); }
	public DbConnectionClosedException(Throwable cause) { super(cause); }


	// helper methods //

	/** also checks the causes, since the SQLException is often wrapped (IE in a CompletionException) */
	public static boolean isClosedException(Throwable e)
	{
		for (Throwable throwable = e; throwable != null; throwable = throwable.getCause())
		{
			if (throwable instanceof DbConnectionClosedException)
			{
				return true;
			}

			if (!(throwable instanceof SQLException)
				|| throwable.getMessage() == null)
			{
				continue;
			}

			String message = throwable.getMessage().toLowerCase();
			if (message.contains("connection closed")
				|| message.contains("pointer is closed")
				|| message.contains("stmt pointer is closed")
				|| message.contains("database has been closed"))
			{
				return true;
			}
		}

		return false;
	}

}
