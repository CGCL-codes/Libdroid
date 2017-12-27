/**
 * 
 */
package org.meicorl.unikernel.Scheduler;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Enumeration;

/**
 * @author MeiCorl
 * @date 2017.07.14
 */
public class DBHelper {

	// dirIP: the ip address of the Scheduler
	private static String dirIP = getSchedulerIP();
	/**
	 * MySQL service is running on hostOS,using Xampp (Lampp),default port is 3306
 	 */
	private static final String url = "jdbc:mysql://" + dirIP +":3306/unikernels";
	private static final String name = "com.mysql.jdbc.Driver";
	private static final String user = "MeiCorl";
	private static final String password = "meicorl123";

	private Connection conn = null;
	private Statement dbstate = null;

	public DBHelper() {
		try {
			Class.forName(name);   //ָ 加载数据库驱动
			conn = DriverManager.getConnection(url, user, password);  // 连接数据库
			System.out.println("Database connected");
			dbstate = conn.createStatement();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public ResultSet dbSelect(String sql) 
	{
		try{ 
			ResultSet dbresult = dbstate.executeQuery(sql); 
			return dbresult; 
		}catch(Exception err){ 
			System.out.println("Exception: " + err.getMessage()); 
			return null;
		} 
	}

	public boolean dbDelete(String sql) 
	{
		boolean delResult = false; 
		try{ 
			dbstate.executeUpdate(sql);
			delResult = true; 
		}catch(Exception e){ 
			System.out.println ("Exception: " + e.getMessage()); 
		} 
		if (delResult) 
			return true; 
		else 
			return false; 
	}
	

	public boolean dbUpdate(String sql) 
	{
		boolean updateResult = false;
		try 
		{ 
			dbstate.executeUpdate(sql); 
			updateResult = true; 
		}catch(Exception err){ 
			System.out.println("Exception: " + err.getMessage()); 
		} 
		return updateResult;
	}
	 

	public boolean dbInsert(String sql) 
	{
		boolean insertResult = false;
		try 
		{ 
			dbstate.executeUpdate(sql); 
			insertResult = true; 
		}catch(Exception e){ 
			System.out.println("Exception: " + e.getMessage()); 
		} 
		return insertResult; 
	}

	public boolean dbClose() 
	{ 
		boolean closeResult = false; 
		try 
		{ 
			conn.close(); 
			closeResult = true; 
		}catch(Exception e){ 
			System.out.println("Exception: " + e.getMessage()); 
		} 
		return closeResult;
	}

	/**
	 * get the ip addrress of the Scheduler its self
	 * @author MeiCorl
	 * @data 2017.07.17
	 */
	private static String getSchedulerIP() {
		String localip = null;
		String netip = null;

		Enumeration<NetworkInterface> netInterfaces = null;
		try {
			netInterfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		InetAddress ip = null;
		boolean finded = false;// 是否找到外网IP
		while (netInterfaces.hasMoreElements() && !finded) {
			NetworkInterface ni = netInterfaces.nextElement();
			Enumeration<InetAddress> address = ni.getInetAddresses();
			while (address.hasMoreElements()) {
				ip = address.nextElement();
				if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1)
				{
					netip = ip.getHostAddress();
					finded = true;
					break;
				} else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress()
						&& ip.getHostAddress().indexOf(":") == -1) {
					localip = ip.getHostAddress();
				}
			}
		}
		if (netip != null && !"".equals(netip)) {
			return netip;
		} else {
			return localip;
		}
	}
}
