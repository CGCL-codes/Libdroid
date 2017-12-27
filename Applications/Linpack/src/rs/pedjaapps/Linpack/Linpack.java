package rs.pedjaapps.Linpack;

import java.lang.reflect.Method;

import android.util.Log;
import org.meicorl.unikernel.lib.ExecutionController;
import org.meicorl.unikernel.lib.Remoteable;

public class Linpack extends Remoteable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	transient private static String TAG = "Linpack";
	transient private ExecutionController controller;

	public Linpack(){
		
	}
	
	public Linpack(ExecutionController controller){
		this.controller = controller;
	}
	
	public Result doLinpack(int[] arr) {
		Method toExecute;
		Class<?>[] paramTypes = {int[].class};
		Object[] paramValues = {arr};
		Result result = null;
		try {
			toExecute = this.getClass().getDeclaredMethod("localRunLinpack", paramTypes);
			result = (Result) controller.execute(toExecute, paramValues, this);
		} catch (SecurityException e) {
			// Should never get here
			e.printStackTrace();
			throw e;
		} catch (NoSuchMethodException e) {
			// Should never get here
			e.printStackTrace();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	public Result localRunLinpack(int[] arr){
		Result res = null;
//		Log.d("Hello","Meicorl");
		for(int i=0;i<arr.length;i++)
			res = new JavaLinpack(arr[i] * 100).run_benchmark();
		return res;
	}
	
	@Override
	public void copyState(Remoteable state) {

	}

	public void SayHello(String s){
		System.out.println(s);
	}
}
