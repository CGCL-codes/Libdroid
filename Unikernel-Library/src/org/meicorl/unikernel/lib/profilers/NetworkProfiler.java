package org.meicorl.unikernel.lib.profilers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.meicorl.unikernel.lib.ControlMessages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Network information profiler
 * 
 * @author Andrius
 * 
 */
public class NetworkProfiler {
	private static final String TAG = "PowerDroid-Client";
	private static final int bwWindowMaxLength = 20;
	private static final int rttInfinite = 100000000;
	private static final int rttPings = 1;

	public static Double bandwidth = 0.0;
	public static int rtt = rttInfinite;
	public static String currentNetworkTypeName;
	public static String currentNetworkSubtypeName;
	public long rxBytes;
	public long txBytes;

	private Context context;
	private NetworkInfo netInfo;
	private PhoneStateListener listener;
	private TelephonyManager telephonyManager;
	private ConnectivityManager connectivityManager;
	private WifiManager wifiManager;
	private BroadcastReceiver networkStateReceiver;

	private static List<Double> bwWindow = new LinkedList<Double>();
	private static Double mBwSum = 0.0;

	private boolean stopReadingFiles;
	private ArrayList<Long> wifiTxPackets;
	private ArrayList<Long> wifiRxPackets;
	private ArrayList<Long> wifiTxBytes; // uplink data rate
	private ArrayList<Byte> threeGActiveState;
	public static final byte THREEG_IN_IDLE_STATE = 0;
	public static final byte THREEG_IN_FACH_STATE = 1;
	public static final byte THREEG_IN_DCH_STATE  = 2;

	private static int apiVersion;

	public NetworkProfiler(Context context) {
		this.context = context;
		telephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);

		stopReadingFiles = false;
		wifiTxPackets = new ArrayList<Long>();
		wifiRxPackets = new ArrayList<Long>();
		wifiTxBytes = new ArrayList<Long>();
		threeGActiveState = new ArrayList<Byte>();

		apiVersion = Integer.valueOf(android.os.Build.VERSION.SDK);
	}

	/**
	 * Add a calculated bandwidth estimate for averaging the total bandwidth
	 * 
	 * @param estimate
	 *            bytes per second
	 */
	public static void addNewBandwidthEstimate(Double estimate) {
		Log.d(TAG, "New bandwidth estimate - " + estimate);
		if (bwWindow.size() < bwWindowMaxLength) {
			mBwSum += estimate;
			bwWindow.add(estimate);
			bandwidth = mBwSum / bwWindow.size();
		} else {
			mBwSum = mBwSum - bwWindow.get(0) + estimate;
			bwWindow.remove(0);
			bwWindow.add(estimate);
			bandwidth = mBwSum / bwWindow.size();
		}
	}

	/**
	 * Add a new bandwidth estimate for averaging the total bandwidth
	 * 
	 * @param bytes
	 *            number of bytes transmitted
	 * @param time
	 *            time taken for sending (in nanoseconds)
	 */
	public static void addNewBandwidthEstimate(Long bytes, Long time) {
		Log.d(TAG, "Sent - " + bytes + " bytes in " + time + "ns");
		// The 1000000000 comes from measuring time in nanoseconds
		addNewBandwidthEstimate(((double) bytes / (double) time) * 1000000000);
	}

	/**
	 * Doing a few pings on a given connection to measure how big the RTT is
	 * between the client and the remote machine
	 * 
	 * @param in
	 * @param out
	 * @return
	 */
	public static int rttPing(InputStream in, OutputStream out) {
		Log.d(TAG, "Pinging");
		int tRtt = 0;
		int response;
		try {
			for (int i = 0; i < rttPings; i++) {
				Long start = System.nanoTime();
				Log.d(TAG, "Send Ping");
				out.write(ControlMessages.PING);

				Log.d(TAG, "Read Response");
				response = in.read();
				// response = (Integer)in.readObject();
				if (response == ControlMessages.PONG){
					tRtt = (int) (tRtt + (System.nanoTime() - start) / 2);
					Log.d("PowerDroid-Profiler", "Response Ping - " + tRtt / 1000000 + "ms");
				}
				else {
					Log.d(TAG, "Bad Response to Ping - " + response);
					tRtt = rttInfinite;
				}

			}
			rtt = tRtt / rttPings;
			Log.d("PowerDroid-Profiler", "Ping - " + rtt / 1000000 + "ms");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			tRtt = rttInfinite;
		}
		return rtt;
	}

	private int uid;
	private Long startRx;
	private Long startTx;

	/**
	 * Start counting transmitted data at a certain point for the current
	 * process (RX/TX bytes from /sys/class/net/proc/uid_stat)
	 */
	public void startTransmittedDataCounting() {
		uid = android.os.Process.myUid();

		if (apiVersion > 7) {
			startRx = TrafficStats.getUidRxBytes(uid);
			startTx = TrafficStats.getUidTxBytes(uid);
		}
		else {
			try {
				startRx = SysClassNet.getUidRxBytes(uid);
				startTx = SysClassNet.getUidTxBytes(uid);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Sokol: need this only for powerTutor when using Dream phone
		/*
		if(connectivityManager.getActiveNetworkInfo().getTypeName().equals("WIFI"))
			calculateWifiRxTxPackets();
		else
			calculate3GStates();
		*/
	}

	/**
	 * Stop counting transmitted data and store it in the profiler object
	 */
	public void stopAndCollectTransmittedData() {

		synchronized (this) {
			stopReadingFiles = true;
		}
		// Sokol: need this only when using dream
//		calculatePacketRate();
//		calculateUplinkDataRate();

		if (apiVersion > 7) {
			rxBytes = TrafficStats.getUidRxBytes(uid) - startRx;
			txBytes = TrafficStats.getUidTxBytes(uid) - startTx;
		}
		else {
			try {
				rxBytes = SysClassNet.getUidRxBytes(uid) - startRx;
				txBytes = SysClassNet.getUidTxBytes(uid) - startTx;
				Log.d(TAG, "UID: " + uid + " RX bytes: " + rxBytes + " TX bytes: "
						+ txBytes);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Interface to the implementation of system class to retrieve RX bytes of
	 * current process
	 * 
	 * @return RX bytes
	 */
	public static Long getProcessRxBytes() {
		int uid = android.os.Process.myUid();
		try {
			if (apiVersion > 7)
				return TrafficStats.getUidRxBytes(uid);
			else
				return SysClassNet.getUidRxBytes(uid);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1L;
		}
	}

	/**
	 * Interface to the implementation of system class to retrieve TX bytes of
	 * current process
	 * 
	 * @return TX bytes
	 */
	public static Long getProcessTxBytes() {
		int uid = android.os.Process.myUid();
		try {
			if (apiVersion > 7)
				return TrafficStats.getUidTxBytes(uid);
			else
				return SysClassNet.getUidTxBytes(uid);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1L;
		}
	}

	/**
	 * Intent based network state tracking - helps to monitor changing
	 * conditions without the overheads of polling and only updating when needed
	 * (i.e. when something actually has changes)
	 */
	public void registerNetworkStateTrackers() {
		networkStateReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				// context.unregisterReceiver(this);

				netInfo = connectivityManager.getActiveNetworkInfo();
				if (netInfo == null) {
					Log.d("PowerDroid-Profiler", "No Connectivity");
					currentNetworkTypeName = "";
					currentNetworkSubtypeName = "";
				} else {
					Log.d("PowerDroid-Profiler", "Connected to network type "
							+ netInfo.getTypeName() + " subtype "
							+ netInfo.getSubtypeName());
					currentNetworkTypeName = netInfo.getTypeName();
					currentNetworkSubtypeName = netInfo.getSubtypeName();
				}
			}
		};

		Log.d("PowerDroid-Profiler", "Register Connectivity State Tracker");
		IntentFilter networkStateFilter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		context.registerReceiver(networkStateReceiver, networkStateFilter);

		listener = new PhoneStateListener() {
			@Override
			public void onDataConnectionStateChanged(int state, int networkType) {
				if (state == TelephonyManager.DATA_CONNECTED) {
					if (networkType == TelephonyManager.NETWORK_TYPE_EDGE)
						Log.d("PowerDroid-Profiler",
								"Connected to EDGE network");
					else if (networkType == TelephonyManager.NETWORK_TYPE_GPRS)
						Log.d("PowerDroid-Profiler",
								"Connected to GPRS network");
					else if (networkType == TelephonyManager.NETWORK_TYPE_UMTS)
						Log.d("PowerDroid-Profiler",
								"Connected to UMTS network");
					else
						Log.d("PowerDroid-Profiler",
								"Connected to other network - " + networkType);
				} else if (state == TelephonyManager.DATA_DISCONNECTED) {
					Log.d("PowerDroid-Profiler", "Data connection lost");
				} else if (state == TelephonyManager.DATA_SUSPENDED) {
					Log.d("PowerDroid-Profiler", "Data connection suspended");
				}

			}
		};

		Log.d("PowerDroid-Profiler",
				"Register Telephony Data Connection State Tracker");
		telephonyManager.listen(listener,
				PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

	}

	/**
	 * Implementation based on the way the operations are done in Android 2.2,
	 * android.net.TrafficStats class (the underlying C implementation,
	 * actually)
	 * 
	 * @author Andrius
	 * 
	 */
	private static class SysClassNet {

		private static final String SYS_CLASS_NET = "/sys/class/net/";

		private static final String CARRIER = "/carrier";

		private static final String RX_BYTES = "/statistics/rx_bytes";

		private static final String TX_BYTES = "/statistics/tx_bytes";

		private static final String UID_STAT = "/proc/uid_stat/";

		private static final String UID_RX_BYTES = "/tcp_rcv";

		private static final String UID_TX_BYTES = "/tcp_snd";

		/**
		 * Private constructor. This is an utility class.
		 */
		private SysClassNet() {
		}

		@SuppressWarnings("unused")
		public static boolean isUp(String inter) {
			StringBuilder sb = new StringBuilder();
			sb.append(SYS_CLASS_NET).append(inter).append(CARRIER);
			return new File(sb.toString()).canRead();
		}

		@SuppressWarnings("unused")
		public static long getRxBytes(String inter) throws IOException {
			StringBuilder sb = new StringBuilder();
			sb.append(SYS_CLASS_NET).append(inter).append(RX_BYTES);
			return readLong(sb.toString());
		}

		@SuppressWarnings("unused")
		public static long getTxBytes(String inter) throws IOException {
			StringBuilder sb = new StringBuilder();
			sb.append(SYS_CLASS_NET).append(inter).append(TX_BYTES);
			return readLong(sb.toString());
		}

		public static long getUidRxBytes(int uid) throws IOException {
			StringBuilder sb = new StringBuilder();
			sb.append(UID_STAT).append(uid).append(UID_RX_BYTES);
			return readLong(sb.toString());
		}

		public static long getUidTxBytes(int uid) throws IOException {
			StringBuilder sb = new StringBuilder();
			sb.append(UID_STAT).append(uid).append(UID_TX_BYTES);
			return readLong(sb.toString());
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
				Log.d("PowerDroid-Client", "Could not read network data: " + e);
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

		/**
		 * Added functions to get the number of packets sent and received from WiFi and 3G<br>
		 * Based on Android 2.2 implementation for TrafficStats<br>
		 * We calculate the received and transmitted packets for 3G reading the files<br>
		 * "/sys/class/net/rmnet0/statistics/rx_packets" or "/sys/class/net/ppp0/statistics/rx_packets"<br>
		 * "/sys/class/net/rmnet0/statistics/tx_packets" or "/sys/class/net/ppp0/statistics/tx_packets"<br>
		 * 
		 * For WiFi interface we read the files<br>
		 * "/sys/class/net/tiwlan0/statistics/rx_packets" and "/sys/class/net/tiwlan0/statistics/tx_packets"
		 * 
		 * @author Sokol
		 */

		// Return the number from the first file which exists and contains data
		private static long tryBoth(String a, String b) {
			long num = readLong(a);
			return num >= 0 ? num : readLong(b);
		}

		// Note the individual files can come and go at runtime, so we check
		// each file every time (rather than caching which ones exist).

		private static long getMobileTxPackets() {
			return tryBoth(
					"/sys/class/net/rmnet0/statistics/tx_packets",
					"/sys/class/net/ppp0/statistics/tx_packets");
		}

		private static long getMobileRxPackets() {
			return tryBoth(
					"/sys/class/net/rmnet0/statistics/rx_packets",
					"/sys/class/net/ppp0/statistics/rx_packets");
		}

		private static long getMobileTxBytes() {
			return tryBoth(
					"/sys/class/net/rmnet0/statistics/tx_bytes",
					"/sys/class/net/ppp0/statistics/tx_bytes");
		}

		private static long getMobileRxBytes() {
			return tryBoth(
					"/sys/class/net/rmnet0/statistics/rx_bytes",
					"/sys/class/net/ppp0/statistics/rx_bytes");
		}

		private static long getWiFiTxPackets() {
			return readLong("/sys/class/net/tiwlan0/statistics/tx_packets");
		}

		private static long getWiFiRxPackets() {
			return readLong("/sys/class/net/tiwlan0/statistics/rx_packets");
		}

	}

	/**
	 * Get the number of packets tx and rx every second and update the arrays.<br>
	 * 
	 * @author Sokol
	 */
	private void calculateWifiRxTxPackets() {
		Thread t = new Thread() {
			public void run() {

				while (!stopReadingFiles) {

					wifiRxPackets.add( SysClassNet.getWiFiRxPackets() );
					wifiTxPackets.add( SysClassNet.getWiFiTxPackets() );

					try {

						wifiTxBytes.add(SysClassNet.getUidTxBytes(uid));

					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

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

	private void calculatePacketRate()
	{
		for(int i = 0; i < wifiRxPackets.size() - 1; i++)
			wifiRxPackets.set(i, wifiRxPackets.get(i+1) - wifiRxPackets.get(i));

		for(int i = 0; i < wifiTxPackets.size() - 1; i++)
			wifiTxPackets.set(i, wifiTxPackets.get(i+1) - wifiTxPackets.get(i));
	}

	private void calculateUplinkDataRate()
	{
		for(int i = 0; i < wifiTxBytes.size() - 1; i++)
		{
			wifiTxBytes.set(i, wifiTxBytes.get(i+1) - wifiTxBytes.get(i));
		}
	}


	byte timeoutDchFach = 6; 	// Innactivity timer for transition from DCH -> FACH
	byte timeoutFachIdle = 4; 	// Innactivity timer for transition from FACH -> IDLE
	int uplinkThreshold = 151;
	int downlikThreshold = 119;
	byte threegState = THREEG_IN_IDLE_STATE;
	boolean fromIdleState = true;
	boolean fromDchState = false;
	private long prevRxBytes, prevTxBytes;

	private void calculate3GStates()
	{
		Thread t = new Thread() {
			public void run() {

				while (!stopReadingFiles) {

					switch(threegState){
					case THREEG_IN_IDLE_STATE:
						threegIdleState();
						break;
					case THREEG_IN_FACH_STATE:
						threegFachState();
						break;
					case THREEG_IN_DCH_STATE:
						threegDchState();
						break;
					}

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

	private void threegIdleState()
	{
		int dataActivity = telephonyManager.getDataActivity();

		if( dataActivity == TelephonyManager.DATA_ACTIVITY_IN || 
				dataActivity == TelephonyManager.DATA_ACTIVITY_OUT ||
				dataActivity == TelephonyManager.DATA_ACTIVITY_INOUT )
		{ 
			// 3G is in the FACH state because is sending or receiving data
			Log.d("PowerDroid-Energy", "3G in FACH state from IDLE");
			threegState = THREEG_IN_FACH_STATE;
			fromIdleState = true;
			threegFachState();
			return;
		}

		Log.d("PowerDroid-Energy", "3G in IDLE state");
		// 3G is in the IDLE state
		threeGActiveState.add(THREEG_IN_IDLE_STATE);
	}

	private void threegFachState()
	{
		if(fromIdleState || fromDchState)
		{
			// The FACH state is just entered from IDLE or DCH, we should stay here at least 1 second
			// to measure the size of the buffer and in case to transit in DCH in the next second
			fromIdleState = false;
			fromDchState = false;
			prevRxBytes = SysClassNet.getMobileRxBytes();
			prevTxBytes = SysClassNet.getMobileTxBytes();
		}
		else
		{ // 3G was in FACH
			if(timeoutFachIdle == 0)
			{
				Log.d("PowerDroid-Energy", "3G in IDLE state from FACH");
				timeoutFachIdle = 4;
				threegState = THREEG_IN_IDLE_STATE;
				threegIdleState();
				return;
			}
			else if(telephonyManager.getDataActivity() == TelephonyManager.DATA_ACTIVITY_NONE)
			{
				Log.d("PowerDroid-Energy", "3G in FACH state with no data activity");
				timeoutFachIdle--;
			}
			else
				timeoutFachIdle = 4;

			if((SysClassNet.getMobileRxBytes() - prevRxBytes) > downlikThreshold ||
					(SysClassNet.getMobileTxBytes() - prevTxBytes) > uplinkThreshold )
			{
				Log.d("PowerDroid-Energy", "3G in DCH state from FACH");
				timeoutFachIdle = 4;
				threegState = THREEG_IN_DCH_STATE;
				threegDchState();
				return;
			}
		}

		Log.d("PowerDroid-Energy", "3G in FACH state");
		threeGActiveState.add(THREEG_IN_FACH_STATE);
	}

	private void threegDchState()
	{
		if(timeoutDchFach == 0)
		{
			Log.d("PowerDroid-Energy", "3G in FACH state from DCH");
			timeoutDchFach = 6;
			threegState = THREEG_IN_FACH_STATE;
			fromDchState = true;
			threegFachState();
			return;
		}
		else if(telephonyManager.getDataActivity() == TelephonyManager.DATA_ACTIVITY_NONE)
		{
			Log.d("PowerDroid-Energy", "3G in DCH state with no data activity");
			timeoutDchFach--;
		}
		else
			timeoutDchFach = 6;

		Log.d("PowerDroid-Energy", "3G in DCH state");
		threeGActiveState.add(THREEG_IN_DCH_STATE);
	}



	public int getWiFiRxPacketRate(int i)
	{
		return wifiRxPackets.get(i).intValue();
	}

	public int getWiFiTxPacketRate(int i)
	{
		return wifiTxPackets.get(i).intValue();
	}

	public boolean noConnectivity()
	{
		return (connectivityManager.getActiveNetworkInfo())== null;
	}

	public int getLinkSpeed()
	{
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		return wifiInfo.getLinkSpeed();
	}

	public byte get3GActiveState(int i)
	{
		return threeGActiveState.get(i);
	}

	public long getUplinkDataRate(int i)
	{
		return wifiTxBytes.get(i);
	}

	public void onDestroy() {
		this.context.unregisterReceiver(networkStateReceiver);
	}
}
