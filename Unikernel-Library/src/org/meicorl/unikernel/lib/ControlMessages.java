package org.meicorl.unikernel.lib;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

import android.content.Context;
import android.util.Log;

/**
 * Control Messages for client-server communication Message IDs up to 255 - one
 * byte only, as they are sent over sockets using write()/read() - only one byte
 * read/written.
 */
public class ControlMessages {

	public static final int	STATIC_LOCAL 						= 1;
	public static final int	STATIC_REMOTE 						= 3;
	public static final int USER_CARES_ONLY_TIME				= 4;
	public static final int USER_CARES_ONLY_ENERGY 				= 5;
	public static final int USER_CARES_TIME_ENERGY 				= 6;

	public static final int PING								= 11;
	public static final int PONG								= 12;
	
	// Communication Phone <-> Clone
	public static final int APK_REGISTER 						= 21;
	public static final int APK_PRESENT 						= 22;
	public static final int APK_REQUEST 						= 23;

	public static final String THINKAIR_FOLDER 					= "/mnt/sdcard/thinkAir/";
	public static final String PHONE_CONFIG_FILE 				= THINKAIR_FOLDER + "config-phone.dat";
	public static final String FILE_NOT_OFFLOADED				= THINKAIR_FOLDER + "notOffloaded";

	// The constants of the configuration files
	public static final String 	DIRSERVICE_IP					= "[DIRSERVICE IP]";
	public static final String 	DIRSERVICE_PORT					= "[DIRSERVICE PORT]";

	
	// Communication Phone/Clone <-> DirectoryService
	public static final int 	PHONE_CONNECTION 				= 30;
	public static final int 	PHONE_AUTHENTICATION			= 31;
	public static final int     PHONE_DISCONNECTION             = 32;

	public static final int		PHONE_COMPUTATION_REQUEST		        = 40;
	public static final int		PHONE_COMPUTATION_REQUEST_WITH_FILE		= 41;
	public static final int		SEND_FILE_REQUEST			            = 42;


	/**
	 * An empty file will be created automatically on the phone by ThinkAir-Client.
	 * The presence or absence of this file can let the method know 
	 * if it is running on the phone or on the clone.
	 * @return <b>True</b> if it is running on the clone<br>
	 * <b>False</b> if it is running on the phone.
	 */
	public static boolean checkIfOffloaded() {
		try {
			File tempFile = new File(ControlMessages.FILE_NOT_OFFLOADED);
			if ( tempFile.exists() )	return false;
			else						return true;
		} catch (Exception e) {
			return true;
		}
	}

	public static void executeShellCommand(String TAG, String cmd, boolean asRoot) {
		Process p = null;
		try {
			if (asRoot) 
				p = Runtime.getRuntime().exec("su " + cmd);
			else        
				p = Runtime.getRuntime().exec(cmd);

			DataOutputStream outs = new DataOutputStream(p.getOutputStream());

			// outs.writeBytes(cmd + "\n");
			outs.writeBytes("exit\n");
			outs.close();

			p.waitFor();
			Log.i(TAG, "Executed cmd: " + cmd);

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}


