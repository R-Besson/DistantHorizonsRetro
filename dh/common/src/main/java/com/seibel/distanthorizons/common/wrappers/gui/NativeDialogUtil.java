package com.seibel.distanthorizons.common.wrappers.gui;

import javax.swing.JOptionPane;

/**
 * Should be used instead of showing dialogs directly
 * so we can run additional validation and string cleanup.
 */
public class NativeDialogUtil
{
	// TODO these string options should be replaced with an enum
	/**
	 * @param dialogType    the dialog type. One of:<br><table><tr><td>"ok"</td><td>"okcancel"</td><td>"yesno"</td><td>"yesnocancel"</td></tr></table>
	 * @param iconType      the icon type. One of:<br><table><tr><td>"info"</td><td>"warning"</td><td>"error"</td><td>"question"</td></tr></table>
	 */
	public static void showDialog(String title, String message, String dialogType, String iconType)
	{
		// some dialog backends don't support the following characters
		String unsafeCharsRegex = "['\"`]";

		title = title.replaceAll(unsafeCharsRegex, "");
		message = message.replaceAll(unsafeCharsRegex, "");

		jSwingDialog(title, message, iconType);
	}

	private static void jSwingDialog(String title, String message, String iconType)
	{
		int messageType;
		switch (iconType)
		{
			case "error":
				messageType = JOptionPane.ERROR_MESSAGE;
				break;
			case "warning":
				messageType = JOptionPane.WARNING_MESSAGE;
				break;
			case "info":
				messageType = JOptionPane.INFORMATION_MESSAGE;
				break;
			case "question":
				messageType = JOptionPane.QUESTION_MESSAGE;
				break;
			default:
				messageType = JOptionPane.PLAIN_MESSAGE;
				break;
		}
		JOptionPane.showConfirmDialog(null, message, title, JOptionPane.DEFAULT_OPTION, messageType);
	}


	/**
	 * if we're using Java Swing for our UI
	 * dialogs then AWT needs to be set to "false"
	 * otherwise the AWT setting doesn't matter.
	 */
	public static boolean needsAwtHeadless()
	{
		return false;
	}



}
