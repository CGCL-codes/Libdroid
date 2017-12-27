package org.meicorl.unikernel.lib;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;

public class Configuration {

	private String				dirServiceIp;
	private int					dirServiceport;

	private Configuration (String ip, int port) {
		this.dirServiceIp = ip;
		this.dirServiceport = port;
	}
	
	/**
	 * Read the configuration file with the following format: <br>
	 * # Comment <br>
	 * [Category] <br>
	 * @throws FileNotFoundException 
	 * 
	 */
	  public static Configuration parseConfigFile(String configFilePath)   {
		Scanner configFileScanner = null;
		String ip = "";
		int port = -1;
		try {
			configFileScanner = new Scanner(new FileReader(configFilePath) );
			while (configFileScanner.hasNext()) {
				
				// Get the next line of the file and remove any extra spaces
				String line = configFileScanner.nextLine().trim();

				if (line.equals(ControlMessages.DIRSERVICE_IP)) {
					ip = configFileScanner.nextLine().trim();
				}
				else if (line.equals(ControlMessages.DIRSERVICE_PORT)) {
					port = configFileScanner.nextInt();
				}
			}
		} catch(FileNotFoundException e)
		{
			System.err.println("Can not find the Configuration file: " + ControlMessages.PHONE_CONFIG_FILE);
			System.exit(-1);
		} finally {
			if(configFileScanner!=null)
				configFileScanner.close();
		}
		return  new Configuration(ip, port);
	}

	public String getDirServiceIp()
	{
		return dirServiceIp;
	}

	/**
	 * @return the port
	 */
	public int getDirServicePort()
	{
		return dirServiceport;
	}
}