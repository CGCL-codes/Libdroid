package org.meicorl.unikernel.lib;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.provider.Settings;
import org.meicorl.unikernel.lib.db.DatabaseQuery;
import org.meicorl.unikernel.lib.profilers.DeviceProfiler;
import org.meicorl.unikernel.lib.profilers.LogRecord;
import org.meicorl.unikernel.lib.profilers.NetworkProfiler;
import org.meicorl.unikernel.lib.profilers.Profiler;
import org.meicorl.unikernel.lib.profilers.ProgramProfiler;

import com.google.gson.Gson;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * The interface to the framework for the client program - controls
 * ExecutionSolver, profilers, communicates with remote server
 * 
 */
public class ExecutionController {
	
	public static final String TAG = "ExecutionController";
	
	public static final int 	REGIME_CLIENT = 1;
	public static final int 	REGIME_SERVER = 2;

	public LogRecord 			lastLogRecord;

	private Long 				mPureExecutionDuration;

	private static int			mRegime;
	private String				mAppName;
	private Context				mContext;
	private PackageManager		mPManager;

	private boolean				onLine;
	private ExecutionSolver 	mSolver;
	private DeviceProfiler		mDevProfiler;
	private NetworkProfiler 	netProfiler;

	private Configuration       config;
	private OutputStream		mOutStream;
	private ObjectOutputStream	mObjOutStream;
	private InputStream			mInStream;
	private ObjectInputStream	mObjInStream;
	
	private Socket				dirSocket;

	private DatabaseQuery 		query;

	public static String		myId = null;

	
	/**
	 * Create ExecutionController which decides where to execute remoteable
	 * methods and handles execution
	 *
	 * @param pManager
	 *            Package manager for finding apk file of the application
	 */
	public ExecutionController(String appName, PackageManager pManager, final Context context) {

		Log.d(TAG, "ExecutionController Created");
		mRegime = REGIME_CLIENT;
		ExecutionController.myId = Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
		this.mAppName 	= appName;
		this.mPManager 	= pManager;
		this.mContext 	= context;

		mDevProfiler = new DeviceProfiler(context);
		mDevProfiler.trackBatteryLevel();
		netProfiler = new NetworkProfiler(context);
		netProfiler.registerNetworkStateTrackers();

		mSolver = new ExecutionSolver(ExecutionSolver.EXECUTION_LOCATION_STATIC_REMOTE);
		Runnable r = new Runnable() {
			@Override
			public void run() {
				createNotOffloadedFile();

				try {
					establishConnection();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}

				// connect to the scheduler
				if((dirSocket != null) && dirSocket.isConnected())
					testNetworkAndSendApk();

				// Create the database
				query = new DatabaseQuery(context);
				// Close the database
				try {
					query.destroy();
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		};
		new Thread(r).start();
	}

	/**
	 * To be used on server side, only local execution
	 */
	public ExecutionController() {
		mRegime = REGIME_SERVER;
		mSolver = new ExecutionSolver(ExecutionSolver.EXECUTION_LOCATION_STATIC_LOCAL);
	}

	/**
	 * Create an empty file on the phone in order to let the method know
	 * where is being executed (on the phone or on the clone).
	 */
	private void createNotOffloadedFile(){
		try {
			File f = new File(ControlMessages.FILE_NOT_OFFLOADED);
			f.createNewFile();
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	/**
	 * 1、Read the config file to get the IP and port for Scheduler.
	 * 2、setup a tcp connection to the Scheduler,and get the I/O stream of the connection.
	 * @throws IOException
	 * @throws UnknownHostException
	 * @throws ClassNotFoundException
	 */
	private void establishConnection() throws IOException, ClassNotFoundException {
		config =  Configuration.parseConfigFile(ControlMessages.PHONE_CONFIG_FILE);

		this.dirSocket = new Socket();
		this.dirSocket.connect(new InetSocketAddress(config.getDirServiceIp(), config.getDirServicePort()), 3000);
		this.mOutStream = this.dirSocket.getOutputStream();
		this.mInStream = this.dirSocket.getInputStream();

		mOutStream.write(ControlMessages.PHONE_CONNECTION);

		mObjOutStream = new ObjectOutputStream(mOutStream);
		mObjInStream = new ObjectInputStream(mInStream);

		// Send the name and id to DirService
		mOutStream.write(ControlMessages.PHONE_AUTHENTICATION);
		mObjOutStream.writeObject(ExecutionController.myId);
		mObjOutStream.flush();
	}

	/**
	 * Set up streams for the socket connection, perform initial communication
	 * with the server: determine RTT, enquire if it has the required apk and
	 * send it if not.
	 */
	private void testNetworkAndSendApk() {
		try {
			synchronized (this) {
				onLine = true;
			}
			// Find rtt to the server
			NetworkProfiler.rttPing(mInStream, mOutStream);

			// send apk if neccessary
			String apkName = mPManager.getApplicationInfo(mAppName, 0).sourceDir;
			mOutStream.write(ControlMessages.APK_REGISTER);
			mObjOutStream.writeObject(mAppName);
			int response = mInStream.read();

			if (response == ControlMessages.APK_REQUEST) {
				sendApk(apkName, mObjOutStream);
			}
		} catch (UnknownHostException e) {
			fallBackToLocalExecution("Connection setup to server failed: " + e.getMessage());
		} catch (IOException e) {
			fallBackToLocalExecution("Connection setup to server failed: " + e.getMessage());
		} catch (NameNotFoundException e) {
			fallBackToLocalExecution("Application not found: " + e.getMessage());
		} catch (Exception e) {
			fallBackToLocalExecution("Could not connect: " + e.getMessage());
		}
	}

	/**
	 * release the connect beteewn phone and Scheduler.
	 * @author MeiCorl
	 * @data  2017.07.16
	 */
	public void releaseConnection()
	{
		mDevProfiler.onDestroy();
		netProfiler.onDestroy();
		try {
			if(mOutStream != null){
				mOutStream.write(ControlMessages.PHONE_DISCONNECTION);
				this.mObjInStream.close();
				this.mObjOutStream.close();
				this.dirSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void fallBackToLocalExecution(String message) {
		Log.d(TAG, message);
		
		// we still allow offloading incase the connection comes back
		mSolver.setRegime(ExecutionSolver.EXECUTION_LOCATION_DYNAMIC);
		
		synchronized (this) {
			onLine = false;
		}
	}
	
	/**
	 * For Connection repaire
	 */
	private void reestablishConnection() {
		try {
			Configuration config = Configuration.parseConfigFile(ControlMessages.PHONE_CONFIG_FILE);

			this.dirSocket  = new Socket(config.getDirServiceIp(), config.getDirServicePort());
			this.mOutStream = this.dirSocket.getOutputStream();
			this.mInStream = this.dirSocket.getInputStream();

			mOutStream.write(ControlMessages.PHONE_CONNECTION);

			mObjOutStream = new ObjectOutputStream(mOutStream);
			mObjInStream = new ObjectInputStream(mInStream);

			// Send the name and id to DirService
			mOutStream.write(ControlMessages.PHONE_AUTHENTICATION);
			mObjOutStream.writeObject(ExecutionController.myId);
			mObjOutStream.flush();
			
			establishConnection();
		} catch (UnknownHostException e) {
			fallBackToLocalExecution("Connection setup to server failed: " + e.getMessage());
		} catch (IOException e) {
			fallBackToLocalExecution("Connection setup to server failed: " + e.getMessage());
		} catch (Exception e) {
			fallBackToLocalExecution("Could not connect: " + e.getMessage());
		}
		
	}

	/**
	 * Wrapper of the execute method with no parameters for the executable
	 * method
	 * 
	 * @param m
	 * @param o
	 * @return
	 * @throws Throwable
	 */
	public Object execute(Method m, Object o) throws Throwable {
		return execute(m, (Object[]) null, o);
	}

	/**
	 * Call ExecutionSolver to decide where to execute the operation, start
	 * profilers, execute (either locally or remotely), collect profiling data
	 * and return execution results.
	 * 
	 * @param m
	 *            method to be executed
	 * @param pValues
	 *            with parameter values
	 * @param o
	 *            one object
	 * @return result of execution, or an exception if it happened
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 */
	public Object execute(Method m, Object[] pValues, Object o)
			throws IllegalArgumentException, SecurityException,
			IllegalAccessException, ClassNotFoundException,
			NoSuchMethodException {
		Object result;
		String classMethodName = o.getClass().toString() + m.getName();
		ProgramProfiler progProfiler = new ProgramProfiler(classMethodName);

		try {

			if (!this.netProfiler.noConnectivity() && mSolver.executeRemotely(mContext, classMethodName) ) {

				Profiler profiler = new Profiler(mRegime, mContext, progProfiler, this.netProfiler, mDevProfiler);

				// Start tracking execution statistics for the method
				profiler.startExecutionInfoTracking();
				result = executeRemotely(m, pValues, o);
				// Collect execution statistics
				profiler.stopAndLogExecutionInfoTracking(mPureExecutionDuration);
				lastLogRecord = profiler.lastLogRecord;
				return result;
			} 
			else 
			{ 
				// Execute locally
				if(this.netProfiler.noConnectivity()){
					onLine =false;
				}
				Profiler profiler1 = new Profiler(mRegime, mContext, progProfiler, null, mDevProfiler);

				// Start tracking execution statistics for the method
				profiler1.startExecutionInfoTracking();
				result = executeLocally(m, pValues, o);
				// Collect execution statistics
				profiler1.stopAndLogExecutionInfoTracking(mPureExecutionDuration);
				lastLogRecord = profiler1.lastLogRecord;
				return result;
			}

		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.d(TAG, "InvocationTargetException " + e);
			return e;
			// throw e.getTargetException();
		}
	}
	
	
	//execute with file to send first
	public Object execute(Method m, Object[] pValues, Object o, String fileName)
			throws IllegalArgumentException, SecurityException,
			IllegalAccessException, ClassNotFoundException,
			NoSuchMethodException {
		Object result;
		String classMethodName = o.getClass().toString() + m.getName();

		ProgramProfiler progProfiler = new ProgramProfiler(classMethodName);

		try {

			if (!this.netProfiler.noConnectivity() && mSolver.executeRemotely(mContext, classMethodName) ) {

				Profiler profiler = new Profiler(mRegime, mContext, progProfiler, this.netProfiler, mDevProfiler);

				// Start tracking execution statistics for the method
				profiler.startExecutionInfoTracking();
				result = executeRemotely(m, pValues, o, fileName);
				// Collect execution statistics
				profiler.stopAndLogExecutionInfoTracking(mPureExecutionDuration);
				lastLogRecord = profiler.lastLogRecord;
				return result;
			} else { // Execute locally
				if(this.netProfiler.noConnectivity()){
					onLine =false;
				}
				Profiler profiler1 = new Profiler(mRegime, mContext, progProfiler, null, mDevProfiler);

				// Start tracking execution statistics for the method
				profiler1.startExecutionInfoTracking();
				result = executeLocally(m, pValues, o);
				// Collect execution statistics
				profiler1.stopAndLogExecutionInfoTracking(mPureExecutionDuration);
				lastLogRecord = profiler1.lastLogRecord;
				return result;
			}

		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.d(TAG, "InvocationTargetException " + e);
			return e;
			// throw e.getTargetException();
		}
	}

	/**
	 * Execute the method locally
	 * 
	 * @param m
	 * @param pValues
	 * @param o
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	private Object executeLocally(Method m, Object[] pValues, Object o)
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException {
		// Make sure that the method is accessible
		Object result = null;
		Long startTime = System.nanoTime();
		m.setAccessible(true);
		result = m.invoke(o, pValues); // Access it
		mPureExecutionDuration = System.nanoTime() - startTime;
		Log.d("ExecutionLocation", "LOCAL " + m.getName()
				+ ": Actual Invocation duration - " + mPureExecutionDuration
				/ 1000000 + "ms");
		return result;
	}

	/**
	 * Execute method remotely
	 * 
	 * @param m
	 * @param pValues
	 * @param o
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws SecurityException
	 */
	private Object
	executeRemotely(Method m, Object[] pValues, Object o)
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, SecurityException,
			ClassNotFoundException, NoSuchMethodException {
		Object result = null;
		try {
			Long startTime = System.nanoTime();
			mOutStream.write(ControlMessages.PHONE_COMPUTATION_REQUEST);
			result = sendAndExecute(m, pValues, o, mObjInStream, mObjOutStream);

			Long duration = System.nanoTime() - startTime;
			Log.d("ExecutionLocation", "REMOTE " + m.getName()
					+ ": Actual Send-Receive duration - " + duration / 1000000
					+ "ms");
		} catch (NullPointerException e){
			// establish failed so that mOutStream is null, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		} catch (UnknownHostException e) {
			// No such host exists, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		} catch (IOException e) {
			// Connection broken, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		}

		return result;
	}
	
	/**
	 * Execute method remotely with file sent first
	 * 
	 * @param m
	 * @param pValues
	 * @param o
	 * @param filename
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws SecurityException
	 */
	private Object executeRemotely(Method m, Object[] pValues, Object o, String filename)
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, SecurityException,
			ClassNotFoundException, NoSuchMethodException {
		Object result = null;
		try {
			Long startTime = System.nanoTime();
			
			mOutStream.write(ControlMessages.PHONE_COMPUTATION_REQUEST_WITH_FILE);
			
			mObjOutStream.writeObject(filename);
			int response = mInStream.read();

			if (response == ControlMessages.SEND_FILE_REQUEST) {
				// Send the APK file if needed
				sendFile(filename, mObjOutStream);
			}
			
			result = sendAndExecute(m, pValues, o, mObjInStream, mObjOutStream);

			Long duration = System.nanoTime() - startTime;
			Log.d("ExecutionLocation", "REMOTE " + m.getName()
					+ ": Actual Send-Receive duration - " + duration / 1000000
					+ "ms");
		} catch (NullPointerException e){
			// establish failed so that mOutStream is null, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			e.printStackTrace();
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		} catch (UnknownHostException e) {
			// No such host exists, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			e.printStackTrace();
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		} catch (IOException e) {
			// Connection broken, execute locally
			Log.e(TAG, "ERROR " + m.getName() + ": " + e);
			e.printStackTrace();
			result = executeLocally(m, pValues, o);
			ConnectionRepair repair = new ConnectionRepair();
			repair.start();
		}
		return result;
	}

	/**
	 * Send APK file to the remote server
	 * 
	 * @param apkName
	 *            file name of the APK file (full path)
	 * @param objOut
	 *            ObjectOutputStream to write the file to
	 * @throws IOException
	 */
	private void  sendApk(String apkName, ObjectOutputStream objOut)
			throws IOException {
		File apkFile = new File(apkName);
		FileInputStream fin = new FileInputStream(apkFile);
		BufferedInputStream bis = new BufferedInputStream(fin);
		byte[] tempArray = new byte[(int) apkFile.length()];
		bis.read(tempArray, 0, tempArray.length);
		// Send file length first
		Log.d(TAG, "Sending apk length - " + tempArray.length);
		objOut.writeInt(tempArray.length);
		// Send the file
		long startTime = System.nanoTime();
		Log.d(TAG, "Sending apk");
		objOut.write(tempArray);
		objOut.flush();
		long estimatedTime = System.nanoTime() - startTime;
		// The 1000000000 comes from measuring time in nanoseconds
		Double estimatedBandwidth = ((double) tempArray.length / (double) estimatedTime) * 1000000000;
		NetworkProfiler.addNewBandwidthEstimate(estimatedBandwidth);
		Log.d(TAG, tempArray.length + " bytes sent in "
				+ estimatedTime + " ns");
		Log.d(TAG, "Estimated bandwidth - "
				+ NetworkProfiler.bandwidth + " Bps");
		
		bis.close();
	}
	
	/**
	 * Send necessary file to the remote server for offloading param
	 * 
	 * @param fileName
	 *            file name of the file (full path)
	 * @param objOut
	 *            ObjectOutputStream to write the file to
	 * @throws IOException
	 */
	private void sendFile(String fileName, ObjectOutputStream objOut)
			throws IOException {
		File sentFile = new File(fileName);
		FileInputStream fin = new FileInputStream(sentFile);
		BufferedInputStream bis = new BufferedInputStream(fin);
		byte[] tempArray = new byte[(int) sentFile.length()];
		bis.read(tempArray, 0, tempArray.length);
		// Send file length first
		Log.d(TAG, "Sending File length - " + tempArray.length);
		objOut.writeInt(tempArray.length);
		// Send the file
		long startTime = System.nanoTime();
		Log.d(TAG, "Sending File");
		objOut.write(tempArray);
		objOut.flush();
		long estimatedTime = System.nanoTime() - startTime;
		// The 1000000000 comes from measuring time in nanoseconds
		Double estimatedBandwidth = ((double) tempArray.length / (double) estimatedTime) * 1000000000;
		NetworkProfiler.addNewBandwidthEstimate(estimatedBandwidth);
		Log.d(TAG, tempArray.length + " bytes sent in "
				+ estimatedTime + " ns");
		Log.d(TAG, "Estimated bandwidth - "
				+ NetworkProfiler.bandwidth + " Bps");
		
		bis.close();
	}

	/**
	 * Send the object (along with method and parameters) to the remote server
	 * for execution
	 * 
	 * @param o
	 * @param m
	 * @param pValues
	 * @param objOut
	 * @throws IOException
	 */
	private void sendObject(Object o, Method m, Object[] pValues, ObjectOutputStream objOut) throws IOException {
		objOut.reset();
		Log.d(TAG, "Write Object and data");
		Long startSend = System.nanoTime();
		Long startRx = NetworkProfiler.getProcessRxBytes();
		Long startTx = NetworkProfiler.getProcessTxBytes();
		
		objOut.writeObject(o.getClass().getName());
		Log.d(TAG, "Write classname: " + o.getClass().getName());
		
		// Send object for execution
		Gson gson = new Gson();
		//String serial = toString((Serializable)o);
		String objStr = gson.toJson(o);
		Log.d(TAG, "object string : " + objStr);
		objOut.writeObject(objStr);

		// Send the method to be executed
		Log.d(TAG, "Write Method - " + m.getName());
		objOut.writeObject(m.getName());

		Log.d(TAG, "Write method parameter types");
		Class<?>[] paramType = m.getParameterTypes();
		String[] paramTypeName = new String[paramType.length]; 
		for(int i = 0; i < paramType.length; i++){
			Log.d(TAG, "paramTypename " + i + " : " + paramType[i].getName());
			paramTypeName[i] = paramType[i].getName();
	    }
		objOut.writeObject(paramTypeName);

		String pvalueStr = gson.toJson(pValues);
		objOut.writeObject(pvalueStr);

		objOut.flush();

		// Estimate the perceived bandwidth
		NetworkProfiler.addNewBandwidthEstimate(NetworkProfiler
				.getProcessRxBytes()
				- startRx + NetworkProfiler.getProcessTxBytes() - startTx,
				System.nanoTime() - startSend);
	}
	
	/**
	 * Send the object, the method to be executed and parameter values to the
	 * remote server for execution.
	 * 
	 * @param m
	 *            method to be executed
	 * @param pValues
	 *            parameter values of the remoted method
	 * @param o
	 *            the remoted object
	 * @param objIn
	 *            ObjectInputStream which to read results from
	 * @param objOut
	 *            ObjectOutputStream which to write the data to
	 * @return result of the remoted method or an exception that occurs during
	 *         execution
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 */
	private Object sendAndExecute(Method m, Object[] pValues, Object o,
			ObjectInputStream objIn, ObjectOutputStream objOut)
					throws IOException, ClassNotFoundException,
					IllegalArgumentException, SecurityException,
					IllegalAccessException, InvocationTargetException,
					NoSuchMethodException {

		// Send the object itself
		sendObject(o, m, pValues, objOut);

		// Read the results from the server
		Log.d(TAG, "Read Result");
		Long startSend = System.nanoTime();
		Long startRx = NetworkProfiler.getProcessRxBytes();
		Long startTx = NetworkProfiler.getProcessTxBytes();

		Log.d(TAG, "Read Result 1");
		
		String retType = (String) objIn.readObject();
		Log.d(TAG, "response type : " + retType);
		
		if(retType.equals("int")){
			retType = "java.lang.Integer";
		}else if(retType.equals("double")){
			retType = "java.lang.Double";
		}else if(retType.equals("boolean")){
			retType = "java.lang.Boolean";
		}
		
		String retVal = (String) objIn.readObject();
		Log.d(TAG, "response value : " + retVal);
		
		// Estimate the perceived bandwidth
		NetworkProfiler.addNewBandwidthEstimate(NetworkProfiler
				.getProcessRxBytes()
				- startRx + NetworkProfiler.getProcessTxBytes() - startTx,
				System.nanoTime() - startSend);
		
		Gson gson = new Gson();

		Object result = gson.fromJson(retVal, Class.forName(retType));

		return result;
	}

	/**
	 * Take care of a broken connection - try restarting it when something
	 * breaks down immediately or alternativelylisten to changing network
	 * conditions
	 * 
	 * @author MeiCorl
	 * 
	 */
	public class ConnectionRepair extends Thread {
		/**
		 * Simple reestablish the connection
		 */
		@Override
		public void run() {
			// Try simply restarting the connection
			onLine = false;
			Log.d(TAG,"Trying to reestablish connection to the server");
			reestablishConnection();

			// If still offline, establish intent listeners that would try to
			// restart the connection when the service comes back up
			synchronized (this) {
				if (!onLine) {
					Log.d(TAG, "Reestablishing failed - register listeners for reconnecting");
					final ConnectivityManager connectivityManager = (ConnectivityManager) mContext
							.getSystemService(Context.CONNECTIVITY_SERVICE);

					BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
						public void onReceive(Context context, Intent intent) {
							context.unregisterReceiver(this);
							NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
							if (netInfo != null) {
								Log.d(TAG, "Network back up, try reestablishing the connection");
								reestablishConnection();
							} 
						}
					};
					IntentFilter networkStateFilter = new IntentFilter(
							ConnectivityManager.CONNECTIVITY_ACTION);
					mContext.registerReceiver(networkStateReceiver,	networkStateFilter);
				}
			}
		}
	}

	public void setUserChoice(int userChoice) {
		mSolver.setUserChoice(userChoice);
	}
	
	private int getRegime() {
		return mSolver.getRegime();
	}
}