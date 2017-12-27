package org.meicorl.unikernel.Scheduler;


import java.io.*;
import java.net.*;
import java.util.HashMap;

/**
 * The Server program that runs as a unikernel
 * @author MeiCorl
 * @date  2017.05.13
 */
public class ClientHandler implements Runnable {
	private final Object mutex = new Object();

	// phone-client connect socket
	private Socket clientSocket = null;
	private InputStream is = null;
	private OutputStream os = null;
	private ObjectOutputStream 		oos = null;
	private ObjectInputStream ois = null;
	
	// unikernel worker connect socket
	private Socket 					conSocket = null;
	private InputStream				conis = null;
	private OutputStream			conos = null;
	private ObjectOutputStream conoos = null;
	private ObjectInputStream		conois = null;
	
	private String					phoneID = null;
	private String 					appName = null;						// the app name sent by the phone
	private Unikernel				worker = null;

	private String			        logFileName = null;
	private FileWriter 			    logFileWriter = null;
	private String 			        RequestLog = null;
	private byte[]                  tempArray = null;

	public ClientHandler(Socket clientSocket, InputStream is, OutputStream os, Unikernel worker) throws IOException {
		this.clientSocket 	= clientSocket;
		this.is				= is;
		this.os				= os;
		this.ois = new ObjectInputStream(is);
		this.oos = new ObjectOutputStream(os);

		this.worker         = worker;
		this.conSocket = new Socket();
		connectWorker();    // connect to the worker in a new thread.

	 	this.logFileName = ControlMessages.LOG_FILE_PATH + "execrecord.txt";
		File needlog = new File(ControlMessages.LOG_FILE_PATH + "needlog");
		if(needlog.exists()){
			try {
				File logFile = new File(logFileName);
				logFile.createNewFile(); // Try creating new, if doesn't exist
				logFileWriter = new FileWriter(logFile, true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		System.out.println("Waiting for commands from the phone...");
		int command = 0;
		try{
			HashMap<String, String> result;
			while (command != -1)
			{
				command = is.read();
				System.out.println("Command: " + command);
				switch(command) {
				case ControlMessages.CONNECTION_RELEASED:
				case ControlMessages.PHONE_DISCONNECTION:
					conos.write(ControlMessages.PHONE_DISCONNECTION);
					System.out.println("Connection has been released!");
					System.out.println("goodbye! ^-^");
					return;

				case ControlMessages.PHONE_AUTHENTICATION:
					// Read the ID of the requesting phone
					this.phoneID = (String)ois.readObject();
					break;
					
				case ControlMessages.PING:
					System.out.println("Reply to PING");
					os.write(ControlMessages.PONG);
					break;
					
				case ControlMessages.APK_REGISTER:
					String[] fullAppName= ((String)ois.readObject()).split("\\.");
					appName = fullAppName[fullAppName.length - 1] + ".apk";

					// waiting for the worker be prepaired,and then send apk to worker if neccessary
					System.out.println("waiting for sending apk!");
					synchronized(mutex){
						mutex.wait();
					}
					conos.write(ControlMessages.APK_REGISTER);
					conoos.writeObject(appName);
					int res = conis.read();
					if(res != ControlMessages.APK_PRESENT) {
						// receive apk
						System.out.println("request APK :" + appName);
						os.write(ControlMessages.APK_REQUEST);
						receiveFile(ois, true);
						System.out.println("received APK");

						// send apk
						conoos.writeInt(tempArray.length);
						conoos.write(tempArray);
						conoos.flush();
						System.out.println("Successfully send apk!");
					}else{
						System.out.println("APK present :" + appName);
						os.write(ControlMessages.APK_PRESENT);
					}
					break;
					
				case ControlMessages.PHONE_COMPUTATION_REQUEST:	
					System.out.println("Execute request");
					conos.write(ControlMessages.PHONE_COMPUTATION_REQUEST);
					
					//receive the object from phone-client ois and repost the request to Unikernel server
					result = receiveAndRepost();
					try {
						// Send back over the socket connection
						System.out.println("Sending result back...");
						System.out.println("Send retType is: " + result.get("retType"));
						this.oos.writeObject(result.get("retType"));
						System.out.println("Send retVal is: " + result.get("retVal"));
						this.oos.writeObject(result.get("retVal"));
						// Clear ObjectOutputCache - Java caching unsuitable
						// in this case
						this.oos.flush();
						System.out.println("Result successfully sent");
					} catch (IOException e) {
						System.out.println("Connection failed when sending result back");
						e.printStackTrace();
						return;
					}
					this.traceLog(this.RequestLog);
					this.RequestLog = "";
					break;
					
				case ControlMessages.PHONE_COMPUTATION_REQUEST_WITH_FILE:					
					System.out.println("Execute request with file,The offloading need to send file first");
					conos.write(ControlMessages.PHONE_COMPUTATION_REQUEST_WITH_FILE);
					
					String filePath = (String) ois.readObject();
					String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
					filePath = ControlMessages.DIRSERVICE_RESOURCE_DIR+ fileName;
					//Actually we should always request the file.
					System.out.println("request File " + filePath);
					os.write(ControlMessages.SEND_FILE_REQUEST);
					// Receive the files from the client
					receiveFile(ois,false);
					//send file
					result = receiveAndRepost();
					try {
						// Send back over the socket connection
						System.out.println("Send result back");
						this.oos.writeObject(result.get("retType"));
						this.oos.writeObject(result.get("retVal"));
						// Clear ObjectOutputCache - Java caching unsuitable
						// in this case
						this.oos.flush();
						System.out.println("Result successfully sent");
					} catch (IOException e) {
						System.out.println("Connection failed when sending result back");
						e.printStackTrace();
						return;
					}
					this.traceLog(this.RequestLog);
					this.RequestLog = "";
					break;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e){
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			releaseConnection();
			shutdownWorker();
		}
	}
	
	/**
	 * @return
	 * 		return true if file exists,otherwise return false
	 */
	private boolean filePresent(String filename) {
		File file = new File(filename);
		return file.exists();
	}
	
	/**
	 * Method to receive an apk or common files of an application that needs to be executed
	 * @param objIn
	 *            Object input stream to simplify retrieval of data
	 * @return the file where the apk package is stored
	 * @throws IOException
	 *             throw up an exception thrown if socket fails
	 * @author MeiCorl
	 */
	private void receiveFile(ObjectInputStream objIn, boolean isApk) throws IOException
	{
		// Get the length of the file receiving
		int fileLength = objIn.readInt();
		if(isApk)
			System.out.println("Read apkLength: " + fileLength);
		else
			System.out.println("Read fileLength: " + fileLength);

		// read file content
		tempArray = new byte[fileLength];
		objIn.readFully(tempArray);
	}

	/**
	 * Reads in the object to execute an operation on, name of the method to be
	 * executed and repost it
	 * @author MeiCorl
	 */
	private HashMap<String ,String> receiveAndRepost() {
		// Read the object in for execution
		try {
			// receive data from phone
			System.out.println("Reading data from Phone...");
			Object className    = ois.readObject();
			Object objToExecute = ois.readObject();
			Object methodName   = ois.readObject();
			Object tempTypes    = ois.readObject();
			Object pValuestr    = ois.readObject();

			// write to the unikernel server
			// firstly write the URL of the apk;
			// unikernel will download the apk through http if necessary
			System.out.println("Sending data to Unikernel-server...");
			conoos.writeObject(className);
		 	conoos.writeObject(objToExecute);
			conoos.writeObject(methodName);
			conoos.writeObject(tempTypes);
			conoos.writeObject(pValuestr);
			conoos.flush();
			
			//waiting to retrieve result from container
			System.out.println("Reading result from  Unikernel-server...");
			HashMap<String ,String> result = new HashMap<>();
			String retType = (String) conois.readObject();
			result.put("retType", retType);
			String response = (String) conois.readObject();
			result.put("retVal", response);
			return result;
		} catch (IOException | ClassNotFoundException e) {
			// catch and return any exception since we do not know how to handle
			// them on the server side
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * try to connect to the Unikernel worker until the worker starts completely.
	 * @author MeiCorl
	 */
	public void connectWorker() 
	{
		new Thread(){
			@Override
			public void run() {
				// try to connect to the Unikernel worker
				InetSocketAddress workerAddr = new InetSocketAddress(worker.getIP(), worker.getPort());
				while(!conSocket.isConnected())
				{
					try {
						try {
							Thread.sleep(1000);
							conSocket.connect(workerAddr, 2000);
						} catch (ConnectException e) {
							Thread.sleep(500);
							System.out.println("Tring to connect to worker...");
						} catch (NoRouteToHostException e) {
							Thread.sleep(500);
							System.out.println("Tring to connect to worker...");
						} catch (SocketTimeoutException e) {
							Thread.sleep(500);
							System.out.println("Tring to connect to worker...");
						} catch (UnknownHostException e) {
							Thread.sleep(500);
							System.out.println("Tring to connect to worker...");
						} catch (IOException e) {
							// TODO Auto-generated catch block
							shutdownWorker();
							e.printStackTrace();
							return;
						}
					}catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				System.out.println("connect to the worker Successfully!");
			    try {
					conis = conSocket.getInputStream();
					conos = conSocket.getOutputStream();
					conoos = new ObjectOutputStream(conos);
					conois = new ObjectInputStream(conis);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					synchronized (mutex){
						mutex.notify();
					}
				}
			}
		}.start();
	}

	private void releaseConnection(){
		try {
			// System.out.println("close the connection to client");		
			oos.close();
			ois.close();
			clientSocket.close();
			
			// System.out.println("close the connection to unikernel server");
			if(this.conoos != null)
				this.conoos.close();
			if(this.conois != null)
				this.conois.close();
			if(this.conSocket != null)
				this.conSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void shutdownWorker()
	{
		// this the unikernel vm is still running, power it off
		if(this.worker.getStatus() == ControlMessages.UnikernelStatus_unAvailable)
			this.worker.shutdown();
	}
	
	private void traceLog(String log){
		if (logFileWriter != null) {
			try {
				logFileWriter.append(log + "\n");
				logFileWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}