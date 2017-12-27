package org.meicorl.unikernel.lib.profilers;

/**
 * Log record of the profiler.
 * 
 * Structure of a log record:
 * 
 * Method Name,Execution Location,Execution Duration (nanoseconds), Execution
 * Duration (excl. overheads),Thread CPU time,Instruction Count,Method
 * Invocation Count,Thread Allocation Size,Garbage Collector invocation count
 * (thread),Garbage Collector invocation count (global),Current Network
 * Type,Current Network Subtype,Current RTT,Current Bandwidth, Bytes
 * Received(RX),Bytes Transmitted(TX),Battery Voltage Change,Timestamp
 * 
 * @author Andrius
 * 
 */
public class LogRecord {
	public String methodName;
	public String execLocation;
	public Long execDuration;
	public Long pureDuration;
	public Long energyConsumption;
	public double cpuEnergy;
	public double screenEnergy;
	public double wifiEnergy;
	public double threeGEnergy;
	
	public Long threadCpuTime;
	public int instructionCount;
	public int methodCount;
	public int threadAllocSize;
	public int threadGcInvocationCount;
	public int globalGcInvocationCount;

	public String networkType;
	public String networkSubtype;
	public int rtt;
	public Double bandwidth;
	public Long rxBytes;
	public Long txBytes;

	public Long batteryVoltageChange;
	public Long logRecordTime;

	/**
	 * Collect readings of the different profilers together from the different
	 * running profilers
	 * 
	 * @param progProfiler
	 *            instace of ProgramProfiler
	 * @param netProfiler
	 *            instance of NetworkProfiler
	 * @param devProfiler
	 *            instance of DeviceProfiler
	 */
	public LogRecord(ProgramProfiler progProfiler, NetworkProfiler netProfiler,
			DeviceProfiler devProfiler) {
		methodName = progProfiler.methodName;
		execDuration = progProfiler.execTime;
		threadCpuTime = progProfiler.threadCpuTime;

		instructionCount = progProfiler.instructionCount;
		methodCount = progProfiler.methodInvocationCount;

		threadAllocSize = progProfiler.threadAllocSize;
		threadGcInvocationCount = progProfiler.gcThreadInvocationCount;
		globalGcInvocationCount = progProfiler.gcGlobalInvocationCount;

		networkType = NetworkProfiler.currentNetworkTypeName;
		networkSubtype = NetworkProfiler.currentNetworkSubtypeName;
		rtt = NetworkProfiler.rtt;
		bandwidth = NetworkProfiler.bandwidth;
		if (netProfiler != null) {
			rxBytes = netProfiler.rxBytes;
			txBytes = netProfiler.txBytes;
		} else {
			rxBytes = null;
			txBytes = null;
		}

		batteryVoltageChange = devProfiler.batteryVoltageDelta;
	}

	/**
	 * Convert the log record to string for storing
	 */
	public String toString() {
		logRecordTime = System.currentTimeMillis();
		String progProfilerRecord = methodName + "," + execLocation + ","
				+ execDuration + "," + pureDuration + "," + energyConsumption + "," + 
				cpuEnergy + "," + screenEnergy + "," + wifiEnergy + "," + threeGEnergy
				+ "," + threadCpuTime + ","
				+ instructionCount + "," + methodCount + "," + threadAllocSize
				+ "," + threadGcInvocationCount + "," + globalGcInvocationCount;

		String netProfilerRecord = " , , , , , ";

		if (execLocation == "REMOTE")
			netProfilerRecord = networkType + ", " + networkSubtype + "," + rtt
					+ "," + bandwidth + "," + rxBytes + "," + txBytes;

		String devProfilerRecord = "" + batteryVoltageChange;
		
		return progProfilerRecord + "," + netProfilerRecord + ","
				+ devProfilerRecord + "," + logRecordTime;
		/*String Record = methodName + "," + execLocation + "," + execDuration/1000000 + " ms.";
		return  Record;*/
	}
}
