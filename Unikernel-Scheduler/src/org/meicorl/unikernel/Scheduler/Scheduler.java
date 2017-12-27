package org.meicorl.unikernel.Scheduler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * This class is responsible for starting the clones needed for the C2C platform and to give the needed info to the phones.
 * It reads the configuration file for the number of clones that has to be started and for the other info.
 * @author MeiCorl
 */
public class Scheduler{
	private static ServerSocket serverSocket;
	private final static int    Port = 6035;
	private static Logger log=Logger.getLogger(Scheduler.class.getName()); 
	private static DBHelper dbh = null;
	
	public static void main(String[] args) {

		System.out.println("Connecting database and initing dbh...");
		dbh = new DBHelper();
		try {
			serverSocket = new ServerSocket(Port);
			while (true) 
			{
				log.info("Waiting for clients on port: " + Port);
				Socket clientSocket = serverSocket.accept();
				InputStream	is 	= clientSocket.getInputStream();
				OutputStream os	= clientSocket.getOutputStream();

				int whatIsThisClient = is.read();
				log.info("New client connected is: " + whatIsThisClient);
			    if ( whatIsThisClient == ControlMessages.PHONE_CONNECTION ){
					// setup a unikernel server
					Unikernel vm = findAvailableUnikernel();
					if(vm==null)
					{
						System.err.println("Unable find an available unikernel,please try again!");
					}
					else {
						log.info("Starting the ClientHandler");
						vm.start();
						new Thread(new ClientHandler(clientSocket, is, os, vm)).start();
					}
				}	
				else
					System.out.println("Unknown client!");
			}
		} catch (FileNotFoundException e) {
			System.err.println("Configuration file not found, exiting...");
		} catch (IOException e) {
			System.err.println("Could not start server");
			e.printStackTrace();
			System.exit(-1);
		} catch (IllegalStateException e) {
			System.out.println( e.getMessage() );
		}finally {
			try {
				dbh.dbClose();
				serverSocket.close();
				System.err.println("Socket is now closed correctly");
			} catch (Exception e) {
				System.err.println("Socket was never opened");
			}
		}
	}

    private static Unikernel findAvailableUnikernel()
    {
    	try{
			dbh.dbUpdate("lock tables unikernels write"); 
			String sql = "select * from unikernels where status=" +ControlMessages.UnikernelStatus_available + " limit 0,1";
			ResultSet rs = dbh.dbSelect(sql);
			
			if(rs.next()){
				 String name = rs.getString("name").trim();
				 String ip = rs.getString("ip").trim();
				 int status = rs.getInt("status");
				//only update status in database so that nobody will choose this unikernel anymore, 
				dbh.dbUpdate("update unikernels set status = " + ControlMessages.UnikernelStatus_unAvailable +" where name = '" + name + "'");
				dbh.dbUpdate("unlock tables");
				return new Unikernel(name, ip, status, dbh);
			}else{
				dbh.dbUpdate("unlock tables");
				//  here to Create a new Unikernel
				return null;
			}
		} catch (SQLException e) {
            e.printStackTrace();
            return null;
        } 
    }
}