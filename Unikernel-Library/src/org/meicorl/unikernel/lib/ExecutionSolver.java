package org.meicorl.unikernel.lib;

import java.util.ArrayList;
import org.meicorl.unikernel.lib.db.DatabaseQuery;
import org.meicorl.unikernel.lib.profilers.NetworkProfiler;

import android.content.Context;
import android.util.Log;


/**
 * ExecutionSolver decides whether to execute the requested method locally or
 * remotely.
 * 
 * (Dummy implementation for now - can set to have static location as well as
 * decide randomly)
 * 
 * @author meicorl
 * 
 */
public class ExecutionSolver {
	
	private static final String TAG	= "ExecutionSolver";
	
	static final int EXECUTION_LOCATION_STATIC_LOCAL	= 1;
	static final int EXECUTION_LOCATION_DYNAMIC			= 2;
	static final int EXECUTION_LOCATION_STATIC_REMOTE	= 3;
	static final int USER_CARES_ONLY_TIME				= 4;
	static final int USER_CARES_ONLY_ENERGY				= 5;
	static final int USER_CARES_TIME_ENERGY				= 6;
	
	public static boolean ShouldOffload;

	private String currentNetworkTypeName;
	private String currentNetworkSubtypeName;
	private ArrayList<String> queryString;
	private DatabaseQuery query;
	private Context context;
	private String classMethodName;
	
	private int regime;
	private int userChoice;

	ExecutionSolver(int regime) {
		this.regime = regime;
		//userChoice = EXECUTION_LOCATION_STATIC_REMOTE;
	}

	/**
	 * Decide whether to execute remotely or locally
	 * 
	 * @return True if need to execute remotely, False if locally
	 */
	boolean executeRemotely(Context context, String classMethodName) {
		
		if (regime == EXECUTION_LOCATION_STATIC_LOCAL || userChoice == EXECUTION_LOCATION_STATIC_LOCAL)
			return false;
		else if(userChoice == EXECUTION_LOCATION_STATIC_REMOTE)
			return true;
		else { // if regime == EXECUTION_LOCATION_DYNAMIC
			
			this.context = context;
			this.classMethodName = classMethodName;
			
			ShouldOffload = getDecision();
			
			if (ShouldOffload) {
				Log.d(TAG, "Execute Remotely - True");
			} else {
				Log.d(TAG, "Execute Remotely - False");
			}		
			
			return ShouldOffload;
		}
	}
	
	/**
	 * 
	 * @return	True if:
	 * 				1. meanExecTimeRemote < meanExecTimeLocal
	 * 				2. The method has never been executed remotely (meanExecTimeRemote == 0)
	 * 						(obviously in this point the connection is available)
	 * 						(the reason is that we want to explore the remote execution)
	 * 			False if:
	 * 				1. meanExecTimeRemote > meanExecTimeLocal
	 * 				2. The method has been executed remotely but never locally (just to have what to compare) 
	 */
	boolean getDecision()
	{
		currentNetworkTypeName = NetworkProfiler.currentNetworkTypeName;
		currentNetworkSubtypeName = NetworkProfiler.currentNetworkSubtypeName;
		long meanExecDurationLocally = 0;
		long meanExecDurationRemotely = 0;
		long meanEnergyConsumptionLocally = 0;
		long meanEnergyConsumptionRemotely = 0;
		
		query = new DatabaseQuery(context);
		
		String selection = "methodName = ? AND execLocation = ? AND " +
		"networkType = ? AND networkSubType = ?";
		String[] selectionArgsLocal = new String[] {classMethodName, "LOCAL", "", ""};
		String[] selectionArgsRemote = new String[] {classMethodName, "REMOTE", 
										currentNetworkTypeName, currentNetworkSubtypeName};
		
		/**
		 * Check if the method has been executed LOCALLY in previous runs
		 */
		queryString = query.getData(new String[] {"execDuration", "energyConsumption"}, selection, 
												selectionArgsLocal, null, null,  "execDuration", " ASC");
		if(!queryString.isEmpty())
		{
			meanExecDurationLocally = Long.parseLong(queryString.get(0));
			meanEnergyConsumptionLocally = Long.parseLong(queryString.get(1));
			queryString.clear();
		}
		
		/**
		 * Check if the method has been executed REMOTELY in previous runnings
		 */
		queryString = query.getData(new String[] {"execDuration", "energyConsumption"}, selection, 
												selectionArgsRemote, null, null,  "execDuration", " ASC");
		if(!queryString.isEmpty())
		{
			meanExecDurationRemotely = Long.parseLong(queryString.get(0));
			meanEnergyConsumptionRemotely = Long.parseLong(queryString.get(1));
			queryString.clear();
		}
		
		Log.d(TAG, "Method execLocal execRemote energyLocal energyRemote: "  + 
				classMethodName + " " + meanExecDurationLocally + " " + meanExecDurationRemotely + " " + 
				meanEnergyConsumptionLocally + " " + meanEnergyConsumptionRemotely);
		
		// Close the database
        try {
			query.destroy();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		if(userChoice == USER_CARES_ONLY_TIME)
		{
			Log.d(TAG, "Making a choice for fast execution");
			return (meanExecDurationRemotely == 0) ? true : (meanExecDurationRemotely < meanExecDurationLocally);
		}
		else if(userChoice == USER_CARES_ONLY_ENERGY)
		{
			Log.d(TAG, "Making a choice to conserve energy");
			return (meanEnergyConsumptionRemotely == 0) ? true : (meanEnergyConsumptionRemotely < meanEnergyConsumptionLocally);
		}
		else
		{
			Log.d(TAG, "Making a choice to conserve energy and fast execution");
			return ((meanExecDurationRemotely == 0) ? true : (meanExecDurationRemotely < meanExecDurationLocally)) &&
					((meanEnergyConsumptionRemotely == 0) ? true : (meanEnergyConsumptionRemotely < meanEnergyConsumptionLocally));
		}
		
		/*Random r = new Random();
		int choice = r.nextInt(1000);
		int threshold = NetworkProfiler.rtt / 1000000 * 2;
		if(choice <= threshold){
			return false;
		}else{
			return true;
		}*/
	}
	
	public void setUserChoice(int userChoice)
	{
		this.userChoice = userChoice;
	}
	
	public void setRegime(int regime)
	{
		this.regime = regime;
	}

	public int getRegime()
	{
		return this.regime;
	}
}
