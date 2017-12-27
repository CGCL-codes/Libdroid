package org.meicorl.unikernel.Scheduler;

import java.io.IOException;
import java.io.Serializable;

/**
 * A unikernel is a standlone virtual mechine constructed by library operating system,
 * in which the server runs.
 * @author MeiCorl
 */
class Unikernel  implements Serializable{

	private static final long serialVersionUID = 1L;

	private Process worker_process = null;
	private String name = null;
	private String ip = null;
	private static final int port = 6034;
	/**
	 * status
	 * 		 0: stands for "vm is power off, is avaiable"   
	 * 	     1: stands for "vm is running, is unavailable"
	 */
	private int status = ControlMessages.UnikernelStatus_unAvailable;
    private DBHelper dbh = null;

    
	Unikernel(String name, String ip, int status, DBHelper dbh) {
		this.ip = ip;
		this.name = name;
		this.status = status ;
		this.dbh = dbh;
	}

	String getIP()
    {
    	return this.ip;
    }

    int getPort()
	{
		return Unikernel.port;
	}
    
    int getStatus()
    {
    	return this.status;
    }
    
	void start()
    {
    	executeCommand("virsh start " + this.name);
		/** 'startvm' is script to setup an unikernel server */
		//executeCommand("startvm " + ControlMessages.IMAGE_HUB + this.name + ".img");
    	this.status = ControlMessages.UnikernelStatus_unAvailable;
    }

	/**
	 * Close the worker process if it hasn't closed automaticly.
	 */
	void shutdown()
    {
    	executeCommand("virsh destroy " + this.name);
		//if(worker_process.isAlive())
			//worker_process.destroy();
		dbh.dbUpdate("lock tables unikernels write"); 
		dbh.dbUpdate("update unikernels set status = " + ControlMessages.UnikernelStatus_available +" where name = '" + name + "'");
		dbh.dbUpdate("unlock tables");
		this.status = ControlMessages.UnikernelStatus_available;
    }
    
    private void executeCommand(String command) {
		try {
			worker_process = Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}