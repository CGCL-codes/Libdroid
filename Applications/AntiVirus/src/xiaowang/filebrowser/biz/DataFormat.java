package xiaowang.filebrowser.biz;

import java.text.DecimalFormat;

public class DataFormat {
	
	static DecimalFormat df = new DecimalFormat("#0.00");
	
	public static String sizeFormat(long size){
		
		String total=(size*1.0/1024)<1024?df.format(size*1.0/1024)+"KB"
				:(size*1.0/1024/1024)<1024?df.format(size*1.0/1024/1024)+"MB"
						:df.format(size*1.0/1024/1024/1024)+"GB";
		return total;
		
	}
	
	public static String progressFormat(long progress, long size){
		
		String percent=df.format(progress*100.0/size)+"%";
		return percent;
		
	}
	
	public static String timeFormat(String time){
		
		return time.substring(11);
	}
}
