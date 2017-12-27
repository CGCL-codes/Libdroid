package org.meicorl.unikernel.lib.profilers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.StringTokenizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

/**
 * Device state profiler - currently only tracks battery state, listening to
 * ACTION_BATTERY_CHANGED intent to update the battery level and allows to track
 * change in voltage between two points in a program (where battery voltage
 * readings are taken from /sys/class/power_supply, based on Android OS source)
 * 
 * @author Andrius
 * 
 */
public class DeviceProfiler {
	public static int batteryLevel;
	public static boolean batteryTrackingOn = false;

	/** Not valid value of brightness */
    private static final int NOT_VALID = -1;
    
	public Long batteryVoltageDelta;

	private Context context;
	private Long mStartBatteryVoltage;
	
	/**
	 * Variables for CPU Usage
	 */
	private int PID;
	private boolean stopReadingFiles;
	private ArrayList<Long> pidCpuUsage;
	private ArrayList<Long> systemCpuUsage;
	private long uTime;
	private long sTime;
	private long pidTime;
	private long diffPidTime;
	private long prevPidTime;
	private long userMode;
	private long niceMode;
	private long systemMode;
	private long idleTask;
	private long ioWait;
	private long irq;
	private long softirq;
	private long runningTime;
	private long prevrunningTime;
	private long diffRunningTime;
	private final String pidStatFile;
	private final String statFile;
	private long diffIdleTask;
	private long prevIdleTask;
	private ArrayList<Long> idleSystem;
	private ArrayList<Integer> screenBrightness;
	
	private BroadcastReceiver batteryLevelReceiver;
	
	/**
	 * Variables for CPU frequency<br>
	 * Obtained reading the files:<br>
	 * /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq<br>
	 * /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq<br>
	 * /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq
	 */
	private int currentFreq; // The current frequency
	private ArrayList<Integer> frequence;
	private final String curFreqFile = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
	
	
	public DeviceProfiler(Context context) {
		this.context = context;
		batteryVoltageDelta = null;
		
		initializeVariables();
		
		PID = android.os.Process.myPid();
		
		pidStatFile = "/proc/" + PID + "/stat";
		statFile = "/proc/stat";
		
		synchronized (this) {
			stopReadingFiles = false;
		}
	}

	private void initializeVariables()
	{
		pidCpuUsage = new ArrayList<Long>();
		systemCpuUsage = new ArrayList<Long>();
		idleSystem = new ArrayList<Long>();
		frequence = new ArrayList<Integer>();
		screenBrightness = new ArrayList<Integer>();
	}
	
	/**
	 * Start device information tracking from a certain point in a program
	 * (currently only battery voltage)
	 */
	public void startDeviceProfiling() {
		mStartBatteryVoltage = SysClassBattery.getCurrentVoltage();

		// Sokol: start these only on HTC Dream (G1)
//		calculatePidCpuUsage();
//		calculateScreenBrightness();
	}
	
	public void onDestroy() {
		if (batteryLevelReceiver != null)
			this.context.unregisterReceiver(batteryLevelReceiver);
	}
	
	/**
	 * Stop device information tracking and store the data in the object
	 */
	public void stopAndCollectDeviceProfiling() {
		batteryVoltageDelta = SysClassBattery.getCurrentVoltage()
				- mStartBatteryVoltage;

		synchronized (this) {
			stopReadingFiles = true;
		}
	}

	/**
	 * Computes the battery level by registering a receiver to the intent
	 * triggered by a battery status/level change.
	 */
	public void trackBatteryLevel() {
		if (batteryTrackingOn == false) {
			batteryLevelReceiver = new BroadcastReceiver() {
				public void onReceive(Context context, Intent intent) {
					// context.unregisterReceiver(this);
					int rawlevel = intent.getIntExtra("level", -1);
					int scale = intent.getIntExtra("scale", -1);
					int level = -1;
					if (rawlevel >= 0 && scale > 0) {
						level = (rawlevel * 100) / scale;
					}
					Log.d("PowerDroid-Profiler", "Battery level - " + level
							+ ", voltage - "
							+ SysClassBattery.getCurrentVoltage());
					batteryLevel = level;
				}
			};
			IntentFilter batteryLevelFilter = new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED);
			context.registerReceiver(batteryLevelReceiver, batteryLevelFilter);
			synchronized (this) {
				batteryTrackingOn = true;
			}
		}
	}

	/**
	 * Class exposing battery information, based on battery service and Android
	 * OS implementation
	 * 
	 * @author Andrius
	 * 
	 */
	private static class SysClassBattery {
		private final static String SYS_CLASS_POWER = "/sys/class/power_supply";
		//private final static String BATTERY = "/battery";
		private final static String BATTERY = "/max170xx_battery";
		private final static String VOLTAGE = "/batt_vol";
		private final static String VOLTAGE_ALT = "/voltage_now";

		/**
		 * Read current battery voltage from
		 * /sys/class/power_supply/battery/batt_vol or
		 * /sys/class/power_supply/battery/voltage_now - try both files since it
		 * is done in the battery service of Android, so must be model/version
		 * dependent
		 */
		public static Long getCurrentVoltage() {
			StringBuilder sb = new StringBuilder();
			sb.append(SYS_CLASS_POWER).append(BATTERY).append(VOLTAGE);
			Long result = readLong(sb.toString());
			if (result != -1)
				return result;
			else {
				sb = new StringBuilder();
				sb.append(SYS_CLASS_POWER).append(BATTERY).append(VOLTAGE_ALT);
				result = readLong(sb.toString());
				return result;
			}

		}

		private static RandomAccessFile getFile(String filename)
				throws IOException {
			File f = new File(filename);
			return new RandomAccessFile(f, "r");
		}

		private static long readLong(String file) {
			RandomAccessFile raf = null;
			try {
				raf = getFile(file);
				return Long.valueOf(raf.readLine());
			} catch (Exception e) {
				Log.d("PowerDroid-Client", "Could not read voltage: " + e);
				return -1;
			} finally {
				if (raf != null) {
					try {
						raf.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	/**
	 * Calculate the CPU usage of process every second<br>
	 * These values are registered in the array <b>pidCpuUsage[]</b>
	 * s
	 * @author Sokol
	 */
	private void calculatePidCpuUsage() {
		Thread t = new Thread() {
			public void run() {
				
				boolean firstTime = true;

				while (!stopReadingFiles) {

					calculateProcessExecutionTime();
					calculateSystemExecutionTime();
					getCurrentCpuFreq();

					/**
					 * To prevent errors from the first running don't consider it
					 */
					if (!firstTime) {

						pidCpuUsage.add(diffPidTime);
						systemCpuUsage.add(diffRunningTime);
						
						frequence.add(currentFreq);
						
						idleSystem.add(diffIdleTask);

					}

					prevPidTime = pidTime;
					prevrunningTime = runningTime;
					prevIdleTask = idleTask;
					
					firstTime = false;

					try {

						Thread.sleep(1000);

					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		t.start();
	}

	/**
	 * Open the file "/proc/$PID/stat" and read utime and stime<br>
	 * <b>utime</b>: execution of process in user mode (in jiffies)<br>
	 * <b>stime</b>: execution of process in kernel mode (in jiffies)<br>
	 * These are 14th and 15th variables respectively in the file<br>
	 * The sum <b>pidTime = utime + stime</b> gives the total running time of process<br>
	 * <b>diffPidTime</b> is the running time of process during the last second<br>
	 * 
	 * @author Sokol
	 */
	private void calculateProcessExecutionTime() {
		try {
			
			FileReader inPidStat = new FileReader(pidStatFile);
			BufferedReader brPidStat = new BufferedReader(inPidStat);
			
			String strLine = brPidStat.readLine();

			StringTokenizer st = new StringTokenizer(strLine);
			
			for (int i = 1; i < 14; i++)
				st.nextToken();

			uTime = Long.parseLong(st.nextToken());
			sTime = Long.parseLong(st.nextToken());
			pidTime = uTime + sTime;
			diffPidTime = pidTime - prevPidTime;
			
			brPidStat.close();
			
		} catch (IOException e) {// Catch exception if any
			Log.d("PowerDroid-CpuUsage", "Could not read the file " + pidStatFile);
			stopReadingFiles = true;
		}
		catch (NumberFormatException n) {
			Log.d("PowerDroid-CpuUsage", "Number is not Long");
			stopReadingFiles = true;
		}
		catch (Exception e) {
			Log.d("PowerDroid-CpuUsage", "Some error happened");
			stopReadingFiles = true;
		}
	}

	/**
	 * Open the file "/proc/stat" and read information about system execution<br>
	 * <b>userMode</b>: normal processes executing in user mode (in jiffies)<br>
	 * <b>niceMode</b>: niced processes executing in user mode (in jiffies)<br>
	 * <b>systemMode</b>: processes executing in kernel mode (in jiffies)<br>
	 * <b>idleTask</b>: twiddling thumbs (in jiffies)<br>
	 * <b>runningTime</b>: total time of execution (in jiffies)<br>
	 * <b>ioWait</b>: waiting for I/O to complete (in jiffies)<br>
	 * <b>irq</b>: servicing interrupts (in jiffies)<br>
	 * <b>softirq</b>: servicing softirq (in jiffies)<br>
	 * <b>diffRunningTime</b>: time of execution during the last second (in jiffies)<br>
	 * 
	 * @author Sokol
	 */
	private void calculateSystemExecutionTime() {
		try {

			FileReader inStat = new FileReader(statFile);
			BufferedReader brStat = new BufferedReader(inStat);
			
			String strLine = brStat.readLine();

			StringTokenizer st = new StringTokenizer(strLine);
			st.nextToken();

			userMode = Long.parseLong(st.nextToken());
			niceMode = Long.parseLong(st.nextToken());
			systemMode = Long.parseLong(st.nextToken());
			idleTask = Long.parseLong(st.nextToken());
			ioWait = Long.parseLong(st.nextToken());
			irq = Long.parseLong(st.nextToken());
			softirq = Long.parseLong(st.nextToken());
			
			runningTime = userMode + niceMode + systemMode + idleTask + ioWait + irq + softirq;
			diffRunningTime = runningTime - prevrunningTime;
			diffIdleTask = idleTask - prevIdleTask;

			brStat.close();

		} catch (IOException e) {// Catch exception if any
			Log.d("PowerDroid-CpuUsage", "Could not read the file " + statFile);
			stopReadingFiles = true;
		}
		catch (NumberFormatException n) {
			Log.d("PowerDroid-CpuUsage", "Number is not Long");
			stopReadingFiles = true;
		}
		catch (Exception e) {
			Log.d("PowerDroid-CpuUsage", "Some error happened");
			stopReadingFiles = true;
		}
	}
	
	

	private void getCurrentCpuFreq()
	{
		try {
			FileReader inFreq = new FileReader(curFreqFile);
			BufferedReader brFreq = new BufferedReader(inFreq);
			
			String strLine = brFreq.readLine();
			
			currentFreq = Integer.parseInt(strLine);

			brFreq.close();

		} catch (IOException e) {// Catch exception if any
			Log.d("PowerDroid-CpuUsage", "Could not read the file " + curFreqFile);
			stopReadingFiles = true;
		}
		catch (NumberFormatException n) {
			Log.d("PowerDroid-CpuUsage", "Number is not Integer");
			stopReadingFiles = true;
		}
		catch (Exception e) {
			Log.d("PowerDroid-CpuUsage", "Some error happened");
			stopReadingFiles = true;
		}
	}
	
	/**
	 * For now the implementation is very dummy: is assumed that the screen is always ON
	 * during the execution.
	 * 
	 * TODO: better implementation of this method, account also the fact that the screen
	 * can go off.
	 * 
	 * @author Sokol
	 */
	
	private void calculateScreenBrightness()
	{
		Thread t = new Thread() {
			public void run() {
				
				while (!stopReadingFiles) {
					
					int brightness = Settings.System.getInt(context.getContentResolver(), 
                            Settings.System.SCREEN_BRIGHTNESS, NOT_VALID);

					screenBrightness.add(brightness);
					
					Log.d("PDroid-ScreenBrightness", "Screen brightness: " + brightness);

					try {

						Thread.sleep(1000);

					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		t.start();
	}
	
	public int getSeconds()
	{
		return pidCpuUsage.size();
	}
	
	public long getSystemCpuUsage(int i)
	{
		return systemCpuUsage.get(i);
	}
	
	public long getPidCpuUsage(int i)
	{
		return pidCpuUsage.get(i);
	}
	
	public int getFrequence(int i)
	{
		return frequence.get(i);
	}
	
	public long getIdleSystem(int i)
	{
		return idleSystem.get(i);
	}
	
	public int getScreenBrightness(int i)
	{
		return screenBrightness.get(i);
	}
}
