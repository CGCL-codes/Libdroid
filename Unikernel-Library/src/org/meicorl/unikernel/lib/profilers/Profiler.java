package org.meicorl.unikernel.lib.profilers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.meicorl.unikernel.lib.db.DatabaseQuery;

import android.content.Context;
import android.util.Log;

public class Profiler {
	private ProgramProfiler progProfiler;
	private NetworkProfiler netProfiler;
	private DeviceProfiler devProfiler;
	private Context mContext;
	private int mRegime;
	public static final int REGIME_CLIENT = 1;
	public static final int REGIME_SERVER = 2;

	private static final String logFileName = "/sdcard/PowerDroid-performanceLog.txt";
	private static FileWriter logFileWriter;

	private String mLocation;

	public LogRecord lastLogRecord;
	
	private ArrayList<String> queryString;
	private DatabaseQuery query;
	
	private final int MIN_FREQ = 245760; // The minimum frequency for HTC Dream CPU
	private final int MAX_FREQ = 352000; // The maximum frequency for HTC Dream CPU
	private long totalEstimatedEnergy;
	private double estimatedCpuEnergy;
	private double estimatedScreenEnergy;
	private double estimatedWiFiEnergy;
	private double estimated3GEnergy;
	
//	private final int MIN_FREQ = 480000; // The minimum frequency for HTC Hero CPU
//	private final int MAX_FREQ = 528000; // The maximum frequency for HTC Hero CPU

	public Profiler(int regime, Context context, ProgramProfiler progProfiler, NetworkProfiler netProfiler,
			DeviceProfiler devProfiler) {
		this.progProfiler = progProfiler;
		this.netProfiler = netProfiler;
		this.devProfiler = devProfiler;
		this.mContext = context;
		this.mRegime = regime;

		if(mRegime == REGIME_CLIENT)
		{
			this.devProfiler.trackBatteryLevel();

			if (logFileWriter == null)
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

	public void startExecutionInfoTracking() {
		if (netProfiler != null) {
			
			netProfiler.startTransmittedDataCounting();
			mLocation = "REMOTE";
		} else {
			mLocation = "LOCAL";
		}
		Log.d("PowerDroid-Client", mLocation + " " + progProfiler.methodName);
		Log.d("PowerDroid-Profiler", mLocation + " " + progProfiler.methodName);
		progProfiler.startExecutionInfoTracking();
		
		if(mRegime == REGIME_CLIENT)
			devProfiler.startDeviceProfiling();
	}

	/**
	 * Stop running profilers and log current information
	 * 
	 */
	public LogRecord stopAndLogExecutionInfoTracking(Long pureExecTime) {
		
		if(mRegime == REGIME_CLIENT)
			devProfiler.stopAndCollectDeviceProfiling();
		
		progProfiler.stopAndCollectExecutionInfoTracking();
		
		if (netProfiler != null)
			netProfiler.stopAndCollectTransmittedData();

		LogRecord record = new LogRecord(progProfiler, netProfiler, devProfiler);
		record.pureDuration = pureExecTime;
		record.execLocation = mLocation;
		
		lastLogRecord = record;
		
		if(mRegime == REGIME_CLIENT)
		{
//			estimateEnergyConsumption();
		
			record.energyConsumption = totalEstimatedEnergy;
			record.cpuEnergy = estimatedCpuEnergy;
			record.screenEnergy = estimatedScreenEnergy;
			record.wifiEnergy = estimatedWiFiEnergy;
			record.threeGEnergy = estimated3GEnergy;
		
			Log.d("PowerDroid-Profiler", "Log record - " + record.toString());
			try {
				if (logFileWriter == null) {
					File logFile = new File(logFileName);
					logFile.createNewFile(); // Try creating new, if doesn't exist
					logFileWriter = new FileWriter(logFile, true);
				}

				logFileWriter.append(record.toString() + "\n");
				logFileWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			updateDB();
		}
		
		return record;
	}
	
	
	public void updateDB()
	{
		query = new DatabaseQuery(mContext);
		long meanExecDuration;
		long meanEnergyConsumption;
		String[] selectionArgs;
		String selection = "methodName = ? AND execLocation = ? AND " +
							"networkType = ? AND networkSubType = ?";
		
		 // Populate the database
        query.appendData("methodName", lastLogRecord.methodName);
        query.appendData("execLocation", lastLogRecord.execLocation);
        
        if(lastLogRecord.execLocation.equals("REMOTE"))
        {
        	query.appendData("networkType", lastLogRecord.networkType);
        	query.appendData("networkSubType", lastLogRecord.networkSubtype);
        	selectionArgs = new String[] {lastLogRecord.methodName, lastLogRecord.execLocation,
    				lastLogRecord.networkType, lastLogRecord.networkSubtype};
        }
        else
        {
        	query.appendData("networkType", "");
        	query.appendData("networkSubType", "");
        	selectionArgs = new String[] {lastLogRecord.methodName, lastLogRecord.execLocation, "", ""};
        }
		
		/**
		 *  Find previous executions of the method and update as needed:
		 *  calculate the average of the execDuration including the current one
		 *  and update it: for now I think to give more importance to the recent
		 *  measure (exponential smoothing)
		 */
        queryString = query.getData(new String[] {"execDuration", "energyConsumption"}, selection, 
        					selectionArgs , null, null, "execDuration", " ASC");

		if(queryString.isEmpty())
		{
			// The method has NOT been executed previosly, so insert info in the db
	        query.appendData("execDuration", "" + lastLogRecord.execDuration);
	        query.appendData("energyConsumption", "" + lastLogRecord.energyConsumption);
	        query.addRow();
		}
		else
		{
			// The method has been executed, so calculate the average (exponential smooth)
			// and update the entry in db
			meanExecDuration = (lastLogRecord.execDuration + Long.parseLong(queryString.get(0))) / 2;
			meanEnergyConsumption = (lastLogRecord.energyConsumption + Long.parseLong(queryString.get(1))) / 2;
	        query.appendData("execDuration", "" + meanExecDuration);
	        query.appendData("energyConsumption", "" + meanEnergyConsumption);
	        query.updateRow();
		}
		
		queryString.clear();
        
        // Close the database
        try {
			query.destroy();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void estimateEnergyConsumption()
	{
		int duration = devProfiler.getSeconds();
		
		estimatedCpuEnergy = estimateCpuEnergy(duration);
		Log.d("PowerDroid-Energy", "CPU energy: " + estimatedCpuEnergy + " mJ");
		
		estimatedScreenEnergy = estimateScreenEnergy(duration);
		Log.d("PowerDroid-Energy", "Screen energy: " + estimatedScreenEnergy + " mJ");
		
		if(lastLogRecord.execLocation.equals("REMOTE") && lastLogRecord.networkType.equals("WIFI"))
		{
			estimatedWiFiEnergy = estimateWiFiEnergy(duration);
			Log.d("PowerDroid-Energy", "WiFi energy: " + estimatedWiFiEnergy + " mJ");
		}
		else if(lastLogRecord.execLocation.equals("REMOTE") && lastLogRecord.networkType.equals("MOBILE"))
		{
			estimated3GEnergy = estimate3GEnergy(duration);
			Log.d("PowerDroid-Energy", "3G energy: " + estimated3GEnergy + " mJ");
		}
		
		totalEstimatedEnergy = (long) (estimatedCpuEnergy + estimatedScreenEnergy + estimatedWiFiEnergy + estimated3GEnergy);
		
		Log.d("PowerDroid-Energy", "Total energy: " + totalEstimatedEnergy + " mJ");
		Log.d("PowerDroid-Energy", "-------------------------------------------");
	}
	
	/**
	 * Estimate the Power for the CPU every send: P0, P1, P2, ..., Pt<br>
	 * where t is the execution time in seconds.<br>
	 * If we calculate the average power Pm = (P0 + P1 + ... + Pt) / t and multiply<br>
	 * by the execution time we obtain the Energy consumed by the CPU executing the method.<br>
	 * This is: E_cpu = Pm * t which is equal to: E_cpu = P0 + P1 + ... + Pt<br>
	 * NOTE: This is due to the fact that we measure every second.<br>
	 * 
	 * @param duration Duration of method execution
	 * @return The estimated energy consumed by the CPU (mJ)
	 * 
	 * @author Sokol
	 */
	private double estimateCpuEnergy(int duration)
	{
		double estimatedCpuEnergy = 0;
		double betaUh = 4.34;
		double betaUl = 3.42;
		double betaCpu = 121.46;
		byte freqL = 0, freqH = 0;
		int util;
		byte cpuON;
		
		for(int i = 0; i < duration; i++)
		{
			util = calculateCpuUtil(i);
			
			if(devProfiler.getFrequence(i) == MAX_FREQ)
				freqH = 1;
			else
				freqL = 1;

			/**
			 * If the CPU has been in idle state for more than 90 jiffies<br>
			 * then decide to consider it in idle state for all the second
			 * (1 jiffie = 1/100 sec)
			 */
			cpuON = (byte) ((devProfiler.getIdleSystem(i) < 90) ? 1 : 0);
			
			estimatedCpuEnergy += (betaUh*freqH + betaUl*freqL)*util + betaCpu*cpuON;
			
//			Log.d("PowerDroid-Energy", "util freqH freqL cpuON power: " + 
//					util + "  " + freqH + "  " + freqL + "  " + cpuON + 
//					"  " + estimatedCpuEnergy + "mJ");
			
			Log.d("PowerDroid-Energy", "CPU Power: " + estimatedCpuEnergy + "mJ");
			
			freqH = 0;
			freqL = 0;
		}
		
		return estimatedCpuEnergy;
	}
	
	private int calculateCpuUtil(int i)
	{
		return (int)Math.ceil(100 * devProfiler.getPidCpuUsage(i) / 
				devProfiler.getSystemCpuUsage(i));
	}
	
	
	private double estimateScreenEnergy(int duration)
	{
		double estimatedScreenEnergy = 0;
		double betaBrightness = 2.4;
		
		for(int i = 0; i < duration; i++)
			estimatedScreenEnergy += betaBrightness * devProfiler.getScreenBrightness(i);
		
		return estimatedScreenEnergy;
		
	}
	
	
	/**
	 * The WiFi interface can be (mainly) in two states: high_state or low_state<br>
	 * Transition from low_state to high_state happens when packet_rate > 15<br>
	 * packet_rate = (nRxPackets + nTxPackets) / s<br>
	 * RChannel: WiFi channel rate<br>
	 * Rdata: WiFi data rate<br>
	 * 
	 * @param duration Duration of method execution
	 * @return The estimated energy consumed by the WiFi interface (mJ)
	 * 
	 * @author Sokol
	 */
	private double estimateWiFiEnergy(int duration)
	{
		double estimatedWiFiEnergy = 0;
		boolean inHighPowerState = false;
		int nRxPackets, nTxPackets;
		double betaRChannel;
		byte betaWiFiLow = 20;
		double betaWiFiHigh;
		double Rdata;
		
		for(int i = 0; i < duration; i++)
		{
			nRxPackets = netProfiler.getWiFiRxPacketRate(i);
			nTxPackets = netProfiler.getWiFiTxPacketRate(i);
			Rdata = (netProfiler.getUplinkDataRate(i) * 8) / 1000000; // Convert from B/s -> b/s -> Mb/s
			
			// The Wifi interface transits to the high-power state if the packet rate
			// is higher than 15 (according to the paper of PowerTutor)
			// Then the transition to the low-power state is done when the packet rate
			// is lower than 8
			if(!inHighPowerState)
			{
				if( nRxPackets + nTxPackets > 15 )
				{
					inHighPowerState = true;
					betaRChannel = calculateBetaRChannel();
					betaWiFiHigh = 710 + betaRChannel*Rdata;
					estimatedWiFiEnergy += betaWiFiHigh;
				}
				else
					estimatedWiFiEnergy += betaWiFiLow;
			}
			else
			{
				if(nRxPackets + nTxPackets < 8)
				{
					inHighPowerState = false;
					estimatedWiFiEnergy += betaWiFiLow;
				}
				else
				{
					betaRChannel = calculateBetaRChannel();
					betaWiFiHigh = 710 + betaRChannel*Rdata;
					estimatedWiFiEnergy += betaWiFiHigh;
				}
			}
			
//			Log.d("PowerDroid-Energy", "nRxPackets nTxPackets: " + nRxPackets + "  " + nTxPackets);
			Log.d("PowerDroid-Energy", "Wifi Power: " + estimatedWiFiEnergy);
		}
		
		return estimatedWiFiEnergy;
	}
	
	private double calculateBetaRChannel()
	{
		// The Channel Rate of WiFi connection (Mbps)
		int RChannel = netProfiler.getLinkSpeed();
		return 48 - 0.768*RChannel;
	}
	
	/**
	 * In the powerTutor paper the states of 3G interface are three: <b>idle, cell_fach</b> and <b>cell_dch</b><br>
	 * Transition from <b>idle</b> to <b>cell_fach</b> happens if there are data to send or receive<br>
	 * Transition from cell_fach to idle happens if no activity for 4 seconds<br>
	 * Transition from cell_fach to cell_dch happens when uplink_buffer > uplink_queue ore d_b > d_q<br>
	 * Transition from cell_dch to cell_fach happens if no activity for 6 seconds. 
	 * 
	 * @param duration Duration of method execution
	 * @return The estimated energy consumed by the 3G interface (mJ)
	 * 
	 * @author Sokol
	 */
	private double estimate3GEnergy(int duration)
	{
		double estimated3GEnergy = 0;
		int beta3GIdle = 10;
		int beta3GFACH = 401;
		int beta3GDCH = 570;
		
		for(int i = 0; i < duration; i++)
		{
			if(netProfiler.get3GActiveState(i) == NetworkProfiler.THREEG_IN_IDLE_STATE)
				estimated3GEnergy += beta3GIdle;
			else if(netProfiler.get3GActiveState(i) == NetworkProfiler.THREEG_IN_FACH_STATE)
				estimated3GEnergy += beta3GFACH;
			else 
				estimated3GEnergy += beta3GDCH;
		}
		
		return estimated3GEnergy;
	}
}
